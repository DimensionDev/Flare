import AVKit
import ExyteChat
import Kingfisher
import ObjectiveC
import shared
import SwiftUI
import CoreMedia
 

struct DMChatMessageView: View {
    let message: ExyteChat.Message
    let positionInGroup: ExyteChat.PositionInUserGroup
    let positionInMessagesSection: ExyteChat.PositionInMessagesSection
    let positionInCommentsGroup: ExyteChat.CommentsPosition?
    let showContextMenuClosure: () -> Void
    let messageActionClosure: (ExyteChat.Message, ExyteChat.DefaultMessageMenuAction) -> Void
    let showAttachmentClosure: (ExyteChat.Attachment) -> Void

    // --- Mimic MessageView Properties ---
    // Make constants static if they are truly constant for the view type
    static let widthWithMedia: CGFloat = 204
    static let horizontalAttachmentPadding: CGFloat = 1
    
    let font: UIFont = UIFont.systemFont(ofSize: 15)
    let messageStyler: (String) -> AttributedString = AttributedString.init
    let avatarSize: CGFloat = 32
    let horizontalNoAvatarPadding: CGFloat = 8
    let horizontalAvatarPadding: CGFloat = 8
    let horizontalTextPadding: CGFloat = 12
    let statusViewSize: CGFloat = 14
    let horizontalStatusPadding: CGFloat = 8
    let horizontalBubblePadding: CGFloat = 70
    let messageLinkPreviewLimit: Int = 8
    let showMessageTimeView: Bool = true
    @State private var avatarViewSize: CGSize = .zero
    @State private var statusSize: CGSize = .zero
    @State private var timeSize: CGSize = .zero
    @State private var bubbleVStackSize: CGSize = .zero

    // --- Date Arrangement Logic (Copied and adapted from MessageView) ---
    enum DateArrangement {
        case hstack, vstack, overlay
    }

    var dateArrangement: DateArrangement {
        guard !message.text.isEmpty else { return .vstack }

        let timeWidth = timeSize.width + 10
        // Calculate available width *first*
        let availableTextWidth = calculateAvailableTextWidth()
        let styledText = message.text.styled(using: messageStyler)
        let textHeight = styledText.height(withConstrainedWidth: availableTextWidth, font: font)
        let approxNumberOfLines = Int(ceil(textHeight / font.lineHeight))

        if approxNumberOfLines <= 1 {
            // Estimate text width (might need a proper AttributedString width calculation)
            let estimatedTextWidth = styledText.width(withConstrainedWidth: availableTextWidth, font: font)
            if estimatedTextWidth + timeWidth < availableTextWidth {
                 print("[\(message.id.prefix(4))] DateArrangement: hstack (estimated)")
                 return .hstack
            }
        }
        
         print("[\(message.id.prefix(4))] DateArrangement: vstack (default/fallback)")
        return .vstack
    }
    
    func calculateAvailableTextWidth() -> CGFloat {
        let textPaddings = horizontalTextPadding * 2 // Define textPaddings here
        let bubblePadding = horizontalBubblePadding
        let sidePadding = message.user.isCurrentUser ? 
            (horizontalStatusPadding + statusViewSize + horizontalAvatarPadding) : // Corrected order/components on right
            (horizontalAvatarPadding + avatarSize + horizontalAvatarPadding)
            
        let screenWidth = UIScreen.main.bounds.width
        let availableWidth = screenWidth - bubblePadding - sidePadding - textPaddings
        
        let finalWidth = message.attachments.isEmpty ? availableWidth : DMChatMessageView.widthWithMedia - textPaddings
         print("[\(message.id.prefix(4))] Calculated availableTextWidth: \(finalWidth)")
        return finalWidth
    }
    // --- End Date Arrangement Logic ---

    // --- Computed Properties (Padding/Avatar visibility - Adjusted slightly) ---
    var showAvatar: Bool {
        let isConversation = true
        return positionInGroup == .single || (isConversation && positionInGroup == .last)
    }

    var topPadding: CGFloat {
        let isFirstInGroup = (positionInGroup == .first || positionInGroup == .single)
        return isFirstInGroup ? 8 : 4
    }

    var bottomPadding: CGFloat {
        return 0
    }
    // --- End Computed Properties ---

