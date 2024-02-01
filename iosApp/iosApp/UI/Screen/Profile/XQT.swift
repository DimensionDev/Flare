import SwiftUI
import shared

struct XQTFollowButton: View {
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    var body: some View {
        if case .success(let success) = onEnum(of: isMe),
            !success.data.boolValue,
           case .success(let relationData) = onEnum(of: relation),
           let xqtRelation = relationData.data as? UiRelationXQT {
            let text = if xqtRelation.blocking {
                String(localized: "relation_blocked")
            } else if xqtRelation.following {
                String(localized: "relation_following")
            } else {
                String(localized: "relation_follow")
            }
            Button(action: {
                onFollowClick(xqtRelation)
            }, label: {
                Text(text)
            })
            .buttonStyle(.borderless)
        } else {
            EmptyView()
        }
    }
}

struct XQTMenu: View {
    let relation: UiRelationXQT
    let onMuteClick: () -> Void
    let onBlockClick: () -> Void
    var body: some View {
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

struct XQTProfileHeader: View {
    let user: UiUser.XQT
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
                XQTFollowButton(relation: relation, isMe: isMe, onFollowClick: onFollowClick)
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
