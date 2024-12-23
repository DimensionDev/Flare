import SwiftUI

extension SwiftUI.Color {
    // MARK: - Background Colors
    static var backgroundPrimary: SwiftUI.Color {
        Colors.Background.swiftUIPrimary
    }
    
    static var backgroundSecondary: SwiftUI.Color {
        Colors.Background.swiftUISecondary
    }
    
    static var backgroundTertiary: SwiftUI.Color {
        Colors.Background.swiftUITertiary
    }
    
    // MARK: - Text Colors
    static var textPrimary: SwiftUI.Color {
        Colors.Text.swiftUIPrimary
    }
    
    static var textSecondary: SwiftUI.Color {
        Colors.Text.swiftUISecondary
    }
    
    static var textTertiary: SwiftUI.Color {
        Colors.Text.swiftUITertiary
    }
    
    // MARK: - Interactive Colors
    static var interactiveActive: SwiftUI.Color {
        Colors.State.swiftUIActive
    }
    
    static var interactiveInactive: SwiftUI.Color {
        Colors.State.swiftUIDeactive
    }
    
    static var interactiveDisabled: SwiftUI.Color {
        Colors.State.swiftUIDeactiveDarker
    }
    
    // MARK: - Function Colors
    static var functionLink: SwiftUI.Color {
        Colors.Link.swiftUIHyperlink
    }
    
    static var functionMention: SwiftUI.Color {
        Colors.Link.swiftUIMention
    }
    
    static var functionHashtag: SwiftUI.Color {
        Colors.Link.swiftUIHashtag
    }
    
    static var functionCashtag: SwiftUI.Color {
        Colors.Link.swiftUICashtag
    }
} 