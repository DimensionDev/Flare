import Foundation
import Kingfisher
import os.log
import shared
import SwiftUI
import UIKit

struct FLNewMenuView: View {
    @State private var showLogin = false
    @State private var showAccounts = false
    @State private var showLists = false
    @State private var showFeeds = false
    @State private var showSettings = false
    let accountType: AccountType
    let user: UiUserV2?

    @EnvironmentObject private var router: FlareRouter
    @EnvironmentObject private var appState: FlareAppState
    @Environment(FlareTheme.self) private var theme

    init(accountType: AccountType, user: UiUserV2? = nil) {
        self.accountType = accountType
        self.user = user
    }

    var body: some View {
        VStack(spacing: 0) {
            userInfoView
                .padding(.top, 40)
                .padding(.horizontal, 20) 

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
                    if user?.isBluesky == true {
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
                    if user?.isXQt == true || user?.isBluesky == true {
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
                    if user?.isXQt == true {
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

                    if user?.isMastodon == true || user?.isMisskey == true {
                        Spacer()
                        Button(action: {
                            appState.isCustomTabBarHidden = true
                            let host = UserManager.shared.instanceMetadata?.instance.domain ?? user?.key.host ?? ""
                            let platformType = user?.platformType ?? PlatformType.mastodon
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

            Spacer()

            settingsButton
                .padding(.horizontal, 20)
                .padding(.bottom, 180)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .onAppear {
            appState.isCustomTabBarHidden = false
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
            SettingsUIScreen().environment(theme).applyTheme(theme)
        }
    }

    private var userInfoView: some View {
        VStack(alignment: .leading, spacing: 12) {
            Button(action: {
                if user != nil {
                    showAccounts = true
                } else {
                    showLogin = true
                }
            }) {
                HStack(spacing: 12) {
                    if let user {
                        HStack(spacing: 12) {
                            UserAvatar(data: user.avatar, size: 60)
                                .clipShape(Circle())
                                .offset(x: 0)

                            VStack(alignment: .leading, spacing: 4) {
                                Text(user.name.raw)
                                    .font(.headline)
                                Text("\(user.handle)")
                                    .font(.caption)
                                    // .foregroundColor(.gray)
                            }
                        }
                    } else {
                        HStack(spacing: 12) {
                            userAvatarPlaceholder(size: 60)
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
            }  .padding(.top, 15) .padding(.leading, 15)
            .buttonStyle(PlainButtonStyle())

            // Followers/Following count
            if let profile = user as? UiProfile {
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
        }.foregroundColor(theme.labelColor)
                .background( RoundedRectangle(cornerRadius: 10)
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
