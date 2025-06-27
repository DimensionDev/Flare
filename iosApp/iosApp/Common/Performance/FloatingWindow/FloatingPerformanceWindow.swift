import SwiftUI

/// 浮动性能监控窗口
/// 主要的浮动窗口组件，支持最小化和展开状态
struct FloatingPerformanceWindow: View {
    @StateObject private var windowManager = FloatingWindowManager.shared
    @StateObject private var monitor = TimelinePerformanceMonitor.shared
    @State private var dragOffset: CGSize = .zero

    var body: some View {
        Group {
            switch windowManager.state {
            case .hidden:
                EmptyView()

            case .minimized, .dragging, .expanded:
                ExpandedPerformanceView()
                    .gesture(dragGesture)
            }
        }
        .offset(
            x: dragOffset.width,
            y: windowManager.position.y + dragOffset.height
        )
        .opacity(windowManager.currentOpacity)
        .animation(.easeInOut(duration: 0.3), value: windowManager.state)
        .animation(.easeInOut(duration: 0.2), value: windowManager.currentOpacity)
    }

    // MARK: - Drag Gesture (Vertical Only)

    private var dragGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                if !windowManager.isDragging {
                    windowManager.startDragging()
                }
                // 只允许垂直拖动
                dragOffset = CGSize(width: 0, height: value.translation.height)
            }
            .onEnded { value in
                let newPosition = CGPoint(
                    x: windowManager.position.x, // X坐标保持不变
                    y: windowManager.position.y + value.translation.height
                )
                windowManager.updatePosition(newPosition)
                windowManager.endDragging()
                dragOffset = .zero
            }
    }
}

/// 展开状态的性能视图
struct ExpandedPerformanceView: View {
    @StateObject private var windowManager = FloatingWindowManager.shared
    @StateObject private var monitor = TimelinePerformanceMonitor.shared

    var body: some View {
        VStack(spacing: 8) {
            // CPU/MEM/FPS指标和图表选择器
            HStack {
                // 紧凑指标显示
                HStack(spacing: 12) {
                    CompactMetricItem(
                        title: "CPU",
                        value: "\(PerformanceConfig.Formatting.formatCPU(monitor.currentCPUUsage))%",
                        color: cpuColor
                    )

                    CompactMetricItem(
                        title: "MEM",
                        value: "\(PerformanceConfig.Formatting.formatMemoryMB(monitor.currentMemoryUsage))MB",
                        color: memoryColor
                    )

                    CompactMetricItem(
                        title: "FPS",
                        value: "\(PerformanceConfig.Formatting.formatFrameRate(monitor.currentFrameRate))",
                        color: frameRateColor
                    )
                }

                Spacer()

                // 图表选择器
                Picker("Chart", selection: $windowManager.selectedChart) {
                    Text("CPU").tag(0)
                    Text("MEM").tag(1)
                    Text("FPS").tag(2)
                }
                .pickerStyle(.segmented)
                .frame(width: 160)
                .scaleEffect(0.8)

                Spacer()

                // 关闭按钮
                Button(action: {
                    windowManager.hide()
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.red)
                        .font(.title3)
                }
            }

            // 图表内容
            Group {
                switch windowManager.selectedChart {
                case 0:
                    MiniCPUChart()
                case 1:
                    MiniMemoryChart()
                case 2:
                    MiniFrameRateDisplay()
                default:
                    MiniCPUChart()
                }
            }
            .frame(height: 120)

            // Timeline版本切换和性能测试区域
            TimelinePerformanceTestSection()
        }
        .padding()
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(.ultraThinMaterial)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(.white.opacity(0.2), lineWidth: 1)
                )
        )
        .shadow(color: .black.opacity(0.3), radius: 12, x: 0, y: 6)
        .frame(maxWidth: .infinity, maxHeight: 280)
        .padding(.horizontal, 16)
    }

    // MARK: - Computed Properties

    private var cpuColor: Color {
        PerformanceConfig.Colors.cpuColor(for: monitor.currentCPUUsage)
    }

    private var memoryColor: Color {
        let memoryMB = Double(monitor.currentMemoryUsage) / 1_000_000
        return PerformanceConfig.Colors.memoryColor(for: memoryMB)
    }

    private var frameRateColor: Color {
        PerformanceConfig.Colors.frameRateColor(for: monitor.currentFrameRate)
    }
}

