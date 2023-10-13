import SwiftUI
import shared
import NetworkImage

struct MisskeyStatusComponent: View {
    let misskey: UiStatus.Misskey
    var body: some View {
        VStack {
            let actual = misskey.renote ?? misskey
            if misskey.renote != nil {
                StatusRetweetHeaderComponent(iconSystemName: "arrow.left.arrow.right", nameMarkdown: misskey.user.extra.nameMarkdown, text: "boosted a status")
            }
            CommonStatusComponent(content: actual.extra.contentMarkdown, avatar: actual.user.avatarUrl, name: actual.user.extra.nameMarkdown, handle: actual.user.handle, userKey: actual.user.userKey, medias: actual.media, timestamp: actual.createdAt.epochSeconds, headerTrailing: {
                MisskeyVisibilityIcon(visibility: actual.visibility)
            })
            ScrollView(.horizontal) {
                LazyHStack {
                    ForEach(1...misskey.reaction.emojiReactions.count, id: \.self) { index in
                        let reaction = misskey.reaction.emojiReactions[index - 1]
                        HStack {
                            NetworkImage(url: URL(string: reaction.url))
                            Text(reaction.humanizedCount)
                        }
                    }
                }
            }

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
