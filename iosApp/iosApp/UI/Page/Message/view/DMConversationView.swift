import AVKit
import CoreMedia
import ExyteChat
import Kingfisher
import ObjectiveC
import shared
import SwiftUI

var originalMediaStore: [String: UiMedia] = [:]

struct DMConversationView: View {
    let accountType: AccountType
    let roomKey: MicroBlogKey
    let title: String
    @State private var presenter: DMConversationPresenter
    @State private var refreshTrigger = false
    @Environment(FlareTheme.self) private var theme
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
                    .background(theme.primaryBackgroundColor)
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .navigationTitle(title)
        .safeAreaPadding(.bottom, 50)
    }

    @ViewBuilder
    private func conversationContent(state: DMConversationState) -> some View {
        VStack {
            if case let .success(success) = onEnum(of: state.items) {
                if success.itemCount > 0 {
                    ChatView(
                        messages: convertMessages(success),
                        chatType: .conversation
                    ) { draft in
                        if !draft.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            state.send(message: draft.text)
                        }
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

                                FlareLog.debug("DMConversation Edit message: \(editedText)")
                            })
                        case .delete:
                            FlareLog.debug("DMConversation Delete message: \(message.text)")
                        case .print:
                            FlareLog.debug("DMConversation Print message: \(message.text)")
                        }
                    }
                    .setAvailableInputs([.text])
                    .chatTheme(
                        ChatTheme(
                            colors: .init(
                                sendButtonBackground: Color.accentColor
                            )
                        )
                    )
                } else {
                    VStack(spacing: 16) {
                        Spacer()
                        Text("")
                            .foregroundColor(.gray)
                        Button("Refresh") {
                            refreshTrigger.toggle()

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
                ProgressView()
                    .scaleEffect(1.5)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if case .error = onEnum(of: state.items) {
                VStack(spacing: 16) {
                    Spacer()
                    Text("Loading error")
                        .foregroundColor(.red)
                    Button("Refresh") {
                        refreshTrigger.toggle()

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

    private func convertMessages(_ success: PagingStateSuccess<UiDMItem>) -> [ExyteChat.Message] {
        var messages: [ExyteChat.Message] = []

        for index in 0 ..< success.itemCount {
            if let message = success.peek(index: index) {
                if let chatMessage = convertToChatMessage(message) {
                    messages.append(chatMessage)
                    FlareLog.debug("DMConversation Message text: \(chatMessage.text)")
                    FlareLog.debug("DMConversation Message status: \(chatMessage.status)")
                }

                success.get(index: index)
            }
        }

        if messages.isEmpty {
            FlareLog.debug("DMConversation No Message")
        }

        return messages
    }

    private func convertToChatMessage(_ item: UiDMItem) -> ExyteChat.Message? {
        let chatUser = ExyteChat.User(
            id: item.user.key.description,
            name: item.user.name.raw,
            avatarURL: URL(string: item.user.avatar),
            isCurrentUser: item.isFromMe
        )

        var message = ExyteChat.Message(
            id: item.id,
            user: chatUser,
            createdAt: item.timestamp
        )

        if let sendState = item.sendState as? UiDMItem.SendState {
            if sendState == UiDMItem.SendState.sending {
                message.status = .sending
            } else if sendState == UiDMItem.SendState.failed {
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

        switch item.content {
        case is UiDMItemMessageText:
            let textContent = item.content as! UiDMItemMessageText
            message.text = textContent.text.raw

        case is UiDMItemMessageMedia:
            let mediaContent = item.content as! UiDMItemMessageMedia

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
                    message.attachments = [
                        ExyteChat.Attachment(
                            id: UUID().uuidString,
                            url: url,
                            type: .video
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

                message.text = "Unsupported media type: \(type(of: mediaContent.media))"
            }

        case is UiDMItemMessageStatus:
            let statusContent = item.content as! UiDMItemMessageStatus
            message.text = "Repost : " + statusContent.status.content.raw

        case is UiDMItemMessageDeleted:
            message.text = "message deleted"

        default:
            message.text = "unknown message type: \(type(of: item.content))"
        }

        return message
    }

    private func getOriginalMedia(from message: ExyteChat.Message) -> UiMedia? {
        originalMediaStore[message.id]
    }
}
