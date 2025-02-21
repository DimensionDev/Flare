import shared
import SwiftUI

// MARK: - Menu View

struct FLNewMenuView: View {
    @Binding var isOpen: Bool
    @State private var showLogin = false
    let accountType: AccountType
    let user: UiUserV2?

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
                // 预留空列表
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
    }

    // MARK: - User Area View

    private var userAreaView: some View {
        VStack(alignment: .leading, spacing: 12) {
            // 头像和用户名区域
            HStack(spacing: 12) {
                // 头像
                if let user {
                    UserAvatar(data: user.avatar, size: 60)
                        .clipShape(Circle())
                        .offset(x: -10)

                    // 用户信息
                    VStack(alignment: .leading, spacing: 4) {
                        Text(user.name.raw)
                            .font(.headline)
                        Text("\(user.handle)")
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                } else {
                    Button(action: {
                        showLogin = true
                    }) {
                        HStack(spacing: 12) {
                            userAvatarPlaceholder(size: 60)
                                .clipShape(Circle())
                                .offset(x: -30)

                            // 用户信息
                            VStack(alignment: .leading, spacing: 4) {
                                Text("未登录")
                                    .font(.headline)
                                    .foregroundColor(.gray)
                            }
                        }
                    }
                }
            }

            // 关注/粉丝数
            if let profile = user as? UiProfile {
                HStack(spacing: 20) {
                    HStack(spacing: 4) {
                        Text("\(profile.matrices.followsCount)")
                            .font(.headline)
                        Text("fans_title")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                    }

                    HStack(spacing: 4) {
                        Text("\(profile.matrices.fansCount)")
                            .font(.headline)
                        Text("following_title")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                    }
                }
                .padding(.top, 8)
            }
        }
    }

    // MARK: - Settings Button

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
