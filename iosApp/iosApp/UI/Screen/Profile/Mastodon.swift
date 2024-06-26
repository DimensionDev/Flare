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
