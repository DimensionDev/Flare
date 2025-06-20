import SwiftUI
import Foundation

// MARK: - Simple Performance Comparator

/// 简单性能对比工具，用于记录和对比不同Timeline版本的性能数据
/// Simple performance comparator for recording and comparing performance data across Timeline versions
@Observable
class SimplePerformanceComparator {
    static let shared = SimplePerformanceComparator()
    
    // MARK: - Performance Data Storage
    
    /// 版本性能数据存储
    /// Version performance data storage
    private var versionResults: [TimelineVersionManager.TimelineVersion: [PerformanceTestResult]] = [:]
    
    /// 当前测试会话
    /// Current test session
    private var currentSession: TestSession?
    
    // MARK: - Test Session Management
    
    /// 测试会话数据结构
    /// Test session data structure
    struct TestSession {
        let sessionID: String
        let version: TimelineVersionManager.TimelineVersion
        let startTime: Date
        var endTime: Date?
        var fpsData: [Double] = []
        var cpuData: [Double] = []
        var memoryData: [Double] = []
        
        var duration: TimeInterval {
            return (endTime ?? Date()).timeIntervalSince(startTime)
        }
    }
    
    /// 性能测试结果
    /// Performance test result
    struct PerformanceTestResult {
        let sessionID: String
        let version: TimelineVersionManager.TimelineVersion
        let timestamp: Date
        let duration: TimeInterval
        
        // FPS 指标 / FPS metrics
        let averageFPS: Double
        let minFPS: Double
        let maxFPS: Double
        let fpsStandardDeviation: Double
        
        // CPU 指标 / CPU metrics
        let averageCPU: Double
        let maxCPU: Double
        
        // 内存指标 / Memory metrics
        let averageMemoryMB: Double
        let maxMemoryMB: Double
        
        // 性能等级 / Performance grade - 动态适配120Hz设备
        var performanceGrade: String {
            let maxFPS = PerformanceConfig.Thresholds.deviceMaxFrameRate

            if maxFPS >= 120 {
                // 120Hz设备的等级标准
                if averageFPS >= 100 { return "A" }
                else if averageFPS >= 80 { return "B" }
                else if averageFPS >= 60 { return "C" }
                else { return "D" }
            } else {
                // 60Hz设备的等级标准
                if averageFPS >= 55 { return "A" }
                else if averageFPS >= 45 { return "B" }
                else if averageFPS >= 30 { return "C" }
                else { return "D" }
            }
        }
        
        /// 与基准版本的改进百分比
        /// Improvement percentage compared to baseline
        func improvementPercentage(comparedTo baseline: PerformanceTestResult) -> Double {
            return ((averageFPS - baseline.averageFPS) / baseline.averageFPS) * 100
        }
    }
    
    // MARK: - Initialization
    
    private init() {
        print("📊 [SimplePerformanceComparator] Initialized")
    }
    
    // MARK: - Test Session Control
    
    /// 开始性能测试会话
    /// Start performance test session
    func startTestSession(for version: TimelineVersionManager.TimelineVersion) {
        let sessionID = "test_\(version.identifier)_\(Date().timeIntervalSince1970)"
        
        currentSession = TestSession(
            sessionID: sessionID,
            version: version,
            startTime: Date()
        )
        
        print("🚀 [SimplePerformanceComparator] Started test session for \(version.rawValue): \(sessionID)")
    }
    
    /// 记录性能数据点
    /// Record performance data point
    func recordPerformanceData(fps: Double, cpu: Double, memoryMB: Double) {
        guard var session = currentSession else {
            print("⚠️ [SimplePerformanceComparator] No active session to record data")
            return
        }
        
        session.fpsData.append(fps)
        session.cpuData.append(cpu)
        session.memoryData.append(memoryMB)
        
        currentSession = session
        
        if session.fpsData.count % 10 == 0 {
            print("📈 [SimplePerformanceComparator] Recorded \(session.fpsData.count) data points for \(session.version.rawValue)")
        }
    }
    
    /// 结束性能测试会话
    /// End performance test session
    func endTestSession() -> PerformanceTestResult? {
        guard var session = currentSession else {
            print("⚠️ [SimplePerformanceComparator] No active session to end")
            return nil
        }
        
        session.endTime = Date()
        
        // 计算统计数据 / Calculate statistics
        let result = generateTestResult(from: session)
        
        // 存储结果 / Store result
        if versionResults[session.version] == nil {
            versionResults[session.version] = []
        }
        versionResults[session.version]?.append(result)
        
        // 清理当前会话 / Clean up current session
        currentSession = nil
        
        print("✅ [SimplePerformanceComparator] Ended test session for \(session.version.rawValue)")
        print("📊 [SimplePerformanceComparator] Result: FPS \(String(format: "%.1f", result.averageFPS)), Grade \(result.performanceGrade)")
        
        return result
    }
    
