import SwiftUI
import KotlinSharedUI

public struct StatusShareContent: View {
    let data: UiTimelineV2
    let statusKey: MicroBlogKey
    let replies: [UiTimelineV2]
    @Environment(\.timelineAppearance) private var timelineAppearance

    public init(data: UiTimelineV2, statusKey: MicroBlogKey, replies: [UiTimelineV2]) {
        self.data = data
        self.statusKey = statusKey
        self.replies = replies
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            TimelineView(data: data, detailStatusKey: statusKey, showTranslate: false)
            if !replies.isEmpty {
                Divider()
                ForEach(Array(replies.enumerated()), id: \.element.statusKey) { index, reply in
                    if index > 0 { Divider() }
                    TimelineView(data: reply, detailStatusKey: nil, showTranslate: false)
                        .environment(\.timelineAppearance, timelineAppearance.withSharePreviewDefaults(lineLimit: .max))
                }
            }
        }
        .environment(\.timelineAppearance, timelineAppearance.withSharePreviewDefaults(lineLimit: timelineAppearance.lineLimit))
    }
}

public struct StatusShareReplyPicker: View {
    let state: StatusSharePresenterState

    public init(state: StatusSharePresenterState) { self.state = state }

    public var body: some View {
#if os(iOS)
        NavigationStack {
            replyList
                .navigationTitle(Text("share_replies_title", bundle: FlareAppleUILocalization.bundle))
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { cancelButton }
                    ToolbarItem(placement: .confirmationAction) { doneButton }
                    ToolbarItem(placement: .bottomBar) {
                        HStack {
                            selectionCount
                            Spacer()
                            clearButton
                        }
                    }
                }
        }
#elseif os(macOS)
        VStack(alignment: .leading, spacing: 16) {
            Text("share_replies_title", bundle: FlareAppleUILocalization.bundle)
                .font(.title2.bold())
            replyList
                .clipShape(.rect(cornerRadius: 8))
            HStack {
                selectionCount
                clearButton
                Spacer()
                cancelButton
                doneButton.buttonStyle(.borderedProminent)
            }
        }
        .padding(20)
        .frame(width: 560, height: 640)
        .labelStyle(.titleOnly)
#endif
    }

    private var replyList: some View {
        List {
            ForEach(Array(state.replies), id: \.statusKey) { reply in
                Button { state.toggleReply(key: reply.statusKey) } label: {
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: state.isSelected(key: reply.statusKey) ? "checkmark.circle.fill" : "circle")
                            .font(.title2)
                            .foregroundStyle(state.isSelected(key: reply.statusKey) ? Color.accentColor : Color.secondary)
                        TimelineView(data: reply, detailStatusKey: nil, showTranslate: false)
                            .disabled(true)
                            .allowsHitTesting(false)
                            .accessibilityHidden(true)
                    }
                    .foregroundStyle(.primary)
                    .contentShape(Rectangle())
                    .padding(.vertical, 8)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(reply.searchText ?? "")
                .accessibilityAddTraits(state.isSelected(key: reply.statusKey) ? .isSelected : [])
                if state.expandableReplies.contains(reply.statusKey) {
                    Button { state.loadReplyChildren(key: reply.statusKey) } label: {
                        Text("share_replies_children", bundle: FlareAppleUILocalization.bundle)
                    }
                    .disabled(state.loading)
                }
            }
            if state.loading {
                TimelinePlaceholderView()
                    .padding(.vertical, 8)
            } else if state.error != nil {
                Text("share_replies_failed", bundle: FlareAppleUILocalization.bundle)
                Button { state.loadMore() } label: {
                    Text("share_replies_retry", bundle: FlareAppleUILocalization.bundle)
                }
            } else if state.hasMore {
                Button { state.loadMore() } label: {
                    Text("share_replies_more", bundle: FlareAppleUILocalization.bundle)
                }
                .frame(maxWidth: .infinity, alignment: .center)
            } else if state.replies.isEmpty {
                Text("share_replies_empty", bundle: FlareAppleUILocalization.bundle)
                    .foregroundStyle(.secondary)
            }
        }
        .listStyle(.plain)
    }

    private var selectionCount: some View {
        Text(String(format: String(localized: "share_replies_count", bundle: FlareAppleUILocalization.bundle), Int(state.selectedCount)))
            .font(.subheadline)
            .foregroundStyle(.secondary)
    }

    private var clearButton: some View {
        Button { state.clearSelection() } label: {
            Text("share_replies_clear", bundle: FlareAppleUILocalization.bundle)
        }
        .disabled(state.selectedCount == 0)
    }

    private var cancelButton: some View {
        Button(role: .cancel) { state.cancelSelection() } label: {
            Label {
                Text("share_replies_cancel", bundle: FlareAppleUILocalization.bundle)
            } icon: {
                Image(systemName: "xmark")
            }
        }
        .keyboardShortcut(.cancelAction)
    }

    private var doneButton: some View {
        Button { state.confirmSelection() } label: {
            Label {
                Text("share_replies_done", bundle: FlareAppleUILocalization.bundle)
            } icon: {
                Image(systemName: "checkmark")
            }
        }
        .keyboardShortcut(.defaultAction)
    }
}

private extension TimelineAppearance {
    func withSharePreviewDefaults(lineLimit: Int32) -> TimelineAppearance {
        doCopy(
            avatarShape: avatarShape,
            showMedia: showMedia,
            showSensitiveContent: showSensitiveContent,
            expandContentWarning: true,
            expandMediaSize: expandMediaSize,
            mediaLayout: mediaLayout,
            limitMediaGridToNine: limitMediaGridToNine,
            videoAutoplay: .never,
            showLinkPreview: showLinkPreview,
            compatLinkPreview: compatLinkPreview,
            showEmojiReactions: showEmojiReactions,
            showNumbers: showNumbers,
            postActionStyle: postActionStyle,
            postActionLayout: postActionLayout,
            postActionFixedWidth: postActionFixedWidth,
            fullWidthPost: fullWidthPost,
            absoluteTimestamp: absoluteTimestamp,
            showPlatformLogo: showPlatformLogo,
            timelineDisplayMode: timelineDisplayMode,
            aiConfig: aiConfig,
            lineLimit: lineLimit,
            showTranslateButton: showTranslateButton
        )
    }
}
