import Combine
import SwiftUI

class FLNewAppState: ObservableObject {
    // - Published Properties
    @Published var isMenuOpen: Bool
    @Published var currentTab: Int

    // - Public Properties
    let gestureState: FLNewGestureState
    private weak var tabProvider: TabStateProvider?

    // - Private Properties
    private var cancellables = Set<AnyCancellable>()
    private var memoryWarningObserver: NSObjectProtocol?

    // - Performance Monitor
    private let performanceMonitor = FLNewPerformanceMonitor.shared

    // - Initialization
    init(tabProvider: TabStateProvider? = nil, gestureState: FLNewGestureState? = nil) {
        // 从存储加载初始状态，如果没有存储值则默认为关闭状态
//        isMenuOpen = FLNewStateStorage.loadMenuState()  默认为false
        isMenuOpen = false
        currentTab = 0
//        FLNewStateStorage.loadLastTab() // 默认为0

        self.tabProvider = tabProvider
        self.gestureState = gestureState ?? FLNewGestureState(tabProvider: tabProvider)

        setupBindings()
        setupMemoryWarningObserver()

        // 开始性能监控
        performanceMonitor.startMonitoring()
    }

    deinit {
        if let observer = memoryWarningObserver {
            NotificationCenter.default.removeObserver(observer)
        }
        cleanup()
    }

    // - Private Methods
    private func setupBindings() {
        // 监听菜单状态变化
        $isMenuOpen
            .sink { [weak self] isOpen in
                self?.handleMenuStateChange(isOpen)
            }
            .store(in: &cancellables)

        // 监听标签页变化
        tabProvider?.onTabChange = { [weak self] index in
            self?.handleTabProviderChange(index)
        }
    }

    private func setupMemoryWarningObserver() {
        memoryWarningObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didReceiveMemoryWarningNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.handleMemoryWarning()
        }
    }

    private func handleMemoryWarning() {
        // 清理不必要的资源
        cleanup()
    }

    private func cleanup() {
        performanceMonitor.track("状态清理") {
            // 取消所有订阅
            cancellables.forEach { $0.cancel() }
            cancellables.removeAll()

            // 如果菜单打开，关闭它
            if isMenuOpen {
                isMenuOpen = false
            }

            // 清理手势状态
            gestureState.endGesture()
        }
    }

    private func handleMenuStateChange(_ isOpen: Bool) {
        performanceMonitor.track("菜单状态变化") {
            // 发送菜单状态变化通知
            NotificationCenter.default.post(
                name: .flMenuStateDidChange,
                object: nil,
                userInfo: ["isOpen": isOpen]
            )

            // 如果菜单打开，禁用手势
            gestureState.isGestureEnabled = !isOpen

            // 保存状态
//            FLNewStateStorage.saveMenuState(isOpen)
        }
    }

    private func handleTabProviderChange(_ index: Int) {
        performanceMonitor.track("标签页变化") {
            // 更新当前标签页
            currentTab = index

            // 根据标签页位置启用/禁用手势
            if currentTab > 0 {
                gestureState.isGestureEnabled = false
            } else {
                gestureState.isGestureEnabled = true
            }

            // 保存状态
//            FLNewStateStorage.saveLastTab(currentTab)
        }
    }

    // - Public Methods
    func resetState() {
        performanceMonitor.track("重置状态") {
            isMenuOpen = false
            currentTab = 0
//            FLNewStateStorage.clearAll()
            cleanup()
        }
    }

    // - Performance Methods
    func getCurrentPerformance() -> Double {
        performanceMonitor.getCurrentFPS()
    }

    func logPerformance(_ name: String, operation: () -> Void) {
        performanceMonitor.logGesturePerformance(name, operation: operation)
    }
}

 
