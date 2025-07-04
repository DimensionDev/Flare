import Foundation

public enum FlareLog {
    /// 调试日志输出
    /// - Parameter message: 要输出的消息
    /// - Note: 只在Debug模式下输出，Release模式下完全不执行
    public static func debug(_ message: String) {
        #if DEBUG
            print("🔍 [Flare] \(message)")
        #endif
    }

    /// 信息日志输出
    /// - Parameter message: 要输出的消息
    /// - Note: 只在Debug模式下输出，Release模式下完全不执行
    public static func info(_ message: String) {
        #if DEBUG
            print("ℹ️ [Flare] \(message)")
        #endif
    }

    /// 警告日志输出
    /// - Parameter message: 要输出的消息
    /// - Note: 只在Debug模式下输出，Release模式下完全不执行
    public static func warning(_ message: String) {
        #if DEBUG
            print("⚠️ [Flare] \(message)")
        #endif
    }

    /// 错误日志输出
    /// - Parameter message: 要输出的消息
    /// - Note: 在Debug和Release模式下都会输出，用于记录关键错误
    public static func error(_ message: String) {
        print("❌ [Flare] \(message)")
    }

    /// 性能相关日志输出
    /// - Parameter message: 要输出的消息
    /// - Note: 只在Debug模式下输出，用于性能调试
    public static func performance(_ message: String) {
        #if DEBUG
            print("⚡ [Flare] \(message)")
        #endif
    }
}
