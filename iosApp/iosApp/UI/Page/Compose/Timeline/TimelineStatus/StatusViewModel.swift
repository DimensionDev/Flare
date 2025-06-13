import Awesome
import Generated
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import os.log
import shared
import SwiftDate
import SwiftUI
import UIKit

@Observable
class StatusContentViewModel {
    let content: UiRichText
    let isRTL: Bool

    init(content: UiRichText) {
        self.content = content
        isRTL = content.isRTL
    }

    var hasContent: Bool { !content.raw.isEmpty }
    var rawText: String { content.raw }
    var markdownText: String { content.markdown }
}

struct StatusViewModel {
    let data: UiTimelineItemContentStatus
    let isDetail: Bool
    let enableTranslation: Bool

    init(data: UiTimelineItemContentStatus, isDetail: Bool, enableTranslation: Bool = true) {
        self.data = data
        self.isDetail = isDetail
        self.enableTranslation = enableTranslation
    }

    var statusData: UiTimelineItemContentStatus { data }
    var shouldShowTranslation: Bool { enableTranslation }
    var isDetailView: Bool { isDetail }

    var hasUser: Bool { data.user != nil }
    var hasAboveTextContent: Bool { data.aboveTextContent != nil }
    var hasContentWarning: Bool { data.contentWarning != nil && !data.contentWarning!.raw.isEmpty }
    var hasContent: Bool { !data.content.raw.isEmpty }
    var hasImages: Bool { !data.images.isEmpty }
    var hasCard: Bool { data.card != nil }
    var hasQuote: Bool { !data.quote.isEmpty }
    var hasBottomContent: Bool { data.bottomContent != nil }
    var hasActions: Bool { !data.actions.isEmpty }

    var isPodcastCard: Bool {
        guard let card = data.card,
              let url = URL(string: card.url) else { return false }
        return url.scheme == "flare" && url.host?.lowercased() == "podcast"
    }

    var shouldShowLinkPreview: Bool {
        guard let card = data.card else { return false }
        return !isPodcastCard && card.media != nil
    }

    func getProcessedActions() -> (mainActions: [StatusAction], moreActions: [StatusActionItem]) {
        ActionProcessor.processActions(data.actions)
    }

    func getFormattedDate() -> String {
        let dateInRegion = DateInRegion(data.createdAt, region: .current)
        return dateInRegion.toRelative(since: DateInRegion(Date(), region: .current))
    }
}

enum ActionProcessor {
    static func processActions(_ actions: [StatusAction]) -> (mainActions: [StatusAction], moreActions: [StatusActionItem]) {
        var bottomMainActions: [StatusAction] = []
        var bottomMoreActions: [StatusActionItem] = []

        for action in actions {
            switch onEnum(of: action) {
            case let .item(item):
                // 所有非 More 的 item 都加入主操作
                if !(item is StatusActionItemMore) {
                    //                    if item is StatusActionItemReaction {
                    //                        // misskey 的+ emoji，先去掉
                    //                    } else {

                    bottomMainActions.append(action)
                    //                    }
                }
            case let .group(group):
                let displayItem = group.displayItem
                if (displayItem as? StatusActionItemMore) != nil {
                    // 只处理 More 菜单中的操作
                    for subAction in group.actions {
                        if case let .item(item) = onEnum(of: subAction) {
                            if item is StatusActionItemBookmark {
                                // 将书签添加到主操作
                                bottomMainActions.append(subAction)
                            } else {
                                // 其他操作添加到更多操作
                                // bottomMoreActions.append(item)
                            }
                        } else if subAction is StatusActionAsyncActionItem {}
                    }
                } else {
                    // 其他 group（比如转发组）保持原样
                    bottomMainActions.append(action)
                }
            case .asyncActionItem:
                break
            }
        }

        return (bottomMainActions, bottomMoreActions)
    }
}
