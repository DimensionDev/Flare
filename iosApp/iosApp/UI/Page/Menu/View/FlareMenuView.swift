import Foundation
import Kingfisher
import os.log
import shared
import SwiftUI
import UIKit

struct FlareMenuView: View {
    @State private var showLogin = false
    @State private var showAccounts = false
    @State private var showLists = false
    @State private var showFeeds = false
    @State private var showSettings = false
    @State private var showPerformanceTest = false
//    let accountType: AccountType
//    let user: UiUserV2?

    @State private var currentUser: UiUserV2? = nil

    @State private var accountType: AccountType = AccountTypeGuest()

    @Environment(FlareRouter.self) private var router
    @Environment(FlareAppState.self) private var appState
    @Environment(FlareTheme.self) private var theme
    @StateObject private var monitor = TimelinePerformanceMonitor.shared

//    init(accountType: AccountType, user: UiUserV2? = nil) {
//        self.accountType = accountType
//        self.user = user
//    }

    var body: some View {
        VStack(spacing: 0) {
            Button(action: {
                if currentUser != nil {
                    showAccounts = true
                } else {
                    showLogin = true
                }
            }) {
                userInfoView
                    .padding(.top, 40)
                    .padding(.horizontal, 20)
            }.padding(.top, 15)
                .buttonStyle(PlainButtonStyle())

            VStack(spacing: 16) {
                // only show list button when user login
                if !(accountType is AccountTypeGuest) {
                    Button(action: {
                        appState.isCustomTabBarHidden = true
                        router.navigate(to: .lists(accountType: accountType))
                    }) {
                        HStack {
                            Image(systemName: "list.bullet")
                                .frame(width: 28, height: 28)
                                .foregroundColor(theme.tintColor)
                            Text("List")
                                .font(.body)
                            Spacer()
                        }
                        .padding(.vertical, 12)
                        .padding(.horizontal, 10)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(MenuButtonStyle())

                    // only show feeds button when user login and platform is Bluesky
                    if currentUser?.isBluesky == true {
                        Button(action: {
                            appState.isCustomTabBarHidden = true
                            router.navigate(to: .feeds(accountType: accountType))
                        }) {
                            HStack {
                                Image(systemName: "number")
                                    .frame(width: 28, height: 28)
                                    .foregroundColor(theme.tintColor)
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
                    if currentUser?.isXQt == true || currentUser?.isBluesky == true {
                        Button(action: {
                            appState.isCustomTabBarHidden = true
                            router.navigate(to: .messages(accountType: accountType))
                        }) {
                            HStack {
                                Image(systemName: "bubble.left.and.bubble.right")
                                    .frame(width: 28, height: 28)
                                    .foregroundColor(theme.tintColor)
                                Text("Message")
                                    .font(.body)
                                Spacer()
                            }
                            .padding(.vertical, 12)
                            .padding(.horizontal, 10)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(MenuButtonStyle())
                    }
                    // X Spaces
                    if currentUser?.isXQt == true {
                        Button(action: {
                            appState.isCustomTabBarHidden = true
                            router.navigate(to: .spaces(accountType: accountType))
                        }) {
                            HStack {
                                Image(systemName: "person.3")
                                    .frame(width: 28, height: 28)
                                    .foregroundColor(theme.tintColor)
                                Text("XSpace")
                                    .font(.body)
                                Spacer()
                            }
                            .padding(.vertical, 12)
                            .padding(.horizontal, 10)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(MenuButtonStyle())
                    }

                    // download manager
                    Button(action: {
                        appState.isCustomTabBarHidden = true
                        router.navigate(to: .download(accountType: accountType))
                    }) {
                        HStack {
                            Image(systemName: "arrow.down.circle")
                                .frame(width: 28, height: 28)
                                .foregroundColor(theme.tintColor)
                            Text("Donwload Manager")
                                .font(.body)
                            Spacer()
                        }
                        .padding(.vertical, 12)
                        .padding(.horizontal, 10)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(MenuButtonStyle())

                    if currentUser?.isMastodon == true || currentUser?.isMisskey == true {
                        Spacer()
                        Button(action: {
                            appState.isCustomTabBarHidden = true
                            let host = UserManager.shared.instanceMetadata?.instance.domain ?? currentUser?.key.host ?? ""
                            let platformType = currentUser?.platformType ?? PlatformType.mastodon
                            router.navigate(to: .instanceScreen(host: host, platformType: platformType))
                        }) {
                            if let bannerUrlString = UserManager.shared.instanceMetadata?.instance.bannerUrl,
                               !bannerUrlString.isEmpty,
                               let bannerUrl = URL(string: bannerUrlString)
                            {
                                KFImage(bannerUrl)
                                    .placeholder {
                                        Color.gray.opacity(0.3)
                                    }
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(height: 120)
                                    .clipped()
                                    .cornerRadius(8)
                                    .padding(.vertical, 6)
                                    .padding(.horizontal, 0)
                            } else {
                                HStack {
                                    Image(systemName: "server.rack")
                                        .frame(width: 28, height: 28)
                                        .background(theme.secondaryBackgroundColor)
                                    Text("Server Info")
                                        .font(.body)
                                    Spacer()
                                }
                                .padding(.vertical, 12)
                                .padding(.horizontal, 10)
                            }
                        }
                        .buttonStyle(MenuButtonStyle())
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 20)

//            Spacer()

//            FloatingWindowControlPanel()

            settingsButton
                .padding(.horizontal, 20)
                .padding(.bottom, 180)
                .padding(.top, 20)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .onAppear {
            appState.isCustomTabBarHidden = false
            checkAndUpdateUserState()
        }
        .sheet(isPresented: $showLogin) {
            ServiceSelectScreen(toHome: {
                showLogin = false
            })
        }
        .sheet(isPresented: $showAccounts) {
            NavigationView {
                AccountsScreen().environment(theme).applyTheme(theme)
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsUIScreen().environment(theme).applyTheme(theme) // need keep
        }
    }

    private func checkAndUpdateUserState() {
        let result = UserManager.shared.getCurrentUser()
        if let user = result.0 {
            currentUser = user
            accountType = result.1
        } else {
            accountType = AccountTypeGuest()
            currentUser = nil
        }
    }

    private var userInfoView: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                if let currentUser {
                    HStack(spacing: 12) {
                        UserAvatar(data: currentUser.avatar, size: 60)
                            .clipShape(Circle())
                            .offset(x: 0)

                        VStack(alignment: .leading, spacing: 4) {
                            Text(currentUser.name.raw)
                                .font(.headline)
                            Text("\(currentUser.handleWithoutFirstAt)")
                                .font(.caption)
                            // .foregroundColor(.gray)
                        }
                    }
                } else {
                    HStack(spacing: 12) {
                        UserAvatarPlaceholder(size: 60)
                            .clipShape(Circle())
                            .offset(x: 0)

                        VStack(alignment: .leading, spacing: 4) {
                            Text("No Login")
                                .font(.headline)
                                // .foregroundColor(.gray)
                                .offset(x: 10)
                        }
                    }
                }
            }

            // Followers/Following count
            if let profile = currentUser as? UiProfile {
                HStack(spacing: 20) {
                    HStack(spacing: 4) {
                        Text(formatCount(profile.matrices.followsCount))
                            .font(.subheadline)
                        Text("fans_title")
                            .font(.subheadline)
                        // .foregroundColor(.gray)
                    }

                    HStack(spacing: 4) {
                        Text(formatCount(profile.matrices.fansCount))
                            .font(.subheadline)
                        Text("following_title")
                            .font(.subheadline)
                        // .foregroundColor(.gray)
                    }
                }
                .padding(.top, 8)
                .padding(.leading, 25)
            }

            Divider()
                .padding(.top, 8)
        }.padding(.top, 20)
            .padding(.leading, 16)
            .foregroundColor(theme.labelColor)
            .background(RoundedRectangle(cornerRadius: 10)
                .fill(theme.secondaryBackgroundColor))
    }

    private var settingsButton: some View {
        Button(action: {
            showSettings = true
        }) {
            HStack {
                Image(systemName: "gearshape.fill").foregroundColor(theme.tintColor)
                Text("settings_title")
                Spacer()
            }

            .padding()
            .background(Color(theme.secondaryBackgroundColor))
            .cornerRadius(10)
        }
    }
}

struct MenuButtonStyle: ButtonStyle {
    @Environment(FlareTheme.self) private var theme

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundColor(theme.labelColor)
            .background(
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color(theme.secondaryBackgroundColor))
            )
            .contentShape(Rectangle())
    }
}
