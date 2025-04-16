import AVKit
import ExyteChat
import Kingfisher
import ObjectiveC
import shared
import SwiftUI
import CoreMedia
 
struct DMMessageView: View {
    let message: ExyteChat.Message
    let positionInGroup: ExyteChat.PositionInUserGroup
    let positionInMessagesSection: ExyteChat.PositionInMessagesSection
    let positionInCommentsGroup: ExyteChat.CommentsPosition?
    let showContextMenuClosure: () -> Void
    let messageActionClosure: (ExyteChat.Message, ExyteChat.DefaultMessageMenuAction) -> Void
    let showAttachmentClosure: (ExyteChat.Attachment) -> Void
    
    var body: some View {
        VStack(alignment: message.user.isCurrentUser ? .trailing : .leading, spacing: 4) {
           
            VStack(alignment: message.user.isCurrentUser ? .trailing : .leading, spacing: 4) {
                // 文本消息
                if !message.text.isEmpty {
                    HStack(alignment: .center, spacing: 8) {
                        Text(message.text)
                            .foregroundColor(message.user.isCurrentUser ? .white : .primary)
//                            .onTapGesture {
//                                showContextMenuClosure()
//                            }

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
                    ForEach(message.attachments, id: \.id) { attachment in
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
//                                    .onTapGesture {
//                                        showAttachmentClosure(attachment)
//                                    }

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
//                                        .onTapGesture {
//                                            showContextMenuClosure()
//                                        }

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
//        .contentShape(Rectangle())
//        .onTapGesture {
//            showContextMenuClosure()
//        }
        
        if let positionInCommentsGroup {
            if positionInCommentsGroup.isLastInCommentsGroup {
                Color.gray.frame(height: 0.5)
                    .padding(.vertical, 10)
            } else if positionInCommentsGroup.isLastInChat {
                Color.clear.frame(height: 5)
            } else {
                Color.clear.frame(height: 10)
            }
        }
    }
    
    
    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
    
    
    private func getOriginalMedia(from message: ExyteChat.Message) -> UiMedia? {
        // 从字典中获取原始媒体
        originalMediaStore[message.id]
    }
}
