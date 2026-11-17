package com.github.arca.android.data

/** Response bodies of the public page above this size are failures (checked after gzip decoding). */
internal const val SCAMALYTICS_WEB_MAX_BYTES = 1024 * 1024

private val RISK_BANDS = setOf("low", "medium", "high", "very high")
private val UNKNOWN_CELL_VALUES = setOf("", "n/a", "na", "null", "nil", "unknown", "none", "-")

private val NON_CONTENT = Regex(
    "<!--[\\s\\S]*?(?:-->|$)|<script\\b[\\s\\S]*?(?:</script\\s*>|$)|<style\\b[\\s\\S]*?(?:</style\\s*>|$)|<noscript\\b[\\s\\S]*?(?:</noscript\\s*>|$)",
    RegexOption.IGNORE_CASE
)
private val TAG = Regex("<[^>]*>")
private val TITLE = Regex("<title\\b[^>]*>([\\s\\S]*?)(?:</title\\s*>|$)", RegexOption.IGNORE_CASE)
private val HEADING = Regex("<h[1-3]\\b[^>]*>([\\s\\S]*?)</h[1-3]\\s*>", RegexOption.IGNORE_CASE)
private val PRE_BLOCK = Regex("<(pre|code)\\b[^>]*>([\\s\\S]*?)</\\1\\s*>", RegexOption.IGNORE_CASE)
private val TABLE_ROW = Regex("<tr\\b[^>]*>([\\s\\S]*?)(?=</tr\\s*>|<tr\\b|</table\\s*>|$)", RegexOption.IGNORE_CASE)
private val CELL_START = Regex("<t[hd]\\b[^>]*>", RegexOption.IGNORE_CASE)
private val ENTITY = Regex("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z]{2,8});")
private val WHITESPACE = Regex("\\s+")
private val CHALLENGE_MARKUP = Regex(
    "challenge-form|g-recaptcha|h-captcha|hcaptcha|cf-turnstile|cf-browser-verification|cf-captcha-container|challenge-running",
    RegexOption.IGNORE_CASE
)
private val CHALLENGE_TEXT = listOf(
    "just a moment", "attention required", "captcha", "verify you are human", "checking your browser",
    "access denied", "are you a robot", "are you human"
)
private val SCORE_BANNER = Regex("Fraud\\s+Score\\s*:\\s*([^\\s<]{1,16})", RegexOption.IGNORE_CASE)
private val JSON_SCORE = Regex("\"score\"\\s*:\\s*(?:\"([^\"]*)\"|([^,}\\s]+))")
private val JSON_RISK = Regex("\"risk\"\\s*:\\s*\"([^\"]*)\"")
private val JSON_IP = Regex("\"ip\"\\s*:\\s*\"([^\"]*)\"")
private val RISK_SEGMENT = Regex("^(low|medium|high|very high) risk$", RegexOption.IGNORE_CASE)
private val IPV4_TOKEN = Regex("(?<![0-9A-Za-z.])(?:\\d{1,3}\\.){3}\\d{1,3}(?![0-9A-Za-z]|\\.\\d)")
private val IPV6_TOKEN = Regex("(?<![0-9A-Za-z:.])[0-9A-Fa-f]*:[0-9A-Fa-f:.]*(?![0-9A-Za-z:])")

private enum class Check { DATACENTER, VPN, TOR, SERVER, PUBLIC_PROXY, WEB_PROXY, ROBOT, RESIDENTIAL,
    FIREHOL, IP2PROXY_LITE, IPSUM, SPAMHAUS, X4BNET_SPAMBOT }

private val CHECK_LABELS: Map<String, Check> = mapOf(
    "datacenter" to Check.DATACENTER, "datacenter/hosting" to Check.DATACENTER, "data center" to Check.DATACENTER,
    "anonymizing vpn" to Check.VPN,
    "tor exit node" to Check.TOR,
    "server" to Check.SERVER,
    "public proxy" to Check.PUBLIC_PROXY,
    "web proxy" to Check.WEB_PROXY,
    "search engine robot" to Check.ROBOT,
    "residential proxy" to Check.RESIDENTIAL,
    "firehol" to Check.FIREHOL,
    "ip2proxylite" to Check.IP2PROXY_LITE, "ip2proxy lite" to Check.IP2PROXY_LITE,
    "ipsum" to Check.IPSUM,
    "spamhaus" to Check.SPAMHAUS,
    "x4bnet spambot" to Check.X4BNET_SPAMBOT
)

