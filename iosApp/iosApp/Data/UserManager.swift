import Foundation
import os
import shared

class UserManager {
    static let shared = UserManager()

    private var currentUser: UiUserV2?
    private var accountType: AccountType = AccountTypeGuest()
    private(set) var instanceMetadata: UiInstanceMetadata?

    private var presenter = ActiveAccountPresenter()
    private var isInitialized = false

    private init() {}

    // 这个地方可以返回 UiUserV2 accountType
    func getCurrentUser() -> (UiUserV2?, AccountType) {
        if !isInitialized {
            initialize()
        }

        return (currentUser, accountType)
    }

    func getCurrentAccountType() -> AccountType? {
        let (user, acctype) = getCurrentUser()
        if let user {
            return acctype
        }
        return AccountTypeGuest()
    }

    /// 判断给定用户是否是当前登录用户
    func isCurrentUser(user: UiUserV2) -> Bool {
        let (currentUser, _) = getCurrentUser()
        if let currentUser {
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
                    self.accountType = AccountTypeSpecific(accountKey: data.data.key)

                    isInitialized = true

                    // 初始化AppBarTabSettingStore
                    AppBarTabSettingStore.shared.initialize(with: accountType, user: data.data)

                    // 发送通知
//                    NotificationCenter.default.post(name: .userDidUpdate, object: data.data)
                    os_log("[UserManager] 初始化完成，用户: %{public}@", log: .default, type: .debug, data.data.name.raw)
                    // 获取到用户后就退出循环

                    if self.currentUser?.isMastodon == true || self.currentUser?.isMisskey == true {
                        fetchAndStoreInstanceMetadata(host: data.data.key.host, platformType: data.data.platformType)
                    }
                }
            }
        }
    }

    private func fetchAndStoreInstanceMetadata(host: String, platformType: PlatformType) {
        FlareLog.debug("UserManager (PresenterBase Model): 初始化 \(host) 的元数据获取...")
        instanceMetadata = nil

        let presenter = InstanceMetadataPresenter(host: host, platformType: platformType)
        Task { @MainActor in
            FlareLog.debug("UserManager (PresenterBase Model): Task started for \(host)")

            do {
                for try await stateContainer in presenter.models {
                    if Task.isCancelled {
                        FlareLog.debug("UserManager (PresenterBase Model): Observation task cancelled for \(host).")

                        break
                    }

                    let uiState = stateContainer.data
                    FlareLog.debug("UserManager (PresenterBase Model): Received state for \(host): \(uiState)")

                    // self.isLoadingInstanceMetadata = uiState is UiStateLoading<UiInstanceMetadata>

                    if let successState = uiState as? UiStateSuccess<UiInstanceMetadata> {
                        self.instanceMetadata = successState.data
                        FlareLog.debug("UserManager (PresenterBase Model): Success for \(host).")
                        break
                    } else if let errorState = uiState as? UiStateError<UiInstanceMetadata> {
                        self.instanceMetadata = nil
                        let errorMessage = errorState.throwable.message ?? "An unknown error occurred."
                        FlareLog.error("UserManager (PresenterBase Model): Error for \(host) - \(errorMessage).")
                        break
                    } else if uiState is UiStateLoading<UiInstanceMetadata> {
                        FlareLog.debug("UserManager (PresenterBase Model): Still loading for \(host).")
                    }
                }
            } catch {
                if Task.isCancelled {
                    FlareLog.debug("UserManager (PresenterBase Model): Observation task for \(host) caught cancellation error.")
                } else {
                    FlareLog.error("UserManager (PresenterBase Model): Error observing \(host) metadata flow - \(error.localizedDescription)")
                }
                self.instanceMetadata = nil
            }
        }
    }
}
