import Combine
import shared
import SwiftUI

class FlareAppState: ObservableObject {
    // hidden  home custom tabbar
    @Published var isCustomTabBarHidden: Bool = false

//    @Published var selectedTab: Int = 0
//    @Published var previousTab: Int = 0

//    @Published var currentAccount: AccountType?

//    private var cancellables = Set<AnyCancellable>()

    init() {
//        setupObservers()

//        currentAccount = UserManager.shared.getCurrentAccount()
    }

//    func switchTab(_ index: Int) {
    ////        previousTab = selectedTab
    ////        selectedTab = index
//    }

//    func updateCurrentAccount(_ account: AccountType?) {
//        currentAccount = account
//    }

//    private func setupObservers() {
//        // 监听用户userDidUpdate通知
//        NotificationCenter.default.publisher(for: .userDidUpdate)
//            .sink { [weak self] notification in
//                if let user = notification.object as? UiUserV2 {
//                    self?.currentAccount = AccountTypeSpecific(accountKey: user.key)
//                }
//            }
//            .store(in: &cancellables)
//    }
}
