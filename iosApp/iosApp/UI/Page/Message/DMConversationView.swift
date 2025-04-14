import SwiftUI
import shared
import ExyteChat

/// DM对话详情视图
struct DMConversationView: View {
    let accountType: AccountType
    let roomKey: MicroBlogKey
    
    var body: some View {
        DMConversationContent(
            presenter: DMConversationPresenter(
                accountType: accountType,
                roomKey: roomKey
            )
        )
    }
}

/// 使用KMPView协议封装DM对话内容
struct DMConversationContent: KMPView {
    typealias P = DMConversationPresenter
    typealias S = DMConversationState
    
    let presenter: DMConversationPresenter
    @State private var messages: [ExyteChat.Message] = []
    @State private var conversationTitle: String = "对话"
    @State private var isLoading = false
    @State private var refreshTrigger = false // 用于触发刷新
    
    // 创建当前用户
    private let currentUser = ExyteChat.User(
        id: UUID().uuidString,
        name: "我",
        avatarURL: nil,
        isCurrentUser: true
    )
    
    func body(state: DMConversationState) -> some View {
        VStack {
            if isLoading {
                ProgressView()
                    .scaleEffect(1.5)
                    .padding()
            } else if messages.isEmpty {
                VStack(spacing: 16) {
                    Spacer()
                    Text("暂无消息")
                        .foregroundColor(.gray)
                    Button("刷新") {
                        refreshTrigger.toggle() // 切换触发器状态
                        updateMessages(state: state)
                    }
                    .buttonStyle(.bordered)
                    Spacer()
                }
            } else {
                ExyteChat.ChatView(
                    messages: messages,
                    chatType: .conversation
                ) { draft in
                    // 暂时只实现UI部分，不处理发送逻辑
                    print("发送消息: \(draft.text)")
                }
                .chatTheme(
                    ChatTheme(
                        colors: .init(
                            sendButtonBackground: Color.accentColor
                        )
                    )
                )
            }
        }
        .navigationTitle(conversationTitle)
        .onAppear {
            // 更新对话标题
            updateConversationTitle(state: state)
            // 初始加载消息
            loadMessages(state: state)
        }
        // 监听刷新触发器变化
        .onChange(of: refreshTrigger) { _ in
            loadMessages(state: state)
        }
        .onDisappear {
            // 清理资源
            messages = []
        }
    }
    
