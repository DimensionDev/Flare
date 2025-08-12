import Foundation
import os.log
import shared
import SwiftUI

class ProfilePresenterWrapper: ObservableObject {
    let presenter: ProfileNewPresenter
    @Published var isShowAppBar: Bool? = nil // nil: 初始状态, true: 显示, false: 隐藏

    // 新增：TimelineViewModel集成
    private(set) var timelineViewModel: TimelineViewModel?
    private let accountType: AccountType
    private let userKey: MicroBlogKey?

    init(accountType: AccountType, userKey: MicroBlogKey?) {
        os_log("[📔][ProfilePresenterWrapper - init]初始化: accountType=%{public}@, userKey=%{public}@", log: .default, type: .debug, String(describing: accountType), userKey?.description ?? "nil")

        self.accountType = accountType
        self.userKey = userKey
        presenter = .init(accountType: accountType, userKey: userKey)

        isShowAppBar = nil
    }

    func updateNavigationState(showAppBar: Bool?) {
        os_log("[📔][ProfilePresenterWrapper]更新导航栏状态: showAppBar=%{public}@", log: .default, type: .debug, String(describing: showAppBar))
        Task { @MainActor in
            isShowAppBar = showAppBar
        }
    }

    // 新增：TimelineViewModel初始化方法
    @MainActor
    func setupTimelineViewModel(with tabStore: ProfileTabSettingStore) async {
        guard timelineViewModel == nil else {
            os_log("[📔][ProfilePresenterWrapper]TimelineViewModel已存在，跳过初始化", log: .default, type: .debug)
            return
        }

        os_log("[📔][ProfilePresenterWrapper]开始初始化TimelineViewModel", log: .default, type: .debug)

        // 创建TimelineViewModel实例
        let viewModel = TimelineViewModel()
        timelineViewModel = viewModel

        // 从ProfileTabSettingStore获取当前的Timeline Presenter
        if let timelinePresenter = tabStore.currentPresenter {
            await viewModel.setupDataSource(presenter: timelinePresenter)
            os_log("[📔][ProfilePresenterWrapper]TimelineViewModel初始化完成，使用TabStore的presenter", log: .default, type: .debug)
        } else {
            os_log("[📔][ProfilePresenterWrapper]⚠️ TabStore中没有可用的Timeline Presenter", log: .default, type: .error)
        }
    }

    // 新增：更新TimelineViewModel的数据源（当tab切换时调用）
    @MainActor
    func updateTimelineViewModel(with presenter: TimelinePresenter) async {
        guard let viewModel = timelineViewModel else {
            os_log("[📔][ProfilePresenterWrapper]⚠️ TimelineViewModel未初始化，无法更新", log: .default, type: .error)
            return
        }

        os_log("[📔][ProfilePresenterWrapper]更新TimelineViewModel数据源", log: .default, type: .debug)
        await viewModel.setupDataSource(presenter: presenter)
    }

    // 新增：获取TimelineViewModel（如果已初始化）
    func getTimelineViewModel() -> TimelineViewModel? {
        timelineViewModel
    }
}
