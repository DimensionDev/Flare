import AVKit
import ExyteChat
import Kingfisher
import ObjectiveC
import shared
import SwiftUI
import CoreMedia

// 用于存储原始媒体的字典
// Global store for original media objects, keyed by message ID
var originalMediaStore: [String: UiMedia] = [:]


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
                    ChatView(
                        messages: convertMessages(success),
                        chatType: .conversation
                    ) { draft in
                        // 暂时只实现UI部分，不处理发送逻辑
                        print("发送消息: \(draft.text)")
                    }
                    messageBuilder: { message, positionInGroup, positionInMessagesSection, positionInCommentsGroup,
                        showContextMenuClosure, messageActionClosure, showAttachmentClosure in
                        DMChatMessageView(
                            message: message,
                            positionInGroup: positionInGroup,
                            positionInMessagesSection: positionInMessagesSection,
                            positionInCommentsGroup: positionInCommentsGroup,
                            showContextMenuClosure: showContextMenuClosure,
                            messageActionClosure: messageActionClosure,
                            showAttachmentClosure: showAttachmentClosure 
                        )
                    }
                    messageMenuAction: { (action: DMMessageMenuActionAction, defaultActionClosure, message) in
                        switch action {
                        case .copy:
                            defaultActionClosure(message, .copy)
                        case .reply:
                            defaultActionClosure(message, .reply)
                        case .edit:
                            defaultActionClosure(message, .edit { editedText in
                                // update this message's text in your datasource
                                print(editedText)
                            })
                        case .delete:
                            print(message.text)
                        case .print:
                            print(message.text)
                        }

                   } 
                    .setAvailableInputs([.text]) // Only allow text input for now
                    .chatTheme(
                        ChatTheme(
                            colors: .init(
                                sendButtonBackground: Color.accentColor
                            )
                        )
                    )
                } else {
                    // Empty state when no messages
                    VStack(spacing: 16) {
                        Spacer()
                        Text("")
                            .foregroundColor(.gray)
                        Button("刷新") {
                            refreshTrigger.toggle()
                            // Recreate presenter to trigger refresh
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
                // Loading state
                ProgressView()
                    .scaleEffect(1.5)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if case .error = onEnum(of: state.items) {
                // Error state
                VStack(spacing: 16) {
                    Spacer()
                    Text("加载失败")
                        .foregroundColor(.red)
                    Button("重试") {
                        refreshTrigger.toggle()
                        // Recreate presenter to trigger refresh
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

    /// Converts KMP `PagingStateSuccess<UiDMItem>` to `[ExyteChat.Message]`.
    private func convertMessages(_ success: PagingStateSuccess<UiDMItem>) -> [ExyteChat.Message] {
        var messages: [ExyteChat.Message] = []

        // Define the current user for ExyteChat
        let currentUser = ExyteChat.User(
            id: UUID().uuidString,
            name: "Me",
            avatarURL: nil,
            isCurrentUser: true
        )

        // Iterate through KMP items and convert them
        for index in 0 ..< success.itemCount {
            if let message = success.peek(index: index) {
                if let chatMessage = convertToChatMessage(message) {
                    messages.append(chatMessage)
                }

                // Trigger loading of the actual item data
                success.get(index: index)
            }
        }

        // Debug print if the list is empty despite success state
        if messages.isEmpty {
            print("消息列表为空，但状态是成功")
        }

        return messages
    }

    /// Converts a single `UiDMItem` from KMP to an `ExyteChat.Message`.
    private func convertToChatMessage(_ item: UiDMItem) -> ExyteChat.Message? {
        // Map KMP user to ExyteChat user
        let chatUser = ExyteChat.User(
            id: item.user.key.description,
            name: item.user.name.raw,
            avatarURL: URL(string: item.user.avatar),
            isCurrentUser: item.isFromMe
        )

        // Create the basic ExyteChat message structure
        var message = ExyteChat.Message(
            id: item.id,
            user: chatUser,
            createdAt: item.timestamp
        )

        // Map KMP send state to ExyteChat message status
        if let sendState = item.sendState {
            let className = String(describing: type(of: sendState))

            if className.contains("Sending") {
                message.status = .sending
            } else if className.contains("Failed") {
                // Use an empty draft message for the error state payload
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

        // Map KMP message content to ExyteChat message properties
        switch item.content {
        case is UiDMItemMessageText:
            let textContent = item.content as! UiDMItemMessageText
            message.text = textContent.text.raw

        case is UiDMItemMessageMedia:
            let mediaContent = item.content as! UiDMItemMessageMedia
            // Store the original KMP media object for later use (e.g., photo browser)
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
                    // Treat GIFs as video attachments for display purposes
                    message.attachments = [
                        ExyteChat.Attachment(
                            id: UUID().uuidString,
                            url: url,
                            type: .video // Treat GIFs as video for auto-play
                        ),
                    ]
                }

            case is UiMediaAudio:
                let audioMedia = mediaContent.media as! UiMediaAudio
                if let url = URL(string: audioMedia.url) {
                 
                    let recording = ExyteChat.Recording(
                        duration: 0,
                        waveformSamples: [],
                        url: url
                    )
                    message.recording = recording
                }

            default:
                // 其他未知媒体类型
                message.text = "Unsupported media type: \(type(of: mediaContent.media))"
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

    /// Formats a Date into a short time string (e.g., "10:30 AM").
    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }

    /// Retrieves the original KMP `UiMedia` object associated with an `ExyteChat.Message` ID.
    private func getOriginalMedia(from message: ExyteChat.Message) -> UiMedia? {
        // 从字典中获取原始媒体
        // Retrieve from the global store populated during message conversion
        originalMediaStore[message.id]
    }
}

