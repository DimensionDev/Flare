import Combine
import SwiftUI

/// 浮动窗口状态枚举
enum FloatingWindowState: String, CaseIterable {
    case hidden
    case minimized
    case expanded
    case dragging

    var isVisible: Bool {
        self != .hidden
    }
}

/// 浮动窗口管理器
/// 管理浮动性能监控窗口的状态、位置和行为
class FloatingWindowManager: ObservableObject {
    static let shared = FloatingWindowManager()

    // MARK: - Published Properties

    @Published var state: FloatingWindowState = .hidden
    @Published var position: CGPoint = .init(x: 50, y: 100)
    @Published var isDragging: Bool = false
    @Published var opacity: Double = 0.9
    @Published var selectedChart: Int = 0 // 0: CPU, 1: Memory, 2: Frame Rate

    // MARK: - Private Properties

    private var screenSize: CGSize = .zero
    private let edgeMargin: CGFloat = PerformanceConfig.FloatingWindow.Layout.edgeMargin
    private let snapDistance: CGFloat = PerformanceConfig.FloatingWindow.Layout.snapDistance

    // MARK: - Initialization

    private init() {
        loadPersistedState()
    }

    // MARK: - Public Methods

    /// 显示浮动窗口（展开状态）
    func show() {
        // 添加调试日志
        if PerformanceConfig.isVerboseLoggingEnabled {
            FlareLog.debug("FloatingWindow Show window called, Debug mode enabled: \(PerformanceConfig.isDebugModeEnabled)")
        }

        // 只在Debug模式下显示
        guard PerformanceConfig.isDebugModeEnabled else {
            if PerformanceConfig.isVerboseLoggingEnabled {
                FlareLog.debug("FloatingWindow Debug mode disabled, window not shown")
            }
            return
        }

        if PerformanceConfig.isVerboseLoggingEnabled {
            FlareLog.debug("FloatingWindow Showing floating window in expanded state")
        }

        // 确保性能监控已启动
        let monitor = TimelinePerformanceMonitor.shared
        if !monitor.isMonitoring {
            if PerformanceConfig.isVerboseLoggingEnabled {
                FlareLog.debug("FloatingWindow Starting performance monitoring")
            }
            monitor.startMonitoring()
        }

        withAnimation(.easeInOut(duration: PerformanceTheme.stateTransitionDuration)) {
            state = .expanded
        }
    }

    /// 隐藏浮动窗口
    func hide() {
        withAnimation(.easeInOut(duration: PerformanceTheme.stateTransitionDuration)) {
            state = .hidden
        }
        saveState()
    }

    /// 切换展开状态（现在只是隐藏窗口）
    func toggleExpanded() {
        hide()
    }

    /// 开始拖拽
    func startDragging() {
        isDragging = true
        state = .dragging
        withAnimation(.easeInOut(duration: PerformanceTheme.dragFeedbackDuration)) {
            opacity = PerformanceConfig.FloatingWindow.Opacity.dragging
        }
    }

    /// 结束拖拽
    func endDragging() {
        isDragging = false

        // 边缘吸附
        let snappedPosition = snapToEdge(position)

        withAnimation(.spring(response: PerformanceTheme.snapAnimationDuration, dampingFraction: 0.8)) {
            position = snappedPosition
            opacity = PerformanceConfig.FloatingWindow.Opacity.normal
            state = .expanded
        }

        savePosition()
    }

    /// 更新拖拽位置
    func updatePosition(_ newPosition: CGPoint) {
        let constrainedPosition = constrainToScreen(newPosition)
        position = constrainedPosition
    }

    /// 设置屏幕尺寸
    func setScreenSize(_ size: CGSize) {
        screenSize = size
        // 确保当前位置在屏幕范围内
        position = constrainToScreen(position)
    }

    /// 选择图表类型
    func selectChart(_ index: Int) {
        selectedChart = index
        UserDefaults.standard.set(index, forKey: PerformanceConfig.FloatingWindow.UserDefaultsKeys.selectedChart)
    }

    // MARK: - Private Methods

    /// 约束位置到屏幕范围内（仅Y轴，X轴固定为0）
    private func constrainToScreen(_ point: CGPoint) -> CGPoint {
        guard screenSize != .zero else { return point }

        let windowSize = getWindowSize()
        let minY = edgeMargin
        let maxY = screenSize.height - windowSize.height - edgeMargin

        return CGPoint(
            x: 0, // X轴固定为0，让窗口水平居中
            y: max(minY, min(maxY, point.y))
        )
    }

    /// 边缘吸附（仅上下边缘）
    private func snapToEdge(_ point: CGPoint) -> CGPoint {
        guard screenSize != .zero else { return point }

        let windowSize = getWindowSize()
        let centerY = point.y + windowSize.height / 2

        // 判断距离上下边缘的距离
        let topDistance = centerY
        let bottomDistance = screenSize.height - centerY

        let minDistance = min(topDistance, bottomDistance)

        // 只有距离边缘足够近才吸附
        guard minDistance < snapDistance else { return constrainToScreen(point) }

        var snappedPoint = point
        snappedPoint.x = 0 // X轴固定为0

        if minDistance == topDistance {
            snappedPoint.y = edgeMargin
        } else if minDistance == bottomDistance {
            snappedPoint.y = screenSize.height - windowSize.height - edgeMargin
        }

        return snappedPoint
    }

    /// 获取窗口尺寸
    private func getWindowSize() -> CGSize {
        switch state {
        case .minimized, .dragging:
            PerformanceConfig.FloatingWindow.Size.minimized
        case .expanded:
            PerformanceConfig.FloatingWindow.Size.expanded
        case .hidden:
            .zero
        }
    }

    /// 保存状态到UserDefaults
    private func saveState() {
        UserDefaults.standard.set(state.rawValue, forKey: PerformanceConfig.FloatingWindow.UserDefaultsKeys.state)
    }

    /// 保存位置到UserDefaults
    private func savePosition() {
        UserDefaults.standard.set(position.x, forKey: PerformanceConfig.FloatingWindow.UserDefaultsKeys.positionX)
        UserDefaults.standard.set(position.y, forKey: PerformanceConfig.FloatingWindow.UserDefaultsKeys.positionY)
    }

    /// 从UserDefaults加载持久化状态
    private func loadPersistedState() {
        // 加载位置
        let x = UserDefaults.standard.double(forKey: PerformanceConfig.FloatingWindow.UserDefaultsKeys.positionX)
        let y = UserDefaults.standard.double(forKey: PerformanceConfig.FloatingWindow.UserDefaultsKeys.positionY)
        if x > 0 || y > 0 {
            position = CGPoint(x: x, y: y)
        }

        // 加载状态（启动时默认隐藏）
        state = .hidden

        // 加载选中的图表
        selectedChart = UserDefaults.standard.integer(forKey: PerformanceConfig.FloatingWindow.UserDefaultsKeys.selectedChart)
    }
}

// MARK: - Computed Properties Extension

extension FloatingWindowManager {
    /// 当前窗口尺寸
    var currentWindowSize: CGSize {
        getWindowSize()
    }

    /// 是否可以拖拽
    var canDrag: Bool {
        state.isVisible && !isDragging
    }

    /// 窗口透明度
    var currentOpacity: Double {
        state == .hidden ? 0 : opacity
    }

    /// 是否应该显示浮动窗口（Debug模式检查）
    var shouldShowFloatingWindow: Bool {
        PerformanceConfig.isDebugModeEnabled
    }
}
