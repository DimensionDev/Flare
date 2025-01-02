import Foundation
import shared
import SwiftUI
import os.log

class ProfileTabStore: ObservableObject {
    // MARK: - Published Properties
    @Published var currentTimelinePresenter: TimelinePresenter?
    @Published var currentProfileMediaPresenter: ProfileMediaPresenter?
    @Published private(set) var selectedTabKey: String?
    @Published private(set) var isRefreshing: Bool = false
    
    // MARK: - Private Properties
    private var timelinePresenters: [String: TimelinePresenter] = [:]
    private var mediaPresenter: ProfileMediaPresenter?
    private let accountType: AccountType
    private let userKey: MicroBlogKey?
    
    // MARK: - Init
    init(accountType: AccountType, userKey: MicroBlogKey?) {
        self.accountType = accountType
        self.userKey = userKey

        os_log("[📔][ProfileTabStore - init]初始化: accountType=%{public}@, userKey=%{public}@", log: .default, type: .debug, String(describing: accountType), userKey?.description ?? "nil")
        
        // 监听账号切换事件
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleAccountChanged),
            name: .accountChanged,
            object: nil
        )
    }
    
    deinit {
        os_log("[📔][ProfileTabStore - deinit]释放", log: .default, type: .debug)
        NotificationCenter.default.removeObserver(self)
    }
    
    // MARK: - Public Methods
    func getOrCreateTimelinePresenter(for timeline: ProfileStateTabTimeline) -> TimelinePresenter {
        let key = "\(timeline.type)"
        os_log("[📔][ProfileTabStore]获取或创建时间线 Presenter: type=%{public}@", log: .default, type: .debug, key)
        
        if let cached = timelinePresenters[key] {
            os_log("[📔][ProfileTabStore]使用缓存的时间线 Presenter: type=%{public}@", log: .default, type: .debug, key)
            return cached
        }

        let presenter = createTimelinePresenter(for: timeline)
        timelinePresenters[key] = presenter
        
        os_log("[📔][ProfileTabStore]创建新的时间线 Presenter: type=%{public}@", log: .default, type: .debug, key)
        return presenter
    }
    
    func getOrCreateMediaPresenter() -> ProfileMediaPresenter {
        os_log("[📔][ProfileTabStore]获取或创建媒体 Presenter", log: .default, type: .debug)
        if let cached = mediaPresenter {
            os_log("[📔][ProfileTabStore]使用缓存的媒体 Presenter", log: .default, type: .debug)
            return cached
        }
        
        let profileMediaPresenter = ProfileMediaPresenter(accountType: accountType, userKey: userKey)
        mediaPresenter = profileMediaPresenter
        
        os_log("[📔][ProfileTabStore]创建新的媒体 Presenter", log: .default, type: .debug)
        return profileMediaPresenter
    }
    
    func updateCurrentPresenter(for tab: ProfileStateTab) {
        switch onEnum(of: tab) {
        case .timeline(let timeline):
            os_log("[📔][ProfileTabStore]更新当前 Presenter: timeline type=%{public}@", log: .default, type: .debug, String(describing: timeline.type))
            selectedTabKey = "\(timeline.type)"
            let presenter = getOrCreateTimelinePresenter(for: timeline)
            currentTimelinePresenter = presenter
            // DispatchQueue.main.async {
            //     self.currentTimelinePresenter = presenter
            // }
        case .media:
            os_log("[📔][ProfileTabStore]更新当前 Presenter: media", log: .default, type: .debug)
            selectedTabKey = "media"
            let presenter = getOrCreateMediaPresenter()
            currentProfileMediaPresenter = presenter
            // DispatchQueue.main.async {
            //     self.currentProfileMediaPresenter = presenter
            // }
        }
    }
    
    // MARK: - Private Methods
    private func createTimelinePresenter(for timeline: ProfileStateTabTimeline) -> TimelinePresenter {
        os_log("[📔][ProfileTabStore]创建时间线 Presenter: type=%{public}@", log: .default, type: .debug, String(describing: timeline.type))
        return HomeTimelinePresenter(accountType: accountType)
    }
    
    @objc private func handleAccountChanged() {
        os_log("[📔][ProfileTabStore]处理账号变更", log: .default, type: .debug)
        selectedTabKey = nil
        currentTimelinePresenter = nil
        currentProfileMediaPresenter = nil
        clearCache()
    }
    
    private func clearCache() {
        os_log("[📔][ProfileTabStore]清理缓存: timelineCount=%{public}d, hasMediaPresenter=%{public}@", log: .default, type: .debug, timelinePresenters.count, mediaPresenter != nil ? "true" : "false")
        timelinePresenters.removeAll()
        mediaPresenter = nil
    }
    
    // MARK: - Memory Management
    func handleMemoryWarning() {
        os_log("[📔][ProfileTabStore]处理内存警告", log: .default, type: .debug)
        clearCache()
    }
    
    func handleBackground() {
        os_log("[📔][ProfileTabStore]处理后台", log: .default, type: .debug)
        clearCache()
    }
} 