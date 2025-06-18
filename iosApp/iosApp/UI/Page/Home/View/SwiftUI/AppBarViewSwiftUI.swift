import shared
import SwiftUI

struct AppBarViewSwiftUI: View {
    @Binding var selectedHomeAppBarTab: String
    let tabs: [FLTabItem]
    let user: UiUserV2?
    let accountType: AccountType
    let onAvatarTap: () -> Void
    let onSettingsTap: () -> Void
    let onScrollToTop: (String) -> Void
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        HStack(spacing: 0) {
            // 头像按钮
            Button(action: onAvatarTap) {
                if let user {
                    UserAvatar(data: user.avatar, size: 29)
                        .clipShape(Circle())
                        .padding(.trailing, 6)
                } else {
                    UserAvatarPlaceholder(size: 29)
                        .clipShape(Circle())
                        .padding(.trailing, 6)
                }
            }
            .frame(width: 44)
            .padding(.leading, 8)

            // 标签栏
            TabItemsViewSwiftUI(
                selection: $selectedHomeAppBarTab,
                items: tabs,
                onScrollToTop: onScrollToTop
            )

            // 设置按钮 - 仅在 登录模式 下显示
            if !(accountType is AccountTypeGuest) {
                Button(action: onSettingsTap) {
                    Image(systemName: "line.3.horizontal")
                        .foregroundColor(theme.tintColor)
                }
                .frame(width: 44)
                .padding(.trailing, 8)
            } else {
                // Spacer()
                //     .frame(width: 44)
                //     .padding(.trailing, 8)
            }
        }
        .frame(height: 44)
//        .background(Color(.systemBackground).opacity(0.95))
    }
}
