# Rust DataStore 库选型调研

核对日期为 2026-11-07。目标是复用现成库，保留 Flare 原生端现有 Protobuf 文件，Web 使用 IndexedDB，面向 iOS、Android、macOS、Windows、Linux 和浏览器 Wasm。应用通过 Rust ViewModel 使用存储。

后续筛选优先考虑由组织或团队维护的 Tokio 与官方 Tauri 项目。

本次核对的候选中，成熟组件可以承担共享状态和订阅，但持久化顺序、失败回滚、取消后的提交通知及现有文件兼容仍需要由 Flare 的存储层协调。尚未找到仅接入 Buffa 编解码即可覆盖上述要求且已有广泛采用的通用库。

| 候选 | 已提供的能力 | Flare 需要承担的部分 |
| --- | --- | --- |
| Tokio `watch` | 多订阅者、最新状态、独立观察进度 | Protobuf、文件和 IndexedDB、事务顺序、初始化、句柄作用域 |
| `eyeball` | `SharedObservable`、共享值、标准 Stream 订阅、去重更新 | 持久化和提交后的状态发布 |
| `futures-signals` | `Mutable`、Signal、map/dedupe/组合、Stream 转换 | 持久化和提交后的状态发布 |
| Leptos `reactive_stores` | 结构体字段级响应式状态、`Store`、`ArcStore` | 持久化，以及 reactive_graph 的运行和生命周期接入 |
| 官方 `tauri-plugin-store` | 文件存储、应用内共享、变更事件、自定义序列化 | Tauri 运行时接入、类型转换、IndexedDB 后端及事务语义调整 |
| 非官方 `tauri-store` | 响应式键值存储、跨窗口同步、自定义 marshaler | Tauri 运行时接入、存储后端与提交顺序调整 |
| `amethystate` | 持久化字段、订阅、派生状态、多个内置引擎、durable API | 自定义 Protobuf/IndexedDB 后端、Serde 模型适配、发布顺序调整 |