    /// 从会话数据生成测试结果
    /// Generate test result from session data
    private func generateTestResult(from session: TestSession) -> PerformanceTestResult {
        // FPS 统计 / FPS statistics
        let averageFPS = session.fpsData.isEmpty ? 0 : session.fpsData.reduce(0, +) / Double(session.fpsData.count)
        let minFPS = session.fpsData.min() ?? 0
        let maxFPS = session.fpsData.max() ?? 0
        let fpsVariance = session.fpsData.map { pow($0 - averageFPS, 2) }.reduce(0, +) / Double(session.fpsData.count)
        let fpsStandardDeviation = sqrt(fpsVariance)
        
        // CPU 统计 / CPU statistics
        let averageCPU = session.cpuData.isEmpty ? 0 : session.cpuData.reduce(0, +) / Double(session.cpuData.count)
        let maxCPU = session.cpuData.max() ?? 0
        
        // 内存统计 / Memory statistics
        let averageMemoryMB = session.memoryData.isEmpty ? 0 : session.memoryData.reduce(0, +) / Double(session.memoryData.count)
        let maxMemoryMB = session.memoryData.max() ?? 0
        
        return PerformanceTestResult(
            sessionID: session.sessionID,
            version: session.version,
            timestamp: session.startTime,
            duration: session.duration,
            averageFPS: averageFPS,
            minFPS: minFPS,
            maxFPS: maxFPS,
            fpsStandardDeviation: fpsStandardDeviation,
            averageCPU: averageCPU,
            maxCPU: maxCPU,
            averageMemoryMB: averageMemoryMB,
            maxMemoryMB: maxMemoryMB
        )
    }
    
    // MARK: - Data Retrieval
    
    /// 获取指定版本的最新测试结果
    /// Get latest test result for specified version
    func getLatestResult(for version: TimelineVersionManager.TimelineVersion) -> PerformanceTestResult? {
        return versionResults[version]?.last
    }
    
    /// 获取指定版本的所有测试结果
    /// Get all test results for specified version
    func getAllResults(for version: TimelineVersionManager.TimelineVersion) -> [PerformanceTestResult] {
        return versionResults[version] ?? []
    }
    
    /// 获取所有版本的最新结果
    /// Get latest results for all versions
    func getAllLatestResults() -> [TimelineVersionManager.TimelineVersion: PerformanceTestResult] {
        var latestResults: [TimelineVersionManager.TimelineVersion: PerformanceTestResult] = [:]
        
        for version in TimelineVersionManager.TimelineVersion.allCases {
            if let latest = getLatestResult(for: version) {
                latestResults[version] = latest
            }
        }
        
        return latestResults
    }
    
    // MARK: - Comparison and Reporting
    
    /// 生成简单对比报告
    /// Generate simple comparison report
    func generateSimpleReport() -> String {
        let latestResults = getAllLatestResults()
        
        guard !latestResults.isEmpty else {
            return "=== Timeline Performance Comparison ===\nNo test data available. Please run performance tests first."
        }
        
        var report = "=== Timeline Performance Comparison ===\n"
        report += "Generated: \(DateFormatter.localizedString(from: Date(), dateStyle: .short, timeStyle: .short))\n\n"
        
        // 按版本顺序显示结果 / Display results in version order
        for version in TimelineVersionManager.TimelineVersion.allCases {
            if let result = latestResults[version] {
                report += "\(version.rawValue):\n"
                report += "  FPS: \(String(format: "%.1f", result.averageFPS)) (Range: \(String(format: "%.1f", result.minFPS))-\(String(format: "%.1f", result.maxFPS)))\n"
                report += "  Grade: \(result.performanceGrade)\n"
                report += "  CPU: \(String(format: "%.1f", result.averageCPU))%\n"
                report += "  Memory: \(String(format: "%.1f", result.averageMemoryMB))MB\n"
                report += "  Duration: \(String(format: "%.1f", result.duration))s\n\n"
            } else {
                report += "\(version.rawValue): No data\n\n"
            }
        }
        
        // 计算改进百分比 / Calculate improvement percentages
        if let baseResult = latestResults[.base] {
            report += "=== Performance Improvements ===\n"
            
            for version in TimelineVersionManager.TimelineVersion.allCases {
                guard version != .base, let result = latestResults[version] else { continue }
                
                let improvement = result.improvementPercentage(comparedTo: baseResult)
                let improvementText = improvement > 0 ? "+\(String(format: "%.1f", improvement))%" : "\(String(format: "%.1f", improvement))%"
                
                report += "\(version.rawValue) vs Base: \(improvementText)"
                
                if improvement > 15 {
                    report += " (Significant)"
                } else if improvement > 5 {
                    report += " (Moderate)"
                } else if improvement > 0 {
                    report += " (Minor)"
                } else {
                    report += " (Regression)"
                }
                
                report += "\n"
            }
        }
        
        // 推荐最佳版本 / Recommend best version
        if let bestVersion = findBestPerformingVersion(from: latestResults) {
            report += "\n=== Recommendation ===\n"
            report += "Best Performance: \(bestVersion.rawValue)\n"
            
            if let bestResult = latestResults[bestVersion] {
                report += "Reason: \(String(format: "%.1f", bestResult.averageFPS)) FPS, Grade \(bestResult.performanceGrade)\n"
            }
        }
        
        return report
    }
    
    /// 找到性能最佳的版本
    /// Find the best performing version
    private func findBestPerformingVersion(from results: [TimelineVersionManager.TimelineVersion: PerformanceTestResult]) -> TimelineVersionManager.TimelineVersion? {
        return results.max { first, second in
            first.value.averageFPS < second.value.averageFPS
        }?.key
    }
    
    /// 清除所有测试数据
    /// Clear all test data
    func clearAllData() {
        versionResults.removeAll()
        currentSession = nil
        print("🧹 [SimplePerformanceComparator] Cleared all test data")
    }
    
    /// 获取测试统计信息
    /// Get test statistics
    func getTestStatistics() -> (totalTests: Int, versionsWithData: Int, latestTestDate: Date?) {
        let totalTests = versionResults.values.flatMap { $0 }.count
        let versionsWithData = versionResults.filter { !$0.value.isEmpty }.count
        let latestTestDate = versionResults.values.flatMap { $0 }.map { $0.timestamp }.max()
        
        return (totalTests: totalTests, versionsWithData: versionsWithData, latestTestDate: latestTestDate)
    }
}
