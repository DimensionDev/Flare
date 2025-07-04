import Foundation
import SwiftUI

// Timeline Version Manager

/// Timeline版本管理器，支持动态切换不同优化版本
/// Timeline version manager for dynamic switching between optimization versions
@Observable
class TimelineVersionManager {
    static let shared = TimelineVersionManager()

    // Version Definitions

    /// Timeline版本枚举
    /// Timeline version enumeration
    enum TimelineVersion: String, CaseIterable, Identifiable {
        case base = "Base"
        case v1_1 = "1.1 Stable ID"
        case v2_0 = "2.0 Data-flow"
        case v3_0 = "3.0 "
        case v4_0 = "4 ScrollViewReader + List"

        var id: String { rawValue }

        /// 版本描述
        /// Version description
        var description: String {
            switch self {
            case .base:
                "Baseline version with original ForEach implementation"
            case .v1_1:
                "Stable ID system optimization only"
            case .v2_0:
                "Data-flow optimization with SwiftTimelineDataSource"
            case .v3_0:
                "List + Anchor optimization with removed id modifiers"
            case .v4_0:
                "iOS 18+ scrollPosition API with system-level optimization"
            }
        }

        /// 版本标识符，用于日志和调试
        /// Version identifier for logging and debugging
        var identifier: String {
            switch self {
            case .base:
                "base"
            case .v1_1:
                "1.1"
            case .v2_0:
                "2.0"
            case .v3_0:
                "3.0"
            case .v4_0:
                "4.0"
            }
        }

        /// 预期性能改进
        /// Expected performance improvement
        var expectedImprovement: String {
            switch self {
            case .base:
                "Baseline (0%)"
            case .v1_1:
                "10-20% FPS improvement"
            case .v2_0:
                "50-65% overall performance improvement"
            case .v3_0:
                "85-90% memory reduction, 90%+ speed improvement"
            case .v4_0:
                "90-95% memory reduction, 95%+ speed improvement, 90%+ scroll precision"
            }
        }
    }

    // State Management

    /// 当前选中的版本
    /// Currently selected version
    var currentVersion: TimelineVersion = .v4_0 {
        didSet {
            if oldValue != currentVersion {
                handleVersionChange(from: oldValue, to: currentVersion)
                // 同步到AppSettings（避免循环调用）
                syncToAppSettings()
            }
        }
    }

    /// 是否正在切换版本
    /// Whether version switching is in progress
    var isSwitching: Bool = false

    /// 版本切换历史
    /// Version switching history
    private var switchHistory: [(from: TimelineVersion, to: TimelineVersion, timestamp: Date)] = []

    // Initialization

    private init() {
        // 从AppSettings读取保存的版本
        loadVersionFromSettings()
        FlareLog.debug("TimelineVersionManager Initialized with version: \(currentVersion.rawValue)")
    }

    // Version Switching

