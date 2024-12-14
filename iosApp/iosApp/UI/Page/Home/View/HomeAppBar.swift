import Awesome
import shared
import SwiftUI
import MarkdownUI

struct HomeAppBar: ToolbarContent {
    @State private var tabStore: TabSettingsStore?
    @State private var selectedSecondaryItem: String?
    @State private var presenterCache: [String: TimelinePresenter] = [:]
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    let router: Router
    let accountType: AccountType
    @Binding var showSettings: Bool
    @Binding var showLogin: Bool
    @Binding var selectedHomeTab: Int
    @State private var showTabSettings = false
    @State private var showUserSettings = false
    @State private var presenter = ActiveAccountPresenter()
    
    @Binding var currentPresenter: TimelinePresenter?
    
    init(router: Router, accountType: AccountType, showSettings: Binding<Bool>, showLogin: Binding<Bool>, selectedHomeTab: Binding<Int>, currentPresenter: Binding<TimelinePresenter?>) {
        self.router = router
        self.accountType = accountType
        self._showSettings = showSettings
        self._showLogin = showLogin
        self._selectedHomeTab = selectedHomeTab
        self._currentPresenter = currentPresenter
    }
    
    private func getOrCreatePresenter(for tab: FLTabItem) -> TimelinePresenter? {
        if let timelineItem = tab as? FLTimelineTabItem {
            let key = tab.key
            if let cachedPresenter = presenterCache[key] {
                return cachedPresenter
            } else {
                let presenter = timelineItem.createPresenter()
                presenterCache[key] = presenter
                return presenter
            }
        }
        return nil
    }
    
    private func initializeTabStore(with user: UiUserV2) {
        if tabStore == nil {
            tabStore = TabSettingsStore(user: user)
            if selectedSecondaryItem == nil {
                if let firstItem = tabStore?.secondaryItems.first {
                    selectedSecondaryItem = firstItem.key
                    currentPresenter = getOrCreatePresenter(for: firstItem)
                }
            }
        }
    }
    
    var body: some ToolbarContent {
        if !(accountType is AccountTypeGuest) {
            // 左边的用户头像按钮
            ToolbarItem(placement: .navigation) {
                ObservePresenter(presenter: presenter) { state in
                    switch onEnum(of: state.user) {
                    case .loading:
                        Button {
                            showSettings = true
                        } label: {
                            userAvatarPlaceholder(size: 32)
                                .clipShape(Circle())
                                .padding(.leading, 8)
                        }
                    case .error:
                        Button {
                            showSettings = true
                        } label: {
                            Awesome.Classic.Solid.user.image
                                .foregroundColor(.init(.accentColor))
                                .frame(width: 32, height: 32)
                                .padding(.leading, 8)
                        }
                    case .success(let data):
                        Button {
                            showSettings = true
                        } label: {
                            UserAvatar(data: data.data.avatar, size: 32)
                                .clipShape(Circle())
                                .padding(.leading, 8)
                        }
                        .task {
                            initializeTabStore(with: data.data)
                        }
                    }
                }
            }
            
            // 中间的标签栏
            ToolbarItem(placement: .principal) {
                ScrollView(.horizontal, showsIndicators: false) {
                    if let store = tabStore {
                        HStack(spacing: 24) {
                            ForEach(store.secondaryItems, id: \.key) { tab in
                                Button(action: {
                                    withAnimation {
                                        selectedSecondaryItem = tab.key
                                        currentPresenter = getOrCreatePresenter(for: tab)
                                    }
                                }) {
                                    VStack(spacing: 4) {
                                        switch tab.metaData.title {
                                        case .text(let title):
                                            Text(title)
                                                .font(.system(size: 16))
                                                .foregroundColor(selectedSecondaryItem == tab.key ? .primary : .gray)
                                                .fontWeight(selectedSecondaryItem == tab.key ? .semibold : .regular)
                                        case .localized(let key):
                                            Text(NSLocalizedString(key, comment: ""))
                                                .font(.system(size: 16))
                                                .foregroundColor(selectedSecondaryItem == tab.key ? .primary : .gray)
                                                .fontWeight(selectedSecondaryItem == tab.key ? .semibold : .regular)
                                        }
                                        
                                        Rectangle()
                                            .fill(selectedSecondaryItem == tab.key ? Color.accentColor : Color.clear)
                                            .frame(height: 2)
                                            .frame(width: 24)
                                    }
                                }
                            }
                        }
                        .padding(.horizontal)
                    }
                }
                .frame(maxWidth: UIScreen.main.bounds.width - 120)
                .frame(height: 44)
            }
            
            // 右边的设置按钮
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showTabSettings = true
                } label: {
                    Image(systemName: "line.3.horizontal")
                        .foregroundColor(.primary)
                        .frame(width: 32, height: 32)
                        .padding(.trailing, 8)
                        .padding(.top, -7)
                }
                .sheet(isPresented: $showTabSettings) {
                    if let store = tabStore {
                        TabSettingsView(store: store)
                    }
                }
            }
        } else {
            // 访客模式下显示登录按钮
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showLogin = true
                } label: {
                    Text("Login")
                }
            }
        }
    }
}
//
//struct SettingsView: View {
//    @Environment(\.dismiss) var dismiss
//    @State private var items = [
//        "首页",
//        "书签",
//        "精选"
//    ]
//    
//    var body: some View {
//        NavigationView {
//            List {
//                ForEach(items, id: \.self) { item in
//                    HStack {
//                        Text(item)
//                        Spacer()
//                        Image(systemName: "line.3.horizontal")
//                            .foregroundColor(.gray)
//                    }
//                }
//                .onMove { source, destination in
//                    items.move(fromOffsets: source, toOffset: destination)
//                }
//            }
//            .navigationTitle("标签设置")
//            .navigationBarItems(
//                trailing: Button("完成") {
//                    dismiss()
//                }
//            )
//        }
//        .environment(\.editMode, .constant(.active))
//    }
//}
