import SwiftUI

struct FLNewSideMenuConfiguration {
    let menuWidth: CGFloat
    let animationDuration: Double
    let shadowRadius: CGFloat
    let shadowOpacity: Float

    static let `default` = FLNewSideMenuConfiguration(
        menuWidth: UIScreen.main.bounds.width * 0.7,
        animationDuration: 0.3,
        shadowRadius: 5,
        shadowOpacity: 0.3
    )
}
