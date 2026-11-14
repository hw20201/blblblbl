# Glasmio（试验接入）

本分支通过 Maven Central 依赖 `io.github.neoruaa.glasmio:glasmio-glass:0.1.3` 调用 Glasmio 官方玻璃材质渲染器；本地未复制其实现源码。官方项目及 API 说明见 https://github.com/NEORUAA/Glasmio 。

Glasmio 材质运行需要 Android 13（API 33）或更高版本。Arca 的 `minSdk 26` 未调整；低于 API 33 时仍走原有渲染路径。发布或复制本依赖时，请一并保留本目录的 `LICENSE` 与 `THIRD_PARTY_NOTICES.md`，并遵守其中 Apache-2.0 及第三方资源说明。
