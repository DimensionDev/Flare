import Foundation
import os
import shared

class UserManager {
    static let shared = UserManager()

    private var currentUser: UiUserV2?
    private var presenter = ActiveAccountPresenter()
    private var isInitialized = false

    private init() {}

    func getCurrentUser() -> UiUserV2? {
        if !isInitialized {
            initialize()
        }
        return currentUser
    }

    func getCurrentAccount() -> AccountType? {
        if let user = getCurrentUser() {
            return AccountTypeSpecific(accountKey: user.key)
        }
        return AccountTypeGuest()
    }

    /// 判断给定用户是否是当前登录用户
    func isCurrentUser(user: UiUserV2) -> Bool {
        if let currentUser = getCurrentUser() {
            // 比较用户ID
            return user.key.id == currentUser.key.id
        }
        return false
    }

    func initialize() {
        guard !isInitialized else { return }

        Task { @MainActor in
            for await state in presenter.models {
                if case let .success(data) = onEnum(of: state.user) {
                    self.currentUser = data.data
                    isInitialized = true

                    // 初始化AppBarTabSettingStore
                    let account = AccountTypeSpecific(accountKey: data.data.key)
                    AppBarTabSettingStore.shared.initialize(with: account, user: data.data)

                    // 发送通知
                    NotificationCenter.default.post(name: .userDidUpdate, object: data.data)
                    os_log("[UserManager] 初始化完成，用户: %{public}@", log: .default, type: .debug, data.data.name.raw)
                    // 获取到用户后就退出循环
                }
            }
        }
    }
}

// 添加通知名称
