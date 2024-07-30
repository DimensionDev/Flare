import SwiftUI
import shared

//struct BlueskyProfileHeader: View {
//    let user: UiUser.Bluesky
//    let relation: UiState<UiRelation>
//    let isMe: UiState<KotlinBoolean>
//    let onFollowClick: (UiRelation) -> Void
//    var body: some View {
//        CommonProfileHeader(
//            bannerUrl: user.bannerUrl,
//            avatarUrl: user.avatarUrl,
//            displayName: user.extra.nameMarkdown,
//            handle: user.handle,
//            description: user.extra.descriptionMarkdown,
//            headerTrailing: { BlueskyFollowButton(relation: relation, isMe: isMe, onFollowClick: onFollowClick) },
//            content: {
//                MatrixView(
//                    followCount: user.matrices.followsCountHumanized,
//                    fansCount: user.matrices.fansCountHumanized
//                )
//            })
//    }
//}
//
//struct BlueskyFollowButton: View {
//    let relation: UiState<UiRelation>
//    let isMe: UiState<KotlinBoolean>
//    let onFollowClick: (UiRelation) -> Void
//    var body: some View {
//        if case .success(let success) = onEnum(of: isMe),
//           !success.data.boolValue,
//           case .success(let relationData) = onEnum(of: relation),
//           let actualRelation = relationData.data as? UiRelationBluesky {
//            let text = if actualRelation.blocking {
//                String(localized: "relation_blocked")
//            } else if actualRelation.following {
//                String(localized: "relation_following")
//            } else {
//                String(localized: "relation_follow")
//            }
//            Button(action: {
//                onFollowClick(actualRelation)
//            }, label: {
//                Text(text)
//            })
//            .buttonStyle(.borderless)
//        } else {
//            EmptyView()
//        }
//    }
//}
