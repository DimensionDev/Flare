# Direct Message (DM) 功能文档 - Kotlin Multiplatform

## 1. 概述

Direct Message (DM) 功能是 Flare 多平台应用中的私信模块，通过 Kotlin Multiplatform (KMP) 实现跨平台共享核心逻辑。本功能支持在各个社交媒体平台（Twitter、Mastodon、Bluesky、Misskey等）上统一的私信体验。

## 2. 核心组件结构

### 2.1 Presenter 层

| 类名 | 功能描述 | 主要接口 |
|------|---------|---------|
| `DMListPresenter` | 负责显示用户的所有私信对话列表 | `body()`: 返回 `DMListState` |
| `DMConversationPresenter` | 负责管理特定对话内的消息列表 | `body()`: 返回 `DMConversationState` |
| `UserDMConversationPresenter` | 负责创建与特定用户的新私信对话 | `body()`: 返回 `UserDMConversationPresenter.State` |

### 2.2 状态接口

#### DMListState
```kotlin
interface DMListState {
    val items: PagingState<UiDMRoom>       // 分页加载的对话列表
    val isRefreshing: Boolean              // 是否正在刷新
    suspend fun refreshSuspend()           // 刷新数据
}
```

#### DMConversationState
```kotlin
interface DMConversationState {
    val items: PagingState<UiDMItem>       // 分页加载的消息列表
    val users: UiState<ImmutableList<UiUserV2>>  // 对话参与者信息
    fun send(message: String)              // 发送消息
    fun retry(key: MicroBlogKey)           // 重试发送失败的消息
    fun leave()                            // 离开对话
}
```

#### UserDMConversationState
```kotlin
interface State {
    val roomKey: UiState<MicroBlogKey>     // 创建的对话房间ID
}
```

### 2.3 数据模型

#### UiDMRoom
私信对话房间模型，包含：
- `key`: 对话唯一标识 (MicroBlogKey)
- `users`: 参与对话的用户列表 (ImmutableList<UiUserV2>)
- `lastMessage`: 最后一条消息 (UiDMItem?)
- `unreadCount`: 未读消息数量 (Long)
- `lastMessageText`: 最后消息文本内容 (String)
- `id`: 对话ID (String)
- `hasUser`: 是否有用户参与 (Boolean)

#### UiDMItem
私信消息模型，包含：
- `key`: 消息唯一标识 (MicroBlogKey)
- `user`: 发送消息的用户 (UiUserV2)
- `content`: 消息内容 (Message)
- `timestamp`: 消息时间戳 (UiDateTime)
- `isFromMe`: 是否为自己发送 (Boolean)
- `sendState`: 发送状态 (SendState?)
- `showSender`: 是否显示发送者 (Boolean)
- `id`: 消息ID (String)

#### Message 类型
- `Text`: 文本消息
- `Media`: 媒体消息
- `Status`: 转发的状态消息
- `Deleted`: 已删除消息

#### SendState 状态
- `Sending`: 发送中
- `Failed`: 发送失败

## 3. 数据源接口 (DirectMessageDataSource)

```kotlin
interface DirectMessageDataSource {
    // 获取对话列表
    fun directMessageList(scope: CoroutineScope): Flow<PagingData<UiDMRoom>>
    
    // 获取特定对话的消息列表
    fun directMessageConversation(
        roomKey: MicroBlogKey,
        scope: CoroutineScope,
    ): Flow<PagingData<UiDMItem>>
    
    // 发送消息
    fun sendDirectMessage(
        roomKey: MicroBlogKey,
        message: String,
    )
    
    // 重试发送消息
    fun retrySendDirectMessage(messageKey: MicroBlogKey)
    
    // 删除消息
    fun deleteDirectMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    )
    
    // 获取对话信息
    fun getDirectMessageConversationInfo(roomKey: MicroBlogKey): CacheData<UiDMRoom>
    
    // 拉取对话新消息
    suspend fun fetchNewDirectMessageForConversation(roomKey: MicroBlogKey)
    
    // 未读消息计数
    val directMessageBadgeCount: CacheData<Int>
    
    // 离开对话
    fun leaveDirectMessage(roomKey: MicroBlogKey)
    
    // 创建新对话房间
    fun createDirectMessageRoom(userKey: MicroBlogKey): Flow<UiState<MicroBlogKey>>
    
    // 检查是否可以向特定用户发送私信
    suspend fun canSendDirectMessage(userKey: MicroBlogKey): Boolean
}
```

## 4. 实现功能

### 4.1 基础功能
- 查看所有私信对话列表
- 查看特定对话的消息历史
- 发送文本消息
- 定时自动拉取新消息 (5秒间隔)
- 显示未读消息计数
- 与特定用户创建新对话

### 4.2 高级功能
- 支持发送媒体消息
- 支持转发状态作为消息
- 支持消息重发机制
- 支持删除消息
- 支持离开对话

### 4.3 UI状态管理
- 消息发送状态指示 (发送中、失败)
- 下拉刷新
- 分页加载历史消息
- 新消息自动加载

## 5. iOS 集成方式

### 5.1 导入共享模块
```swift
import shared
```

### 5.2 实例化 Presenter
```swift
// 获取私信列表
let dmListPresenter = DMListPresenter(accountType: accountType)

// 获取特定对话
let dmConversationPresenter = DMConversationPresenter(
    accountType: accountType,
    roomKey: roomKey
)

// 创建新对话
let userDMConversationPresenter = UserDMConversationPresenter(
    accountType: accountType,
    userKey: userKey
)
```

### 5.3 对接 SwiftUI
```swift
struct DMListView: View {
    let accountType: AccountType
    
    var body: some View {
        DMListContent(presenter: DMListPresenter(accountType: accountType))
    }
}

struct DMListContent: KMPView {
    let presenter: DMListPresenter
    
    func body(state: DMListState) -> some View {
        List {
            ForEach(state.items.content, id: \.id) { room in
                NavigationLink(destination: DMConversationView(
                    accountType: presenter.accountType,
                    roomKey: room.key
                )) {
                    DMRoomRow(room: room)
                }
            }
        }
        .refreshable {
            await state.refreshSuspend()
        }
    }
}
```

## 6. 跨平台兼容性

### 6.1 支持的平台
- iOS (SwiftUI)
- Android (Jetpack Compose)

### 6.2 跨平台特性
- 使用 Kotlin Flow 进行异步数据处理
- 使用 KMP 共享核心业务逻辑
- 通过平台特定适配层对接原生 UI 框架

## 7. 多社交平台支持

### 7.1 支持的社交平台
- Twitter
- Mastodon
- Bluesky
- Misskey
- 更多平台可通过实现 DirectMessageDataSource 接口扩展

### 7.2 扩展机制
每个社交平台需实现 DirectMessageDataSource 接口，处理平台特有的 API 细节，而上层 Presenter 和 UI 可保持一致。 