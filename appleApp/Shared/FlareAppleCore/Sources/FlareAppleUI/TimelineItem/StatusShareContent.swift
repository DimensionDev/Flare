import SwiftUI
import KotlinSharedUI

public struct StatusShareContent: View {
    let data: UiTimelineV2
    let statusKey: MicroBlogKey
    let replies: [UiTimelineV2]

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
                Text("share_replies_partial", bundle: FlareAppleUILocalization.bundle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                ForEach(replies, id: \.statusKey) { reply in
                    TimelineView(data: reply, detailStatusKey: reply.statusKey, showTranslate: false)
                    Divider()
                }
            }
        }
    }
}

public struct StatusShareReplyPicker: View {
    let state: StatusSharePresenterState

    public init(state: StatusSharePresenterState) { self.state = state }

    public var body: some View {
        VStack(spacing: 12) {
            Text(String(format: String(localized: "share_replies_select", bundle: FlareAppleUILocalization.bundle), Int(state.selectedCount)))
                .font(.headline)
            HStack {
                Button { state.clearSelection() } label: {
                    Text("share_replies_clear", bundle: FlareAppleUILocalization.bundle)
                }
                Spacer()
                Button { state.cancelSelection() } label: {
                    Text("share_replies_cancel", bundle: FlareAppleUILocalization.bundle)
                }
                .keyboardShortcut(.cancelAction)
                Button { state.confirmSelection() } label: {
                    Text("share_replies_done", bundle: FlareAppleUILocalization.bundle)
                }
                .keyboardShortcut(.defaultAction)
            }
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    ForEach(Array(state.replies), id: \.statusKey) { reply in
                        Button { state.toggleReply(key: reply.statusKey) } label: {
                            HStack(alignment: .top) {
                                Image(systemName: state.isSelected(key: reply.statusKey) ? "checkmark.circle.fill" : "circle")
                                    .font(.title2)
                                    .foregroundStyle(Color.accentColor)
                                TimelineView(data: reply, detailStatusKey: reply.statusKey, showTranslate: false)
                                    .allowsHitTesting(false)
                                    .accessibilityHidden(true)
                            }
                            .contentShape(Rectangle())
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
                        Divider()
                    }
                    if state.loading {
                        ProgressView()
                    } else if state.error != nil {
                        Text("share_replies_failed", bundle: FlareAppleUILocalization.bundle)
                        Button { state.loadMore() } label: {
                            Text("share_replies_retry", bundle: FlareAppleUILocalization.bundle)
                        }
                    } else if state.hasMore {
                        Button { state.loadMore() } label: {
                            Text("share_replies_more", bundle: FlareAppleUILocalization.bundle)
                        }
                    } else if state.replies.isEmpty {
                        Text("share_replies_empty", bundle: FlareAppleUILocalization.bundle)
                    }
                }
                .padding(.vertical, 8)
            }
        }
        .padding()
    }
}
