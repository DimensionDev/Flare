import Combine
import shared
import SwiftUI

class FlareAppState: ObservableObject {
//    @Published var isMenuOpen: Bool = false
//    @Published var menuProgress: CGFloat = 0.0 // 0表示完全关闭，1表示完全打开

    @Published var isCustomTabBarHidden: Bool = false

    @Published var selectedTab: Int = 0
    @Published var previousTab: Int = 0

    @Published var currentAccount: AccountType?

    private var cancellables = Set<AnyCancellable>()

    init() {
        setupObservers()

        currentAccount = UserManager.shared.getCurrentAccount()
    }

//    func toggleMenu() {
//        withAnimation(.spring()) {
//            isMenuOpen.toggle()
//            menuProgress = isMenuOpen ? 1.0 : 0.0
//        }
//    }
//
//    func closeMenu() {
//        if isMenuOpen {
//            withAnimation(.spring()) {
//                isMenuOpen = false
//                menuProgress = 0.0
//            }
//        }
//    }

    func switchTab(_ index: Int) {
        previousTab = selectedTab
        selectedTab = index
    }

    func updateCurrentAccount(_ account: AccountType?) {
        currentAccount = account
    }

    private func setupObservers() {
//        // 观察Menu通知
//        NotificationCenter.default.publisher(for: .flMenuStateDidChange)
//            .sink { [weak self] notification in
//                if let isOpen = notification.object as? Bool {
//                    self?.isMenuOpen = isOpen
//                    withAnimation(.spring()) {
//                        self?.menuProgress = isOpen ? 1.0 : 0.0
//                    }
//                }
//            }
//            .store(in: &cancellables)
//
//        //  头像点击打开菜单
//        NotificationCenter.default.publisher(for: .flShowNewMenu)
//            .sink { [weak self] _ in
//                withAnimation(.spring()) {
//                    self?.isMenuOpen = true
//                    self?.menuProgress = 1.0
//                }
//            }
//            .store(in: &cancellables)

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
