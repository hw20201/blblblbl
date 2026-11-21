package com.github.arca.android.data.subscription

import android.content.Context
import com.github.arca.android.data.ConfigManager
import com.github.arca.android.data.ProfileItem
import com.github.arca.android.data.requireSecureRemoteUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** One candidate -> validation -> atomic publication path. This never applies the running VPN. */
object SubscriptionManager {
    private val publication = Mutex()
    private val networkSlots = Semaphore(3)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build()

    suspend fun state(context: Context, id: String): ManagedSubscription? = withContext(Dispatchers.IO) {
        publication.withLock { SubscriptionStore(context).read(id) }
    }

    suspend fun preview(context: Context, drafts: List<SourceDraft>): ImportPreview = withContext(Dispatchers.IO) {
        withTimeout(120_000) {
            require(drafts.isNotEmpty() && drafts.size <= SUBSCRIPTION_MAX_SOURCES) { "请添加 1–20 个来源" }
            val results = coroutineScope {
                drafts.mapIndexed { index, draft -> async {
                    try {
                        val text = draft.text.trim()
                        val remote = draft.remote ?: (draft.mode != "proxy" && !text.contains('\n') &&
                            (text.startsWith("https://", true) || text.startsWith("http://", true)))
                        val source = SubscriptionSource(UUID.randomUUID().toString(),
                            draft.name.cleanLabel(), text.takeIf { remote }, draft.token, draft.mode)
                        val snapshot = fetch(context, source, text.takeUnless { remote })
                        val label = source.name.ifBlank { snapshot.suggestedName }.ifBlank { "来源 ${index + 1}" }
                        snapshot.copy(source = source.copy(name = label)) to null
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        null to ImportDiagnostic(index + 1, 0, "source", "来源 ${index + 1}：${safeFailure(failure)}")
                    }
                } }.awaitAll()
            }
            ImportPreview(results.mapNotNull { it.first }, results.mapNotNull { it.second })
        }
    }

    suspend fun create(context: Context, name: String, preview: ImportPreview,
        standalone: Boolean, allowPartial: Boolean): ProfileItem = withContext(Dispatchers.IO) {
        publication.withLock {
            val title = name.cleanLabel().ifBlank { preview.suggestedName.cleanLabel() }.ifBlank { "聚合配置" }
            require(allowPartial || preview.rejected == 0) { "请先确认仅导入通过校验的项目" }
            val usable = preview.sources.filter { it.inspection.usable }
            require(usable.isNotEmpty()) { "没有可导入的来源" }
            val version = build(context, title, usable, standalone)
            publish(context, ManagedSubscription(profileId = UUID.randomUUID().toString(), current = version), null)
        }
    }

    suspend fun update(context: Context, profile: ProfileItem, sourceId: String? = null): SubscriptionUpdate =
        withContext(Dispatchers.IO) { publication.withLock { withTimeout(120_000) {
            val old = SubscriptionStore(context).read(profile.id) ?: migrate(context, profile)
            val results = coroutineScope {
                old.current.sources.map { snapshot -> async {
                    val selected = sourceId == null || snapshot.source.id == sourceId
                    val shouldRefresh = snapshot.source.enabled && snapshot.source.url != null && selected
                    if (!shouldRefresh) {
                        snapshot to SourceUpdate(snapshot.source.id, snapshot.source.name, "保持快照")
                    } else try {
                        val fresh = fetch(context, snapshot.source, null, snapshot)
                        require(fresh.inspection.usable && fresh.inspection.diagnostics.isEmpty()) { "候选存在无效项目" }
                        require(!largeDrop(snapshot.inspection.nodes.size, fresh.inspection.nodes.size)) { "节点减少超过 50%，请重新导入预览后确认" }
                        if (old.current.standalone) require(fresh.inspection.completeValid) { "完整配置未通过校验" }
                        fresh to SourceUpdate(snapshot.source.id, snapshot.source.name, fresh.status)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        val message = "${safeFailure(failure)} · 沿用旧版"
                        snapshot.copy(status = message, retained = true, attemptedAt = System.currentTimeMillis()) to
                            SourceUpdate(snapshot.source.id, snapshot.source.name, message, true)
                    }
                } }.awaitAll()
            }
            val candidate = build(context, profile.name, results.map { it.first }, old.current.standalone, old.current.nodeNames)
            val changed = candidate.content != old.current.content
            val next = old.copy(current = candidate, previous = if (changed) old.current else old.previous)
            publish(context, next, profile)
            SubscriptionUpdate(changed, results.map { it.second })
        } } }