    var body: some View {
        HStack(alignment: .bottom, spacing: 0) {
            // --- Avatar (Left side for other users) ---
            if !message.user.isCurrentUser {
                avatarView
                    .sizeGetter($avatarViewSize)
            }

            // --- Main Content Bubble Area (VStack) ---
            VStack(alignment: message.user.isCurrentUser ? .trailing : .leading, spacing: 2) {
                // TODO: Add Reply Bubble View later if needed
                bubbleView(message)
                    .sizeGetter($bubbleVStackSize)
                    .onAppear { print("[\(message.id.prefix(4))] Bubble VStack Appeared Size: \(bubbleVStackSize)") }
                    .onChange(of: bubbleVStackSize) { print("[\(message.id.prefix(4))] Bubble VStack Size Changed: \($0)") }
            }

            // --- Status View (Right side for current user) ---
            if message.user.isCurrentUser, let status = message.status {
                 Image(systemName: statusIconName(for: status))
                     .resizable()
                     .scaledToFit()
                     .foregroundColor(.gray)
                     .frame(width: statusViewSize, height: statusViewSize)
                     .padding(.trailing, horizontalStatusPadding)
                     .sizeGetter($statusSize)
            }
        }
        // Apply padding EXACTLY like official MessageView
        .padding(.top, topPadding)
        .padding(.bottom, bottomPadding)
        .padding(.trailing, message.user.isCurrentUser ? horizontalNoAvatarPadding : 0)
        .padding(.leading, message.user.isCurrentUser ? 0 : horizontalNoAvatarPadding)
        // Then apply the bubble padding on the opposite side
        .padding(message.user.isCurrentUser ? .leading : .trailing, horizontalBubblePadding)
        .frame(maxWidth: .infinity, alignment: message.user.isCurrentUser ? .trailing : .leading)
        .onAppear { print("[\(message.id.prefix(4))] Message Appeared. Text: '\(message.text)', Length: \(message.text.count)") }
    }

    // --- Helper View Builders ---
    @ViewBuilder
    private var avatarView: some View {
        Group {
             if showAvatar {
                 KFImage(message.user.avatarURL)
                     // Apply KFImage specific modifiers first
                     .cacheOriginalImage()
                     .appendProcessor(DownsamplingImageProcessor(size: CGSize(width: avatarSize * 2, height: avatarSize * 2)))
                     .fade(duration: 0.25)
                     .onFailure { error in
                         print("KFImage failed for avatar: \(error.localizedDescription)")
                     }
                     // Then apply general SwiftUI modifiers
                     .resizable()
                     .scaledToFill()
                     .frame(width: avatarSize, height: avatarSize)
                     .clipShape(Circle())
                     .background { // Use background for placeholder if needed
                         Circle().fill(Color.gray.opacity(0.5))
                            .frame(width: avatarSize, height: avatarSize)
                     }
                     .padding(.horizontal, horizontalAvatarPadding)
             } else {
                 Spacer().frame(width: avatarSize + horizontalAvatarPadding * 2)
             }
         }
    }

    @ViewBuilder
    private func bubbleView(_ message: ExyteChat.Message) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            // --- Attachments --- 
            if !message.attachments.isEmpty {
                 attachmentsView(message)
                 // Add padding similar to official implementation if needed
                    .padding(.top, DMChatMessageView.horizontalAttachmentPadding)
                    .padding(.horizontal, DMChatMessageView.horizontalAttachmentPadding)
            }
            
            // --- Text and Time --- 
            if !message.text.isEmpty {
                 textWithTimeView(message)
                    .font(Font(font)) // Apply the font
            }

