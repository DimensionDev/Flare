import SwiftUI

// 让 Color 可以被 AppStorage 持久化存储
extension SwiftUI.Color: @retroactive RawRepresentable {
  public init?(rawValue: Int) {
    let red = Double((rawValue & 0xFF0000) >> 16) / 255.0
    let green = Double((rawValue & 0x00FF00) >> 8) / 255.0
    let blue = Double(rawValue & 0x0000FF) / 255.0
    self.init(red: red, green: green, blue: blue)
  }

  public var rawValue: Int {
    guard let coreImageColor else {
      return 0
    }
    let red = Int(coreImageColor.red * 255 + 0.5)
    let green = Int(coreImageColor.green * 255 + 0.5)
    let blue = Int(coreImageColor.blue * 255 + 0.5)
    return (red << 16) | (green << 8) | blue
  }

  private var coreImageColor: CIColor? {
    CIColor(color: UIColor(self))
  }
}

// 添加颜色辅助方法，方便使用十六进制颜色
extension SwiftUI.Color {
  init(hex: Int, opacity: Double = 1.0) {
    let red = Double((hex & 0xFF0000) >> 16) / 255.0
    let green = Double((hex & 0xFF00) >> 8) / 255.0
    let blue = Double((hex & 0xFF) >> 0) / 255.0
    self.init(red: red, green: green, blue: blue, opacity: opacity)
  }
}

// 添加 Flare 主题颜色便捷访问扩展，避免与 Asset.Color 冲突
extension SwiftUI.Color {
  // 使用 flare 前缀避免与资源文件命名冲突
  static var flareTint: SwiftUI.Color {
    SwiftUI.Color(red: 187 / 255, green: 59 / 255, blue: 226 / 255)
  }
  
  static var flarePrimaryBackground: SwiftUI.Color {
    SwiftUI.Color(red: 16 / 255, green: 21 / 255, blue: 35 / 255)
  }
  
  static var flareSecondaryBackground: SwiftUI.Color {
    SwiftUI.Color(red: 30 / 255, green: 35 / 255, blue: 62 / 255)
  }
  
  static var flareLabel: SwiftUI.Color {
    SwiftUI.Color(UIColor.label)
  }
} 
 