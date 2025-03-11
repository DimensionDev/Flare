import shared
import SwiftUI

struct FLNewMenuView: View {
    @Binding var isOpen: Bool
    @State private var showLogin = false
    @State private var showAccounts = false
    @State private var showLists = false
    let accountType: AccountType
    let user: UiUserV2?
    @EnvironmentObject private var router: Router

    init(isOpen: Binding<Bool>, accountType: AccountType, user: UiUserV2? = nil) {
        _isOpen = isOpen
        self.accountType = accountType
        self.user = user
    }

    var body: some View {
        VStack(spacing: 0) {
            // 用户区域
            userAreaView
                .padding(.top, 60)
                .padding(.horizontal, 20)

            // 中间列表区域
            List {
                // 列表入口
                Button(action: {
                    // 关闭菜单
                    isOpen = false
                    // 使用showLists展示列表
                    showLists = true
                }) {
                    HStack {
                        Image(systemName: "list.bullet")
                            .frame(width: 28, height: 28)
                        Text("List")
                            .font(.body)
                        Spacer()
                    }
                    .padding(.vertical, 8)
                }
                .buttonStyle(PlainButtonStyle())
            }
            .listStyle(PlainListStyle())

            // 底部设置按钮
            settingsButton
                .padding(.horizontal, 20)
                .padding(.bottom, 80)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(Color(UIColor.systemBackground))
        .sheet(isPresented: $showLogin) {
            ServiceSelectScreen(toHome: {
                showLogin = false
            })
        }
        .sheet(isPresented: $showAccounts) {
            NavigationView {
                AccountsScreen()
            }
        }
        .sheet(isPresented: $showLists) {
            NavigationView {
                AllListsView(accountType: accountType)
            }
        }
    }

    private var userAreaView: some View {
        VStack(alignment: .leading, spacing: 12) {
            // 头像和用户名区域
            Button(action: {
                if user != nil {
                    showAccounts = true
                } else {
                    showLogin = true
                }
            }) {
                HStack(spacing: 12) {
                    // 头像
                    if let user {
                        HStack(spacing: 12) {
                            UserAvatar(data: user.avatar, size: 60)
                                .clipShape(Circle())
                                .offset(x: 0)

                            // 用户信息
                            VStack(alignment: .leading, spacing: 4) {
                                Text(user.name.raw)
                                    .font(.headline)
                                Text("\(user.handle)")
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                        }
                    } else {
                        HStack(spacing: 12) {
                            userAvatarPlaceholder(size: 60)
                                .clipShape(Circle())
                                .offset(x: 0)

                            // 用户信息
                            VStack(alignment: .leading, spacing: 4) {
                                Text("未登录")
                                    .font(.headline)
                                    .foregroundColor(.gray)
                                    .offset(x: 10)
                            }
                        }
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())

            // 关注/粉丝数
            if let profile = user as? UiProfile {
                HStack(spacing: 20) {
                    HStack(spacing: 4) {
                        Text(formatCount(profile.matrices.followsCount))
                            .font(.subheadline)
                        Text("fans_title")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                    }

                    HStack(spacing: 4) {
                        Text(formatCount(profile.matrices.fansCount))
                            .font(.subheadline)
                        Text("following_title")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                    }
                }
                .padding(.top, 8)
                .padding(.leading, 15)
            }

            // 添加分隔线
            Divider()
                .padding(.top, 8)
        }
    }

    private var settingsButton: some View {
        Button(action: {
            NotificationCenter.default.post(name: NSNotification.Name("ShowSettings"), object: nil)
        }) {
            HStack {
                Image(systemName: "gearshape.fill")
                Text("settings_title")
                Spacer()
            }
            .foregroundColor(.primary)
            .padding()
            .background(Color(UIColor.secondarySystemBackground))
            .cornerRadius(10)
        }
    }
}