    // 初始加载消息
    private func loadMessages(state: DMConversationState) {
        isLoading = true
        // 延迟一小段时间让UI更新，确保KMP数据加载完成
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            updateMessages(state: state)
            isLoading = false 
        }
    }
    
    // 更新对话标题
    private func updateConversationTitle(state: DMConversationState) {
        if case let .success(users) = onEnum(of: state.users) {
            if (users as NSObject).value(forKey: "count") as? Int ?? 0 > 0 {
                let userArray = users as! [UiUserV2]
                if userArray.count > 0 {
                    // 使用参与者名称作为标题
                    let names = userArray.prefix(2).map { $0.name.raw }.joined(separator: ", ")
                    conversationTitle = userArray.count > 2 ? "\(names)..." : names
                }
            } else {
                conversationTitle = "私信对话"
            }
        }
    }
    
    // 更新消息列表
    private func updateMessages(state: DMConversationState) {
        print("开始更新消息列表")
        
        if case let .success(success) = onEnum(of: state.items) {
            print("成功获取消息数据，消息数量: \(success.itemCount)")
            
            var newMessages: [ExyteChat.Message] = []
            
            // 将KMP的消息转换为ExyteChat的消息
            for index in 0..<success.itemCount {
                if let message = success.peek(index: index) {
                    print("处理第\(index)条消息: \(message.id)")
                    
                    if let chatMessage = convertToChatMessage(message) {
                        newMessages.append(chatMessage)
                        print("转换成功: \(chatMessage.text)")
                    } else {
                        print("消息转换失败")
                    }
                    
                    // 加载当前消息
                    success.get(index: index)
                } else {
                    print("无法获取第\(index)条消息")
                }
            }
            
            print("转换后的消息数量: \(newMessages.count)")
            
            // 如果没有实际消息，添加一个测试消息
            if newMessages.isEmpty {
                // 添加一条测试消息
                var testMessage = ExyteChat.Message(
                    id: UUID().uuidString,
                    user: currentUser,
                    createdAt: Date()
                )
                testMessage.text = "这是一条测试消息，实际消息加载失败。"
                newMessages.append(testMessage)
                
                // 添加一条来自其他用户的测试消息
                let otherUser = ExyteChat.User(
                    id: UUID().uuidString,
                    name: "测试用户",
                    avatarURL: nil,
                    isCurrentUser: false
                )
                var testMessage2 = ExyteChat.Message(
                    id: UUID().uuidString,
                    user: otherUser,
                    createdAt: Date().addingTimeInterval(-60)
                )
                testMessage2.text = "你好，这是一条来自其他用户的测试消息。"
                newMessages.append(testMessage2)
            }
            
            // 更新消息列表
            self.messages = newMessages
        } else if case .loading = onEnum(of: state.items) {
            print("消息正在加载中")
            isLoading = true
        } else if case let .error(error) = onEnum(of: state.items) {
            print("加载消息失败: \(error)")
            // 添加一条错误提示消息
            var errorMessage = ExyteChat.Message(
                id: UUID().uuidString,
                user: currentUser,
                createdAt: Date()
            )
            errorMessage.text = "加载消息失败，请重试。"
            self.messages = [errorMessage]
        } else {
            print("未知状态")
        }
    }
    
    // 将UiDMItem转换为Chat库的Message
    private func convertToChatMessage(_ item: UiDMItem) -> ExyteChat.Message? {
        print("转换消息 ID: \(item.id), 用户: \(item.user.name.raw)")
        
        // 创建用户
        let chatUser = ExyteChat.User(
            id: item.user.key.description,
            name: item.user.name.raw,
            avatarURL: URL(string: item.user.avatar),
            isCurrentUser: item.isFromMe
        )
        
        // 创建消息基本信息
        var message = ExyteChat.Message(
            id: item.id,
            user: chatUser,
            createdAt: item.timestamp
        )
        
        // 设置消息状态
        if let sendState = item.sendState {
            let className = String(describing: type(of: sendState))
            print("消息状态类名: \(className)")
            
            if className.contains("Sending") {
                message.status = .sending
            } else if className.contains("Failed") {
                // 创建一个空的DraftMessage作为错误状态
                let draftMessage = ExyteChat.DraftMessage(
                    text: "", 
                    medias: [],
                    giphyMedia: nil,
                    recording: nil,
                    replyMessage: nil,
                    createdAt: Date()
                )
                message.status = .error(draftMessage)
            } else {
                message.status = .sent
            }
        } else {
            message.status = .sent
        }
        
        // 设置消息内容
        switch item.content {
        case is UiDMItemMessageText:
            let textContent = item.content as! UiDMItemMessageText
            message.text = textContent.text.raw
            print("文本消息: \(message.text)")
            
        case is UiDMItemMessageMedia:
            let mediaContent = item.content as! UiDMItemMessageMedia
            // 处理不同类型的媒体
            switch mediaContent.media {
            case is UiMediaImage:
                let imageMedia = mediaContent.media as! UiMediaImage
                if let url = URL(string: imageMedia.url) {
                    message.attachments = [
                        ExyteChat.Attachment(id: UUID().uuidString, url: url, type: .image)
                    ]
                    print("图片消息: \(imageMedia.url)")
                }
                
            case is UiMediaVideo:
                let videoMedia = mediaContent.media as! UiMediaVideo
                if let url = URL(string: videoMedia.url) {
                    message.attachments = [
                        ExyteChat.Attachment(id: UUID().uuidString, url: url, type: .video)
                    ]
                    print("视频消息: \(videoMedia.url)")
                }
                
            default:
                // 其他媒体类型
                message.text = "不支持的媒体类型"
                print("不支持的媒体类型")
            }
            
        case is UiDMItemMessageStatus:
            let statusContent = item.content as! UiDMItemMessageStatus
            message.text = "转发: " + statusContent.status.content.raw
            print("转发消息: \(message.text)")
            
        case is UiDMItemMessageDeleted:
            message.text = "此消息已删除"
            print("已删除消息")
            
        default:
            message.text = "未知消息类型: \(type(of: item.content))"
            print("未知消息类型: \(type(of: item.content))")
        }
        
        return message
    }
}

// 消息气泡视图
struct MessageBubbleView: View {
    let message: UiDMItem
    
