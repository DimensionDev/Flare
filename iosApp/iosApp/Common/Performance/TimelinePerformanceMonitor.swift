import Foundation
import os.log
import UIKit

// MARK: - Timeline Performance Monitor

class TimelinePerformanceMonitor: ObservableObject {
    static let shared = TimelinePerformanceMonitor()

    // MARK: - Performance Metrics

    @Published var currentCPUUsage: Double = 0
    @Published var currentMemoryUsage: UInt64 = 0
    @Published var currentFrameRate: Double = 0

    // MARK: - Monitoring State

    var isMonitoring = false
    private var monitoringTimer: Timer?
    private var frameRateMonitor: CADisplayLink?
    private var frameCount = 0
    private var lastFrameTime: CFTimeInterval = 0

    // MARK: - Scroll Performance Tracking (Integrated)

    private var scrollFrameRates: [Double] = []
    private var scrollStutterCount = 0

    // MARK: - Performance History

    private var cpuHistory: [Double] = []
    private var memoryHistory: [UInt64] = []
    private var frameRateHistory: [Double] = []

    // MARK: - Performance Thresholds

    private let cpuThreshold: Double = PerformanceConfig.Thresholds.cpuUsage
    private let memoryThreshold: UInt64 = PerformanceConfig.Thresholds.memoryUsage
    private let frameRateThreshold: Double = PerformanceConfig.Thresholds.frameRate

    private init() {
        setupNotificationObservers()
    }

    deinit {
        removeNotificationObservers()
        stopMonitoring()
    }

    // MARK: - Notification Observers