    /// 切换到指定版本
    /// Switch to specified version
    func switchTo(_ version: TimelineVersion) {
        guard version != currentVersion else {
            FlareLog.debug("TimelineVersionManager Already on version \(version.rawValue), no switch needed")
            return
        }

        guard !isSwitching else {
            FlareLog.warning("TimelineVersionManager Version switch already in progress, ignoring request")
            return
        }

        FlareLog.debug("TimelineVersionManager Starting switch from \(currentVersion.rawValue) to \(version.rawValue)")

        isSwitching = true
        let oldVersion = currentVersion

        // 执行切换动画
        // Execute switch animation
        withAnimation(.easeInOut(duration: 0.3)) {
            currentVersion = version
        }

        // 记录切换历史
        // Record switch history
        recordVersionSwitch(from: oldVersion, to: version)

        // 延迟重置切换状态
        // Delay reset of switching state
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            self.isSwitching = false
            FlareLog.debug("TimelineVersionManager Version switch completed: \(version.rawValue)")
        }
    }

    /// 处理版本变化
    /// Handle version change
    private func handleVersionChange(from oldVersion: TimelineVersion, to newVersion: TimelineVersion) {
        FlareLog.debug("TimelineVersionManager Version changed: \(oldVersion.rawValue) → \(newVersion.rawValue)")

        // 发送版本变化通知
        // Send version change notification
        NotificationCenter.default.post(
            name: .timelineVersionChanged,
            object: self,
            userInfo: [
                "oldVersion": oldVersion,
                "newVersion": newVersion,
                "timestamp": Date(),
            ]
        )

        // 清理可能的缓存状态
        // Clear possible cached states
        clearVersionSpecificCache()
    }

    /// 记录版本切换历史
    /// Record version switch history
    private func recordVersionSwitch(from oldVersion: TimelineVersion, to newVersion: TimelineVersion) {
        let record = (from: oldVersion, to: newVersion, timestamp: Date())
        switchHistory.append(record)

        // 保持历史记录在合理范围内
        // Keep history within reasonable limits
        if switchHistory.count > 50 {
            switchHistory.removeFirst()
        }

        FlareLog.debug("TimelineVersionManager Recorded switch: \(oldVersion.identifier) → \(newVersion.identifier)")
    }

    /// 清理版本特定的缓存
    /// Clear version-specific cache
    private func clearVersionSpecificCache() {
        // 这里可以添加清理逻辑，比如清理性能数据缓存等
        // Add cleanup logic here, such as clearing performance data cache
        FlareLog.debug("TimelineVersionManager Cleared version-specific cache")
    }

    // Utility Methods

    /// 获取版本切换历史
    /// Get version switch history
    func getSwitchHistory() -> [(from: TimelineVersion, to: TimelineVersion, timestamp: Date)] {
        switchHistory
    }

    /// 获取当前版本的详细信息
    /// Get detailed information about current version
    func getCurrentVersionInfo() -> (version: TimelineVersion, description: String, improvement: String) {
        (
            version: currentVersion,
            description: currentVersion.description,
            improvement: currentVersion.expectedImprovement
        )
    }

    /// 重置到默认版本
    /// Reset to default version
    func resetToDefault() {
        switchTo(.v2_0) // 默认使用2.0版本，数据流优化版本
    }

    /// 循环切换到下一个版本
    /// Cycle to next version
    func switchToNext() {
        let allVersions = TimelineVersion.allCases
        guard let currentIndex = allVersions.firstIndex(of: currentVersion) else { return }

        let nextIndex = (currentIndex + 1) % allVersions.count
        let nextVersion = allVersions[nextIndex]

        switchTo(nextVersion)
    }

    /// 循环切换到上一个版本
    /// Cycle to previous version
    func switchToPrevious() {
        let allVersions = TimelineVersion.allCases
        guard let currentIndex = allVersions.firstIndex(of: currentVersion) else { return }

        let previousIndex = (currentIndex - 1 + allVersions.count) % allVersions.count
        let previousVersion = allVersions[previousIndex]

        switchTo(previousVersion)
    }

    /// 从AppSettings加载版本设置
    /// Load version setting from AppSettings
    private func loadVersionFromSettings() {
        // 获取AppSettings实例（通过UserDefaults）
        let savedTimelineVersion = UserDefaults.standard.appearanceSettings.timelineVersion
        currentVersion = savedTimelineVersion.toManagerVersion()
        FlareLog.debug("TimelineVersionManager Loaded version from settings: \(currentVersion.rawValue)")
    }

    /// 同步当前版本到AppSettings
    /// Sync current version to AppSettings
    private func syncToAppSettings() {
        // 避免在初始化期间同步
        guard !isSwitching else { return }

        let settingVersion = TimelineVersionSetting.from(currentVersion)
        var currentSettings = UserDefaults.standard.appearanceSettings
        currentSettings.timelineVersion = settingVersion
        UserDefaults.standard.appearanceSettings = currentSettings

        FlareLog.debug("TimelineVersionManager Synced version to settings: \(currentVersion.rawValue)")
    }

    /// 从外部更新版本（由设置页面调用）
    /// Update version from external source (called by settings page)
    func updateFromSettings(_ version: TimelineVersion) {
        guard version != currentVersion else { return }

        let oldVersion = currentVersion
        FlareLog.debug("TimelineVersionManager Updating from settings: \(oldVersion.rawValue) → \(version.rawValue)")

        // 直接更新版本，不触发同步回AppSettings
        let oldIsSwitching = isSwitching
        isSwitching = true
        currentVersion = version
        isSwitching = oldIsSwitching

        // 发送通知（使用正确的from/to参数）
        handleVersionChange(from: oldVersion, to: version)
    }
}

// Notification Extensions

extension Notification.Name {
    /// Timeline版本变化通知
    /// Timeline version change notification
    static let timelineVersionChanged = Notification.Name("timelineVersionChanged")
}

// Timeline Version Picker View

/// Timeline版本选择器视图
/// Timeline version picker view
struct TimelineVersionPicker: View {
    @State private var versionManager = TimelineVersionManager.shared

    var body: some View {
        VStack(spacing: 8) {
            // 版本选择器标题
            // Version picker title
//            HStack {
//                Text("Timeline Version")
//                    .font(.caption)
//                    .fontWeight(.medium)
//                    .foregroundColor(.secondary)
//
//                Spacer()
//
//                if versionManager.isSwitching {
//                    ProgressView()
//                        .scaleEffect(0.7)
//                }
//            }

            // 版本选择器
            // Version picker

            Picker("Timeline Version", selection: $versionManager.currentVersion) {
                ForEach(TimelineVersionManager.TimelineVersion.allCases) { version in
                    Text(version.rawValue)
                        .tag(version)
                }
            }
            .pickerStyle(SegmentedPickerStyle())
            .disabled(versionManager.isSwitching)

            // 当前版本信息
            // Current version info
//            VStack(alignment: .leading, spacing: 4) {
//                Text(versionManager.currentVersion.description)
//                    .font(.caption2)
//                    .foregroundColor(.secondary)
//
//                Text("Expected: \(versionManager.currentVersion.expectedImprovement)")
//                    .font(.caption2)
//                    .foregroundColor(.blue)
//            }
//            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 18)
//        .padding(.horizontal, 12)
//        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
}

// Timeline Version Indicator

/// Timeline版本指示器（紧凑模式）
/// Timeline version indicator (compact mode)
struct TimelineVersionIndicator: View {
    @State private var versionManager = TimelineVersionManager.shared

    var body: some View {
        HStack(spacing: 4) {
            Circle()
                .fill(versionColor)
                .frame(width: 6, height: 6)

            Text(versionManager.currentVersion.identifier)
                .font(.system(size: 10, weight: .medium, design: .monospaced))
                .foregroundColor(.secondary)
        }
    }

    private var versionColor: Color {
        switch versionManager.currentVersion {
        case .base:
            .red
        case .v1_1:
            .orange
        case .v2_0:
            .green
        case .v3_0:
            .blue
        case .v4_0:
            .purple
        }
    }
}

// Preview

#Preview {
    VStack(spacing: 16) {
        TimelineVersionPicker()
        TimelineVersionIndicator()
    }
    .padding()
}
