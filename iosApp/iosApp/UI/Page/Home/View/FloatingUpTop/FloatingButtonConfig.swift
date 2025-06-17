import SwiftUI

/// 浮动滚动到顶部按钮的配置
enum FloatingButtonConfig {
    /// 按钮大小，与底部 tabbar 的 home 按钮大小一致
    static let buttonSize: CGFloat = 44

    /// 屏幕边距
    static let screenPadding: CGFloat = 20

    /// 底部额外边距（避免与 tabbar 重叠）
    static let bottomExtraMargin: CGFloat = 100

    /// 默认位置偏移量
    static let defaultPositionOffset = CGSize(width: 80, height: 250)

    /// 拖动时的缩放效果
    static let dragScale: CGFloat = 1.1

    /// 图标大小
    static let iconSize: CGFloat = 20

    /// 阴影参数
    static let shadowRadius: CGFloat = 8
    static let shadowOffset = CGSize(width: 0, height: 4)
    static let shadowOpacity: Double = 0.2

    /// 动画参数
    static let springResponse: Double = 0.3
    static let springDamping: Double = 0.7
    static let showHideAnimationDuration: Double = 0.3

    /// 滚动检测阈值
    static let scrollThreshold: CGFloat = 50

    /// z-index 层级
    static let zIndex: Double = 999
}
