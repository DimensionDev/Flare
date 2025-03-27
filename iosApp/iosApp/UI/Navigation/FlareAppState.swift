import Combine
import shared
import SwiftUI

class FlareAppState: ObservableObject {
    @Published var isMenuOpen: Bool = false

    @Published var selectedTab: Int = 0
    @Published var previousTab: Int = 0

    @Published var currentAccount: AccountType?

    private var cancellables = Set<AnyCancellable>()

    init() {
        setupObservers()

        currentAccount = UserManager.shared.getCurrentAccount()
    }

    func toggleMenu() {
        withAnimation(.spring()) {
            isMenuOpen.toggle()
        }
    }

    func closeMenu() {
        if isMenuOpen {
            withAnimation(.spring()) {
                isMenuOpen = false
            }
        }
    }

    func switchTab(_ index: Int) {
        previousTab = selectedTab
        selectedTab = index
    }

    func updateCurrentAccount(_ account: AccountType?) {
        currentAccount = account
    }

    private func setupObservers() {
        // 观察Menu通知
        NotificationCenter.default.publisher(for: .flMenuStateDidChange)
            .sink { [weak self] notification in
                if let isOpen = notification.object as? Bool {
                    self?.isMenuOpen = isOpen
                }
            }
            .store(in: &cancellables)

        //  头像点击打开菜单
        NotificationCenter.default.publisher(for: .flShowNewMenu)
            .sink { [weak self] _ in
                withAnimation(.spring()) {
                    self?.isMenuOpen = true
                }
            }
            .store(in: &cancellables)

        // 监听用户userDidUpdate通知
        NotificationCenter.default.publisher(for: .userDidUpdate)
            .sink { [weak self] notification in
                if let user = notification.object as? UiUserV2 {
                    self?.currentAccount = AccountTypeSpecific(accountKey: user.key)
                }
            }
            .store(in: &cancellables)
    }
}
