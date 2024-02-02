import SwiftUI
import shared

struct MastodonFollowButton: View {
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    var body: some View {
        if case .success(let success) = onEnum(of: isMe),
            !success.data.boolValue,
           case .success(let relationData) = onEnum(of: relation),
           let mastodonRelation = relationData.data as? UiRelationMastodon {
            let text = if mastodonRelation.blocking {
                String(localized: "relation_blocked")
            } else if mastodonRelation.following {
                String(localized: "relation_following")
            } else if mastodonRelation.requested {
                String(localized: "relation_requested")
            } else {
                String(localized: "relation_follow")
            }
            Button(action: {
                onFollowClick(mastodonRelation)
            }, label: {
                Text(text)
            })
            .buttonStyle(.borderless)
        } else {
            EmptyView()
        }
    }
}

struct MastodonMenu: View {
    let relation: UiRelationMastodon
    let onMuteClick: () -> Void
    let onBlockClick: () -> Void
    var body: some View {
        Button(action: onMuteClick, label: {
            let text = if relation.muting {
                String(localized: "unmute")
            } else {
                String(localized: "mute")
            }
            let icon = if relation.muting {
                "speaker"
            } else {
                "speaker.slash"
            }
            Label(text, systemImage: icon)
        })
        Button(action: onBlockClick, label: {
            let text = if relation.blocking {
                String(localized: "unblock")
            } else {
                String(localized: "block")
            }
            let icon = if relation.blocking {
                "xmark.circle"
            } else {
                "checkmark.circle"
            }
            Label(text, systemImage: icon)
        })
    }
}

struct MastodonProfileHeader: View {
    let user: UiUser.Mastodon
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    var body: some View {
        CommonProfileHeader(
            bannerUrl: user.bannerUrl,
            avatarUrl: user.avatarUrl,
            displayName: user.extra.nameMarkdown,
            handle: user.handle,
            description: user.extra.descriptionMarkdown,
            headerTrailing: {
                MastodonFollowButton(relation: relation, isMe: isMe, onFollowClick: onFollowClick)
            }, content: {
                VStack(alignment: .leading) {
                    MatrixView(
                        followCount: user.matrices.followsCountHumanized,
                        fansCount: user.matrices.fansCountHumanized
                    )
                    FieldsView(fields: user.extra.fieldsMarkdown)
                }
            })
    }
}
