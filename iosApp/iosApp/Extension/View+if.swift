import SwiftUI

// extension View {
//  @ViewBuilder public func `if`<Content: View>(_ condition: Bool, transform: (Self) -> Content)
//    -> some View
//  {
//    if condition {
//      transform(self)
//    } else {
//      self
//    }
//  }
// }

extension View {
    /// 添加骨架屏加载效果
    func shimmering() -> some View {
        modifier(ShimmeringModifier())
    }

    @ViewBuilder
    func `if`(_ condition: Bool, transform: (Self) -> some View) -> some View {
        if condition {
            transform(self)
        } else {
            self
        }
    }

    @ViewBuilder
    func modifyIf(_ condition: Bool, transform: (Self) -> some View) -> some View {
        if condition {
            transform(self)
        } else {
            self
        }
    }
}
