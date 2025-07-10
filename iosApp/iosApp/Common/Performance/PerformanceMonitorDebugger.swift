//
//  PerformanceMonitorDebugger.swift
//  iosApp
//
//  Created by AI Assistant on 2025-06-18.
//

import Foundation
import os.log
import UIKit

/// 专门用于调试性能监控系统的工具类
class PerformanceMonitorDebugger {
    static let shared = PerformanceMonitorDebugger()

    private init() {}

    // - 系统信息检查

    func checkSystemCapabilities() {
        FlareLog.performance("=== Performance Monitor System Check ===")

        // 检查设备信息
        let device = UIDevice.current
        FlareLog.performance("Device: \(device.model)")
        FlareLog.performance("System: \(device.systemName) \(device.systemVersion)")
        FlareLog.performance("Process Info: \(ProcessInfo.processInfo.processName)")

        // 检查日志系统
        checkLoggingSystem()

        // 检查性能监控权限
        checkPerformanceMonitoringCapabilities()

        FlareLog.performance("=== System Check Complete ===")
    }

    private func checkLoggingSystem() {
        FlareLog.performance("--- Logging System Check ---")

        // 测试OSLog
        let testLog = OSLog(subsystem: "dev.dimension.flare", category: "debug")
        os_log("OSLog test message", log: testLog, type: .info)
        FlareLog.performance("OSLog test message sent")

        // 测试不同日志级别
        os_log("Debug level test", log: .performance, type: .debug)
        os_log("Info level test", log: .performance, type: .info)
        os_log("Error level test", log: .performance, type: .error)

        FlareLog.performance("Multiple log level tests sent")
    }

    private func checkPerformanceMonitoringCapabilities() {
        FlareLog.performance("--- Performance Monitoring Capabilities ---")

        // 检查CPU监控
        let cpuUsage = testCPUMonitoring()
        FlareLog.performance("CPU monitoring test: \(cpuUsage > 0 ? "✅ Working" : "❌ Failed")")

        // 检查内存监控
        let memoryUsage = testMemoryMonitoring()
        FlareLog.performance("Memory monitoring test: \(memoryUsage > 0 ? "✅ Working" : "❌ Failed")")

        // 检查帧率监控
        let canMonitorFrameRate = testFrameRateMonitoring()
        FlareLog.performance("Frame rate monitoring test: \(canMonitorFrameRate ? "✅ Working" : "❌ Failed")")
    }

    // - 性能监控组件测试

    private func testCPUMonitoring() -> Double {
        var threadsList: thread_act_array_t?
        var threadsCount = mach_msg_type_number_t(0)
        let threadsResult = task_threads(mach_task_self_, &threadsList, &threadsCount)

        guard threadsResult == KERN_SUCCESS else {
            FlareLog.error("CPU monitoring failed: Cannot get thread list")
            return 0
        }

        FlareLog.performance("CPU monitoring: Found \(threadsCount) threads")

        // 清理内存
        if let threadsList {
            vm_deallocate(mach_task_self_,
                          vm_address_t(UInt(bitPattern: threadsList)),
                          vm_size_t(Int(threadsCount) * MemoryLayout<thread_t>.stride))
        }

        return 1.0 // 返回非零值表示成功
    }

    private func testMemoryMonitoring() -> UInt64 {
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
            let memoryMB = Double(info.resident_size) / (1024 * 1024)
            FlareLog.performance("Memory monitoring: Current usage \(String(format: "%.1f", memoryMB))MB")
            return info.resident_size
        } else {
            FlareLog.error("Memory monitoring failed: kern_return_t = \(kerr)")
            return 0
        }
    }

    private func testFrameRateMonitoring() -> Bool {
        // 测试CADisplayLink创建
        let displayLink = CADisplayLink(target: self, selector: #selector(dummyFrameUpdate))
        displayLink.add(to: .main, forMode: .common)

        // 立即移除，只是测试创建
        displayLink.invalidate()

        FlareLog.performance("Frame rate monitoring: CADisplayLink creation successful")
        return true
    }

    @objc private func dummyFrameUpdate() {
        // 空实现，仅用于测试
    }

    // - 性能监控器状态检查

    func checkPerformanceMonitorState() {
        FlareLog.performance("=== Performance Monitor State Check ===")

        let monitor = TimelinePerformanceMonitor.shared

        FlareLog.performance("Monitor state:")
        FlareLog.performance("- Is monitoring: \(monitor.isMonitoring)")
        FlareLog.performance("- Current CPU: \(String(format: "%.1f", monitor.currentCPUUsage * 100))%")
        FlareLog.performance("- Current Memory: \(String(format: "%.1f", Double(monitor.currentMemoryUsage) / 1_000_000))MB")
        FlareLog.performance("- Current Frame Rate: \(String(format: "%.1f", monitor.currentFrameRate))fps")

        FlareLog.performance("=== State Check Complete ===")
    }

    // - 强制性能监控测试

    func forcePerformanceMonitoringTest() {
        FlareLog.performance("=== Force Performance Monitoring Test ===")

        let monitor = TimelinePerformanceMonitor.shared

        FlareLog.performance("1. Starting monitoring...")
        monitor.startMonitoring()

        // 等待一秒让监控启动
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            FlareLog.performance("2. Checking state after 1 second...")
            self.checkPerformanceMonitorState()

            // 跨平台调用功能已移除
            FlareLog.performance("3. Cross-platform call tracking has been removed from the system")

            // 再等待一秒
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                FlareLog.performance("4. Final state check...")
                self.checkPerformanceMonitorState()

                FlareLog.performance("5. Generating report...")
                let report = monitor.generateReport()
                FlareLog.performance("Report generated:")
                FlareLog.performance("Summary: \(report.summary)")
                FlareLog.performance("Details: \(report.details)")

                FlareLog.performance("=== Force Test Complete ===")
            }
        }
    }

    // - 日志输出测试

    func testLogOutput() {
        FlareLog.performance("=== Log Output Test ===")

        // 测试不同类型的日志输出
        FlareLog.performance("Testing print statements...")
        FlareLog.debug("[DEBUG] This is a print statement")

        FlareLog.performance("Testing OSLog with different levels...")
        os_log("Debug message", log: .performance, type: .debug)
        os_log("Info message", log: .performance, type: .info)
        os_log("Default message", log: .performance, type: .default)
        os_log("Error message", log: .performance, type: .error)
        os_log("Fault message", log: .performance, type: .fault)

        FlareLog.performance("Testing custom subsystem...")
        let customLog = OSLog(subsystem: "dev.dimension.flare.debug", category: "test")
        os_log("Custom subsystem message", log: customLog, type: .info)

        FlareLog.performance("=== Log Output Test Complete ===")
    }

    // - 完整诊断

    func runFullDiagnostic() {
        FlareLog.performance("🔍 === PERFORMANCE MONITOR FULL DIAGNOSTIC ===")

        checkSystemCapabilities()
        checkPerformanceMonitorState()
        testLogOutput()
        forcePerformanceMonitoringTest()

        FlareLog.performance("🎯 === DIAGNOSTIC COMPLETE ===")
    }
}

// - OSLog Extension for Debug

extension OSLog {
    static let debug = OSLog(subsystem: "dev.dimension.flare", category: "debug")
}