    /** Reconfiguration uses existing checked snapshots; adding new sources requires another preview. */
    suspend fun reconfigure(context: Context, profile: ProfileItem, sources: List<SubscriptionSource>,
        additions: ImportPreview? = null, allowPartial: Boolean = false): ProfileItem = withContext(Dispatchers.IO) {
        publication.withLock {
            val old = SubscriptionStore(context).read(profile.id) ?: error("该配置没有来源版本")
            require(sources.map { it.id }.distinct().size == sources.size) { "来源重复" }
            require(additions == null || additions.rejected == 0 || allowPartial) { "请先确认仅导入通过校验的项目" }
            val snapshots = sources.map { source ->
                val saved = old.current.sources.firstOrNull { it.source.id == source.id } ?: error("来源不存在")
                require(saved.source.url == source.url) { "更换地址请通过重新导入预览" }
                saved.copy(source = source.copy(name = source.name.cleanLabel()))
            } + additions?.sources.orEmpty().filter { it.inspection.usable }
            require(snapshots.size in 1..SUBSCRIPTION_MAX_SOURCES) { "请保留 1–20 个来源" }
            val relabeled = snapshots.filter { snapshot ->
                val saved = old.current.sources.firstOrNull { it.source.id == snapshot.source.id }
                saved != null && saved.source.prefix != snapshot.source.prefix
            }.flatMap { it.inspection.nodes }.map { SubscriptionAggregator.fingerprint(it.data) }.toSet()
            val names = old.current.nodeNames.filterKeys { it !in relabeled }
            val candidate = build(context, profile.name, snapshots, old.current.standalone, names)
            publish(context, old.copy(current = candidate, previous = old.current), profile)
        }
    }

    suspend fun rollback(context: Context, profile: ProfileItem): ProfileItem = withContext(Dispatchers.IO) {
        publication.withLock {
            val old = SubscriptionStore(context).read(profile.id) ?: error("尚无可回滚版本")
            val previous = old.previous ?: error("尚无可回滚版本")
            require(SubscriptionValidator.inspect(context, previous.content).completeValid) { "上一版本已不兼容当前核心，未替换配置" }
            publish(context, old.copy(current = previous.copy(name = profile.name), previous = old.current), profile)
        }
    }

    /** Default replacements stay locally editable; other profiles keep versioned rollback. */
    suspend fun replace(context: Context, profile: ProfileItem, text: String): ProfileItem {
        val preview = preview(context, listOf(SourceDraft(profile.name, text, remote = false)))
        require(preview.failures.isEmpty() && preview.rejected == 0) { "内容未通过校验，请使用导入预览查看逐项原因" }
        return withContext(Dispatchers.IO) { publication.withLock {
            val standalone = preview.sources.single().inspection.completeValid
            val version = build(context, profile.name, preview.sources, standalone)
            if (profile.id == ConfigManager.DEFAULT_PROFILE_ID) {
                check(ConfigManager.saveProfileContent(context, profile.id, version.content)) { "默认配置保存失败" }
                ConfigManager.getOrCreateDefaultProfile(context)
            } else {
                val oldVersion = SubscriptionStore(context).read(profile.id)?.current ?: migrate(context, profile).current
                publish(context, ManagedSubscription(profileId = profile.id, current = version, previous = oldVersion), profile)
            }
        } }
    }

    internal fun largeDrop(before: Int, after: Int): Boolean = before > 0 && after.toLong() * 2 < before