Tokio 的 `watch` 保留最新值，适合观察配置状态。`sync` 模块的同步原语可以运行于其他异步运行时，仅使用这些原语不要求采用 Tokio 的任务执行器。[watch](https://docs.rs/tokio/latest/tokio/sync/watch/index.html)、[运行时兼容性](https://docs.rs/tokio/latest/tokio/sync/index.html#runtime-compatibility)

`eyeball::SharedObservable` 提供 Stream 订阅和去重设置，`subscribe_reset` 可以把当前值作为订阅起点。源码仓库已迁往 Codeberg，旧 GitHub 路径的可访问性不能单独作为停止维护的证据。[API](https://docs.rs/eyeball/0.8.8/eyeball/)、[原仓库迁移说明](https://github.com/jplatte/eyeball/tree/notice)、[新仓库](https://codeberg.org/jplatte/eyeball)

`futures-signals` 提供与 UI 框架独立的 `Mutable`、只读观察句柄和派生 Signal。`SignalExt` 支持 map、dedupe 和 to_stream，项目 README 展示了浏览器 dominator 的使用路径。它本身不定义事务性持久化。[Mutable](https://docs.rs/futures-signals/0.3.34/futures_signals/signal/struct.Mutable.html)、[SignalExt](https://docs.rs/futures-signals/0.3.34/futures_signals/signal/trait.SignalExt.html)、[项目 README](https://github.com/Pauan/rust-signals)

Leptos 的 `reactive_stores` 基于 `reactive_graph`，支持嵌套字段和集合元素的精细响应式跟踪。它承担的是内存状态观察，持久化仍由应用接入。对当前使用 Crux 的 Flare，接入成本需要包含另一套响应式图的运行和生命周期管理。[reactive_stores API](https://docs.rs/reactive_stores/0.4.4/reactive_stores/)

官方 `tauri-plugin-store` 面向 Tauri 的 `AppHandle` 和 `Runtime`。序列化回调的输入输出是 `HashMap<String, serde_json::Value>`，接入 Buffa 需要处理类型表示与未知字段保留。平台列表覆盖五个原生目标，浏览器独立 Wasm 不在该列表中。[官方文档](https://v2.tauri.app/plugin/store/)、[回调类型及 StoreBuilder](https://docs.rs/tauri-plugin-store/latest/src/tauri_plugin_store/store.rs.html)

进一步核对使用发布版 `tauri-plugin-store 2.5.0`，发布包对应插件仓库提交 `a2364a5f216324439feedeb25b2db74e7b1eba90`，依赖解析得到 `tauri 2.12.5`。Rust API 可以通过 `AppHandle` 调用，同一路径会复用 Tauri App 资源表中的 Store。零窗口 Tauri App 可以构建并初始化插件，Store 自身也支持完全通过 Rust 操作。现有原生宿主仍需接入 Tauri App、运行时、路径服务和事件系统。[发布版 StoreBuilder](https://github.com/tauri-apps/plugins-workspace/blob/a2364a5f216324439feedeb25b2db74e7b1eba90/plugins/store/src/store.rs#L48)、[Tauri App 构建](https://github.com/tauri-apps/tauri/blob/tauri-v2.12.5/crates/tauri/src/app.rs#L2408)

Android 的 Tauri App 构建会注册 path 核心插件，随后通过 JNI 调用 Activity 的 `getPluginManager()`。Flare 当前的 `MainActivity` 继承 `ComponentActivity`，接入还需要 Tauri 的 Android 初始化和 PluginManager 环境。[path 插件初始化](https://github.com/tauri-apps/tauri/blob/tauri-v2.12.5/crates/tauri/src/path/plugin.rs#L231)、[JNI PluginManager 调用](https://github.com/tauri-apps/tauri/blob/tauri-v2.12.5/crates/tauri/src/plugin/mobile.rs#L208)、[Flare Activity](/Users/tlaster/.codex/worktrees/65d1/Flare/app/src/main/java/dev/dimension/flare/MainActivity.kt:30)

发布版 Store 的 iOS 依赖会显式启用 Tauri `wry`，即使关闭默认功能也会引入该运行时。Tao 在 iOS 创建事件循环时要求主线程并安装主循环观察器，其标准 `run()` 会调用 `UIApplicationMain`，并拒绝在已经启动 UIApplication 的进程中再次运行。macOS 的事件循环创建还会设置 NSApplication delegate。因此已有 SwiftUI/UIKit 生命周期的应用需要单独验证与适配。[Store iOS 依赖](https://github.com/tauri-apps/plugins-workspace/blob/a2364a5f216324439feedeb25b2db74e7b1eba90/plugins/store/Cargo.toml)、[Tao iOS](https://docs.rs/crate/tao/0.37.0/source/src/platform_impl/ios/event_loop.rs)、[Tao macOS](https://docs.rs/crate/tao/0.37.0/source/src/platform_impl/macos/event_loop.rs)

独立临时工程关闭 Store 默认功能后，对 `wasm32-unknown-unknown` 的首次编译遇到 getrandom 配置错误。补齐浏览器随机数功能及 backend 配置后，仍被 Tauri 引入的 Tokio `rt-multi-thread` 功能阻断。Tauri 的该依赖为固定启用，Store 的序列化回调也只能返回字节，磁盘访问固定使用 `std::fs`，浏览器版本仍需 IndexedDB 后端。单独使用 Tokio `sync/watch` 的方案可以避开多线程运行时依赖。[Tauri Tokio 依赖](https://github.com/tauri-apps/tauri/blob/tauri-v2.12.5/crates/tauri/Cargo.toml#L44)、[Wasm 编译日志](/private/tmp/flare-tauri-store-probe.CTPlO8/wasm-configured-check.log)、[Tokio Wasm 支持](https://docs.rs/tokio/1.54.1/tokio/#wasm-support)

它的 `set` 先修改内存并发送变更事件，再触发自动保存。`save` 使用 `fs::write`。因此自定义 serializer 不能独自提供 Flare 所需的提交成功后发布、失败保留旧快照以及原子文件替换。[set、save 与自动保存源码](https://docs.rs/tauri-plugin-store/latest/src/tauri_plugin_store/store.rs.html)

临时工程使用 Tauri 官方 `test` 功能的 MockRuntime，执行了三项通过的行为探针。第一项把现有 Kotlin Protobuf fixture 作为完整字节放入单个 `payload` 键，通过自定义 codec 原样写回，并确认同路径得到同一个 `Arc<Store>`。第二项在关闭自动保存后确认 `set` 已发通知，随后制造文件系统写入失败，缓存仍保留新值。第三项确认首次 `build()` 会忽略反序列化错误并返回空缓存，而显式 `reload()` 会报错。由此可确认旧文件兼容可以实现，事务与错误语义仍需要适配。[探针源码](/private/tmp/flare-tauri-store-probe.CTPlO8/tests/behavior.rs)、[测试日志](/private/tmp/flare-tauri-store-probe.CTPlO8/host-test.log)、[发布版初次加载路径](https://github.com/tauri-apps/plugins-workspace/blob/a2364a5f216324439feedeb25b2db74e7b1eba90/plugins/store/src/store.rs#L232)

MockRuntime 是官方 `test` 功能下的单元测试工具，上述结果只验证 Rust API、字节兼容与存储行为。实际 SwiftUI/UIKit/Compose 宿主与生产 Tauri 运行时共存的行为尚待验证。[官方测试模块说明](https://docs.rs/tauri/2.12.5/tauri/test/)

非官方 `tauri-store` 支持自定义 `Marshaler`，但公开状态表示仍围绕 JSON。源码 `on_state_change` 先发送事件和调用 watcher，随后执行保存策略，即便选择 immediate 保存也沿用这个顺序。存储对象持有 Tauri `AppHandle`，普通浏览器 IndexedDB 需要额外实现。[自定义 Marshaler](https://tb.dev.br/tauri-store/guide/custom-serialization)、[状态和保存源码](https://docs.rs/tauri-store/latest/src/tauri_store/store/mod.rs.html)

`rpstate` 已迁移为 `amethystate`。核对时 rpstate 的 11 个已发布版本全部被 yanked，crates.io 的 `max_stable_version` 为 null。后续评估应使用当前 amethystate，避免依赖搜索结果中的旧 rpstate 0.5.0 文档。[rpstate 元数据](https://crates.io/api/v1/crates/rpstate)、[amethystate 元数据](https://crates.io/api/v1/crates/amethystate)

amethystate 当前稳定版为 0.29.0。核对源码固定于 `0e25d911e688e9b91221cd7c96d8fccf83baf2aa`。其内置引擎包括 redb、SQLite、JSON、TOML、RON 和浏览器 localStorage。标准 `StoreBuilder` 通过内置 `Backend` 枚举选择引擎。[README](https://github.com/guinea-rs/amethystate/blob/0e25d911e688e9b91221cd7c96d8fccf83baf2aa/README.md)、[Backend 枚举](https://github.com/guinea-rs/amethystate/blob/0e25d911e688e9b91221cd7c96d8fccf83baf2aa/crates/main/amethystate/src/store/builder.rs#L37)

它公开了同步和异步 backend trait，不过这些接口以 Serde 类型、结构化路径、前缀扫描和删除为契约。直接接入原来的整份 Protobuf 消息和异步 IndexedDB，需要实现存储与模型适配。[backend traits](https://github.com/guinea-rs/amethystate/blob/0e25d911e688e9b91221cd7c96d8fccf83baf2aa/crates/core/amethystate-core/src/backend.rs)、[异步订阅 backend](https://github.com/guinea-rs/amethystate/blob/0e25d911e688e9b91221cd7c96d8fccf83baf2aa/crates/core/amethystate-core/src/async_impl/backend.rs)

默认字段写入进入内存缓冲并触发响应式更新。`durable().set` 和 `set_async` 先执行 `write`，再等待 commit。redb 后端先入缓冲、安排 flush 并调用 `emit_events`，所以等待 durable 返回不能保证订阅者只看到已提交值。采用前需要调整发布时序和写失败后的状态行为。项目 README 也明确说明，约一半引擎尚无真实应用使用。[字段写入及 durable 源码](https://github.com/guinea-rs/amethystate/blob/0e25d911e688e9b91221cd7c96d8fccf83baf2aa/crates/main/amethystate/src/reactive/field.rs#L554)、[redb 写入及事件顺序](https://github.com/guinea-rs/amethystate/blob/0e25d911e688e9b91221cd7c96d8fccf83baf2aa/crates/main/amethystate/src/store/backend/redb/mod.rs#L677)、[项目使用情况说明](https://github.com/guinea-rs/amethystate/blob/0e25d911e688e9b91221cd7c96d8fccf83baf2aa/README.md)

`confy` 适用于 Serde 配置文件的加载和保存，默认格式围绕 TOML、YAML、RON。公开 API 没有 DataStore 式状态订阅，因此它更适合配置文件管理。[confy API](https://docs.rs/confy/2.0.0/confy/)

以下为 crates.io API 在核对日期返回的 `recent_downloads`。下载量包含 CI 和传递依赖，能辅助判断采用规模，不能直接等同于独立项目数量。

| crate | 当前稳定版 | 该版本发布日期 | recent_downloads |
| --- | --- | --- | --- |
| [tokio](https://crates.io/api/v1/crates/tokio) | 1.54.1 | 2026-10-21 | 295,951,413 |
| [tauri-plugin-store](https://crates.io/api/v1/crates/tauri-plugin-store) | 2.5.0 | 2026-09-26 | 1,576,622 |
| [reactive_stores](https://crates.io/api/v1/crates/reactive_stores) | 0.4.4 | 2026-09-26 | 1,519,642 |
| [eyeball](https://crates.io/api/v1/crates/eyeball) | 0.8.8 | 2024-06-22 | 534,748 |
| [confy](https://crates.io/api/v1/crates/confy) | 2.0.0 | 2025-10-27 | 211,099 |
| [futures-signals](https://crates.io/api/v1/crates/futures-signals) | 0.3.34 | 2024-07-26 | 97,517 |
| [tauri-store](https://crates.io/api/v1/crates/tauri-store) | 1.2.3 | 2026-07-22 | 25,300 |
| [amethystate](https://crates.io/api/v1/crates/amethystate) | 0.29.0 | 2026-10-21 | 4,069 |

Tokio 仓库当日约 33,705 stars，futures-signals 仓库为 785 stars，amethystate 仓库为 2 stars。Leptos 仓库的 21,472 stars 属于整个框架，不能单独归属于 reactive_stores。[Tokio 元数据](https://api.github.com/repos/tokio-rs/tokio)、[futures-signals 元数据](https://api.github.com/repos/Pauan/rust-signals)、[amethystate 元数据](https://api.github.com/repos/guinea-rs/amethystate)、[Leptos 元数据](https://api.github.com/repos/leptos-rs/leptos)

Matrix Rust SDK 使用 `SharedObservable` 保存房间状态，同时提供独立的 StateStore 持久化抽象。响应处理中的 `save_and_apply` 先 await `save_changes`，成功后执行 `apply_changes` 并广播房间更新。这为存储和观察组件组合使用提供了直接实例。[房间状态](https://github.com/matrix-org/matrix-rust-sdk/blob/main/crates/matrix-sdk-base/src/room/mod.rs)、[save_and_apply](https://github.com/matrix-org/matrix-rust-sdk/blob/main/crates/matrix-sdk-base/src/response_processors/changes.rs)、[StateStore](https://github.com/matrix-org/matrix-rust-sdk/blob/main/crates/matrix-sdk-base/src/store/mod.rs)

按照组织维护优先的要求，Flare 当前架构优先采用 Tokio watch 处理状态订阅，由现有 Protobuf 文件和 IndexedDB 实现负责提交。官方 Tauri Store 适合已有 Tauri App 运行时的应用，Flare 接入还需要原生宿主生命周期、Web 后端、提交时序和初次读取错误处理的适配。

本轮完成了文档、源码和发布元数据核对，并运行了 Tauri Store 的 macOS MockRuntime 探针及浏览器目标编译探针。六平台真实宿主集成、系统升级和崩溃恢复行为尚待验证。