            // --- Recording / Audio --- 
            if let recording = message.recording,
               let url = recording.url,
               // Attempt to get the corresponding UiMediaAudio object
               let media = getOriginalMedia(from: message), media is UiMediaAudio {
                DMAudioMessageView(
                    url: url,
                    media: media, // Already checked it's UiMediaAudio
                    isCurrentUser: message.user.isCurrentUser
                )
                // Add padding similar to MessageView's recordingView
                .padding(.horizontal, horizontalTextPadding)
                .padding(.top, 8)
                // If there's no text or attachments, audio needs a timestamp separately
                if message.text.isEmpty && message.attachments.isEmpty && showMessageTimeView {
                     HStack {
                         Spacer()
                         messageTimeView(date: message.createdAt)
                     }
                     .padding(.trailing, horizontalTextPadding)
                     .padding(.bottom, 8)
                 }
            }
        }
        .bubbleBackground(message)
        // TODO: Add reactions view later if needed
    }

    @ViewBuilder
    private func attachmentsView(_ message: ExyteChat.Message) -> some View {
        // Fetch our custom media object
        if let media = getOriginalMedia(from: message),
           // Get the corresponding ExyteChat.Attachment (assuming first for now)
           let attachment = message.attachments.first { 

            // Display Image or Video using DMSingleMediaView
            if media is UiMediaImage || media is UiMediaVideo || media is UiMediaGif {
                DMSingleMediaView(
                    viewModel: DMMediaViewModel.from(media),
                    media: media
                )
                // Apply sizing similar to old DMMessageView
                .frame(
                    width: min(200, UIScreen.main.bounds.width * 0.7), // Use UIScreen carefully or replace
                    height: min(200, UIScreen.main.bounds.height * 0.4)
                )
                // Use a consistent corner radius (matching bubbleBackground's logic for attachments)
                .cornerRadius(12) 
                .clipped() // Ensure content stays within bounds
//                .onTapGesture {
//                    showAttachmentClosure(attachment) // Use the closure
//                }
                .overlay(alignment: .bottomTrailing) {
                    // Show time overlay ONLY if text is empty
                    if message.text.isEmpty && showMessageTimeView {
                        messageTimeView(date: message.createdAt)
                             // Use capsule background similar to official MessageView
                            .modifier(MessageTimeCapsuleModifier())
                            .padding(4) // Padding around the capsule
                            .padding(8) // Padding from the corner
                    }
                }
            } else {
                 // Placeholder for other media types (e.g., audio, file)
                 Text("[Unsupported Media: \(String(describing: type(of: media)))]")
                     .font(.caption)
                     .foregroundColor(.red)
                     .padding()
                     .background(Color.gray.opacity(0.2))
                     .cornerRadius(12)
             }
        } else {
             // Placeholder if media or attachment not found
             Text("[Media/Attachment Error for msg \(message.id.prefix(4))]")
                 .font(.caption)
                 .foregroundColor(.red)
                 .padding()
                 .background(Color.gray.opacity(0.1))
                 .cornerRadius(12)
         }
    }

    @ViewBuilder
    private func textWithTimeView(_ message: ExyteChat.Message) -> some View {
        let textView = Text(message.text)
            .fixedSize(horizontal: false, vertical: true) // IMPORTANT: Added fixedSize
            .padding(.horizontal, horizontalTextPadding)
            .padding(.vertical, 8) // Consolidated padding
        
        let timeView = messageTimeView(date: message.createdAt)
            .padding(.horizontal, horizontalTextPadding) // Padding for time
            .padding(.bottom, 8) // Padding for time

        // Apply layout based on calculated arrangement
        switch dateArrangement {
        case .hstack:
            HStack(alignment: .lastTextBaseline, spacing: 12) {
                textView
                if !message.attachments.isEmpty { Spacer() } // Match official behavior
                timeView
            }
        case .vstack:
            VStack(alignment: .leading, spacing: 4) { // Use leading alignment for text
                textView
                HStack { // Push time to the right within the vstack
                     Spacer()
                     timeView
                 }
            }
        case .overlay:
             // Basic overlay implementation (Might need adjustment)
             textView
                 .overlay(alignment: .bottomTrailing) {
                     timeView.padding(EdgeInsets(top: 0, leading: 0, bottom: 2, trailing: 5)) // Fine-tune padding
                 }
        }
    }

    @ViewBuilder
    private func messageTimeView(date: Date) -> some View {
        Text(date, style: .time)
            .font(.caption)
            .foregroundColor(message.user.isCurrentUser ? .white.opacity(0.7) : .gray)
            .sizeGetter($timeSize) // Measure time view
    }
    // --- End Helper View Builders ---
    
    // --- Helper Function for Placeholder Status --- 
    private func statusIconName(for status: ExyteChat.Message.Status) -> String {
        switch status {
        case .sending: return "arrow.up.circle"
        case .sent: return "checkmark.circle"
        case .read: return "checkmark.circle.fill"
        case .error: return "exclamationmark.circle"
        }
    }

    // --- Helper Functions ---
    private func getOriginalMedia(from message: ExyteChat.Message) -> UiMedia? {
         // Assuming originalMediaStore is accessible globally or passed in
         // Need to ensure originalMediaStore is populated correctly elsewhere
         originalMediaStore[message.id]
     }
}

// --- ViewModifier to mimic .bubbleBackground ---
struct BubbleBackgroundModifier: ViewModifier {
    let message: ExyteChat.Message
    let radius: CGFloat
    let widthWithMedia: CGFloat
    let horizontalAttachmentPadding: CGFloat

    init(message: ExyteChat.Message, widthWithMedia: CGFloat, horizontalAttachmentPadding: CGFloat) {
        self.message = message
        self.radius = !message.attachments.isEmpty ? 12 : 20
        self.widthWithMedia = widthWithMedia
        self.horizontalAttachmentPadding = horizontalAttachmentPadding
    }

    func body(content: Content) -> some View {
        content
            .frame(width: message.attachments.isEmpty ? nil : self.widthWithMedia + (message.attachments.count > 1 ? self.horizontalAttachmentPadding * 2 : 0))
            .foregroundColor(message.user.isCurrentUser ? .white : .primary)
            .background {
                // Apply background if text OR recording exists (match official logic)
                if !message.text.isEmpty || message.recording != nil {
                    RoundedRectangle(cornerRadius: radius)
                        .fill(message.user.isCurrentUser ? Color.accentColor : Color(uiColor: .systemGray5))
                }
            }
            .cornerRadius(message.text.isEmpty && message.recording == nil ? radius : 0) // Apply corner radius directly only if no background (i.e. media only)
    }
}

// Modifier for the capsule background on time overlay for media
struct MessageTimeCapsuleModifier: ViewModifier {
     func body(content: Content) -> some View {
         content
             .padding(.vertical, 4)
             .padding(.horizontal, 8)
             .background(Capsule().fill(Color.black.opacity(0.4)))
     }
 }

extension View {
    func bubbleBackground(_ message: ExyteChat.Message) -> some View {
        self.modifier(BubbleBackgroundModifier(message: message, 
                                             widthWithMedia: DMChatMessageView.widthWithMedia, 
                                             horizontalAttachmentPadding: DMChatMessageView.horizontalAttachmentPadding))
    }
}