/** Tag-stripped, entity-decoded, whitespace-collapsed text of an HTML fragment. */
private fun textOf(fragment: String): String =
    decodeHtmlEntities(fragment.replace(TAG, " ")).replace('\u00A0', ' ').replace(WHITESPACE, " ").trim()

/** Single-pass decoding so "&amp;nbsp;" stays the literal text "&nbsp;". */
internal fun decodeHtmlEntities(text: String): String = ENTITY.replace(text) { match ->
    val body = match.groupValues[1]
    when {
        body.startsWith("#x") || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)?.let(::codePointText)
        body.startsWith("#") -> body.substring(1).toIntOrNull()?.let(::codePointText)
        else -> when (body.lowercase()) {
            "nbsp" -> " "; "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
            else -> null
        }
    } ?: match.value
}

private fun codePointText(code: Int): String? =
    if (code in 1..0x10FFFF && code !in 0xD800..0xDFFF) String(Character.toChars(code)) else null

private sealed interface Parsed<out T> {
    data object Invalid : Parsed<Nothing>
    data class Value<T>(val value: T) : Parsed<T>
}

/** Only a plain integer 0-100 is a score; anything else ("abc", "101", "-1", "1.5") is invalid. */
private fun scoreToken(token: String): Parsed<Int> {
    val value = token.trim().takeIf { Regex("\\d{1,3}").matches(it) }?.toIntOrNull()
    return if (value != null && value in 0..100) Parsed.Value(value) else Parsed.Invalid
}

private fun ipTokens(text: String): List<String> =
    IPV4_TOKEN.findAll(text).map { it.value }.toList() +
        IPV6_TOKEN.findAll(text).map { it.value.trimEnd('.') }.filter { it.validIp() != null }.toList()

private fun containsIp(text: String, requestedIp: String): Boolean =
    ipTokens(text).any { sameIpAddress(it, requestedIp) }

private fun normalizedLabel(label: String): String =
    label.lowercase().trim().trimEnd(':').trim().replace(Regex("\\s*/\\s*"), "/").replace(WHITESPACE, " ")

/**
 * Parses the public Scamalytics web page for one IP (https://scamalytics.com/ip/<ip>).
 *
 * Script, style, noscript and comments are discarded first. Checks are read from real table rows
 * (`<tr>` with `<th>`/`<td>` cells: label cell -> explicit Yes/No cell). The fraud score is accepted
 * only from the "Fraud Score:" banner text or the embedded JSON `score` field (a `<pre>`/`<code>`
 * block), only as an integer 0-100; the risk band only from the JSON `risk` field or a standalone
 * "<Band> Risk" label. Missing or unrecognised values stay null. Captcha/challenge pages, empty
 * pages, pages for another IP and pages with neither a valid score nor a single explicit check are
 * rejected, so stale or invented data can never appear as a result.
 */
private fun emptyWebPage(): Nothing = throw IpLookupException("Scamalytics 网页返回了空页面。")

private fun webPageTooLarge(): Nothing = throw IpLookupException("Scamalytics 网页返回的内容过大，已丢弃。")

private fun webPageChallenge(): Nothing = throw IpLookupException("Scamalytics 网页需要人机验证或拒绝访问，暂时无法读取。")

