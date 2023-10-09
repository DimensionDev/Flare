import SwiftUI
import shared

struct MisskeyStatusComponent: View {
    let misskey: UiStatus.Misskey
    var body: some View {
        VStack {
            let actual = misskey.renote ?? misskey
            CommonStatusComponent(content: actual.extra.contentMarkdown, avatar: actual.user.avatarUrl, name: actual.user.extra.nameMarkdown, handle: actual.user.handle, userKey: actual.user.userKey, medias: actual.media, timestamp: actual.createdAt.epochSeconds, headerTrailing: {
                MisskeyVisibilityIcon(visibility: actual.visibility)
            })
            Spacer()
                .frame(height: 8)
            HStack {
                Button(action: {
                }) {
                    HStack {
                        Image(systemName: "arrowshape.turn.up.left")
                        if let humanizedReplyCount = actual.matrices.humanizedReplyCount {
                            Text(humanizedReplyCount)
                        }
                    }
                }
                Spacer()
                Button(action: {}) {
                    HStack {
                        Image(systemName: "arrow.left.arrow.right")
                        if let humanizedReNoteCount = actual.matrices.humanizedReNoteCount {
                            Text(humanizedReNoteCount)
                        }
                    }
                }
                Spacer()
                Button(action: {}) {
                    HStack {
                        Image(systemName: "star")
                    }
                }
                Spacer()
                Button(action: {}) {
                    Image(systemName: "ellipsis")
                }
            }
            .buttonStyle(.borderless)
            .tint(.primary)
            .opacity(0.6)
            .font(.caption)
        }
    }
}