/// 迷你CPU图表
struct MiniCPUChart: View {
    @StateObject private var monitor = TimelinePerformanceMonitor.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("CPU Usage")
                    .font(.subheadline)
                    .fontWeight(.medium)

                Spacer()

                Text("\(PerformanceConfig.Formatting.formatCPU(monitor.currentCPUUsage))%")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(cpuColor)
            }

            SimpleLineChart(
                data: Array(monitor.cpuHistoryData.suffix(20)).map { $0 * 100 },
                color: cpuColor,
                threshold: 30,
                maxValue: 100
            )
        }
    }

    private var cpuColor: Color {
        PerformanceConfig.Colors.cpuColor(for: monitor.currentCPUUsage)
    }
}

/// 迷你内存图表
struct MiniMemoryChart: View {
    @StateObject private var monitor = TimelinePerformanceMonitor.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Memory Usage")
                    .font(.subheadline)
                    .fontWeight(.medium)

                Spacer()

                Text("\(PerformanceConfig.Formatting.formatMemoryMB(monitor.currentMemoryUsage))MB")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(memoryColor)
            }

            SimpleLineChart(
                data: Array(monitor.memoryHistoryData.suffix(20)).map { Double($0) / 1_000_000 },
                color: memoryColor,
                threshold: 200,
                maxValue: max(currentMemoryMB * 1.2, 250),
                unit: "MB"
            )
        }
    }

    private var currentMemoryMB: Double {
        Double(monitor.currentMemoryUsage) / 1_000_000
    }

    private var memoryColor: Color {
        PerformanceConfig.Colors.memoryColor(for: currentMemoryMB)
    }
}

/// 迷你帧率显示
/// 浮动窗口中的帧率柱状图显示，从 FrameRateDisplayView 移植而来
struct MiniFrameRateDisplay: View {
    @StateObject private var monitor = TimelinePerformanceMonitor.shared

    // Configuration - 适配浮动窗口的尺寸，支持120Hz设备
    private let frameRateThreshold: Double = 55.0 // 55fps
    private let stutterThreshold: Double = 45.0 // 45fps
    private let chartHeight: CGFloat = 80 // 减小高度以适应浮动窗口

