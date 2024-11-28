import Awesome
import shared
import SwiftUI
import MarkdownUI

struct HomeAppBar: ToolbarContent {
    @StateObject private var tabStore = TabSettingsStore()
    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    let router: Router
    let accountType: AccountType
    @Binding var showSettings: Bool
    @Binding var showLogin: Bool
    @Binding var selectedHomeTab: Int
    @State private var showTabSettings = false
    @State private var showUserSettings = false
    @State private var presenter = ActiveAccountPresenter()
    
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
                    }
                }
            }
            
            // 中间的标签栏
            ToolbarItem(placement: .principal) {
                HStack(spacing: 24) {
                    ForEach(0..<3) { index in
                        Button(action: {
                            withAnimation {
                                selectedHomeTab = index
                            }
                        }) {
                            VStack(spacing: 4) {
                                Text(index == 0 ? "首页" : index == 1 ? "书签" : "精选")
                                    .font(.system(size: 16))
                                    .foregroundColor(selectedHomeTab == index ? .primary : .gray)
                                    .fontWeight(selectedHomeTab == index ? .semibold : .regular)
                                
                                Rectangle()
                                    .fill(selectedHomeTab == index ? Color.accentColor : Color.clear)
                                    .frame(height: 2)
                                    .frame(width: 24)
                            }
                        }
                    }
                }
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
                    TabSettingsView(store: tabStore)
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

struct SettingsView: View {
    @Environment(\.dismiss) var dismiss
    @State private var items = [
        "首页",
        "书签",
        "精选"
    ]
    
    var body: some View {
        NavigationView {
            List {
                ForEach(items, id: \.self) { item in
                    HStack {
                        Text(item)
                        Spacer()
                        Image(systemName: "line.3.horizontal")
                            .foregroundColor(.gray)
                    }
                }
                .onMove { source, destination in
                    items.move(fromOffsets: source, toOffset: destination)
                }
            }
            .navigationTitle("标签设置")
            .navigationBarItems(
                trailing: Button("完成") {
                    dismiss()
                }
            )
        }
        .environment(\.editMode, .constant(.active))
    }
}
