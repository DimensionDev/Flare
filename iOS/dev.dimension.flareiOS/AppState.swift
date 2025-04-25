import Foundation
import Combine // 导入 Combine 以使用 ObservableObject 和 @Published

// 应用的全局状态管理器
class AppState: ObservableObject {
    // @Published 会在属性值改变时通知 SwiftUI 更新视图
    @Published var isMenuOpen: Bool = false         // 菜单是否打开
    // @Published var isHomeFirstTabActive: Bool = false // 不再需要，手势逻辑已本地化
    @Published var navigationDepth: Int = 0         // 当前导航层级深度 (0代表根视图)
    @Published var menuDragOffset: CGFloat = 0 // 新增：同步拖拽偏移量
 } 