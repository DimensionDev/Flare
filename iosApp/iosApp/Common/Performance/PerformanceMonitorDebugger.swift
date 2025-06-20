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
    
    // MARK: - 系统信息检查
    
    func checkSystemCapabilities() {
        print("=== Performance Monitor System Check ===")
        
        // 检查设备信息
        let device = UIDevice.current
        print("Device: \(device.model)")
        print("System: \(device.systemName) \(device.systemVersion)")
        print("Process Info: \(ProcessInfo.processInfo.processName)")
        
        // 检查日志系统
        checkLoggingSystem()
        
        // 检查性能监控权限
        checkPerformanceMonitoringCapabilities()
        
        print("=== System Check Complete ===\n")
    }
    
    private func checkLoggingSystem() {
        print("\n--- Logging System Check ---")
        
        // 测试OSLog
        let testLog = OSLog(subsystem: "dev.dimension.flare", category: "debug")
        os_log("OSLog test message", log: testLog, type: .info)
        print("OSLog test message sent")
        
        // 测试不同日志级别
        os_log("Debug level test", log: .performance, type: .debug)
        os_log("Info level test", log: .performance, type: .info)
        os_log("Error level test", log: .performance, type: .error)
        
        print("Multiple log level tests sent")
    }
    
    private func checkPerformanceMonitoringCapabilities() {
        print("\n--- Performance Monitoring Capabilities ---")
        
        // 检查CPU监控
        let cpuUsage = testCPUMonitoring()
        print("CPU monitoring test: \(cpuUsage > 0 ? "✅ Working" : "❌ Failed")")
        
        // 检查内存监控
        let memoryUsage = testMemoryMonitoring()
        print("Memory monitoring test: \(memoryUsage > 0 ? "✅ Working" : "❌ Failed")")
        
        // 检查帧率监控
        let canMonitorFrameRate = testFrameRateMonitoring()
        print("Frame rate monitoring test: \(canMonitorFrameRate ? "✅ Working" : "❌ Failed")")
    }
    
    // MARK: - 性能监控组件测试
    
    private func testCPUMonitoring() -> Double {
        var threadsList: thread_act_array_t?
        var threadsCount = mach_msg_type_number_t(0)
        let threadsResult = task_threads(mach_task_self_, &threadsList, &threadsCount)
        
        guard threadsResult == KERN_SUCCESS else {
            print("❌ CPU monitoring failed: Cannot get thread list")
            return 0
        }
        
        print("✅ CPU monitoring: Found \(threadsCount) threads")
        
        // 清理内存
        if let threadsList = threadsList {
            vm_deallocate(mach_task_self_, 
                         vm_address_t(UInt(bitPattern: threadsList)), 
                         vm_size_t(Int(threadsCount) * MemoryLayout<thread_t>.stride))
        }
        
        return 1.0 // 返回非零值表示成功
    }
    
    private func testMemoryMonitoring() -> UInt64 {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size)/4
        
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
            print("✅ Memory monitoring: Current usage \(String(format: "%.1f", memoryMB))MB")
            return info.resident_size
        } else {
            print("❌ Memory monitoring failed: kern_return_t = \(kerr)")
            return 0
        }
    }
    
    private func testFrameRateMonitoring() -> Bool {
        // 测试CADisplayLink创建
        let displayLink = CADisplayLink(target: self, selector: #selector(dummyFrameUpdate))
        displayLink.add(to: .main, forMode: .common)
        
        // 立即移除，只是测试创建
        displayLink.invalidate()
        
        print("✅ Frame rate monitoring: CADisplayLink creation successful")
        return true
    }
    
    @objc private func dummyFrameUpdate() {
        // 空实现，仅用于测试
    }
    
    // MARK: - 性能监控器状态检查
    
    func checkPerformanceMonitorState() {
        print("=== Performance Monitor State Check ===")
        
        let monitor = TimelinePerformanceMonitor.shared
        
        print("Monitor state:")
        print("- Is monitoring: \(monitor.isMonitoring)")
        print("- Current CPU: \(String(format: "%.1f", monitor.currentCPUUsage * 100))%")
        print("- Current Memory: \(String(format: "%.1f", Double(monitor.currentMemoryUsage) / 1_000_000))MB")
        print("- Current Frame Rate: \(String(format: "%.1f", monitor.currentFrameRate))fps")
        
        print("=== State Check Complete ===\n")
    }
    
    // MARK: - 强制性能监控测试
    
    func forcePerformanceMonitoringTest() {
        print("=== Force Performance Monitoring Test ===")
        
        let monitor = TimelinePerformanceMonitor.shared
        
        print("1. Starting monitoring...")
        monitor.startMonitoring()
        
        // 等待一秒让监控启动
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
            print("2. Checking state after 1 second...")
            self.checkPerformanceMonitorState()
            
            // 跨平台调用功能已移除
            print("3. Cross-platform call tracking has been removed from the system")
            
            // 再等待一秒
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                print("4. Final state check...")
                self.checkPerformanceMonitorState()
                
                print("5. Generating report...")
                let report = monitor.generateReport()
                print("Report generated:")
                print("Summary: \(report.summary)")
                print("Details: \(report.details)")
                
                print("=== Force Test Complete ===\n")
            }
        }
    }
    
    // MARK: - 日志输出测试
    
    func testLogOutput() {
        print("=== Log Output Test ===")
        
        // 测试不同类型的日志输出
        print("Testing print statements...")
        print("[DEBUG] This is a print statement")
        
        print("Testing OSLog with different levels...")
        os_log("Debug message", log: .performance, type: .debug)
        os_log("Info message", log: .performance, type: .info)
        os_log("Default message", log: .performance, type: .default)
        os_log("Error message", log: .performance, type: .error)
        os_log("Fault message", log: .performance, type: .fault)
        
        print("Testing custom subsystem...")
        let customLog = OSLog(subsystem: "dev.dimension.flare.debug", category: "test")
        os_log("Custom subsystem message", log: customLog, type: .info)
        
        print("=== Log Output Test Complete ===\n")
    }
    
    // MARK: - 完整诊断
    
    func runFullDiagnostic() {
        print("\n🔍 === PERFORMANCE MONITOR FULL DIAGNOSTIC ===\n")
        
        checkSystemCapabilities()
        checkPerformanceMonitorState()
        testLogOutput()
        forcePerformanceMonitoringTest()
        
        print("🎯 === DIAGNOSTIC COMPLETE ===\n")
    }
}

// MARK: - OSLog Extension for Debug

extension OSLog {
    static let debug = OSLog(subsystem: "dev.dimension.flare", category: "debug")
}
