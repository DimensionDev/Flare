import Foundation
import SwiftUI

enum TimelineDisplayType: String, CaseIterable, Identifiable, Codable {
    case timeline
    case mediaWaterfall = "media_waterfall"
    case mediaCardWaterfall = "media_card_waterfall"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .timeline: "timeline"
        case .mediaWaterfall: "media Waterfall"
        case .mediaCardWaterfall: "media Card Waterfall"
        }
    }

    var systemImage: String {
        switch self {
        case .timeline: "list.bullet"
        case .mediaWaterfall: "rectangle.grid.2x2"
        case .mediaCardWaterfall: "rectangle.grid.1x2"
        }
    }

    var description: String {
        switch self {
        case .timeline: "default timeline"
        case .mediaWaterfall: "only media waterfall"
        case .mediaCardWaterfall: "media+text card waterfall"
        }
    }

    static func loadUserPreference(for tabKey: String) -> TimelineDisplayType {
        let key = "timeline_display_type_\(tabKey)"
        let rawValue = UserDefaults.standard.string(forKey: key) ?? TimelineDisplayType.timeline.rawValue
        return TimelineDisplayType(rawValue: rawValue) ?? .timeline
    }

    func saveUserPreference(for tabKey: String) {
        let key = "timeline_display_type_\(tabKey)"
        UserDefaults.standard.set(rawValue, forKey: key)

        FlareLog.debug("TimelineDisplayType Saved preference: \(displayName) for tab: \(tabKey)")
    }
}

enum ClickAction {
    case showMediaPreview(media: Media, allMedias: [Media], index: Int)
    case showWaterfallMediaPreview(media: Media, allWaterfallMedias: [Media], index: Int)
    case showTimelineDetail(timelineItem: TimelineItem)
}

struct WaterfallItem: Identifiable, Hashable {
    let id: String
    let sourceTimelineItem: TimelineItem
    let displayMedia: Media // 要显示的媒体（Card模式用第一个，Media模式用当前这个）
    let mediaIndex: Int // 在原推文中的媒体索引
    let displayType: TimelineDisplayType

    var aspectRatio: CGFloat {
        let width = Double(displayMedia.width ?? 300)
        let height = Double(displayMedia.height ?? 400)
        let ratio = CGFloat(width / max(height, 1.0))
        return ratio > 0 ? ratio : 1.0
    }

    var previewURL: URL? {
        URL(string: displayMedia.previewUrl ?? displayMedia.url)
    }

    var isVideo: Bool {
        displayMedia.type == .video || displayMedia.type == .gif
    }

    var shouldShowText: Bool {
        displayType == .mediaCardWaterfall
    }

    var previewText: String {
        guard shouldShowText else { return "" }
        let text = sourceTimelineItem.content.raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return String(text.prefix(100)) + (text.count > 100 ? "..." : "")
    }

    var imageClickAction: ClickAction {
        .showMediaPreview(
            media: displayMedia,
            allMedias: sourceTimelineItem.images,
            index: mediaIndex
        )
    }

    var contentClickAction: ClickAction {
        .showTimelineDetail(timelineItem: sourceTimelineItem)
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }

    static func == (lhs: WaterfallItem, rhs: WaterfallItem) -> Bool {
        lhs.id == rhs.id
    }
}
