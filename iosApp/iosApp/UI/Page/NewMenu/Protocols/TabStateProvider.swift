import Foundation
import SwiftUI

protocol TabStateProvider: AnyObject {
    // 必需实现的属性
    var selectedIndex: Int { get }
    var tabCount: Int { get }
    
    // 可选实现的属性和方法
    var onTabChange: ((Int) -> Void)? { get set }
    
    // 状态变化通知
    func notifyTabChange()
}

// 提供默认实现
extension TabStateProvider {
    func notifyTabChange() {
        onTabChange?(selectedIndex)
    }
} 