import SwiftUI
import Combine

public enum FlareTheme: String, CaseIterable, Identifiable, Codable {
    case system = "System"
    case flare = "Flare"
    case dim = "Dim"
    case classic = "Classic"
    case snowfall = "Snowfall"
    case breezy = "Breezy"
    case ember = "Ember"
    case sunset = "Sunset"
    case carbon = "Carbon"
    case nord = "Nord"
    case dracula = "Dracula"
    case minuit = "Minuit"
    case noir = "Noir"
    
    public var id: String { self.rawValue }
    
    public var displayName: String {
        self.rawValue
    }
    
    public var isSystemBased: Bool {
        self == .system
    }
    
    public var icon: String {
        switch self {
        case .system:
            return "iphone"
        case .flare:
            return "app.badge"
        case .dim:
            return "moon.stars"
        case .classic:
            return "sun.max"
        case .snowfall:
            return "snowflake"
        case .breezy:
            return "wind"
        case .ember:
            return "flame"
        case .sunset:
            return "sunset"
        case .carbon:
            return "diamond"
        case .nord:
            return "snow"
        case .dracula:
            return "drop"
        case .minuit:
            return "sparkles"
        case .noir:
            return "circle.slash"
        }
    }
    
    public var userInterfaceStyle: UIUserInterfaceStyle {
        switch self {
        case .system:
            return .unspecified
        case .flare, .classic, .breezy, .snowfall:
            return .light
        default:
            return .dark
        }
    }
} 