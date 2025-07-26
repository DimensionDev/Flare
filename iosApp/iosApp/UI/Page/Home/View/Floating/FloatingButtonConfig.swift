import SwiftUI

enum FloatingButtonConfig {
    static let buttonSize: CGFloat = 44

    static let screenPadding: CGFloat = 20

    static let bottomExtraMargin: CGFloat = 100

    static let defaultPositionOffset = CGSize(width: 80, height: 250)

    static let dragScale: CGFloat = 1.1

    static let iconSize: CGFloat = 20

    static let shadowRadius: CGFloat = 8
    static let shadowOffset = CGSize(width: 0, height: 4)
    static let shadowOpacity: Double = 0.2

    static let springResponse: Double = 0.3
    static let springDamping: Double = 0.7
    static let showHideAnimationDuration: Double = 0.3

    static let scrollThreshold: CGFloat = 50

    static let zIndex: Double = 999
}