internal fun parseScamalyticsWebPage(html: String, requestedIp: String): ScamalyticsInfo {
    if (html.isBlank()) emptyWebPage()
    if (html.length > SCAMALYTICS_WEB_MAX_BYTES) webPageTooLarge()
    val content = html.replace(NON_CONTENT, " ")
    val text = textOf(content)
    val title = TITLE.find(content)?.groupValues?.get(1)?.let(::textOf).orEmpty()

    val lowered = (title + " " + text).lowercase()
    if (CHALLENGE_MARKUP.containsMatchIn(content) || CHALLENGE_TEXT.any { it in lowered }) {
        webPageChallenge()
    }
    if (text.isEmpty()) emptyWebPage()

    // Structured data blocks (embedded JSON) are only read from pre/code elements.
    val jsonText = PRE_BLOCK.findAll(content).joinToString("\n") {
        decodeHtmlEntities(it.groupValues[2].replace(TAG, ""))
    }

    // --- IP identity: the page must be about the requested IP, on token boundaries. ---
    val zones = buildList {
        add(title)
        HEADING.findAll(content).forEach { add(textOf(it.groupValues[1])) }
        JSON_IP.findAll(jsonText).forEach { add(it.groupValues[1]) }
    }.joinToString(" \n ")
    val identityText = if (ipTokens(zones).isNotEmpty()) zones else text
    if (!containsIp(identityText, requestedIp)) {
        throw IpLookupException("Scamalytics 返回的 IP 与本次检测不匹配。")
    }

    // --- score: banner text and/or embedded JSON; anything invalid or conflicting is unknown. ---
    val scoreStates = buildList<Parsed<Int>> {
        SCORE_BANNER.findAll(text).forEach { add(scoreToken(it.groupValues[1])) }
        JSON_SCORE.findAll(jsonText).forEach { add(scoreToken(it.groupValues[1].ifEmpty { it.groupValues[2] })) }
    }
    val scores = scoreStates.filterIsInstance<Parsed.Value<Int>>().map { it.value }.distinct()
    val score = if (scoreStates.any { it is Parsed.Invalid } || scores.size != 1) null else scores.single()

    // --- risk band: embedded JSON first; otherwise a standalone "<Band> Risk" label, if unambiguous. ---
    val jsonRisks = JSON_RISK.findAll(jsonText).map { it.groupValues[1].trim().lowercase() }.toList()
    val risk = if (jsonRisks.isNotEmpty()) {
        jsonRisks.distinct().singleOrNull()?.takeIf { it in RISK_BANDS }
    } else {
        content.split(TAG).map { textOf(it) }
            .mapNotNull { RISK_SEGMENT.matchEntire(it)?.groupValues?.get(1)?.lowercase() }
            .distinct().singleOrNull()
    }

    // --- table rows: label cell -> explicit Yes/No cell; duplicates must agree. ---
    val explicit = HashMap<Check, MutableSet<Boolean>>()
    val facts = HashMap<String, String>()
    for (row in TABLE_ROW.findAll(content)) {
        val cells = row.groupValues[1].split(CELL_START).drop(1).map(::textOf)
        if (cells.size < 2) continue
        val label = normalizedLabel(cells[0])
        CHECK_LABELS[label]?.let { check ->
            when (cells[1].lowercase()) {
                "yes" -> explicit.getOrPut(check) { mutableSetOf() }.add(true)
                "no" -> explicit.getOrPut(check) { mutableSetOf() }.add(false)
            }
        }
        if (label == "isp name" || label == "organization name") facts.putIfAbsent(label, cells[1])
    }
    fun check(which: Check): Boolean? = explicit[which]?.singleOrNull()
    fun fact(label: String): String? = facts[label]?.takeIf { it.lowercase() !in UNKNOWN_CELL_VALUES }

    val hostingProxy = listOf(
        ScamalyticsIndicator("Scamalytics", "数据中心 / Hosting", check(Check.DATACENTER)),
        ScamalyticsIndicator("Scamalytics", "VPN", check(Check.VPN)),
        ScamalyticsIndicator("Scamalytics", "Tor", check(Check.TOR)),
        ScamalyticsIndicator("Scamalytics", "服务器", check(Check.SERVER)),
        ScamalyticsIndicator("Scamalytics", "公开代理", check(Check.PUBLIC_PROXY)),
        ScamalyticsIndicator("Scamalytics", "网页代理", check(Check.WEB_PROXY)),
        ScamalyticsIndicator("Scamalytics", "搜索引擎机器人", check(Check.ROBOT)),
        ScamalyticsIndicator("Scamalytics", "住宅代理", check(Check.RESIDENTIAL))
    )
    val blacklistMarkers = listOf(
        ScamalyticsIndicator("FireHOL", "黑名单", check(Check.FIREHOL)),
        ScamalyticsIndicator("IP2Proxy Lite", "黑名单", check(Check.IP2PROXY_LITE)),
        ScamalyticsIndicator("IPsum", "黑名单", check(Check.IPSUM)),
        ScamalyticsIndicator("Spamhaus", "黑名单", check(Check.SPAMHAUS)),
        ScamalyticsIndicator("X4Bnet Spambot", "黑名单", check(Check.X4BNET_SPAMBOT))
    )
    if (score == null && hostingProxy.all { it.detected == null } && blacklistMarkers.all { it.detected == null }) {
        throw IpLookupException("Scamalytics 网页解析失败，请重试。")
    }

    return ScamalyticsInfo(
        ipAddress = requestedIp,
        hostingProxySignals = hostingProxy,
        blacklistMarkers = blacklistMarkers,
        blacklistCount = null,
        ip2proxyBlacklistType = null,
        proxyType = null,
        proxyTypeSource = null,
        proxyTypeRequiresPremium = false,
        usageType = null,
        score = score,
        risk = risk,
        mode = "web",
        creditsRemaining = null,
        isp = fact("isp name"),
        organization = fact("organization name")
    )
}
