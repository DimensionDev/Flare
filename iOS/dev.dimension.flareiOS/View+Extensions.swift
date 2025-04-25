import SwiftUI

extension View {
    /// 条件性地应用一个 simultaneousGesture。
    @ViewBuilder func conditionalSimultaneousGesture<T: Gesture>(_ condition: Bool, gesture: T, including mask: GestureMask = .all) -> some View {
        if condition {
            self.simultaneousGesture(gesture, including: mask)
        } else {
            self // 如果条件为 false，返回原始视图，不附加手势
        }
    }

    // 可以在此添加其他通用的 View 扩展...
} 