    // 动态检测设备最大刷新率
    private var maxFPS: Double {
        if #available(iOS 15.0, *) {
            // 获取主屏幕的最大刷新率
            let maxRefreshRate = UIScreen.main.maximumFramesPerSecond
            return Double(maxRefreshRate)
        } else {
            return 60.0 // iOS 15以下默认60fps
        }
    }

    // 动态Y轴刻度，根据设备最大刷新率调整
    private var yAxisValues: [Int] {
        if maxFPS >= 120 {
            [120, 90, 60, 30, 0] // 120Hz设备
        } else {
            [60, 45, 30, 15, 0] // 60Hz设备
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header with current FPS
            HStack {
                Text("Frame Rate")
                    .font(.subheadline)
                    .fontWeight(.medium)

                Spacer()

                Text("\(PerformanceConfig.Formatting.formatFrameRate(monitor.currentFrameRate))fps")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(frameRateColor)
            }

            // Frame rate bar chart - 从 FrameRateDisplayView 移植
            HStack(alignment: .bottom, spacing: 0) {
                // Y-axis scale - 动态适配120Hz设备
                VStack(alignment: .trailing, spacing: 0) {
                    ForEach(yAxisValues, id: \.self) { value in
                        HStack {
                            Text("\(value)")
                                .font(.caption2)
                                .foregroundColor(.secondary)
                                .frame(width: 20, alignment: .trailing) // 增加宽度以适应120

                            Rectangle()
                                .fill(.gray.opacity(0.3))
                                .frame(height: 0.5)
                        }
                        .frame(height: chartHeight / CGFloat(yAxisValues.count))
                    }
                }

                // Chart bars - 使用更小的间距
                HStack(alignment: .bottom, spacing: 0.5) {
                    ForEach(Array(monitor.frameRateHistoryData.enumerated()), id: \.offset) { _, frameRate in
                        Rectangle()
                            .fill(frameRateColor(for: frameRate))
                            .frame(
                                width: 2, // 减小宽度以适应浮动窗口
                                height: max(1, CGFloat(frameRate / maxFPS) * chartHeight)
                            )
                            .animation(.easeInOut(duration: 0.3), value: frameRate)
                    }
                }
                .frame(height: chartHeight)
            }

            // Frame rate statistics - 完整版本，与 FrameRateDisplayView 保持一致
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Current")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                    Text("\(PerformanceConfig.Formatting.formatPercentage(monitor.currentFrameRate))")
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(frameRateColor)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text("Average")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                    Text("\(PerformanceConfig.Formatting.formatPercentage(averageFrameRate))")
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(.primary)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text("Min")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                    Text("\(PerformanceConfig.Formatting.formatPercentage(minFrameRate))")
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(.red)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text("Max")
                        .font(.caption2)
                        .foregroundColor(.secondary)
                    Text("\(PerformanceConfig.Formatting.formatPercentage(maxFrameRateValue))")
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(.green)
                }

                Spacer()
            }
        }
    }

    // MARK: - Computed Properties - 从 FrameRateDisplayView 移植

    private func frameRateColor(for frameRate: Double) -> Color {
        if frameRate < stutterThreshold {
            .red
        } else if frameRate < frameRateThreshold {
            .orange
        } else {
            .green
        }
    }

    private var frameRateColor: Color {
        frameRateColor(for: monitor.currentFrameRate)
    }

    private var averageFrameRate: Double {
        guard !monitor.frameRateHistoryData.isEmpty else { return 0 }
        return monitor.frameRateHistoryData.reduce(0, +) / Double(monitor.frameRateHistoryData.count)
    }

    private var minFrameRate: Double {
        monitor.frameRateHistoryData.min() ?? 0
    }

    private var maxFrameRateValue: Double {
        monitor.frameRateHistoryData.max() ?? 0
    }

    private var stutterColor: Color {
        PerformanceConfig.Colors.stutterColor(for: monitor.scrollStutterRate)
    }
}

/// 紧凑版指标项组件
struct CompactMetricItem: View {
    let title: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 2) {
            Text(title)
                .font(.caption2)
                .foregroundColor(.secondary)

            Text(value)
                .font(.caption)
                .fontWeight(.medium)
                .foregroundColor(color)
        }
    }
}

/// Timeline性能测试区域组件
struct TimelinePerformanceTestSection: View {
    @State private var versionManager = TimelineVersionManager.shared
    @State private var comparator = SimplePerformanceComparator.shared
    @StateObject private var monitor = TimelinePerformanceMonitor.shared
    @State private var isTestRunning = false
    @State private var showResults = false
    @State private var dataCollectionTimer: Timer?

    var body: some View {
        VStack(spacing: 12) {
            TimelineVersionPicker()

            // 性能测试控制（简化版）
            HStack(spacing: 12) {
                Text("Test")
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(.secondary)

                // 开始/停止测试按钮
                Button(action: {
                    if isTestRunning {
                        stopPerformanceTest()
                    } else {
                        startPerformanceTest()
                    }
                }) {
                    Image(systemName: isTestRunning ? "stop.circle.fill" : "play.circle.fill")
                        .font(.title3)
                        .foregroundColor(isTestRunning ? .red : .green)
                }

                // 查看结果按钮
                Button(action: {
                    showResults.toggle()
                }) {
                    Image(systemName: "chart.bar.doc.horizontal")
                        .font(.title3)
                        .foregroundColor(.blue)
                }

                Spacer()
            }

            // 测试结果显示（可折叠）
            if showResults {
                PerformanceTestResultsView()
            }
        }
        .animation(.easeInOut(duration: 0.3), value: showResults)
    }

    // MARK: - Test Control Methods

    private func startPerformanceTest() {
        FlareLog.performance("PerformanceTest Starting performance test for \(versionManager.currentVersion.rawValue)")

        // 确保性能监控正在运行
        if !monitor.isMonitoring {
            FlareLog.performance("PerformanceTest Starting performance monitoring")
            monitor.startMonitoring()
        }

        // 重置滚动性能数据以获得干净的测试数据
        monitor.resetScrollPerformanceData()

        isTestRunning = true
        comparator.startTestSession(for: versionManager.currentVersion)

        // 启动数据收集定时器
        startDataCollection()

        FlareLog.performance("PerformanceTest Test started - will run until manually stopped")
    }

