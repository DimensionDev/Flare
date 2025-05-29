import Foundation

struct AppearanceSettings: Codable, Changeable {
    var theme: Theme = .auto
    var avatarShape: AvatarShape = .circle
    var renderEngine: RenderEngine = .markdown
    var showActions: Bool = true
    var showNumbers: Bool = true
    var showLinkPreview: Bool = true
    var showMedia: Bool = true
    var showSensitiveContent: Bool = false
    var swipeGestures: Bool = false
    var enableFullSwipePop: Bool = true
    var autoTranslate: Bool = false
    var mastodon: Mastodon = .init()
    var misskey: Misskey = .init()
    var bluesky: Bluesky = .init()
    struct Mastodon: Codable, Changeable {
        var showVisibility: Bool = true
        var swipeLeft: SwipeActions = .reply
        var swipeRight: SwipeActions = .none
        enum SwipeActions: Codable {
            case none
            case reply
            case reblog
            case favourite
            case bookmark
        }
    }

    struct Misskey: Codable, Changeable {
        var showVisibility: Bool = true
        var showReaction: Bool = true
        var swipeLeft: SwipeActions = .reply
        var swipeRight: SwipeActions = .none
        enum SwipeActions: Codable {
            case none
            case reply
            case renote
            case favourite
        }
    }

    struct Bluesky: Codable, Changeable {
        var swipeLeft: SwipeActions = .reply
        var swipeRight: SwipeActions = .none
        enum SwipeActions: Codable {
            case none
            case reply
            case reblog
            case favourite
        }
    }
}

enum RenderEngine: Codable, CaseIterable {
    case markdown
    case flareText

    var title: String {
        switch self {
        case .markdown:
            "Markdown"
        case .flareText:
            "FlareText"
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

protocol Changeable {}

extension Changeable {
    func changing<T>(path: WritableKeyPath<Self, T>, to value: T) -> Self {
        var clone = self
        clone[keyPath: path] = value
        return clone
    }
}
