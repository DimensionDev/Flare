import Combine
import Foundation
import os
import shared
import SwiftUI

class UserManager: ObservableObject {
    static let shared = UserManager()
    @Published private(set) var instanceMetadata: UiInstanceMetadata?
    @Published var isLoadingInstanceMetadata: Bool = false
    @Published var instanceMetadataError: String?

    private var currentInstanceMetadataPresenter: InstanceMetadataPresenter?
    private var currentMetadataObservationTask: Task<Void, Error>?
    private var isInitialized: Bool = false
    private let accountsPresenter = ActiveAccountPresenter()

    private init() {}

    @Published var currentUser: UiUserV2? {
        didSet {
             currentMetadataObservationTask?.cancel()

            if currentUser == nil, instanceMetadata != nil {
                resetMetadataState()
                print("UserManager: currentUser is nil, metadata reset.")
            }
        }
    }

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
            for await state in accountsPresenter.models {
                if self.isInitialized {
                    print("UserManager.initialize: Already initialized, ignoring further user states.")
                    break
                }

                if case let .success(data) = onEnum(of: state.user) {
                    self.currentUser = data.data
                    self.isInitialized = true

                    // 初始化AppBarTabSettingStore
                    let account = AccountTypeSpecific(accountKey: data.data.key)
                    AppBarTabSettingStore.shared.initialize(with: account, user: data.data)

                    // 发送通知
                    NotificationCenter.default.post(name: .userDidUpdate, object: data.data)
                    os_log("[UserManager] 初始化完成，用户: %{public}@", log: .default, type: .debug, data.data.name.raw)

                    if let user = self.currentUser {
                        if !user.key.host.isEmpty {
                            fetchAndStoreInstanceMetadata(host: user.key.host, platformType: user.platformType)
                        } else {
                            resetMetadataState()
                            print("UserManager.initialize: User confirmed, but host is empty. Metadata reset.")
                        }
                    } else {
                        resetMetadataState()
                        print("UserManager.initialize: currentUser is unexpectedly nil after assignment. Metadata reset.")
                    }

                    break
                }
            }
        }
    }

    private func fetchAndStoreInstanceMetadata(host: String, platformType: PlatformType) {
        print("UserManager (PresenterBase Model): 初始化 \(host) 的元数据获取...")
        instanceMetadata = nil
        instanceMetadataError = nil
        isLoadingInstanceMetadata = true

        let presenter = InstanceMetadataPresenter(host: host, platformType: platformType)
        currentInstanceMetadataPresenter = presenter

        currentMetadataObservationTask = Task { @MainActor in
            print("UserManager (PresenterBase Model): Task started for \(host)")

            do {
                for try await stateContainer in presenter.models {
                    if Task.isCancelled {
                        print("UserManager (PresenterBase Model): Observation task cancelled for \(host).")
                        self.isLoadingInstanceMetadata = false
                        break
                    }

                    let uiState = stateContainer.data
                    print("UserManager (PresenterBase Model): Received state for \(host): \(uiState)")

                    self.isLoadingInstanceMetadata = uiState is UiStateLoading<UiInstanceMetadata>

                    if let successState = uiState as? UiStateSuccess<UiInstanceMetadata> {
                        self.instanceMetadata = successState.data
                        self.instanceMetadataError = nil
                        self.isLoadingInstanceMetadata = false
                        print("UserManager (PresenterBase Model): Success for \(host).")
                        break
                    } else if let errorState = uiState as? UiStateError<UiInstanceMetadata> {
                        self.instanceMetadata = nil
                        let errorMessage = errorState.throwable.message ?? "An unknown error occurred."
                        self.instanceMetadataError = errorMessage
                        self.isLoadingInstanceMetadata = false
                        print("UserManager (PresenterBase Model): Error for \(host) - \(errorMessage).")
                        break
                    } else if uiState is UiStateLoading<UiInstanceMetadata> {
                        print("UserManager (PresenterBase Model): Still loading for \(host).")
                    }
                }
            } catch {
                if Task.isCancelled {
                    print("UserManager (PresenterBase Model): Observation task for \(host) caught cancellation error.")
                } else {
                    self.instanceMetadataError = error.localizedDescription
                    print("UserManager (PresenterBase Model): Error observing \(host) metadata flow - \(error.localizedDescription)")
                }
                self.isLoadingInstanceMetadata = false
                self.instanceMetadata = nil
            }
        }
    }

    private func resetMetadataState() {
        DispatchQueue.main.async {
            self.instanceMetadata = nil
            self.isLoadingInstanceMetadata = false
            self.instanceMetadataError = nil
            print("UserManager: Metadata state reset.")
        }
    }

    deinit {
        print("UserManager deinit")
        currentMetadataObservationTask?.cancel()
    }
}