    private func setupNotificationObservers() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleMemoryWarning),
            name: UIApplication.didReceiveMemoryWarningNotification,
            object: nil
        )

        if PerformanceConfig.Lifecycle.stopMonitoringOnBackground {
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(handleAppDidEnterBackground),
                name: UIApplication.didEnterBackgroundNotification,
                object: nil
            )
        }

        if PerformanceConfig.Lifecycle.resumeMonitoringOnForeground {
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(handleAppWillEnterForeground),
                name: UIApplication.willEnterForegroundNotification,
                object: nil
            )
        }
    }

    private func removeNotificationObservers() {
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func handleMemoryWarning() {
        if PerformanceConfig.Lifecycle.clearHistoryOnMemoryWarning {
            clearHistoryData()
        }
        os_log("Memory warning received, cleared performance history", log: .performance, type: .info)
    }

    @objc private func handleAppDidEnterBackground() {
        if isMonitoring, PerformanceConfig.Lifecycle.stopMonitoringOnBackground {
            stopMonitoring()
            if PerformanceConfig.isVerboseLoggingEnabled {
                FlareLog.debug("PerformanceMonitor Stopped monitoring due to app entering background")
            }
        }
    }

    @objc private func handleAppWillEnterForeground() {
        if !isMonitoring, PerformanceConfig.Lifecycle.resumeMonitoringOnForeground {
            startMonitoring()
            if PerformanceConfig.isVerboseLoggingEnabled {
                FlareLog.debug("PerformanceMonitor Resumed monitoring due to app entering foreground")
            }
        }
    }

    private func clearHistoryData() {
        cpuHistory.removeAll()
        memoryHistory.removeAll()
        frameRateHistory.removeAll()
        scrollFrameRates.removeAll()
        scrollStutterCount = 0
    }

    // MARK: - Monitoring Control

    func startMonitoring() {
        return
        
        guard !isMonitoring else {
            if PerformanceConfig.isVerboseLoggingEnabled {
                FlareLog.debug("PerformanceMonitor Already monitoring, skipping start")
            }
            return
        }

        // 只在Debug模式下启用监控
        guard PerformanceConfig.isDebugModeEnabled else {
            if PerformanceConfig.isVerboseLoggingEnabled {
                FlareLog.debug("PerformanceMonitor Performance monitoring disabled in release mode")
            }
            return
        }

        isMonitoring = true

        if PerformanceConfig.isVerboseLoggingEnabled {
            FlareLog.debug("PerformanceMonitor Starting Timeline performance monitoring")
        }
        os_log("Starting Timeline performance monitoring", log: .performance, type: .info)

        // 立即测试一次数据收集
        let testCPU = getCurrentCPUUsage()
        let testMemory = getCurrentMemoryUsage()
        if PerformanceConfig.isVerboseLoggingEnabled {
            FlareLog.performance("PerformanceMonitor Initial test - CPU: \(PerformanceConfig.Formatting.formatCPU(testCPU))%, Memory: \(PerformanceConfig.Formatting.formatMemoryMB(testMemory))MB")
        }

        // Start CPU and Memory monitoring
        if PerformanceConfig.isVerboseLoggingEnabled {
            FlareLog.performance("PerformanceMonitor Setting up CPU/Memory timer (\(PerformanceConfig.cpuMemoryUpdateInterval)s interval)...")
        }
        monitoringTimer = Timer.scheduledTimer(withTimeInterval: PerformanceConfig.cpuMemoryUpdateInterval, repeats: true) { [weak self] _ in
            self?.updateCPUAndMemoryMetrics()
        }
        if PerformanceConfig.isVerboseLoggingEnabled {
            FlareLog.debug("PerformanceMonitor CPU/Memory timer started")
        }

        // Start Frame Rate monitoring
        if PerformanceConfig.isVerboseLoggingEnabled {
            FlareLog.debug("PerformanceMonitor Setting up Frame Rate monitor...")
        }
        frameRateMonitor = CADisplayLink(target: self, selector: #selector(updateFrameRate))
        frameRateMonitor?.add(to: .main, forMode: .common)
        if PerformanceConfig.isVerboseLoggingEnabled {
            FlareLog.debug("PerformanceMonitor Frame Rate monitor started")
        }
    }

    func stopMonitoring() {
        guard isMonitoring else {
            if PerformanceConfig.isVerboseLoggingEnabled {
                FlareLog.debug("PerformanceMonitor Not monitoring, skipping stop")
            }
            return
        }
        isMonitoring = false

        monitoringTimer?.invalidate()
        frameRateMonitor?.invalidate()

        monitoringTimer = nil
        frameRateMonitor = nil

        if PerformanceConfig.isVerboseLoggingEnabled {
            FlareLog.debug("PerformanceMonitor Stopped Timeline performance monitoring")
        }
        os_log("Stopped Timeline performance monitoring", log: .performance, type: .info)
    }

    // MARK: - Metrics Update

    private func updateCPUAndMemoryMetrics() {
        currentCPUUsage = getCurrentCPUUsage()
        currentMemoryUsage = getCurrentMemoryUsage()

        // Debug output
        if PerformanceConfig.isVerboseLoggingEnabled {
            FlareLog.performance("PerformanceMonitor CPU: \(PerformanceConfig.Formatting.formatCPU(currentCPUUsage))%, Memory: \(PerformanceConfig.Formatting.formatMemoryMB(currentMemoryUsage))MB")
        }

        // Store history
        cpuHistory.append(currentCPUUsage)
        memoryHistory.append(currentMemoryUsage)

        // Keep only configured number of samples
        if cpuHistory.count > PerformanceConfig.HistoryLimits.cpuMemoryHistory {
            cpuHistory.removeFirst()
            memoryHistory.removeFirst()
        }

        // Check for performance issues
        checkPerformanceThresholds()
    }

    @objc private func updateFrameRate(_ displayLink: CADisplayLink) {
        frameCount += 1

        if lastFrameTime == 0 {
            lastFrameTime = displayLink.timestamp
            return
        }

        let elapsed = displayLink.timestamp - lastFrameTime
        if elapsed >= 1.0 {
            currentFrameRate = Double(frameCount) / elapsed
            frameRateHistory.append(currentFrameRate)

            // Also track for scroll performance analysis
            scrollFrameRates.append(currentFrameRate)

            // Detect stutters for scroll analysis
            if currentFrameRate < PerformanceConfig.Thresholds.stutterFrameRate {
                scrollStutterCount += 1
            }

            // Keep only recent frame rates for scroll analysis
            if scrollFrameRates.count > PerformanceConfig.HistoryLimits.scrollFrameRates {
                scrollFrameRates.removeFirst()
            }

            if PerformanceConfig.isVerboseLoggingEnabled {
                FlareLog.performance("PerformanceMonitor Frame rate: \(PerformanceConfig.Formatting.formatFrameRate(currentFrameRate)) fps")
            }

            frameCount = 0
            lastFrameTime = displayLink.timestamp

            // Keep only configured number of frame rate samples
            if frameRateHistory.count > PerformanceConfig.HistoryLimits.frameRateHistory {
                frameRateHistory.removeFirst()
            }

            // Log low frame rate
            if currentFrameRate < 30 {
                os_log("Low frame rate detected: %.1f fps", log: .performance, type: .error, currentFrameRate)
            }
        }
    }

    // MARK: - Scroll Performance Analysis (Integrated with Frame Rate Monitoring)

    func resetScrollPerformanceData() {
        scrollFrameRates.removeAll()
        scrollStutterCount = 0
        if PerformanceConfig.isVerboseLoggingEnabled {
            FlareLog.performance("PerformanceMonitor Scroll performance data reset")
        }
        os_log("[PerformanceMonitor] Scroll performance data reset", log: OSLog.performance, type: .info)
    }

    var currentScrollFrameRate: Double {
        currentFrameRate // Use the current frame rate from the main monitoring
    }

    var scrollStutterRate: Double {
        guard !scrollFrameRates.isEmpty else { return 0.0 }
        return Double(scrollStutterCount) / Double(scrollFrameRates.count) * 100
    }

    // MARK: - Data Access for Charts

    var cpuHistoryData: [Double] {
        cpuHistory
    }

    var memoryHistoryData: [UInt64] {
        memoryHistory
    }

    var frameRateHistoryData: [Double] {
        frameRateHistory
    }

    func generateScrollReport() -> ScrollPerformanceReport {
        let averageFrameRate = scrollFrameRates.isEmpty ? 0 : scrollFrameRates.reduce(0, +) / Double(scrollFrameRates.count)
        let minFrameRate = scrollFrameRates.min() ?? 0
        let maxFrameRate = scrollFrameRates.max() ?? 0

        // Calculate duration based on frame count (assuming ~60fps target)
        let estimatedDuration = Double(scrollFrameRates.count) / 60.0

        return ScrollPerformanceReport(
            duration: estimatedDuration,
            averageFrameRate: averageFrameRate,
            minFrameRate: minFrameRate,
            maxFrameRate: maxFrameRate,
            stutterCount: scrollStutterCount,
            totalFrames: scrollFrameRates.count
        )
    }

    // MARK: - Performance Analysis

    private func checkPerformanceThresholds() {
        if currentCPUUsage > cpuThreshold {
            // os_log("CPU usage above threshold: %.1f%%", log: .performance, type: .error, currentCPUUsage * 100)
        }

        if currentMemoryUsage > memoryThreshold {
            // os_log("Memory usage above threshold: %.1fMB", log: .performance, type: .error, Double(currentMemoryUsage) / 1_000_000)
        }

        if currentFrameRate < frameRateThreshold, currentFrameRate > 0 {
            // os_log("Frame rate below threshold: %.1ffps", log: .performance, type: .error, currentFrameRate)
        }
    }

    func generateReport() -> PerformanceReport {
        let averageCPU = cpuHistory.isEmpty ? 0 : cpuHistory.reduce(0, +) / Double(cpuHistory.count)
        let peakCPU = cpuHistory.max() ?? 0
        let averageMemory = memoryHistory.isEmpty ? 0 : memoryHistory.reduce(0, +) / UInt64(memoryHistory.count)
        let peakMemory = memoryHistory.max() ?? 0
        let averageFrameRate = frameRateHistory.isEmpty ? 0 : frameRateHistory.reduce(0, +) / Double(frameRateHistory.count)
        let minFrameRate = frameRateHistory.min() ?? 0

        return PerformanceReport(
            averageCPU: averageCPU,
            peakCPU: peakCPU,
            averageMemory: averageMemory,
            peakMemory: peakMemory,
            averageFrameRate: averageFrameRate,
            minFrameRate: minFrameRate,
            timestamp: Date()
        )
    }

    // MARK: - System Metrics Helpers

    private func getCurrentCPUUsage() -> Double {
        var threadsList: thread_act_array_t?
        var threadsCount = mach_msg_type_number_t(0)
        let threadsResult = task_threads(mach_task_self_, &threadsList, &threadsCount)

        guard threadsResult == KERN_SUCCESS else {
            return 0
        }

        var totalCPU: Double = 0

        for i in 0 ..< threadsCount {
            var threadInfo = thread_basic_info()
            var threadInfoCount = mach_msg_type_number_t(THREAD_INFO_MAX)

            let infoResult = withUnsafeMutablePointer(to: &threadInfo) {
                $0.withMemoryRebound(to: integer_t.self, capacity: 1) {
                    thread_info(threadsList![Int(i)], thread_flavor_t(THREAD_BASIC_INFO), $0, &threadInfoCount)
                }
            }

            guard infoResult == KERN_SUCCESS else {
                continue
            }

            let threadCPU = (Double(threadInfo.cpu_usage) / Double(TH_USAGE_SCALE)) * 100.0
            totalCPU += threadCPU
        }

        // Clean up
        vm_deallocate(mach_task_self_, vm_address_t(UInt(bitPattern: threadsList)), vm_size_t(Int(threadsCount) * MemoryLayout<thread_t>.stride))

        return totalCPU / 100.0 // Convert to 0-1 range
    }

    private func getCurrentMemoryUsage() -> UInt64 {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size) / 4

        let kerr: kern_return_t = withUnsafeMutablePointer(to: &info) {
            $0.withMemoryRebound(to: integer_t.self, capacity: 1) {
                task_info(mach_task_self_,
                          task_flavor_t(MACH_TASK_BASIC_INFO),
                          $0,
                          &count)
            }
        }

        if kerr == KERN_SUCCESS {
            return UInt64(info.resident_size)
        }
        return 0
    }
}

