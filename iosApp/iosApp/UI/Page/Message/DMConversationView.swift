import AVKit
import ExyteChat
import Kingfisher
import ObjectiveC
import shared
import SwiftUI
import CoreMedia

// 用于存储原始媒体的字典
  var originalMediaStore: [String: UiMedia] = [:]



/// DM对话详情视图
struct DMConversationView: View {
    let accountType: AccountType
    let roomKey: MicroBlogKey
    let title: String
    @State private var presenter: DMConversationPresenter
    @State private var refreshTrigger = false

    init(accountType: AccountType, roomKey: MicroBlogKey, title: String) {
        self.accountType = accountType
        self.roomKey = roomKey
        self.title = title
        _presenter = State(initialValue: DMConversationPresenter(
            accountType: accountType,
            roomKey: roomKey
        ))
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { anyState in
            if let state = anyState as? DMConversationState {
                conversationContent(state: state)
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle(title)
    }

    @ViewBuilder
    private func conversationContent(state: DMConversationState) -> some View {
        VStack {
            if case let .success(success) = onEnum(of: state.items) {
                if success.itemCount > 0 {
                    // 有消息数据时显示聊天视图
                    ExyteChat.ChatView(
                        messages: convertMessages(success),
                        chatType: .conversation
                    ) { draft in
                        // 暂时只实现UI部分，不处理发送逻辑
                        print("发送消息: \(draft.text)")
                    } messageBuilder: { message, _, _, _, _, _, _ in
                        // 自定义消息视图
                        VStack(alignment: message.user.isCurrentUser ? .trailing : .leading, spacing: 4) {
                            // 消息内容区域
                            VStack(alignment: message.user.isCurrentUser ? .trailing : .leading, spacing: 4) {
                                // 文本消息
                                if !message.text.isEmpty {
                                    HStack(alignment: .center, spacing: 8) {
                                        Text(message.text)
                                            .foregroundColor(message.user.isCurrentUser ? .white : .primary)

                                        // 纯文本消息的时间戳
                                        if message.attachments.isEmpty {
                                            Text(formatTime(message.createdAt))
                                                .font(.caption2)
                                                .foregroundColor(message.user.isCurrentUser ? .white.opacity(0.7) : .secondary)
                                        }
                                    }
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                }

                                // 附件处理（图片、视频等）
                                if !message.attachments.isEmpty {
                                    ForEach(message.attachments, id: \.id) { _ in
                                        VStack(alignment: message.user.isCurrentUser ? .trailing : .leading, spacing: 4) {
                                            ZStack(alignment: .bottomTrailing) {
                                                // 媒体内容
                                                if let media = getOriginalMedia(from: message) {
                                                    DMSingleMediaView(
                                                        viewModel: DMMediaViewModel.from(media),
                                                        media: media
                                                    )
                                                    .frame(
                                                        width: min(200, UIScreen.main.bounds.width * 0.7),
                                                        height: min(200, UIScreen.main.bounds.height * 0.4)
                                                    )
                                                    .cornerRadius(8)

                                                    // 如果没有描述文本，时间戳显示在媒体右下角
                                                    if message.text.isEmpty {
                                                        Text(formatTime(message.createdAt))
                                                            .font(.caption2)
                                                            .foregroundColor(.white)
                                                            .padding(.horizontal, 6)
                                                            .padding(.vertical, 4)
                                                            .background(Color.black.opacity(0.3))
                                                            .cornerRadius(4)
                                                            .padding(8)
                                                    }
                                                }
                                            }

                                            // 如果有描述文本，时间戳跟在描述后面
                                            if !message.text.isEmpty {
                                                HStack(alignment: .center, spacing: 8) {
                                                    Text(message.text)
                                                        .foregroundColor(message.user.isCurrentUser ? .white : .primary)

                                                    Text(formatTime(message.createdAt))
                                                        .font(.caption2)
                                                        .foregroundColor(message.user.isCurrentUser ? .white.opacity(0.7) : .secondary)
                                                }
                                                .padding(.horizontal, 12)
                                                .padding(.vertical, 8)
                                            }
                                        }
                                    }
                                }

                                // 录音消息
                                if let recording = message.recording,
                                   let url = recording.url,
                                   let media = getOriginalMedia(from: message) {
                                    DMAudioMessageView(
                                        url: url,
                                        media: media,
                                        isCurrentUser: message.user.isCurrentUser
                                    )
                                }
                            }
                        }
                        .background(message.user.isCurrentUser ? Color.accentColor : Color.secondary.opacity(0.2))
                        .cornerRadius(16)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                    }
                    .chatTheme(
                        ChatTheme(
                            colors: .init(
                                sendButtonBackground: Color.accentColor
                            )
                        )
                    )
                } else {
                    // 没有消息时显示空状态
                    VStack(spacing: 16) {
                        Spacer()
                        Text("暂无消息")
                            .foregroundColor(.gray)
                        Button("刷新") {
                            refreshTrigger.toggle()
                            // 通过创建新的Presenter触发刷新
                            presenter = DMConversationPresenter(
                                accountType: accountType,
                                roomKey: roomKey
                            )
                        }
                        .buttonStyle(.bordered)
                        Spacer()
                    }
                }
            } else if case .loading = onEnum(of: state.items) {
                // 加载中状态
                ProgressView()
                    .scaleEffect(1.5)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if case .error = onEnum(of: state.items) {
                // 错误状态
                VStack(spacing: 16) {
                    Spacer()
                    Text("加载失败")
                        .foregroundColor(.red)
                    Button("重试") {
                        refreshTrigger.toggle()
                        // 通过创建新的Presenter触发刷新
                        presenter = DMConversationPresenter(
                            accountType: accountType,
                            roomKey: roomKey
                        )
                    }
                    .buttonStyle(.bordered)
                    Spacer()
                }
            }
        }
    }

    // 转换消息列表
    private func convertMessages(_ success: PagingStateSuccess<UiDMItem>) -> [ExyteChat.Message] {
        var messages: [ExyteChat.Message] = []

        // 创建当前用户
        let currentUser = ExyteChat.User(
            id: UUID().uuidString,
            name: "我",
            avatarURL: nil,
            isCurrentUser: true
        )

        // 转换所有消息
        for index in 0 ..< success.itemCount {
            if let message = success.peek(index: index) {
                if let chatMessage = convertToChatMessage(message) {
                    messages.append(chatMessage)
                }

                // 加载当前消息
                success.get(index: index)
            }
        }

        // 如果没有消息但状态是成功，添加提示信息
        if messages.isEmpty {
            print("消息列表为空，但状态是成功")
        }

        return messages
    }

    // 将UiDMItem转换为Chat库的Message
    private func convertToChatMessage(_ item: UiDMItem) -> ExyteChat.Message? {
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

        case is UiDMItemMessageMedia:
            let mediaContent = item.content as! UiDMItemMessageMedia
            // 保存原始媒体到字典中
            originalMediaStore[item.id] = mediaContent.media

            switch mediaContent.media {
            case is UiMediaImage:
                let imageMedia = mediaContent.media as! UiMediaImage
                if let url = URL(string: imageMedia.url) {
                    message.attachments = [
                        ExyteChat.Attachment(
                            id: UUID().uuidString,
                            url: url,
                            type: .image
                        ),
                    ]
                }

            case is UiMediaVideo:
                let videoMedia = mediaContent.media as! UiMediaVideo
                if let url = URL(string: videoMedia.url) {
                    message.attachments = [
                        ExyteChat.Attachment(
                            id: UUID().uuidString,
                            url: url,
                            type: .video
                        ),
                    ]
                }

            case is UiMediaGif:
                let gifMedia = mediaContent.media as! UiMediaGif
                if let url = URL(string: gifMedia.url) {
                    // 处理GIF类型，将其当作自动播放的视频处理
                    message.attachments = [
                        ExyteChat.Attachment(
                            id: UUID().uuidString,
                            url: url,
                            type: .video // GIF作为视频处理，会自动播放
                        ),
                    ]
                }

            case is UiMediaAudio:
                let audioMedia = mediaContent.media as! UiMediaAudio
                if let url = URL(string: audioMedia.url) {
                    // 创建Recording实例
                    let recording = ExyteChat.Recording(
                        duration: 0, // 实际时长会在AudioMessageView中加载
                        waveformSamples: [], // 波形数据会在AudioMessageView中生成
                        url: url
                    )
                    message.recording = recording
                }

            default:
                // 其他未知媒体类型
                message.text = "不支持的媒体类型: \(type(of: mediaContent.media))"
            }

        case is UiDMItemMessageStatus:
            let statusContent = item.content as! UiDMItemMessageStatus
            message.text = "转发: " + statusContent.status.content.raw

        case is UiDMItemMessageDeleted:
            message.text = "此消息已删除"

        default:
            message.text = "未知消息类型: \(type(of: item.content))"
        }

        return message
    }

    // 格式化时间显示
    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }

    // 实现getOriginalMedia方法
    private func getOriginalMedia(from message: ExyteChat.Message) -> UiMedia? {
        // 从字典中获取原始媒体
        originalMediaStore[message.id]
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
                    case let .success(image):
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
                case let .success(image):
                    image
                        .resizable()
                        .scaledToFill()
                        .frame(maxWidth: CGFloat(imageMedia.width), maxHeight: CGFloat(imageMedia.height))
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
                    .frame(width: CGFloat(videoMedia.width), height: CGFloat(videoMedia.height))
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
