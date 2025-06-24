import SwiftUI

/// 浮动窗口覆盖层
/// 全局覆盖层，用于在任何页面上显示浮动性能监控窗口
struct FloatingWindowOverlay: View {
    @StateObject private var windowManager = FloatingWindowManager.shared

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // 浮动窗口
                if windowManager.state.isVisible {
                    FloatingPerformanceWindow()
                        .allowsHitTesting(true)
                        .zIndex(1000) // 确保在最顶层
                }
            }
            .onAppear {
                windowManager.setScreenSize(geometry.size)
            }
            .onChange(of: geometry.size) { newSize in
                windowManager.setScreenSize(newSize)
            }
        }
        .allowsHitTesting(windowManager.state.isVisible)
    }
}

/// 浮动窗口修饰器
/// ViewModifier，用于为任何视图添加浮动窗口功能
struct FloatingWindowModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .overlay(
                FloatingWindowOverlay()
                    .allowsHitTesting(true)
            )
    }
}

// MARK: - View Extension

extension View {
    /// 添加浮动性能监控窗口
    func floatingPerformanceWindow() -> some View {
        modifier(FloatingWindowModifier())
    }
}

/// 浮动窗口控制面板
/// 用于在设置或测试页面中控制浮动窗口
struct FloatingWindowControlPanel: View {
    @StateObject private var windowManager = FloatingWindowManager.shared
    @StateObject private var monitor = TimelinePerformanceMonitor.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // 控制按钮
            HStack {
                Button(action: {
                    if PerformanceConfig.isVerboseLoggingEnabled {
                        print("[FloatingWindowControl] 🔘 Show/Hide button tapped")
                        print("[FloatingWindowControl] Current state: \(windowManager.state)")
                        print("[FloatingWindowControl] Is visible: \(windowManager.state.isVisible)")
                    }

                    if windowManager.state.isVisible {
                        if PerformanceConfig.isVerboseLoggingEnabled {
                            print("[FloatingWindowControl] 👁️ Hiding window")
                        }
                        windowManager.hide()
                    } else {
                        if PerformanceConfig.isVerboseLoggingEnabled {
                            print("[FloatingWindowControl] 👁️ Showing window")
                            print("[FloatingWindowControl] Monitor is monitoring: \(monitor.isMonitoring)")
                        }

                        if !monitor.isMonitoring {
                            if PerformanceConfig.isVerboseLoggingEnabled {
                                print("[FloatingWindowControl] 🚀 Starting monitoring")
                            }
                            monitor.startMonitoring()
                        }

                        if PerformanceConfig.isVerboseLoggingEnabled {
                            print("[FloatingWindowControl] 📱 Calling windowManager.show()")
                        }
                        windowManager.show()

                        if PerformanceConfig.isVerboseLoggingEnabled {
                            print("[FloatingWindowControl] ✅ windowManager.show() completed")
                            print("[FloatingWindowControl] New state: \(windowManager.state)")
                        }
                    }
                }) {
                    HStack {
                        Image(systemName: windowManager.state.isVisible ? "eye.slash" : "eye")
                        Text(windowManager.state.isVisible ? "Hide Monitor Window" : "Show Monitor Window")
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(windowManager.state.isVisible ? Color.red : Color.green)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                }
            }

//            // 状态信息
//            VStack(alignment: .leading, spacing: 8) {
//                Text("Window Status")
//                    .font(.subheadline)
//                    .fontWeight(.medium)
//
//                HStack {
//                    Text("State:")
//                    Spacer()
//                    Text(windowManager.state.rawValue.capitalized)
//                        .foregroundColor(stateColor)
//                        .fontWeight(.medium)
//                }
//
//                HStack {
//                    Text("Position:")
//                    Spacer()
//                    Text("(\(Int(windowManager.position.x)), \(Int(windowManager.position.y)))")
//                        .font(.system(.caption, design: .monospaced))
//                        .foregroundColor(.secondary)
//                }
//
//                if windowManager.state == .expanded {
//                    HStack {
//                        Text("Selected Chart:")
//                        Spacer()
//                        Text(chartName)
//                            .foregroundColor(.blue)
//                            .fontWeight(.medium)
//                    }
//                }
//            }
//            .padding()
//             .cornerRadius(10)
//
//            // 使用说明
//            VStack(alignment: .leading, spacing: 4) {
//                Text("Usage Instructions")
//                    .font(.subheadline)
//                    .fontWeight(.medium)
//
//                Text("• Tap to expand/minimize")
//                Text("• Drag to move position")
//                Text("• Auto-snaps to screen edges")
//                Text("• Position is saved automatically")
//            }
//            .font(.caption)
//            .foregroundColor(.secondary)
//            .padding()
//            .background(Color.blue.opacity(0.1))
//            .cornerRadius(10)
        }
        .padding()
//        .background(
//            RoundedRectangle(cornerRadius: 12)
//                .fill(.background)
//                .shadow(color: .black.opacity(0.1), radius: 4, x: 0, y: 2)
//        )
    }

    // MARK: - Computed Properties

    private var stateColor: Color {
        switch windowManager.state {
        case .hidden:
            .gray
        case .minimized:
            .green
        case .expanded:
            .blue
        case .dragging:
            .orange
        }
    }

    private var chartName: String {
        switch windowManager.selectedChart {
        case 0:
            "CPU Usage"
        case 1:
            "Memory Usage"
        case 2:
            "Frame Rate"
        default:
            "Unknown"
        }
    }
}

// MARK: - Preview

#Preview {
    VStack(spacing: 20) {
        FloatingWindowControlPanel()

        Spacer()
    }
    .padding()
    .background(Color(.systemGroupedBackground))
}
