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

struct AppearanceSettings: Codable, Changeable {
    var theme: Theme = .auto
    var avatarShape: AvatarShape = .circle
    var renderEngine: RenderEngine = .markdown
    var timelineVersion: TimelineVersionSetting = .v4_0 // 新增Timeline版本设置
    var timelineDisplayType: TimelineDisplayType = .timeline // 新增Timeline显示类型设置
    var showActions: Bool = true
    var showNumbers: Bool = true
    var showLinkPreview: Bool = true
    var showMedia: Bool = true
    var showSensitiveContent: Bool = false
    var sensitiveContentSettings: SensitiveContentSettings = .init()
    var swipeGestures: Bool = false
    var enableFullSwipePop: Bool = true
    var hideScrollToTopButton: Bool = false
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
//    case textViewMarkdown
    case emojiText

    var title: String {
        switch self {
        case .markdown:
            "Markdown"
        case .flareText:
            "FlareText"
//        case .textViewMarkdown:
//            "TextViewMarkdown"
        case .emojiText:
            "EmojiText"
        }
    }
}

/// Timeline版本设置枚举
/// 与TimelineVersionManager.TimelineVersion保持一致，但独立定义以避免循环依赖
enum TimelineVersionSetting: String, Codable, CaseIterable, Identifiable {
    case base = "Base"
    case v1_1 = "1.1 Stable ID"
    case v2_0 = "2.0 Data-flow"
    case v3_0 = "3.0"
    case v4_0 = "4 ScrollViewReader + List"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .base:
            "Base (Original)"
        case .v1_1:
            "v1.1 (Stable ID)"
        case .v2_0:
            "v2.0 (Data-flow)"
        case .v3_0:
            "v3.0 (ScrollView + LazyVStack)"
        case .v4_0:
            "v4.0 (List + ScrollViewReader)"
        }
    }

    var description: String {
        switch self {
        case .base:
            "Baseline version with original ForEach implementation"
        case .v1_1:
            "Stable ID system optimization only"
        case .v2_0:
            "Data-flow optimization with SwiftTimelineDataSource"
        case .v3_0:
            "ScrollView + LazyVStack with scrollPosition API"
        case .v4_0:
            "List + ScrollViewReader with iOS 18+ optimization"
        }
    }

    /// 转换为TimelineVersionManager.TimelineVersion
    func toManagerVersion() -> TimelineVersionManager.TimelineVersion {
        switch self {
        case .base:
            .base
        case .v1_1:
            .v1_1
        case .v2_0:
            .v2_0
        case .v3_0:
            .v3_0
        case .v4_0:
            .v4_0
        }
    }

    /// 从TimelineVersionManager.TimelineVersion创建
    static func from(_ managerVersion: TimelineVersionManager.TimelineVersion) -> TimelineVersionSetting {
        switch managerVersion {
        case .base:
            .base
        case .v1_1:
            .v1_1
        case .v2_0:
            .v2_0
        case .v3_0:
            .v3_0
        case .v4_0:
            .v4_0
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