    private suspend fun migrate(context: Context, profile: ProfileItem): ManagedSubscription {
        val text = profile.file.inputStream().use { readBounded(it) }
        val inspection = SubscriptionValidator.inspect(context, text)
        require(inspection.usable && inspection.diagnostics.isEmpty()) { "现有配置无法建立可用快照，请先重新导入" }
        val source = SubscriptionSource(UUID.randomUUID().toString(), profile.name.cleanLabel(), profile.url)
        val version = build(context, profile.name, listOf(SourceSnapshot(source, text, inspection)), inspection.completeValid)
        return ManagedSubscription(profileId = profile.id, current = version)
    }

    private suspend fun build(context: Context, name: String, sources: List<SourceSnapshot>, standalone: Boolean,
        names: Map<String, String> = emptyMap()): SubscriptionVersion {
        require(sources.sumOf { it.text.toByteArray(Charsets.UTF_8).size.toLong() } <= 30L * 1024 * 1024) { "来源总内容超过 30 MiB" }
        if (standalone) {
            val source = sources.singleOrNull() ?: error("独立配置只能保留一个来源")
            require(source.source.enabled && source.inspection.completeValid) { "完整配置未通过校验" }
            return SubscriptionVersion(name, true, sources, source.inspection.complete, nodeCount = source.inspection.nodes.size)
        }
        val generated = SubscriptionAggregator.generate(sources, names)
        require(SubscriptionValidator.inspect(context, generated.yaml).completeValid) { "生成配置未通过核心校验；请检查依赖或改为独立配置" }
        return SubscriptionVersion(name, false, sources, generated.yaml, nodeNames = generated.names,
            nodeCount = generated.nodes, duplicateCount = generated.duplicates)
    }

    private suspend fun publish(context: Context, state: ManagedSubscription, existing: ProfileItem?): ProfileItem {
        currentCoroutineContext().ensureActive()
        if (existing != null) check(ConfigManager.hasProfile(context, state.profileId)) { "配置已删除" }
        val target = SubscriptionStore(context).write(state)
        val info = state.current.sources.singleOrNull()?.userInfo?.let(ConfigManager::parseSubscriptionUserInfo)
        val item = (existing ?: ProfileItem(state.profileId, state.current.name, null, target)).copy(
            name = state.current.name, file = target, url = null, lastUpdated = state.current.generatedAt,
            uploadBytes = info?.upload ?: 0, downloadBytes = info?.download ?: 0,
            totalBytes = info?.total ?: 0, expireTimestamp = info?.expire ?: 0, managed = true,
            canRefresh = state.current.sources.any { it.source.url != null })
        ConfigManager.updateProfile(context, item)
        return item
    }

    suspend fun delete(context: Context, id: String) = withContext(Dispatchers.IO) {
        publication.withLock { ConfigManager.deleteProfile(context, id) }
    }

    private suspend fun fetch(context: Context, source: SubscriptionSource, local: String?, previous: SourceSnapshot? = null): SourceSnapshot {
        if (source.url == null) {
            val text = local ?: previous?.text ?: error("没有本地内容")
            return SourceSnapshot(source, text, SubscriptionValidator.inspect(context, text, source.inputMode),
                suggestedName = source.name)
        }
        val fetched = networkSlots.withPermit { download(source, previous) }
        if (fetched.notModified) return requireNotNull(previous).copy(status = "未变化 · HTTP 304", retained = false,
            attemptedAt = System.currentTimeMillis())
        return SourceSnapshot(source, fetched.text, SubscriptionValidator.inspect(context, fetched.text, source.inputMode),
            etag = fetched.etag, lastModified = fetched.modified, userInfo = fetched.userInfo, status = "已更新",
            suggestedName = fetched.suggestedName)
    }

    private data class Download(val text: String = "", val etag: String? = null, val modified: String? = null,
        val userInfo: String? = null, val notModified: Boolean = false, val suggestedName: String = "")