    private func stopPerformanceTest() {
        FlareLog.performance("PerformanceTest Stopping performance test")

        isTestRunning = false
        stopDataCollection()

        let result = comparator.endTestSession()
        showResults = true

        if let result {
            FlareLog.performance("PerformanceTest Test completed - FPS: \(String(format: "%.1f", result.averageFPS)), Grade: \(result.performanceGrade)")
        }
    }

    // MARK: - Data Collection

    private func startDataCollection() {
        FlareLog.performance("PerformanceTest Starting data collection timer")

        // 使用标准化的数据收集间隔
        dataCollectionTimer = Timer.scheduledTimer(withTimeInterval: PerformanceConfig.performanceTestDataInterval, repeats: true) { _ in
            collectPerformanceData()
        }
    }

    private func stopDataCollection() {
        FlareLog.performance("PerformanceTest Stopping data collection timer")
        dataCollectionTimer?.invalidate()
        dataCollectionTimer = nil
    }

    private func collectPerformanceData() {
        // 使用当前帧率数据，确保数据准确性
        let fps = monitor.currentFrameRate
        let cpu = monitor.currentCPUUsage * 100 // 转换为百分比
        let memoryMB = Double(monitor.currentMemoryUsage) / 1_000_000 // 转换为MB

        // 只有当FPS数据有效时才记录（避免初始化期间的0值）
        if fps > 0 {
            comparator.recordPerformanceData(fps: fps, cpu: cpu, memoryMB: memoryMB)
        }

        // 调试输出（每10次输出一次，避免日志过多）
        if Int.random(in: 1 ... 20) == 1 {
            FlareLog.performance("PerformanceTest Data: FPS=\(String(format: "%.1f", fps)), CPU=\(String(format: "%.1f", cpu))%, MEM=\(String(format: "%.1f", memoryMB))MB")
        }
    }
}

/// 性能测试结果显示组件
struct PerformanceTestResultsView: View {
    @State private var comparator = SimplePerformanceComparator.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Test Results")
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(.secondary)

                Spacer()

                // 清除数据按钮
                Button("Clear") {
                    comparator.clearAllData()
                }
                .font(.caption2)
                .foregroundColor(.red)
            }

            // 结果汇总
            let latestResults = comparator.getAllLatestResults()

            if latestResults.isEmpty {
                Text("No test data available")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .padding(.vertical, 8)
            } else {
                VStack(spacing: 6) {
                    ForEach(TimelineVersionManager.TimelineVersion.allCases, id: \.self) { version in
                        if let result = latestResults[version] {
                            CompactResultRow(version: version, result: result)
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(
            RoundedRectangle(cornerRadius: 8)
                .fill(.background.opacity(0.6))
        )
    }
}

/// 紧凑版结果行组件
struct CompactResultRow: View {
    let version: TimelineVersionManager.TimelineVersion
    let result: SimplePerformanceComparator.PerformanceTestResult

    var body: some View {
        HStack(spacing: 8) {
            // 版本名称
            Text(version.identifier)
                .font(.caption2)
                .fontWeight(.medium)
                .frame(width: 80, alignment: .leading)

            // FPS
            Text("\(String(format: "%.1f", result.averageFPS))fps")
                .font(.caption2)
                .fontWeight(.medium)
                .foregroundColor(fpsColor)
                .frame(width: 50, alignment: .trailing)

            // 性能等级
            Text(result.performanceGrade)
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundColor(gradeColor)
                .frame(width: 25, alignment: .center)

            // FPS范围 (Min-Max)
            Text("\(String(format: "%.0f", result.minFPS))-\(String(format: "%.0f", result.maxFPS))")
                .font(.caption2)
                .foregroundColor(.secondary)
                .frame(width: 50, alignment: .trailing)

            Spacer()
        }
    }

    private var fpsColor: Color {
        PerformanceConfig.Colors.frameRateColor(for: result.averageFPS)
    }

    private var gradeColor: Color {
        switch result.performanceGrade {
        case "A": .green
        case "B": .blue
        case "C": .orange
        case "D": .red
        default: .secondary
        }
    }
}