    var body: some View {
        HStack(alignment: .bottom) {
            // 根据消息发送者调整对齐
            if message.isFromMe {
                Spacer()
            }
            
            // 头像（只在非自己发送的消息显示）
            if !message.isFromMe {
                AsyncImage(url: URL(string: message.user.avatar)) { phase in
                    switch phase {
                    case .empty:
                        Circle().fill(Color.gray.opacity(0.3))
                    case .success(let image):
                        image.resizable().scaledToFill()
                    case .failure:
                        Circle().fill(Color.gray.opacity(0.3))
                            .overlay(
                                Image(systemName: "person.circle.fill")
                                    .resizable()
                                    .scaledToFit()
                                    .padding(5)
                                    .foregroundColor(.gray)
                            )
                    @unknown default:
                        Circle().fill(Color.gray.opacity(0.3))
                    }
                }
                .frame(width: 32, height: 32)
                .clipShape(Circle())
            }
            
            // 消息内容气泡
            VStack(alignment: message.isFromMe ? .trailing : .leading, spacing: 2) {
                // 消息内容
                messageBubbleContent(message)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(message.isFromMe ? Color.blue.opacity(0.8) : Color.secondary.opacity(0.2))
                    .foregroundColor(message.isFromMe ? .white : .primary)
                    .cornerRadius(16)
                
                // 时间戳
                Text(formatTime(message.timestamp))
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            
            if !message.isFromMe {
                Spacer()
            }
        }
        .padding(.vertical, 4)
    }
    
    // 消息内容处理
    @ViewBuilder
    private func messageBubbleContent(_ message: UiDMItem) -> some View {
        switch message.content {
        case is UiDMItemMessageText:
            let textContent = message.content as! UiDMItemMessageText
            Text(textContent.text.raw)
            
        case is UiDMItemMessageMedia:
            let mediaContent = message.content as! UiDMItemMessageMedia
            mediaContentView(mediaContent.media)
            
        case is UiDMItemMessageStatus:
            let statusContent = message.content as! UiDMItemMessageStatus
            VStack(alignment: .leading) {
                Text("转发:")
                    .font(.caption)
                    .foregroundColor(message.isFromMe ? .white.opacity(0.8) : .gray)
                Text(statusContent.status.content.raw)
            }
            
        case is UiDMItemMessageDeleted:
            Text("此消息已删除")
                .italic()
                .foregroundColor(message.isFromMe ? .white.opacity(0.8) : .gray)
            
        default:
            Text("未知消息类型")
        }
    }
    
    // 媒体内容视图
    @ViewBuilder
    private func mediaContentView(_ media: UiMedia) -> some View {
        switch media {
        case is UiMediaImage:
            let imageMedia = media as! UiMediaImage
            AsyncImage(url: URL(string: imageMedia.url)) { phase in
                switch phase {
                case .empty:
                    Rectangle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(width: 200, height: 150)
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFill()
                        .frame(maxWidth: 200, maxHeight: 200)
                        .cornerRadius(8)
                case .failure:
                    Rectangle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(width: 200, height: 150)
                        .overlay(
                            Image(systemName: "photo")
                                .foregroundColor(.gray)
                        )
                @unknown default:
                    Rectangle()
                        .fill(Color.gray.opacity(0.3))
                        .frame(width: 200, height: 150)
                }
            }
            
        case is UiMediaVideo:
            let videoMedia = media as! UiMediaVideo
            ZStack {
                Rectangle()
                    .fill(Color.black.opacity(0.8))
                    .frame(width: 200, height: 150)
                    .cornerRadius(8)
                
                Image(systemName: "play.circle.fill")
                    .resizable()
                    .frame(width: 40, height: 40)
                    .foregroundColor(.white)
                
                Text(videoMedia.url)
                    .font(.caption)
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .padding(4)
                    .background(Color.black.opacity(0.5))
                    .cornerRadius(4)
                    .padding(8)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottom)
            }
            .frame(width: 200, height: 150)
            
        default:
            Text("不支持的媒体类型")
                .padding(8)
                .background(Color.gray.opacity(0.2))
                .cornerRadius(8)
        }
    }
    
    // 格式化时间显示
    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

//// 自定义ForEach替代视图，处理Int32类型
//struct ForEachWithIndex<Content: View>: View {
//    let startIndex: Int32
//    let count: Int32
//    let content: (Int32) -> Content
//    
//    init(_ startIndex: Int32, count: Int32, @ViewBuilder content: @escaping (Int32) -> Content) {
//        self.startIndex = startIndex
//        self.count = count
//        self.content = content
//    }
//    
//    var body: some View {
//        ForEach(0..<Int(count), id: \.self) { index in
//            content(Int32(index) + startIndex)
//        }
//    }
//}