    private suspend fun download(source: SubscriptionSource, previous: SourceSnapshot?): Download = coroutineScope {
        requireSecureRemoteUrl(requireNotNull(source.url))
        var request = Request.Builder().url(source.url).header("User-Agent", ConfigManager.subscriptionUserAgent()).apply {
            if (source.token.isNotBlank()) {
                require(source.url.startsWith("https://", true)) { "设备令牌只允许通过 HTTPS 发送" }
                require(!source.token.contains('\n') && !source.token.contains('\r')) { "设备令牌格式无效" }
                header("Authorization", "Bearer ${source.token}")
            }
            previous?.etag?.let { header("If-None-Match", it) }
            previous?.lastModified?.let { header("If-Modified-Since", it) }
        }.build()
        for (redirect in 0..5) {
            currentCoroutineContext().ensureActive()
            val call = client.newCall(request)
            // Unconfined runs only this cancellation hook, so blocked body reads close immediately.
            val cancellation = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                call.awaitResponse().use { response ->
                    if (response.code in listOf(301, 302, 303, 307, 308)) {
                        require(redirect < 5) { "重定向次数过多" }
                        val destination = request.url.resolve(response.header("Location").orEmpty()) ?: error("重定向地址无效")
                        requireSecureRemoteUrl(destination.toString())
                        if (source.token.isNotBlank()) require(sameOrigin(request.url, destination)) { "带设备凭据的下载不能跳转到其他站点" }
                        request = request.newBuilder().url(destination).build()
                    } else {
                        if (response.code == 304) {
                            require(previous != null) { "缺少缓存，无法使用 HTTP 304" }
                            return@coroutineScope Download(notModified = true)
                        }
                        require(response.isSuccessful) { when (response.code) {
                            401, 403 -> "授权已失效或被撤销，请更新设备令牌"
                            else -> "来源返回 HTTP ${response.code}"
                        } }
                        val body = response.body ?: error("来源内容为空")
                        require(body.contentLength() <= SUBSCRIPTION_MAX_BYTES) { "内容超过 10 MiB 限制" }
                        val text = body.byteStream().use { readBounded(it) }
                        val suggestedName = ConfigManager.extractProfileName(source.name, response, text, source.url).cleanLabel()
                        return@coroutineScope Download(text, response.header("ETag"), response.header("Last-Modified"),
                            response.header("subscription-userinfo"), suggestedName = suggestedName)
                    }
                }
            } finally {
                cancellation.cancel()
            }
        }
        error("重定向次数过多")
    }

    internal fun sameOrigin(a: HttpUrl, b: HttpUrl): Boolean = a.scheme == b.scheme && a.host == b.host && a.port == b.port

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(IOException("来源连接失败，请检查网络后重试"))
            }
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response, onCancellation = { _, value, _ -> value.close() })
            }
        })
    }

    /** Exception text may contain credentials. Only fixed, app-owned messages can reach the UI. */
    internal fun safeFailure(failure: Exception): String {
        val message = failure.message.orEmpty()
        val allowed = listOf("内容超过", "来源返回 HTTP", "授权已失效", "来源连接失败", "节点减少超过", "候选存在",
            "完整配置未通过", "来源未返回", "设备令牌只允许", "设备令牌格式", "带设备凭据", "重定向次数", "校验进程已",
            "无法启动独立", "独立校验", "内容未通过", "内容或节点", "核心校验接口", "筛选后没有", "生成配置未通过", "独立配置只能",
            "该配置没有", "尚无可回滚", "上一版本已", "来源总内容", "来源快照超过", "请添加", "请保留", "没有可导入",
            "请先确认", "请为配置", "配置已删除", "无法读取订阅", "现有配置无法", "订阅与脚本地址必须")
        return if (allowed.any { message.startsWith(it) }) message else "获取或校验失败，请检查来源格式、网络和核心兼容性"
    }

    private fun String.cleanLabel() = filterNot { it.isISOControl() }.trim().take(100)
}
