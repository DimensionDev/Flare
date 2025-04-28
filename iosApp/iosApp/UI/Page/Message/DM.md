# Flare 应用 Direct Message (DM) 功能开发文档

## 1. 概述

Flare 应用中的 Direct Message（DM）功能将通过集成 ExyteChat 开源 UI 框架与 Kotlin Multiplatform (KMP) 共享逻辑实现跨平台私信功能。本文档详细描述了开发需求、功能列表和实现要点，以及 KMP 接口与 ExyteChat 框架的集成方案。

## 2. 技术架构

### 2.1 总体架构

- **前端 UI**: 使用 ExyteChat 框架实现 iOS 端 UI
- **业务逻辑**: 使用 KMP 共享的 Presenter 层处理业务逻辑
- **数据源**: 通过 KMP 接口访问多平台（Twitter、Mastodon、Bluesky等）的数据

```
┌───────────────────┐      ┌───────────────────┐      ┌───────────────────┐
│    SwiftUI UI     │      │   KMP Presenter   │      │  Platform APIs    │
│  (ExyteChat框架)   │<─────┤   (共享逻辑层)     │<─────┤  (多平台数据源)   │
└───────────────────┘      └───────────────────┘      └───────────────────┘
```

### 2.2 ExyteChat 框架特性

ExyteChat 提供以下关键特性：
- 消息列表显示和分页加载
- 自定义消息和输入视图
- 内置媒体选择器（照片、视频）
- Giphy 表情键盘集成
- 消息长按菜单
- 消息回复功能
- 多种消息类型支持（文本、媒体、音频、链接等）

### 2.3 KMP 接口层

Kotlin Multiplatform 暴露的核心接口：
- `DMListPresenter`: 管理对话列表
- `DMConversationPresenter`: 管理单个对话内容
- `UserDMConversationPresenter`: 创建新对话
- `DirectMessageDataSource`: 提供数据操作方法

## 3. 功能需求

### 3.1 核心功能

1. **对话列表**
   - 显示所有私信对话
   - 每个对话显示：对话参与者头像、名称、最后一条消息预览、时间、未读消息数
   - 下拉刷新对话列表
   - 分页加载更多对话

2. **对话详情**
   - 显示与特定用户/群组的消息历史
   - 消息气泡区分自己和他人（不同颜色、位置）
   - 显示消息发送状态（发送中、已发送、发送失败）
   - 支持重试发送失败的消息
   - 分页加载历史消息
   - 定时自动检查新消息（5秒轮询）

3. **消息发送**
   - 发送文本消息
   - 发送媒体消息（图片、视频）
   - 发送GIF/表情（通过Giphy集成）
   - 支持回复特定消息
   - 消息发送前预览

4. **新对话创建**
   - 创建与特定用户的新对话
   - 检查是否可以向用户发送私信

### 3.2 高级功能

1. **媒体处理**
   - 图片/视频缩略图显示
   - 点击媒体显示全屏预览
   - 支持多选媒体发送

2. **消息交互**
   - 长按消息显示操作菜单（回复、删除等）
   - 左右滑动消息显示快捷操作
   - 支持消息引用回复

3. **状态同步**
   - 消息发送状态实时更新
   - 对话未读状态管理
   - 新消息通知

4. **其他功能**
   - 离开对话
   - 转发状态到对话
   - 链接预览

## 4. 集成方案

### 4.1 数据模型映射

| KMP 模型 | ExyteChat 模型 | 映射说明 |
|---------|--------------|---------|
| `UiDMRoom` | - | 构建自定义View显示对话列表项 |
| `UiDMItem` | `Message` | 将 UiDMItem 映射为 Message |
| `UiDMItem.Message.Text` | `Message.text` | 消息文本内容 |
| `UiDMItem.Message.Media` | `Message.attachments` | 媒体内容 |
| `UiUserV2` | `User` | 用户信息映射 |
| `SendState` | `Message.Status` | 消息状态映射 |

### 4.2 Presenter 与 UI 集成

1. **对话列表屏幕**
```swift
struct DMListView: View {
    let accountType: AccountType
    @StateObject private var viewModel = DMListViewModel(accountType: accountType)
    
    var body: some View {
        ChatView(
            messages: viewModel.messages,
            chatType: .conversation,
            didSendMessage: { draft in 
                // 处理新建对话
            }
        )
        .onAppear {
            viewModel.loadConversations()
        }
    }
}

class DMListViewModel: ObservableObject {
    @Published var rooms: [UiDMRoom] = []
    @Published var messages: [Message] = []
    private let presenter: DMListPresenter

    init(accountType: AccountType) {
        self.presenter = DMListPresenter(accountType: accountType)
        // 监听 presenter 状态变化
    }
    
    func loadConversations() {
        // 通过 presenter 加载对话列表
    }
    
    // 将 UiDMRoom 转换为 Message 列表
    private func convertToMessages() { ... }
}
```

