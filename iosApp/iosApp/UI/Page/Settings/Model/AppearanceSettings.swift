import Foundation

struct SensitiveContentTimeRange: Codable, Equatable {
    var startTime: Date
    var endTime: Date
    var isEnabled: Bool = true

    init(startTime: Date = Date(), endTime: Date = Date(), isEnabled: Bool = true) {
        self.startTime = startTime
        self.endTime = endTime
        self.isEnabled = isEnabled
    }

    func isCurrentTimeInRange() -> Bool {
        guard isEnabled else { return false }

        let now = Date()
        let calendar = Calendar.current
        let currentTime = calendar.dateComponents([.hour, .minute], from: now)
        let startComponents = calendar.dateComponents([.hour, .minute], from: startTime)
        let endComponents = calendar.dateComponents([.hour, .minute], from: endTime)

        let currentMinutes = (currentTime.hour ?? 0) * 60 + (currentTime.minute ?? 0)
        let startMinutes = (startComponents.hour ?? 0) * 60 + (startComponents.minute ?? 0)
        let endMinutes = (endComponents.hour ?? 0) * 60 + (endComponents.minute ?? 0)

        if startMinutes <= endMinutes {
            return currentMinutes >= startMinutes && currentMinutes <= endMinutes
        } else {
            //   22:00 to 06:00
            return currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }

    var displayText: String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return "\(formatter.string(from: startTime)) - \(formatter.string(from: endTime))"
    }
}

struct SensitiveContentSettings: Codable, Changeable {
    var hideImages: Bool = false
    var hideVideos: Bool = false
    var timeRange: SensitiveContentTimeRange?
    var hideInTimeline: Bool = true
    var isCollapsed: Bool = false
    var isShowingTimePicker: Bool? = false

    init() {}
}

struct HapticFeedbackSettings: Codable, Changeable {
    var isEnabled: Bool = true
    var intensity: HapticIntensity = .medium

    enum HapticIntensity: String, CaseIterable, Codable {
        case light = "light"
        case medium = "medium"
        case heavy = "heavy"

        var displayName: String {
            switch self {
            case .light: return "Light"
            case .medium: return "Medium"
            case .heavy: return "Heavy"
            }
        }
    }

    init() {}
}

struct AppearanceSettings: Codable, Changeable {
    var theme: Theme = .auto
    var avatarShape: AvatarShape = .circle
    var renderEngine: RenderEngine = .markdown
    var timelineDisplayType: TimelineDisplayType = .timeline
    var showActions: Bool = true
    var showNumbers: Bool = true
    var showLinkPreview: Bool = true
    var showMedia: Bool = true
    var showSensitiveContent: Bool = false
    var sensitiveContentSettings: SensitiveContentSettings = .init()
    var swipeGestures: Bool = false
    var enableFullSwipePop: Bool = true
    var hideScrollToTopButton: Bool = false
    var hapticFeedback: HapticFeedbackSettings = .init()
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
    case emojiText

    var title: String {
        switch self {
        case .markdown:
            "Markdown"
        case .emojiText:
            "EmojiText"
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
