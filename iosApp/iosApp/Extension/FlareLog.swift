import Foundation

public enum FlareLog {
    
    public static func debug(_ message: String) {
        #if DEBUG
            print("🔍 [Flare] \(message)")
        #endif
    }

    
    public static func info(_ message: String) {
        #if DEBUG
            print("ℹ️ [Flare] \(message)")
        #endif
    }

 
    public static func warning(_ message: String) {
        #if DEBUG
            print("⚠️ [Flare] \(message)")
        #endif
    }
 
    public static func error(_ message: String) {
        print("❌ [Flare] \(message)")
    }

 
    public static func performance(_ message: String) {
        #if DEBUG
            print("⚡ [Flare] \(message)")
        #endif
    }
}
