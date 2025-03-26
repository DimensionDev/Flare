import shared
import SwiftUI

struct AppBarViewSwiftUI: View {
    @Binding var selectedTab: String
    let tabs: [FLTabItem]
    let user: UiUserV2?
    let onAvatarTap: () -> Void
    let onSettingsTap: () -> Void
    
    var body: some View {
        HStack(spacing: 0) {
            // 头像按钮
            Button(action: onAvatarTap) {
                if let user = user {
                    UserAvatar(data: user.avatar, size: 29)
                        .clipShape(Circle())
                        .padding(.trailing, 6)
                } else {
                    userAvatarPlaceholder(size: 29)
                        .clipShape(Circle())
                        .padding(.trailing, 6)
                }
            }
            .frame(width: 44)
            .padding(.leading, 8)
            
            // 标签栏
            TabItemsViewSwiftUI(
                selection: $selectedTab,
                items: tabs
            )
            
            // 设置按钮
            Button(action: onSettingsTap) {
                Image(systemName: "line.3.horizontal")
                    .foregroundColor(.primary)
            }
            .frame(width: 44)
            .padding(.trailing, 8)
        }
        .frame(height: 44)
        .background(Color(.systemBackground).opacity(0.95))
    }
} 
