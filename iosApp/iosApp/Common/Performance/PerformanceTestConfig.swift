import Foundation
import SwiftUI

// MARK: - Performance Test Configuration

/// 性能测试配置类，用于控制不同优化功能的开启/关闭
/// Performance test configuration class for controlling different optimization features
@Observable
class PerformanceTestConfig {
    static let shared = PerformanceTestConfig()

    // MARK: - Optimization Feature Toggles

    /// Task 1.1: 稳定 ID 系统优化
    /// Task 1.1: Stable ID system optimization
    var enableStableIDSystem: Bool = true

    /// Task 1.2: 状态观察机制优化
    /// Task 1.2: State observation mechanism optimization
    var enableStateObservationOptimization: Bool = true

    /// Task 1.3: 可见范围检测优化
    /// Task 1.3: Visible range detection optimization
    var enableVisibleRangeDetection: Bool = true

    // MARK: - Test Scenarios

    /// 当前测试场景
    /// Current test scenario
    var currentTestScenario: TestScenario = .baseline

    /// 测试场景枚举
    /// Test scenario enumeration
    enum TestScenario: String, CaseIterable {
        case baseline = "Baseline"
        case stableIDOnly = "Task 1.1 Only"
        case stableIDAndStateOpt = "Task 1.1 + 1.2"
        case allOptimizations = "All Optimizations"

        var description: String {
            switch self {
            case .baseline:
                "No optimizations (baseline)"
            case .stableIDOnly:
                "Stable ID system only"
            case .stableIDAndStateOpt:
                "Stable ID + State observation optimization"
            case .allOptimizations:
                "All optimizations enabled"
            }
        }
    }

    // MARK: - Test Configuration

    /// 是否正在进行性能测试
    /// Whether performance testing is in progress
    var isPerformanceTesting: Bool = false

    /// 当前测试阶段
    /// Current test phase
    var currentTestPhase: TestPhase = .idle

    /// 测试阶段枚举
    /// Test phase enumeration
    enum TestPhase: String, CaseIterable {
        case idle = "Idle"
        case staticTest = "Static Test"
        case slowScroll = "Slow Scroll"
        case fastScroll = "Fast Scroll"
        case continuousScroll = "Continuous Scroll"

        var duration: TimeInterval {
            switch self {
            case .idle:
                0
            case .staticTest:
                10 // 10 seconds
            case .slowScroll:
                10 // 10 seconds
            case .fastScroll:
                10 // 10 seconds
            case .continuousScroll:
                20 // 20 seconds
            }
        }

        var description: String {
            switch self {
            case .idle:
                "Idle state"
            case .staticTest:
                "Static test (no scrolling)"
            case .slowScroll:
                "Slow scrolling test"
            case .fastScroll:
                "Fast scrolling test"
            case .continuousScroll:
                "Continuous scrolling test"
            }
        }
    }

    // MARK: - Test Results

    /// 测试结果存储
    /// Test results storage
    var testResults: [TestResult] = []

    /// 单个测试结果
    /// Individual test result
    struct TestResult: Identifiable {
        let id = UUID()
        let scenario: TestScenario
        let phase: TestPhase
        let averageFPS: Double
        let minFPS: Double
        let maxFPS: Double
        let fpsStability: Double // 标准差 / Standard deviation
        let timestamp: Date

        var summary: String {
            """
            Scenario: \(scenario.rawValue)
            Phase: \(phase.rawValue)
            Average FPS: \(String(format: "%.1f", averageFPS))
            Min FPS: \(String(format: "%.1f", minFPS))
            Max FPS: \(String(format: "%.1f", maxFPS))
            Stability: \(String(format: "%.2f", fpsStability))
            """
        }
    }

    // MARK: - Configuration Methods

    /// 应用测试场景配置
    /// Apply test scenario configuration
    func applyTestScenario(_ scenario: TestScenario) {
        currentTestScenario = scenario

        switch scenario {
        case .baseline:
            enableStableIDSystem = false
            enableStateObservationOptimization = false
            enableVisibleRangeDetection = false

        case .stableIDOnly:
            enableStableIDSystem = true
            enableStateObservationOptimization = false
            enableVisibleRangeDetection = false

        case .stableIDAndStateOpt:
            enableStableIDSystem = true
            enableStateObservationOptimization = true
            enableVisibleRangeDetection = false

        case .allOptimizations:
            enableStableIDSystem = true
            enableStateObservationOptimization = true
            enableVisibleRangeDetection = true
        }

        if PerformanceConfig.isVerboseLoggingEnabled {
            print("🧪 [PerformanceTestConfig] Applied scenario: \(scenario.rawValue)")
            print("   - Stable ID: \(enableStableIDSystem)")
            print("   - State Opt: \(enableStateObservationOptimization)")
            print("   - Visible Range: \(enableVisibleRangeDetection)")
        }
    }

    /// 开始性能测试
    /// Start performance testing
    func startPerformanceTesting() {
        isPerformanceTesting = true
        testResults.removeAll()

        if PerformanceConfig.isVerboseLoggingEnabled {
            print("🧪 [PerformanceTestConfig] Performance testing started")
        }
    }

    /// 结束性能测试
    /// End performance testing
    func endPerformanceTesting() {
        isPerformanceTesting = false
        currentTestPhase = .idle

        if PerformanceConfig.isVerboseLoggingEnabled {
            print("🧪 [PerformanceTestConfig] Performance testing ended")
        }
    }

    /// 添加测试结果
    /// Add test result
    func addTestResult(_ result: TestResult) {
        testResults.append(result)

        if PerformanceConfig.isVerboseLoggingEnabled {
            print("🧪 [PerformanceTestConfig] Test result added:")
            print(result.summary)
        }
    }

    /// 生成性能报告
    /// Generate performance report
    func generatePerformanceReport() -> String {
        guard !testResults.isEmpty else {
            return "No test results available."
        }

        var report = """
        # Timeline Performance Test Report
        Generated: \(Date().formatted())

        ## Test Summary
        Total Tests: \(testResults.count)

        """

        // 按场景分组结果 / Group results by scenario
        let groupedResults = Dictionary(grouping: testResults) { $0.scenario }

        for scenario in TestScenario.allCases {
            if let results = groupedResults[scenario] {
                report += """

                ## \(scenario.rawValue) Results
                \(scenario.description)

                """

                for result in results {
                    report += """
                    ### \(result.phase.rawValue)
                    - Average FPS: \(String(format: "%.1f", result.averageFPS))
                    - Min FPS: \(String(format: "%.1f", result.minFPS))
                    - Max FPS: \(String(format: "%.1f", result.maxFPS))
                    - Stability: \(String(format: "%.2f", result.fpsStability))

                    """
                }
            }
        }

        return report
    }

    private init() {}
}
