import AppleFontAwesome
import FlareAppleCore
import KotlinSharedUI
import SwiftUI

struct SidebarView: View {
    @Binding var selection: HomeTabsPresenterStateHomeTabs?
    @ObservedObject var homeTabsPresenter: KotlinPresenter<HomeTabsPresenterState>
    @State private var isLoginSheetPresented = false
    @StateObject private var notificationBadgePresenter = KotlinPresenter(
        presenter: AllNotificationBadgePresenter())
    @StateObject private var loggedInPresenter = KotlinPresenter(presenter: LoggedInPresenter())
    @StateObject private var canComposePresenter = KotlinPresenter(presenter: CanComposePresenter())
    
    @Environment(\.openSettings) private var openSettings

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(spacing: 8) {
                    if shouldShowLogin {
                        SidebarIconButton(
                            title: LocalizedStrings.string("login_button", fallback: "Log in"),
                            icon: .userPlus,
                            isSelected: isLoginSheetPresented
                        ) {
                            isLoginSheetPresented = true
                        }

                        Divider()
                            .frame(width: 32)
                            .padding(.vertical, 2)
                    }

                    StateView(state: homeTabsPresenter.state.tabs) { tabs in
                        ForEach(tabs.cast(HomeTabsPresenterStateHomeTabs.self), id: \.name) { tab in
                            SidebarIconButton(
                                title: tab.macOSTitle,
                                icon: tab.macOSIcon,
                                badge: notificationBadge(for: tab),
                                isSelected: isSelected(tab)
                            ) {
                                withAnimation {
                                    selection = tab
                                }
                            }
                        }
                    } loadingContent: {
                        SidebarLoadingItems()
                    }

                    if canCompose {
                        SidebarIconButton(
                            title: LocalizedStrings.string("home_compose", fallback: "Compose"),
                            icon: .pen,
                            isSelected: false,
                            isAccent: true
                        ) {
                        }
                        .padding(.top, 4)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 12)
                .padding(.bottom, 8)
            }
        }
        .frame(width: 72)
        .frame(maxHeight: .infinity, alignment: .top)
        .background {
            SidebarMaterialBackground()
                .ignoresSafeArea(.container, edges: [.top, .bottom])
        }
        .overlay(alignment: .trailing) {
            Divider()
                .ignoresSafeArea(.container, edges: [.top, .bottom])
        }
        .sheet(isPresented: $isLoginSheetPresented) {
            NavigationStack {
                ServiceSelectionScreen {
                    isLoginSheetPresented = false
                    selection = .home
                }
                .navigationTitle(LocalizedStrings.string("login_button", fallback: "Log in"))
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button {
                            isLoginSheetPresented = false
                        } label: {
                            Label {
                                Text(LocalizedStrings.string("compose_button_cancel", fallback: "Cancel"))
                            } icon: {
                                Image(systemName: "xmark")
                            }
                        }
                        .help(LocalizedStrings.string("compose_button_cancel", fallback: "Cancel"))
                    }
                }
            }
            .frame(width: 380, height: 480)
        }
    }

    private var shouldShowLogin: Bool {
        if case .success(let state) = onEnum(of: loggedInPresenter.state.isLoggedIn) {
            !state.data.boolValue
        } else {
            false
        }
    }

    private var notificationBadge: Int {
        Int(notificationBadgePresenter.state.count)
    }

    private var canCompose: Bool {
        if case .success(let state) = onEnum(of: canComposePresenter.state.canCompose) {
            state.data.boolValue
        } else {
            false
        }
    }

    private func isSelected(_ tab: HomeTabsPresenterStateHomeTabs) -> Bool {
        selection?.name == tab.name
    }

    private func notificationBadge(for tab: HomeTabsPresenterStateHomeTabs) -> Int {
        switch tab {
        case .notifications:
            notificationBadge
        case .home, .discover:
            0
        }
    }
}

private struct SidebarIconButton: View {
    let title: String
    let icon: FontAwesomeIcon
    var badge: Int = 0
    let isSelected: Bool
    var isAccent: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack(alignment: .topTrailing) {
                Image(fontAwesome: icon)
                    .resizable()
                    .scaledToFit()
                    .foregroundStyle(foregroundColor)
                    .frame(width: 20, height: 20)
                    .frame(width: 44, height: 44)

                if badge > 0 {
                    Text(badgeText)
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .padding(.horizontal, 4)
                        .frame(minWidth: 16, minHeight: 16)
                        .background(.red, in: Capsule())
                        .offset(x: 4, y: -3)
                }
            }
            .frame(width: 44, height: 44)
            .shadow(color: shadowColor, radius: isAccent ? 8 : 0, y: isAccent ? 3 : 0)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .help(title)
        .accessibilityLabel(title)
    }

    private var backgroundColor: Color {
        if isAccent {
            .accentColor
        } else if isSelected {
            .accentColor.opacity(0.14)
        } else {
            .clear
        }
    }

    private var foregroundColor: Color {
        if isAccent {
            .white
        } else if isSelected {
            .accentColor
        } else {
            .secondary
        }
    }

    private var shadowColor: Color {
        isAccent ? .black.opacity(0.22) : .clear
    }

    private var badgeText: String {
        badge > 99 ? "99+" : "\(badge)"
    }
}

private struct SidebarLoadingItems: View {
    var body: some View {
        ForEach(0..<2, id: \.self) { _ in
            Circle()
                .fill(.quaternary)
                .frame(width: 44, height: 44)
                .redacted(reason: .placeholder)
        }
    }
}
