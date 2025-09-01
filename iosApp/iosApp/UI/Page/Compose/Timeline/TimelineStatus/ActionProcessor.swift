import Awesome
import JXPhotoBrowser
import Kingfisher
import MarkdownUI
import os.log
import shared

import SwiftUI
import UIKit

enum ActionProcessor {
    /***
     🔍 [TimelineActionsView] Total actions count: 4
     🔍 [TimelineActionsView] Action[0]: SharedStatusActionItemReply
         📝 Reply Action - count: 2
     🔍 [TimelineActionsView] Action[1]: SharedStatusActionGroup
         📁 Group Action - displayItem: SharedStatusActionItemRetweet
         📁 Group Actions count: 2
             📁 SubAction[0]: SharedStatusActionItemRetweet
                 🔄 Sub-Retweet Action - count: 5, retweeted: false
         📁 SubAction[1]: SharedStatusActionItemQuote
             ❓ Unknown Sub-Item: SharedStatusActionItemQuote
     🔍 [TimelineActionsView] Action[2]: SharedStatusActionItemLike
         ❤️ Like Action - count: 21, liked: false
     🔍 [TimelineActionsView] Action[3]: SharedStatusActionGroup
         📁 Group Action - displayItem: SharedStatusActionItemMore
         📁 Group Actions count: 2
             📁 SubAction[0]: SharedStatusActionItemBookmark
                 🔖 Sub-Bookmark Action - count: 4, bookmarked: false
            📁 SubAction[1]: SharedStatusActionItemReport
                ❓ Unknown Sub-Item: SharedStatusActionItemReport

     ***/

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
