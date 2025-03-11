import shared
import SwiftUI

// MARK: - View扩展

extension View {
    /// 添加骨架屏加载效果
    func shimmering() -> some View {
        modifier(ShimmeringModifier())
    }

    /// 条件性修饰符
    @ViewBuilder
    func `if`(_ condition: Bool, transform: (Self) -> some View) -> some View {
        if condition {
            transform(self)
        } else {
            self
        }
    }

    /// 添加错误提示弹窗
    func errorAlert(error: Binding<Error?>, buttonTitle: String = "确定") -> some View {
        // 创建一个Binding<Bool>来控制警告的显示
        let isPresented = Binding<Bool>(
            get: { error.wrappedValue != nil },
            set: { if !$0 { error.wrappedValue = nil } }
        )

        // 提取错误信息
        let errorTitle = (error.wrappedValue as? LocalizedError)?.errorDescription ?? "错误"
        let errorMessage = (error.wrappedValue as? LocalizedError)?.recoverySuggestion ?? "请稍后重试"

        // 使用基本alert而不是带error参数的版本
        return alert(
            errorTitle,
            isPresented: isPresented,
            actions: {
                Button(buttonTitle) {
                    error.wrappedValue = nil
                }
            },
            message: {
                Text(errorMessage)
            }
        )
    }
}

// MARK: - 骨架屏效果

struct ShimmeringModifier: ViewModifier {
    @State private var phase: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .overlay(
                GeometryReader { geometry in
                    LinearGradient(
                        gradient: Gradient(stops: [
                            .init(color: .clear, location: 0),
                            .init(color: .white.opacity(0.5), location: 0.3),
                            .init(color: .white.opacity(0.5), location: 0.7),
                            .init(color: .clear, location: 1),
                        ]),
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                    .frame(width: geometry.size.width * 2)
                    .offset(x: -geometry.size.width + (geometry.size.width * 2) * phase)
                    .blendMode(.screen)
                }
            )
            .mask(content)
            .onAppear {
                withAnimation(Animation.linear(duration: 1.5).repeatForever(autoreverses: false)) {
                    phase = 1
                }
            }
    }
}

// MARK: - 日期格式化工具

enum DateFormatter {
    static func formatRelative(dateString _: String) -> String {
        // 实际项目中应该实现真正的日期格式化逻辑
        "几分钟前"
    }
}
