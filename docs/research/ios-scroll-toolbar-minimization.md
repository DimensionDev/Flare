# iOS 27 随滚动收起导航栏的接入调查

核查及接入日期：2026-09-10。本文记录官方 API、项目接入位置及验证结果。

## 结论

iOS 27 确实新增了导航栏随滚动最小化的 API。当前 SwiftUI 名称是 `toolbarMinimizationBehavior(_:for:)`，常用配置为 `.toolbarMinimizationBehavior(.onScrollDown, for: .navigationBar)`。官方文档明确支持的 placement 是 `.navigationBar`；与导航栏集成的顶部 tab bar 也会一起最小化。不能据此推断任意自定义顶栏、`safeAreaInset` 或底部 tab bar 都会一起消失。[SwiftUI API](https://developer.apple.com/documentation/swiftui/view/toolbarminimizationbehavior(_:for:))

Flare 已将该行为推广为浏览页面的默认策略。共享的 `ScrollMinimizingNavigationBar` modifier 在 iOS 27 起生效，较早 iOS 及 macOS 返回原内容；传入 `enabled: false` 会在 iOS 27 明确使用 `.never`。底部 tab bar 使用原有配置，安全区调整和恢复时机使用系统默认值。[共享组件](../../appleApp/Shared/FlareAppleCore/Sources/FlareAppleUI/ScrollMinimizingNavigationBar.swift#L3)

## 当前页面策略

| 页面 | 行为 |
| --- | --- |
| 时间线、通知、动态 / 图库详情、搜索、文章、RSS、用户列表、设置列表、草稿列表、聊天记录列表 | 随滚动收起 |
| 首页普通列表与图库 | 随滚动收起 |
| 首页 Deck | `.never`，保留现有多列布局 |
| 发布、登录、举报、反应 / 分享 / 账户选择、Tab 编辑、用户列表编辑 | `.never` |
| 个人页、私信会话、AI 会话与分析会话 | `.never`，保留现有头部及消息滚动逻辑 |
| 全屏媒体查看器 | `.never`，继续使用原有隐藏导航栏及自定义控制层 |
| 独立图库信息、日志详情弹窗和二级菜单 | 随滚动收起 |
| 独立编辑 / 选择弹窗，包括 RSS、OPML、分组、Tab、图标、过滤器、列表成员、AI / 翻译配置、ALT、Emoji、发布时的草稿选择 | `.never` |

`Route.view` 是底部 Tab、侧栏、push 目标、深链接、路由 sheet 和 full-screen cover 的共同页面工厂，在此统一附加 modifier 并维护例外。首页自行选择普通时间线和 Deck 的策略，避免叠加两个互相竞争的配置。独立 `NavigationStack` / `NavigationView` 的内容则显式配置，防止遗漏没有经过路由工厂的弹窗。[统一入口与例外](../../appleApp/ios/UI/Route/Route.swift#L53)、[首页 Deck](../../appleApp/ios/UI/Screen/HomeTimelineScreen.swift#L88)、[首页时间线](../../appleApp/ios/UI/Screen/HomeTimelineScreen.swift#L225)

## API 名称与版本

| 场景 | 当前 API | iOS 最低版本 |
| --- | --- | --- |
| SwiftUI 导航栏随滚动最小化 | `toolbarMinimizationBehavior(_:for:)` | 27.0 |
| SwiftUI 最小化时安全区变化 | `toolbarMinimizationSafeAreaAdjustment(_:for:)` | 27.0 |
| SwiftUI 最小化后恢复时机 | `toolbarMinimizationRestoration(_:for:)` | 27.0 |
| UIKit 导航栏最小化配置 | `UINavigationItem.navigationBarMinimization` | 27.0 |
| SwiftUI tab bar 最小化 | `tabBarMinimizeBehavior(_:)` | 26.0 |
| UIKit 旧滑动隐藏行为 | `UINavigationController.hidesBarsOnSwipe` | 8.0 |

版本已对照本机 Xcode 27.0（27A266a）的 `iPhoneOS27.0.sdk` 核实：SwiftUI 的三个新 modifier 标注 `@available(anyAppleOS 27.0, *)`，其 `.onScrollDown`、`.onScrollUp` 等选项不能用于 macOS、tvOS、watchOS、visionOS。UIKit 27 的声明及 Swift overlay 也已核实。来源为 Apple 随 SDK 分发的 `SwiftUI.swiftinterface`、`UIBarMinimization.h`、`UINavigationController.h`。

**不要直接照抄 WWDC 初版命名。** WWDC26 SwiftUI 示例仍用 `toolbarMinimizeBehavior`，UIKit 视频也展示早期属性；iOS 27 release notes 明确说明 SwiftUI 已改为 `toolbarMinimizationBehavior`（177954148），UIKit 已改为 `navigationBarMinimization`，替代早期的 `barMinimizeBehavior`、`barMinimizationSafeAreaAdjustment`（177953926）。查询时部分官方页面仍标 Beta，本文使用当前文档与本机 SDK 相互印证的名称。[发布说明](https://developer.apple.com/documentation/ios-ipados-release-notes/ios-ipados-27-release-notes)、[WWDC26 SwiftUI 示例](https://developer.apple.com/videos/play/wwdc2026/269/?time=457)、[WWDC26 UIKit 介绍](https://developer.apple.com/videos/play/wwdc2026/278/)

## SwiftUI 共享组件的使用

从 `FlareAppleUI` 使用共享 modifier；普通路由页面会由 `Route.view` 自动配置。独立的导航内容可以直接使用：

```swift
// 阅读页面
.modifier(ScrollMinimizingNavigationBar())

// 编辑页面或有独立滚动逻辑的页面
.modifier(ScrollMinimizingNavigationBar(enabled: false))
```

将它加到 `NavigationStack` 内、定义该页面导航标题和 toolbar 的内容视图上，这与官方示例结构一致。组件内部执行 iOS 27 availability 判断，接入方不直接暴露新系统类型。[官方 SwiftUI 示例](https://developer.apple.com/documentation/swiftui/toolbarminimizationbehavior)

行为选项：

- `.onScrollDown`：向下浏览内容时最小化；`.onScrollUp` 使用相反的滚动方向；`.never` 禁用最小化。[行为定义](https://developer.apple.com/documentation/swiftui/toolbarminimizationbehavior)
- `.automatic` 由系统决定，官方目前说明导航栏默认在 `searchable` 使用 `.toolbarPrincipal` 位置时最小化。因此要稳定启用首页滚动收起，应显式使用 `.onScrollDown`。[automatic](https://developer.apple.com/documentation/swiftui/toolbarminimizationbehavior/automatic)
- 默认在反向滚动时恢复。只有希望用户回到内容滚动边缘才恢复时，才加 `.toolbarMinimizationRestoration(.atScrollEdge, for: .navigationBar)`；当前仅支持 `.navigationBar` 配合 `.onScrollDown`。[恢复行为](https://developer.apple.com/documentation/swiftui/view/toolbarminimizationrestoration(_:for:))
- 默认安全区随导航栏最小化而调整，使内容能使用腾出的空间。`.toolbarMinimizationSafeAreaAdjustment(.disabled, for: .navigationBar)` 会保持安全区不变，适合需要保持内容位置的全屏媒体；不能因为项目使用 UIKit 或 `.ignoresSafeArea` 就直接认定应禁用。[安全区行为](https://developer.apple.com/documentation/swiftui/view/toolbarminimizationsafeareaadjustment(_:for:))

官方将效果描述为导航栏随着滚动交互式滑出。本文使用“收起 / 最小化”描述，没有证据保证所有配置下每个导航按钮都完全消失。[WWDC26 UIKit](https://developer.apple.com/videos/play/wwdc2026/278/)

## UIKit 及 SwiftUI 混合视图

纯 UIKit 页面可配置自己的 `navigationItem`：

```swift
if #available(iOS 27.0, *) {
    navigationItem.navigationBarMinimization.minimizationBehavior = .onScrollDown

    // 只在页面确有需要时覆盖默认值：
    // navigationItem.navigationBarMinimization.safeAreaAdjustment = .disabled
    // navigationItem.navigationBarMinimization.restorationBehavior = .atScrollEdge
}
```

`UIBarMinimization` 在 Objective-C 头文件里是类，在 Swift overlay 中是包含这三个属性的值类型；`navigationBarMinimization` 提供 get/set，以上 Swift 写法已通过类型检查。声明已对照 Xcode 27 的 `UIKit.swiftinterface` 核实。[官方属性文档](https://developer.apple.com/documentation/uikit/uinavigationitem/navigationbarminimization-15u99)

对于 `UIViewControllerRepresentable` 包裹 `UICollectionView` 的页面，没有查到 Apple 文档规定这种结构一律不支持新 modifier，也没有文档保证任意嵌套结构都能自动识别。UIKit 的官方文档说明：系统会从视图层级选择需要观察的 scroll view；复杂层级可能选错，可以用 `setContentScrollView(_:for:)` 明确指定。[setContentScrollView](https://developer.apple.com/documentation/uikit/uiviewcontroller/setcontentscrollview(_:for:))

Flare 当前使用 SwiftUI 页面级 modifier；仅当运行中发现导航栏没有跟随真实时间线时，再检查被观察的 scroll view。UIKit 控制器中的候选调整是：

```swift
// UICollectionView 创建并加入视图层级后。
setContentScrollView(collectionView, for: .top)
```

这一 API 自 iOS 15 可用。官方 SDK 的 `UIViewController.h` 说明其影响包含导航控制器 / tab 控制器观察滚动视图、bar 背景与 content inset 调整；**这只是公开的滚动源指定机制，并非 Flare 新最小化行为已验证的修复**。还需验证 SwiftUI 宿主是否采用子控制器指定的滚动视图，不应先假定修改子控制器 `navigationItem` 就能控制 SwiftUI 管理的导航栏。

## 滚动容器与构建要求

1. **页面入口**：所有普通路由页面由 `Route.view` 统一配置，首页按布局配置；`Router` 继续负责导航、sheet 和 full-screen cover，不更改导航栈的结构。[路由工厂](../../appleApp/ios/UI/Route/Route.swift#L53)、[Router](../../appleApp/ios/UI/Route/Router.swift#L32)
2. **保留底部行为**：`FlareRoot` 已通过 backport 使用 `.tabBarMinimizeBehavior(.onScrollDown)`。它负责 tab bar，新 API 负责 navigation bar，两者配置不同。[FlareRoot](../../appleApp/ios/UI/FlareRoot.swift#L77)、[tab bar API](https://developer.apple.com/documentation/swiftui/view/tabbarminimizebehavior(_:))
3. **先验证滚动识别**：普通列表和图库都通过 UIKit collection view 渲染，并忽略垂直安全区；各自的 collection view 直接贴合控制器根视图四边，目前没有显式 `setContentScrollView`。当前层级可先依赖系统识别，再按观测决定是否补充滚动源指定。[时间线选择](../../appleApp/ios/UI/Component/TimelinePagingView.swift#L39)、[列表容器](../../appleApp/ios/UI/Component/CollectionViewTimeline.swift#L482)、[图库容器](../../appleApp/ios/UI/Component/GalleryTimelinePagingView.swift#L169)
4. **验证安全区联动**：普通列表在 `viewSafeAreaInsetsDidChange` 中更新 content inset，并在列表贴顶时保持滚动锚点。收起 / 展开导航栏会成为需要实际检验的 inset 变化场景，重点观察跳动、顶部空白、下拉刷新及滚动位置恢复。[安全区回调](../../appleApp/ios/UI/Component/CollectionViewTimeline.swift#L436)、[inset 更新](../../appleApp/ios/UI/Component/CollectionViewTimeline.swift#L800)
5. **自定义内容单独评估**：图库的 `ChangeLogNotice` 来自 `.safeAreaInset(edge: .top)`，不是导航栏 toolbar 内容，不能把它随导航栏消失当作 API 保证。普通列表的公告作为 collection accessory item 渲染。Profile 的自定义 header、分页和多个滚动偏移联动，以及 iPad deck，应在首页验证后分别评估。[图库公告](../../appleApp/ios/UI/Screen/HomeTimelineScreen.swift#L117)、[Profile](../../appleApp/ios/UI/Screen/ProfileScreen.swift)
6. **构建要求**：项目最低版本仍为 iOS 17，并使用运行时 availability guard。构建机必须拥有声明新符号的 iOS 27 SDK；`if #available` 不能让旧 SDK 获得新符号。iOS CI 已改用 `xcode-27` runner，并设置 `xcode-version: latest`，以选择包含 beta 在内的最新预装 Xcode；贡献指南也已更新为 Xcode 27。GitHub 官方镜像列出了 iOS 27 SDK 和 iPhone 17 模拟器。[项目设置](../../appleApp/project.yml#L8)、[CI 设置](../../.github/workflows/ios.yml#L23)、[GitHub 镜像](https://github.com/actions/runner-images/blob/main/images/macos/xcode-27-arm64-Readme.md)、[setup-xcode 参数](https://github.com/maxim-lobanov/setup-xcode)

`hidesBarsOnSwipe` 则是 iOS 8 起的旧 UIKit 行为，配置在 `UINavigationController` 上，通过上下 swipe 隐藏 / 显示 navigation bar 和有内容的 toolbar；它不是 iOS 27 的页面级最小化配置，也没有上面独立的恢复与安全区选项。当前项目接入新能力无需先添加这套旧行为。[hidesBarsOnSwipe](https://developer.apple.com/documentation/uikit/uinavigationcontroller/hidesbarsonswipe)

## 已验证与待验证

已用本机 Xcode 27、iOS 27 SDK，按 `arm64-apple-ios17.0` deployment target 和 Swift 6 语言模式运行独立 `swiftc -typecheck`，exit code 为 0。样例覆盖上述 ViewModifier、使用它的 SwiftUI 页面、两个可选 modifier、UIKit 三项配置及 `setContentScrollView`。

全应用推广后的实现已通过 Xcode 27 的完整 iOS Debug 模拟器构建和 macOS Debug 构建（均为 `BUILD SUCCEEDED`），iOS deployment target 仍为 iOS 17。构建使用项目已有的 Kotlin 和 Swift Package 依赖，未跳过 Kotlin 构建。共享 modifier 另按 macOS 14 deployment target 完成独立类型检查。iOS CI 的 YAML 及 Xcode 选择配置已在首次接入时检查通过，远端 CI 结果以 PR 检查为准。

本次 iOS 构建产物已成功安装并启动于 iOS 27.0 的 iPhone 17e 和 iOS 18.6 的 iPhone 15 Pro 模拟器，两个进程持续运行。界面控制工具访问 Xcode 27 Device Hub 仍然超时，因此完成了构建、导航入口覆盖核对和启动检查，未完成手势与画面验收。

交互验收仍需覆盖：普通列表 / 图库、短内容 / 长内容、收起后反向滚动、下拉刷新、切换 tab、进入详情再返回、公告显示、浏览页与例外页相互跳转、打开 / 关闭独立编辑弹窗，以及旧系统的页面行为。此次实现没有新增滚动源桥接或覆盖安全区策略；是否需要这些额外调整，应以运行观察为准。
