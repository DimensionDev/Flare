import SwiftUI
import shared

struct BlueskyProfileHeader: View {
    let user: UiUser.Bluesky
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
            headerTrailing: { BlueskyFollowButton(relation: relation, isMe: isMe, onFollowClick: onFollowClick) },
            content: {
                MatrixView(
                    followCount: user.matrices.followsCountHumanized,
                    fansCount: user.matrices.fansCountHumanized
                )
            })
    }
}

struct BlueskyFollowButton: View {
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    var body: some View {
        if case .success(let success) = onEnum(of: isMe),
           !success.data.boolValue,
           case .success(let relationData) = onEnum(of: relation),
           let actualRelation = relationData.data as? UiRelationBluesky {
            let text = if actualRelation.blocking {
                "Blocked"
            } else if actualRelation.following {
                "Following"
            } else {
                "Follow"
            }
            Button(action: {
                onFollowClick(actualRelation)
            }, label: {
                Text(text)
            })
            .buttonStyle(.borderless)
        } else {
            EmptyView()
        }
    }
}

struct BlueskyMenu: View {
    let relation: UiRelationBluesky
    let onMuteClick: () -> Void
    let onBlockClick: () -> Void
    var body: some View {
        Button(action: onMuteClick, label: {
            let text = if relation.muting {
                "Unmute"
            } else {
                "Mute"
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
                "Unblock"
            } else {
                "Block"
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