// MARK: - Performance Report

struct PerformanceReport {
    let averageCPU: Double
    let peakCPU: Double
    let averageMemory: UInt64
    let peakMemory: UInt64
    let averageFrameRate: Double
    let minFrameRate: Double
    let timestamp: Date

    var isWithinTargets: Bool {
        averageCPU < PerformanceConfig.Thresholds.cpuUsage &&
            peakMemory < PerformanceConfig.Thresholds.memoryUsage &&
            averageFrameRate > PerformanceConfig.Thresholds.frameRate
    }

    var summary: String {
        """
        Performance Report (\(timestamp.formatted()))
        ================================================
        CPU Usage: Avg: \(PerformanceConfig.Formatting.formatCPU(averageCPU))%, Peak: \(PerformanceConfig.Formatting.formatCPU(peakCPU))%
        Memory Usage: Avg: \(PerformanceConfig.Formatting.formatMemoryMB(averageMemory))MB, Peak: \(PerformanceConfig.Formatting.formatMemoryMB(peakMemory))MB
        Frame Rate: Avg: \(PerformanceConfig.Formatting.formatFrameRate(averageFrameRate))fps, Min: \(PerformanceConfig.Formatting.formatFrameRate(minFrameRate))fps
        Within Targets: \(isWithinTargets ? "✅ YES" : "❌ NO")
        """
    }

