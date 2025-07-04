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

// MARK: - Swift原生AccountType定义
//enum SwiftAccountType {
//    case specific(accountKey: String)  // 简化版，使用String代替MicroBlogKey
//    case active
//    case guest
//}
//
//// MARK: - Swift原生MicroBlogKey定义
//struct SwiftMicroBlogKey {
//    let id: String
//    let host: String
//
//    init(id: String, host: String) {
//        self.id = id
//        self.host = host
//    }
//}

struct StatusHeaderViewV2: View {
    // 修改参数：使用TimelineItem替代StatusViewModel
    let item: TimelineItem
    let isDetailView: Bool
    @Environment(FlareRouter.self) private var router
    @Environment(FlareTheme.self) private var theme
    
    var body: some View {
        HStack(alignment: .top) {
            HStack(alignment: .center, spacing: 1) {
                // 使用TimelineItem的user数据
                if item.hasUser, let user = item.user {
                    UserComponentV2(
                        user: user,
                        topEndContent: item.topEndContent
                    )
                    .id("UserComponent_\(user.key)")
                    .environment(router)
                }

                Spacer()

                // 使用TimelineItem的isDetailView和格式化时间
                if !isDetailView {
                    Text(item.getFormattedDate())
                        .foregroundColor(.gray)
                        .font(.caption)
                        .frame(minWidth: 80, alignment: .trailing)
                }
            }
            .padding(.bottom, 1)
        }
        .allowsHitTesting(true)
        .contentShape(Rectangle())
        .onTapGesture {
            // 空的手势处理
        }
    }
}

// MARK: - UserComponentV2 (适配Swift数据类型)

struct UserComponentV2: View {
    let user: User                    // 使用Swift User类型
    let topEndContent: TopEndContent? // 使用Swift TopEndContent类型

    @Environment(FlareRouter.self) private var router

    var body: some View {
        Button(
            action: {
                // 🔥 实现用户点击跳转到用户页面
                let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()
                let userKey = createMicroBlogKey(from: user)

                FlareLog.debug("UserComponent Navigate to profile: \(user.key)")
                router.navigate(to: .profile(
                    accountType: accountType,
                    userKey: userKey
                ))
            },
            label: {
                HStack {
                    UserAvatar(data: user.avatar, size: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        // 显示用户名 - 使用Swift RichText
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

                            // 显示可见性图标 - 使用Swift TopEndContent
                            if let topEndContent = topEndContent {
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

    // MARK: - 辅助方法

    /// 从User创建MicroBlogKey
    private func createMicroBlogKey(from user: User) -> MicroBlogKey {
        // User.key已经是String格式的ID，需要推断host
        let host = extractHostFromHandle(user.handle)
        return MicroBlogKey(id: user.key, host: host)
    }

    /// 从用户handle提取host信息
    private func extractHostFromHandle(_ handle: String) -> String {
        // handle格式通常是 @username@host 或 @username
        if handle.contains("@") {
            let components = handle.components(separatedBy: "@")
            if components.count >= 3 {
                // @username@host 格式
                return components[2]
            } else if components.count == 2 {
                // @username 格式，需要根据其他信息推断
                return "mastodon.social" // 默认值
            }
        }
        return "unknown.host"
    }
}

// MARK: - StatusVisibilityComponentV2 (适配Swift VisibilityType)

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
 
