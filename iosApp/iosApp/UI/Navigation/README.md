# Flare 应用导航系统

## 架构概述

Flare 应用使用 SwiftfulRouting 框架实现可组合的、类型安全的导航系统。

### 核心组件

1. **FlareRouteDestination** - 定义应用中所有可能的导航目标
2. **FlareAppState** - 管理应用状态和导航状态
3. **FlareRouterView** - 应用的根视图，集成 SwiftfulRouting
4. **FlareRoutingExtensions** - 提供 SwiftfulRouting 的便捷扩展

## 迁移计划

### 第一阶段：基础设施搭建（1-2周）

- [x] 创建 FlareRouteDestination 枚举定义所有导航目标
- [x] 实现 FlareAppState 管理应用状态
- [x] 创建 FlareRouterView 替代现有 RouterView
- [x] 开发 FlareRoutingExtensions 提供便捷扩展
- [x] 创建示例页面展示新导航系统的用法

### 第二阶段：页面迁移（3-4周）

- [ ] 迁移主页导航（Home, Search, Notification）
- [ ] 迁移详情页导航（帖子详情、图片查看、视频播放）
- [ ] 迁移功能页导航（设置、标签设置、登录、撰写帖子）
- [ ] 迁移个人资料页面

### 第三阶段：完善与测试（5-8周）

- [ ] 处理深层链接和通知跳转
- [ ] 实现导航历史记录和恢复
- [ ] 添加页面转场动画
- [ ] 全面测试各种导航场景
- [ ] 兼容处理 iOS 15 和 iOS 17+ 的差异

## 使用指南

### 基本导航

```swift
// 从环境获取路由器
@Environment(\.router) private var router

// 导航到页面
router.showFlareDestination(.profile(accountId: "user123"), with: .push)

// 显示弹窗
router.showFlareDestination(.settings, with: .sheet)

// 全屏显示
router.showFlareDestination(.login, with: .fullScreenCover)
```

### 使用应用状态导航

```swift
// 获取应用状态
@EnvironmentObject private var appState: FlareAppState

// 导航到个人资料
appState.showProfile(accountId: "user123")

// 显示帖子详情
appState.showPostDetail(statusKey: "post123")

// 显示撰写帖子
appState.showCompose()
```

### 流程导航

```swift
// 定义导航流程
let destinations: [FlareRouteDestination] = [
    .login,
    .profile(accountId: nil),
    .settings
]

// 启动流程
router.enterFlareFlow(destinations)
```

## 注意事项

1. 确保在添加新页面时更新 FlareRouteDestination 枚举
2. 尽量使用应用状态驱动导航，而非直接操作导航栈
3. 对于复杂导航序列，优先考虑使用流程导航
4. 对于需要在多处复用的导航逻辑，可在 FlareAppState 中添加便捷方法 