    var details: String {
        """
        Detailed Performance Metrics
        ============================
        CPU Analysis:
        - Average Usage: \(String(format: "%.2f", averageCPU * 100))%
        - Peak Usage: \(String(format: "%.2f", peakCPU * 100))%
        - Target: <30% (\(averageCPU < 0.3 ? "✅ PASS" : "❌ FAIL"))

        Memory Analysis:
        - Average Usage: \(String(format: "%.2f", Double(averageMemory) / 1_000_000))MB
        - Peak Usage: \(String(format: "%.2f", Double(peakMemory) / 1_000_000))MB
        - Target: <200MB (\(peakMemory < 200_000_000 ? "✅ PASS" : "❌ FAIL"))

        Frame Rate Analysis:
        - Average FPS: \(String(format: "%.2f", averageFrameRate))
        - Minimum FPS: \(String(format: "%.2f", minFrameRate))
        - Target: >55fps (\(averageFrameRate > 55 ? "✅ PASS" : "❌ FAIL"))

        Overall Assessment: \(isWithinTargets ? "✅ ALL TARGETS MET" : "❌ OPTIMIZATION NEEDED")
        """
    }
}

// MARK: - Scroll Performance Report

struct ScrollPerformanceReport {
    let duration: TimeInterval
    let averageFrameRate: Double
    let minFrameRate: Double
    let maxFrameRate: Double
    let stutterCount: Int
    let totalFrames: Int

    var performanceGrade: String {
        if averageFrameRate >= 55, stutterCount <= 2 {
            "优秀"
        } else if averageFrameRate >= 45, stutterCount <= 5 {
            "良好"
        } else if averageFrameRate >= 30, stutterCount <= 10 {
            "一般"
        } else {
            "需要优化"
        }
    }

    var summary: String {
        """
        Scroll Performance: \(performanceGrade)
        Duration: \(String(format: "%.2f", duration))s
        Avg FPS: \(String(format: "%.1f", averageFrameRate))
        Min FPS: \(String(format: "%.1f", minFrameRate))
        Stutters: \(stutterCount)
        """
    }

    var details: String {
        """
        Scroll Performance Analysis
        ==========================
        Duration: \(String(format: "%.2f", duration)) seconds
        Total Frames: \(totalFrames)

        Frame Rate Analysis:
        - Average: \(String(format: "%.2f", averageFrameRate)) fps
        - Minimum: \(String(format: "%.2f", minFrameRate)) fps
        - Maximum: \(String(format: "%.2f", maxFrameRate)) fps

        Performance Issues:
        - Stutters (< 45fps): \(stutterCount) frames
        - Stutter Rate: \(String(format: "%.1f", Double(stutterCount) / Double(totalFrames) * 100))%

        Overall Grade: \(performanceGrade)
        """
    }
}

// MARK: - OSLog Extension

extension OSLog {
    static let performance = OSLog(subsystem: "dev.dimension.flare", category: "performance")
}