2. **对话详情屏幕**
```swift
struct DMConversationView: View {
    let accountType: AccountType
    let roomKey: MicroBlogKey
    @StateObject private var viewModel: DMConversationViewModel
    
    init(accountType: AccountType, roomKey: MicroBlogKey) {
        self._viewModel = StateObject(
            wrappedValue: DMConversationViewModel(
                accountType: accountType,
                roomKey: roomKey
            )
        )
    }
    
    var body: some View {
        ChatView(
            messages: viewModel.messages,
            didSendMessage: { draft in
                viewModel.sendMessage(draft.text, attachments: draft.medias)
            }
        )
        .navigationTitle(viewModel.conversationTitle)
    }
}

class DMConversationViewModel: ObservableObject {
    @Published var messages: [Message] = []
    @Published var conversationTitle: String = ""
    private let presenter: DMConversationPresenter
    
    init(accountType: AccountType, roomKey: MicroBlogKey) {
        self.presenter = DMConversationPresenter(
            accountType: accountType,
            roomKey: roomKey
        )
        // 监听 presenter 状态变化
    }
    
    func sendMessage(_ text: String, attachments: [MediaPickerModel]) {
        // 通过 presenter 发送消息
    }
    
    // 将 UiDMItem 转换为 Message
    private func convertToMessages() { ... }
}
```

## 5. 开发要点

### 5.1 技术挑战

1. **数据模型转换**
   - KMP 的 UiDMItem/UiDMRoom 需要准确映射到 ExyteChat 的 Message 模型
   - 保持消息状态同步（发送中、失败等）

2. **分页加载**
   - KMP 使用 Flow<PagingData> 提供分页数据
   - 需要与 ExyteChat 的分页机制集成

3. **消息状态管理**
   - 实时更新消息发送状态
   - 处理发送失败和重试逻辑
   - 轮询新消息与 UI 更新

4. **媒体处理**
   - 将 ExyteChat 的媒体选择转换为 KMP 可接受的格式
   - 处理不同平台的媒体限制

### 5.2 UI 自定义

1. **主题适配**
   - 确保 ExyteChat 主题与 Flare 应用整体风格一致
   - 自定义消息气泡、输入框等 UI 元素

2. **多平台适配**
   - 针对不同社交平台可能的 UI 特殊需求进行适配
   - 处理不同平台的功能限制（如某些平台不支持媒体消息）

3. **本地化**
   - 确保界面文本可本地化
   - 适配不同语言下的 UI 布局

### 5.3 性能优化

1. **内存管理**
   - 大量消息和媒体的内存优化
   - 图片缓存策略

2. **网络请求优化**
   - 减少不必要的网络请求
   - 请求失败重试策略
   - 合理的新消息轮询机制

3. **UI 渲染优化**
   - 消息列表滚动性能优化
   - 大量媒体内容的懒加载

## 6. 开发计划

### 6.1 第一阶段：框架集成与基础功能

1. 集成 ExyteChat 框架到项目
2. 实现数据模型转换逻辑
3. 开发对话列表基本界面
4. 开发对话详情基本界面
5. 实现基本文本消息发送功能

### 6.2 第二阶段：高级功能实现

1. 添加媒体消息支持
2. 实现消息回复功能
3. 实现消息长按菜单
4. 添加滑动操作功能
5. 实现 GIF/表情发送

### 6.3 第三阶段：完善与优化

1. 性能优化与内存管理
2. UI 细节完善
3. 错误处理与边缘情况
4. 用户体验优化
5. 自动化测试

## 7. 测试计划

1. **单元测试**
   - 数据模型转换逻辑
   - Presenter 业务逻辑

2. **集成测试**
   - KMP 接口与 UI 的交互
   - 消息发送流程

3. **UI 测试**
   - 各种消息类型的正确显示
   - 长列表滚动性能

4. **用户体验测试**
   - 实际使用场景测试
   - 不同设备和屏幕尺寸测试

## 8. 总结

通过集成 ExyteChat 框架和 KMP 共享逻辑，Flare 应用将实现功能完善、体验一致的跨平台 DM 功能。开发过程中需重点关注数据模型转换、状态管理和性能优化，确保在多平台环境下提供流畅的用户体验。 