# Rust 应用初始化

UI host 加载 Rust 动态库或 Wasm 后，调用统一异步入口，等待成功后再创建依赖 Rust 应用模块的页面。Rust 内部构造共享的 `AppServices`，host 不逐个初始化模块。

本阶段提供初始化入口和绑定，生产 host 的启动流程仍使用原来的 Kotlin 实现。整体切换到 Rust 时，再启用下面的调用，避免并行运行两套 datastore。

## 宿主调用

原生底层入口由现有 BoltFFI 生成，类型生成同时提供异步等待适配。Android 使用应用的 `filesDir`：

```kotlin
import dev.dimension.flare.rust.InitConfig
import dev.dimension.flare.rust.initialize

// 在协程中调用；成功后再进入依赖 Rust 模块的页面。
initialize(InitConfig(androidFilesDir = application.filesDir.absolutePath))
```

Windows/Linux 的 JVM host 使用同一 Kotlin 入口，`androidFilesDir` 传 `null`。桌面 JNI 动态库及资源打包需要由桌面 host 的构建流程提供。

iOS/macOS 由 Rust 解析应用数据目录：

```swift
import FlareRust

try await initialize(config: InitConfig(androidFilesDir: nil))
```

原生调用会抛出生成的 `InitError`。Android 的 `androidFilesDir` 必须是绝对路径；其他平台必须留空。配置验证通过并被接受后，即使启动失败也只能使用相同配置重试。

Web 先生成 ES module、Wasm 和 TypeScript 声明：

```sh
cd rust
rustup target add wasm32-unknown-unknown
just web-bindings
```

把 `target/web` 的产物一起部署，浏览器中调用：

```typescript
import loadWasm, { initialize } from './flare.js';

await loadWasm();
await initialize();
```

Web 使用默认浏览器配置，初始化失败会拒绝 Promise 并返回 JavaScript `Error`。每个 Wasm 实例拥有自己的应用上下文；不同标签页的数据共享和通知仍由 datastore 的 IndexedDB 后端负责。

## 生命周期和错误

- 同时调用会共享一轮初始化及其结果。
- 成功后再次调用相同配置，复用原来的 `AppServices`。
- 取消某个 host 的等待只结束这次等待，已启动的 Rust 初始化继续执行。
- 一轮失败后，等待该轮的调用者都收到该轮错误；下一次调用可以启动新一轮重试。
- 原生后台任务中断后可重试。错误不会触发清空或重建已有数据。
- 全部必要模块构造成功后才发布 `AppServices`。当前只打开 datastore，具体 Protobuf 记录在首次读取或订阅时解码。

上下文的生命周期为原生进程或 Wasm 实例。窗口、页面和 Activity 重建不会重建已经成功初始化的模块。持久化写入沿用 datastore 自身的提交规则，不能依赖应用退出回调才保存数据。

## 增加 Rust 模块

初始化实现位于 `flare-core/src/application.rs`。新增模块时，在 `AppServices` 中添加明确类型的字段，在 `AppServices::build` 中按依赖顺序构造。构造函数直接接收所需依赖，完成后才返回共享上下文。

Rust ViewModel 或应用工厂取得初始化结果后，把依赖传给业务模块：

```rust
use flare_core::application::services;

let app = services()?;
let datastore = app.datastore.clone();
```

`services()` 在初始化成功前返回 `InitError::NotInitialized`。该函数和 `AppServices` 只供 Rust 使用，不导出给 host。页面状态由各自的 ViewModel 实例持有。

原生应用层持有一个共享 Tokio executor，并启用编译时可用的 driver；Web 使用 `wasm_bindgen_futures::spawn_local`。需要定时器或网络 I/O 的模块接入时，在原生依赖中启用相应的 Tokio 功能即可；datastore 的跨平台同步原语配置保持独立。

## 绑定和验证

Apple 使用 `just swift-bindings`，Android 沿用现有 Gradle 构建中的生成步骤。`codegen` 会把 `bindings/Initialization.swift` 或 `Initialization.kt` 加入生成产物。

原生绑定使用 `begin_initialize` 和 Rust 持有的 `InitializationObserver` 完成回调。Swift/Kotlin 外层提供 `initialize` 异步接口，并在取消时结束等待，晚到的结果不会再次恢复已结束的等待。这样不需要由 host 取消或释放 Rust future 句柄。BoltFFI 0.32.1 的直接 async 导出在本次 Swift 取消验证中出现过轮询与释放并发导致的崩溃，因此初始化不采用该路径。

Swift 的回调表使用初始化后只读、地址稳定的进程级存储，以通过 Swift 6 并发检查并满足 Rust 保留表指针的生命周期要求。生成流程中的兼容脚本检查 BoltFFI 0.32.1 的表定义形状；升级依赖时如果模板变化会报错，需要重新核对。

```sh
cargo test --locked --workspace
cargo clippy --locked --workspace --all-targets -- -D warnings
```

浏览器测试使用 `wasm-bindgen-test-runner` 和 Chrome，分别运行 `flare-core` 的 `application_web`、`flare-ffi` 的 `web` 和 `flare-datastore` 的 `web` 集成测试。

`tests/bindings/SwiftInitializationSmoke.swift` 和 `KotlinInitializationSmoke.kt` 用于 macOS 上真实 Swift 6 / JVM JNI 绑定验证。它们与生成的绑定一起编译，接收两个参数：新的临时目录，以及 `crates/flare-datastore/tests/fixtures/app_settings.pb` 路径。运行进程的 `CFFIXED_USER_HOME` 必须等于第一个参数。每次运行检查结构化错误、失败重试、取消、64 次并发初始化，以及旧 Protobuf 文件保持不变。

`KotlinInitializationWaiterTest.kt` 单独与 `bindings/Initialization.kt`、Kotlin 标准库和 coroutines 一起编译，不包含生成的 JNI 绑定。运行 `dev.dimension.flare.rust.KotlinInitializationWaiterTestKt`，通过延迟的回调检查取消后调用方可以释放、已取消的协程不会启动初始化，以及晚到的成功和失败回调安全结束。

原生与 Web 的交叉检查需分开执行；同一个 Cargo 命令同时选择这些目标会合并 Tokio 功能，导致原生的 `rt-multi-thread` 被带入 Wasm。
