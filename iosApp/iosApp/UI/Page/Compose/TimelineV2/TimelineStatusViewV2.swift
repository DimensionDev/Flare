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
    let timelineViewModel: TimelineViewModel?

    let isDetail: Bool = false
    @State private var showAppleTranslation: Bool = false
    @State private var showGoogleTranslation: Bool = false
    @State private var isTranslating: Bool = false

//    @State private var isShareSheetPresented = false
    @State private var isShareAsImageSheetPresented = false
    @State private var isPreparingShare = false

    @Environment(\.appSettings) private var appSettings
    @Environment(\.colorScheme) private var colorScheme
    @Environment(FlareRouter.self) private var router
    @Environment(FlareTheme.self) private var theme

    private var isGuestUser: Bool {
        let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()
        return accountType is AccountTypeGuest
    }

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
                    onMediaClick: { _, _ in
                    },
                    onPodcastCardTap: { card in
                        handlePodcastCardTap(card: card)
                    }
                )

                if !isGuestUser {
                    TimelineActionsViewV2(
                        item: item,
                        timelineViewModel: timelineViewModel,
                        onAction: { actionType, updatedItem in
                            handleTimelineAction(actionType, item: updatedItem)
                        },
                        onShare: { shareType in
                            handleShare(type: shareType)
                        }
                    )
                } else {
                    Spacer().frame(height: 16)
                }
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
            .sheet(isPresented: $isShareAsImageSheetPresented) {
                StatusShareAsImageViewV2(
                    content: self,
                    shareText: getShareTitle(allContent: false)
                )
                .environment(\.appSettings, appSettings)
                .environment(\.colorScheme, colorScheme)
                .environment(router)
                .environment(theme).applyTheme(theme)
            }
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
            let _ = AppDeepLinkHelper().parse(url: card.url)
            if let url = URL(string: card.url) {
                router.handleDeepLink(url)
            }
        }
    }

    private func handleTimelineAction(_ actionType: TimelineActionType, item: TimelineItem) {
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

    // MARK: - 分享功能

    private func handleShare(type: ShareType) {
        switch type {
        case .sharePost:
            handleSharePost()
        case .shareAsImage:
            handleShareAsImage()
        }
    }

    private func handleSharePost() {
        isPreparingShare = true
        prepareScreenshot { image in
            if let image {
                var activityItems: [Any] = []
                let shareTitle = getShareTitle(allContent: true)
                activityItems.append(shareTitle)
                activityItems.append(image)

                if let statusUrl = getStatusUrl() {
                    activityItems.append(statusUrl)
                }

                let activityVC = UIActivityViewController(
                    activityItems: activityItems,
                    applicationActivities: nil
                )

                if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                   let window = windowScene.windows.first,
                   let rootVC = window.rootViewController
                {
                    activityVC.popoverPresentationController?.sourceView = window
                    rootVC.present(activityVC, animated: true)
                }
            }
            isPreparingShare = false
        }
    }

    private func handleShareAsImage() {
        // 立即打开Sheet，截图将在Sheet中进行
        isShareAsImageSheetPresented = true
    }

    private func prepareScreenshot(completion: @escaping (UIImage?) -> Void) {
        let captureView = StatusCaptureWrapperV2(content: self)
            .environment(\.appSettings, appSettings)
            .environment(\.colorScheme, colorScheme)
            .environment(\.isInCaptureMode, true)
            .environment(router)
            .environment(theme).applyTheme(theme)

        let controller = UIHostingController(rootView: captureView)

        let targetSize = controller.sizeThatFits(in: CGSize(
            width: UIScreen.main.bounds.width - 24,
            height: UIView.layoutFittingExpandedSize.height
        ))

        controller.view.bounds = CGRect(origin: .zero, size: targetSize)
        controller.view.backgroundColor = .clear

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            let renderer = UIGraphicsImageRenderer(size: targetSize)
            let image = renderer.image { _ in
                controller.view.drawHierarchy(in: controller.view.bounds, afterScreenUpdates: true)
            }
            completion(image)
        }
    }

    private func getShareTitle(allContent: Bool) -> String {
        let content = item.content.raw
        let author = item.user?.name.raw ?? item.user?.handle ?? "Unknown"

        if allContent {
            return "\(author): \(content)"
        } else {
            return content
        }
    }

    private func getStatusUrl() -> URL? {
        guard !item.url.isEmpty else { return nil }
        return URL(string: item.url)
    }
}

struct StatusCaptureWrapperV2: View {
    let content: TimelineStatusViewV2

    var body: some View {
        content
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
    }
}
