import Awesome
import Generated
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import os.log
import shared
import SwiftDate
import SwiftUI
import UIKit

struct StatusHeaderViewV2: View {
    let timelineItem: TimelineItem
    let isDetailView: Bool
    @Environment(FlareRouter.self) private var router
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        HStack(alignment: .top) {
            HStack(alignment: .center, spacing: 1) {
                if timelineItem.hasUser, let user = timelineItem.user {
                    UserComponentV2(
                        user: user,
                        topEndContent: timelineItem.topEndContent
                    )
                    .id("UserComponent_\(user.key)")
                    .environment(router)
                }

                Spacer()

                if !isDetailView {
                    Text(timelineItem.getFormattedDate())
                        .foregroundColor(.gray)
                        .font(.caption)
                        .frame(minWidth: 80, alignment: .trailing)
                }
            }
            .padding(.bottom, 1)
        }
        .allowsHitTesting(true)
        .contentShape(Rectangle())
        .onTapGesture {}
    }
}

struct UserComponentV2: View {
    let user: User
    let topEndContent: TopEndContent?

    @Environment(FlareRouter.self) private var router

    var body: some View {
        Button(
            action: {
                let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()
                let userKey = user.createMicroBlogKey()

                router.navigate(to: .profile(
                    accountType: accountType,
                    userKey: userKey
                ))
            },
            label: {
                HStack {
                    UserAvatar(data: user.avatar, size: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        if user.name.markdown.isEmpty {
                            Text(" ")
                                .lineLimit(1)
                                .font(.headline)
                        } else {
                            Markdown(user.name.markdown)
                                .lineLimit(1)
                                .font(.headline)
                                .markdownInlineImageProvider(.emoji)
                        }
                        HStack {
                            Text(user.handleWithoutFirstAt)
                                .lineLimit(1)
                                .font(.subheadline)
                                .foregroundColor(.gray)

                            if let topEndContent {
                                switch topEndContent {
                                case let .visibility(visibilityType):
                                    StatusVisibilityComponentV2(visibility: visibilityType)
                                        .foregroundColor(.gray)
                                        .font(.caption)
                                }
                            }
                        }
                    }
                    .padding(.bottom, 2)
                }
            }
        )
        .buttonStyle(.plain)
    }
}

struct StatusVisibilityComponentV2: View {
    let visibility: VisibilityType

    var body: some View {
        switch visibility {
        case .publicType:
            Image(systemName: "globe")
        case .home:
            Image(systemName: "house")
        case .followers:
            Image(systemName: "person.2")
        case .specified:
            Image(systemName: "envelope")
        }
    }
}
