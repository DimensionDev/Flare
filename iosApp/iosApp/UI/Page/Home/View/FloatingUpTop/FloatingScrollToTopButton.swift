import SwiftUI

/// 可拖动的"返回顶部"浮动按钮
/// 功能特性：
/// 1. 可拖动到屏幕任意位置
/// 3. 点击触发滚动到顶部功能
/// 4. 与现有标签栏滚动逻辑集成
struct FloatingScrollToTopButton: View {
    /// 按钮是否可见
    @Binding var isVisible: Bool

    /// 滚动到顶部的触发器，与现有逻辑集成
    @Binding var scrollToTopTrigger: Bool

    /// 累积偏移量（持久化的位置）
    @State private var accumulatedOffset: CGSize = .zero

    /// 拖动时的临时偏移量
    @State private var dragOffset: CGSize = .zero

    /// 拖动状态
    @State private var isDragging = false

    /// 屏幕尺寸（缓存以避免重复计算）
    @State private var screenSize: CGSize = .zero

    @Environment(FlareTheme.self) private var theme

    var body: some View {
        Group {
            if isVisible {
                Button(action: scrollToTopAction) {
                    buttonContent
                }
                .offset(x: accumulatedOffset.width + dragOffset.width,
                        y: accumulatedOffset.height + dragOffset.height)
                .gesture(dragGesture)
                .animation(.spring(response: FloatingButtonConfig.springResponse,
                                   dampingFraction: FloatingButtonConfig.springDamping),
                           value: isDragging)
                .transition(.scale.combined(with: .opacity))
                .zIndex(FloatingButtonConfig.zIndex)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .allowsHitTesting(true)
                .onAppear(perform: setupInitialPosition)
            }
        }
    }

    private var buttonContent: some View {
        Image(systemName: "arrow.up")
            .font(.system(size: FloatingButtonConfig.iconSize, weight: .medium))
            .foregroundColor(.white)
            .frame(width: FloatingButtonConfig.buttonSize, height: FloatingButtonConfig.buttonSize)
            .background(
                Circle()
                    .fill(theme.tintColor)
                    .shadow(
                        color: .black.opacity(FloatingButtonConfig.shadowOpacity),
                        radius: FloatingButtonConfig.shadowRadius,
                        x: FloatingButtonConfig.shadowOffset.width,
                        y: FloatingButtonConfig.shadowOffset.height
                    )
            )
            .scaleEffect(isDragging ? FloatingButtonConfig.dragScale : 1.0)
    }

    private var dragGesture: some Gesture {
        DragGesture()
            .onChanged { value in
                if !isDragging {
                    isDragging = true
                }
                dragOffset = value.translation
            }
            .onEnded { value in
                isDragging = false

                withAnimation(.spring()) {
                    accumulatedOffset.width += value.translation.width
                    accumulatedOffset.height += value.translation.height
                    dragOffset = .zero

                    // 限制在屏幕范围内
                    constrainToScreen()
                }
            }
    }

    private func scrollToTopAction() {
        scrollToTopTrigger.toggle()
    }

    /// 设置初始位置
    private func setupInitialPosition() {
        screenSize = UIScreen.main.bounds.size

        // 只在第一次设置初始位置
        if accumulatedOffset == .zero {
            accumulatedOffset = CGSize(
                width: screenSize.width - FloatingButtonConfig.defaultPositionOffset.width,
                height: screenSize.height - FloatingButtonConfig.defaultPositionOffset.height
            )
            constrainToScreen()
        }
    }

    /// 限制按钮位置在屏幕范围内
    private func constrainToScreen() {
        // 使用缓存的屏幕尺寸，避免重复调用 UIScreen.main.bounds
        let screenWidth = screenSize.width
        let screenHeight = screenSize.height

        // 限制在屏幕范围内，考虑按钮大小和边距
        let minX = FloatingButtonConfig.screenPadding
        let maxX = screenWidth - FloatingButtonConfig.screenPadding - FloatingButtonConfig.buttonSize
        let minY = FloatingButtonConfig.screenPadding
        let maxY = screenHeight - FloatingButtonConfig.screenPadding - FloatingButtonConfig.buttonSize - FloatingButtonConfig.bottomExtraMargin

        accumulatedOffset.width = max(minX, min(maxX, accumulatedOffset.width))
        accumulatedOffset.height = max(minY, min(maxY, accumulatedOffset.height))
    }
}
