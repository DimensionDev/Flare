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

struct TimelineStatusViewV2: View {
    let item: TimelineItem
    let index: Int

    let isDetail: Bool = false
    @State private var showAppleTranslation: Bool = false
    @State private var showGoogleTranslation: Bool = false
    @State private var isTranslating: Bool = false

    @Environment(\.openURL) private var openURL
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareRouter.self) private var router
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        if shouldHideInTimeline {
            EmptyView()
        } else {
            VStack(alignment: .leading) {
                Spacer().frame(height: 5)

                if let topMessage = item.topMessage {
                    StatusRetweetHeaderComponentV2(topMessage: topMessage)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 4)
                }

                StatusHeaderViewV2(
                    timelineItem: item,
                    isDetailView: isDetail
                )

                StatusContentViewV2(
                    item: item,
                    isDetailView: isDetail,
                    enableGoogleTranslation: showGoogleTranslation,
                    appSettings: appSettings,
                    theme: theme,
                    openURL: openURL,
                    onMediaClick: { _, _ in
                    },
                    onPodcastCardTap: { card in
                        handlePodcastCardTap(card: card)
                    }
                )

                TimelineActionsViewV2(
                    item: item,
                    onAction: { actionType, updatedItem in
                        handleTimelineAction(actionType, item: updatedItem, at: index)
                    }
                )
            }
            .padding(.horizontal, 16)
            .frame(alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture {
                handleStatusTap()
            }
            #if canImport(_Translation_SwiftUI)
            .addTranslateView(isPresented: $showAppleTranslation, text: item.content.raw)
            #endif
        }
    }

    private var shouldHideInTimeline: Bool {
        let sensitiveSettings = appSettings.appearanceSettings.sensitiveContentSettings

        guard sensitiveSettings.hideInTimeline else {
            return false
        }

        guard item.sensitive else {
            return false
        }

        if let timeRange = sensitiveSettings.timeRange {
            let shouldHide = timeRange.isCurrentTimeInRange()
            return shouldHide
        } else {
            return true
        }
    }

    private func handleStatusTap() {
        // detailKey == data.statusKey
        let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()

        let statusKey = item.createMicroBlogKey()

        router.navigate(to: .statusDetail(
            accountType: accountType,
            statusKey: statusKey
        ))
    }

    private func handlePodcastCardTap(card: Card) {
        if let route = AppDeepLinkHelper().parse(url: card.url) as? AppleRoute.Podcast {
            router.navigate(to: .podcastSheet(accountType: route.accountType, podcastId: route.id))
        } else {
            let parsedRoute = AppDeepLinkHelper().parse(url: card.url)
            if let url = URL(string: card.url) {
                openURL(url)
            }
        }
    }

    private func handleTimelineAction(_ actionType: TimelineActionType, item: TimelineItem, at _: Int) {
        if actionType == .translate {
            guard !isTranslating else {
                return
            }

            guard !item.content.raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                return
            }

            isTranslating = true
            let provider = appSettings.otherSettings.translationProvider

            if provider == .systemOffline {
                showAppleTranslation = true
            } else {
                showGoogleTranslation = true
            }

            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                isTranslating = false
            }
            return
        }
    }
}
