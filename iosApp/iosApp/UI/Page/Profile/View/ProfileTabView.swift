import SwiftUI
import shared
 
// 单个标签项组件
struct ProfileTabItem: View {
    let tab: ProfileStateTab
    let index: Int
    let selectedTab: Int
    let onTabSelected: (Int) -> Void
    
    var body: some View {
        let title: String = switch onEnum(of: tab) {
        case .timeline(let timeline):
            switch timeline.type {
            case .status: String(localized: "profile_tab_timeline")
            case .statusWithReplies: String(localized: "profile_tab_timeline_with_reply")
            case .likes: String(localized: "profile_tab_likes")
            default: ""
            }
        case .media:
            String(localized: "profile_tab_media")
        }
        
        VStack(spacing: 4) {
            Text(title)
                .font(.system(size: 16))
                .foregroundColor(selectedTab == index ? .primary : .gray)
                .fontWeight(selectedTab == index ? .semibold : .regular)
            
            // 下划线
            Rectangle()
                .fill(selectedTab == index ? Color.accentColor : .clear)
                .frame(height: 2)
                .frame(width: 24)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            withAnimation {
                onTabSelected(index)
            }
        }
    }
}
 
