import shared
import SwiftUI
import UIKit
import Foundation
import os.log


struct FLNewMenuView: View {
    @Binding var isOpen: Bool
    @State private var showLogin = false
    @State private var showAccounts = false
    @State private var showLists = false
    @State private var showFeeds = false
    @State private var showSettings = false
    let accountType: AccountType
    let user: UiUserV2?

    @EnvironmentObject private var router: FlareRouter

    init(isOpen: Binding<Bool>, accountType: AccountType, user: UiUserV2? = nil) {
        _isOpen = isOpen
        self.accountType = accountType
        self.user = user
    }

    var body: some View {
        VStack(spacing: 0) {
            userAreaView
                .padding(.top, 80)
                .padding(.horizontal, 20)

            VStack(spacing: 16) {
                // 仅在登录状态下显示List按钮
                if !(accountType is AccountTypeGuest) {
                    Button(action: {
                        isOpen = false

                        router.navigate(to: .lists(accountType: accountType))
                    }) {
                        HStack {
                            Image(systemName: "list.bullet")
                                .frame(width: 28, height: 28)
                            Text("List")
                                .font(.body)
                            Spacer()
                        }
                        .padding(.vertical, 12)
                        .padding(.horizontal, 10)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(MenuButtonStyle())

                    // 仅在登录状态且平台为Bluesky时显示Feeds按钮
                    if user?.isBluesky == true {
                        Button(action: {
                            isOpen = false

                            router.navigate(to: .feeds(accountType: accountType))
                        }) {
                            HStack {
                                Image(systemName: "number")
                                    .frame(width: 28, height: 28)
                                Text("Feeds")
                                    .font(.body)
                                Spacer()
                            }
                            .padding(.vertical, 12)
                            .padding(.horizontal, 10)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(MenuButtonStyle())
                    }

                    // Message
                    Button(action: {
                        isOpen = false

                        router.navigate(to: .messages(accountType: accountType))
                    }) {
                        HStack {
                            Image(systemName: "bubble.left.and.bubble.right")
                                .frame(width: 28, height: 28)
                            Text("Message")
                                .font(.body)
                            Spacer()
                        }
                        .padding(.vertical, 12)
                        .padding(.horizontal, 10)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(MenuButtonStyle())

                    // X Spaces
                    if user?.isXQt == true {
                        Button(action: {
                            isOpen = false

                            router.navigate(to: .spaces(accountType: accountType))
                        }) {
                            HStack {
                                Image(systemName: "person.3")
                                    .frame(width: 28, height: 28)
                                Text("Spaces")
                                    .font(.body)
                                Spacer()
                            }
                            .padding(.vertical, 12)
                            .padding(.horizontal, 10)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(MenuButtonStyle())
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)

            Spacer()

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
        .sheet(isPresented: $showSettings) {
            NavigationView {
                SettingsUIScreen()
            }
        }
    }

    private var userAreaView: some View {
        VStack(alignment: .leading, spacing: 12) {
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
            isOpen = false
            showSettings = true
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

struct MenuButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundColor(.primary)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(configuration.isPressed ? Color(UIColor.secondarySystemBackground) : Color.clear)
            )
            .contentShape(Rectangle())
    }
}
