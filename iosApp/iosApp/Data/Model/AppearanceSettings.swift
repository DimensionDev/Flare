import Foundation

struct AppearanceSettings: Codable {
    let theme: Theme
    let avatarShape: AvatarShape
    let showActions: Bool
    let showNumbers: Bool
    let showLinkPreview: Bool
    let showMedia: Bool
    let showSensitiveContent: Bool
    let swipeGestures: Bool
    let mastodon: Mastodon
    let misskey: Misskey
    let bluesky: Bluesky
    
    struct Mastodon : Codable {
        let showVisibility: Bool
        let swipeLeft: SwipeActions
        let swipeRight: SwipeActions
        
        enum SwipeActions: Codable {
            case none
            case reply
            case reblog
            case favourite
            case bookmark
        }
    }
    
    struct Misskey: Codable {
        let showVisibility: Bool
        let showReaction: Bool
        let swipeLeft: SwipeActions
        let swipeRight: SwipeActions
        
        enum SwipeActions: Codable {
            case none
            case reply
            case renote
            case favourite
        }
    }
    
    struct Bluesky: Codable {
        let swipeLeft: SwipeActions
        let swipeRight: SwipeActions
        
        enum SwipeActions: Codable {
            case none
            case reply
            case reblog
            case favourite
        }
    }
    
}

enum Theme: Codable {
    case auto
    case light
    case dark
}

enum AvatarShape: Codable {
    case circle
    case square
}
