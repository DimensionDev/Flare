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

struct TimelineItemState {
    var currentSheet: SheetType?

    var isTranslating: Bool = false
    var isPreparingShare: Bool = false
    var enableGoogleTranslation: Bool = false

    enum SheetType {
        case appleTranslation
        case shareAsImage
        case textSelection
        case urlSelection
        case manualTranslation
    }

    var showAppleTranslation: Bool { currentSheet == .appleTranslation }
    var showShareAsImage: Bool { currentSheet == .shareAsImage }
    var showTextForSelection: Bool { currentSheet == .textSelection }
    var showSelectUrlSheet: Bool { currentSheet == .urlSelection }
    var showManualTranslation: Bool { currentSheet == .manualTranslation }
}

struct TimelineStatusViewV2: View, Equatable {
    let item: TimelineItem
    let timelineViewModel: TimelineViewModel?

    let isDetail: Bool = false

    static func == (lhs: TimelineStatusViewV2, rhs: TimelineStatusViewV2) -> Bool {
        guard lhs.item.id == rhs.item.id else { return false }

        return lhs.item.content.raw == rhs.item.content.raw &&
            lhs.item.user?.key == rhs.item.user?.key &&
            lhs.item.timestamp == rhs.item.timestamp &&

            lhs.item.likeCount == rhs.item.likeCount &&
            lhs.item.isLiked == rhs.item.isLiked &&
            lhs.item.retweetCount == rhs.item.retweetCount &&
            lhs.item.isRetweeted == rhs.item.isRetweeted &&
            lhs.item.replyCount == rhs.item.replyCount &&
            lhs.item.bookmarkCount == rhs.item.bookmarkCount &&
            lhs.item.isBookmarked == rhs.item.isBookmarked &&
            lhs.item.sensitive == rhs.item.sensitive &&
            lhs.item.images.count == rhs.item.images.count &&
            lhs.isDetail == rhs.isDetail
    }

    @State private var state = TimelineItemState()

    @Environment(\.appSettings) private var appSettings
    @Environment(\.colorScheme) private var colorScheme
    @Environment(FlareRouter.self) private var router
    @Environment(FlareTheme.self) private var theme

    private var isGuestUser: Bool {
        let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()
        return accountType is AccountTypeGuest
    }

    var body: some View {
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
                enableGoogleTranslation: state.enableGoogleTranslation,
                appSettings: appSettings,
                theme: theme,
                onMediaClick: { _, _ in
                },
                onPodcastCardTap: { card in
                    handlePodcastCardTap(card: card)
                }
            ).contentShape(Rectangle())
                .onTapGesture {
                    handleStatusTap()
                }

            if !isGuestUser {
                TimelineActionsViewV2(
                    item: item,
                    timelineViewModel: timelineViewModel,
                    onAction: { actionType, updatedItem in
                        handleTimelineAction(actionType, item: updatedItem)
                    },
                    onShare: { actionType in
                        handleMoreAction(actionType)
                    }
                )
            } else {
                Spacer().frame(height: 16)
            }
        }
        .padding(.horizontal, 16)
        .frame(alignment: .leading)

        #if canImport(_Translation_SwiftUI)
            .addTranslateView(
                isPresented: Binding(
                    get: { state.showAppleTranslation },
                    set: { _ in state.currentSheet = nil }
                ),
                text: item.content.raw
            )
        #endif
            .sheet(isPresented: Binding(
                get: { state.showShareAsImage },
                set: { _ in state.currentSheet = nil }
            )) {
                StatusShareAsImageViewV2(
                    content: self,
                    shareText: getShareTitle(allContent: false)
                )
                .environment(\.appSettings, appSettings)
                .environment(\.colorScheme, colorScheme)
                .environment(router)
                .environment(theme).applyTheme(theme)
            }
            .sheet(isPresented: Binding(
                get: { state.showTextForSelection },
                set: { _ in state.currentSheet = nil }
            )) {
                let imageURLsString = item.images.map(\.url).joined(separator: "\n")
                let selectableContent = AttributedString(item.content.markdown + "\n" + imageURLsString)
                StatusRowSelectableTextView(content: selectableContent)
                    .tint(.accentColor)
                    .environment(theme)
            }
            .sheet(isPresented: Binding(
                get: { state.showSelectUrlSheet },
                set: { _ in state.currentSheet = nil }
            )) {
                let urlsString = item.images.map(\.url).joined(separator: "\n")
                StatusRowSelectableTextView(content: AttributedString(urlsString))
                    .tint(.accentColor)
                    .environment(theme)
            }
        #if canImport(_Translation_SwiftUI)
            .addTranslateView(
                isPresented: Binding(
                    get: { state.showManualTranslation },
                    set: { _ in state.currentSheet = nil }
                ),
                text: item.content.raw
            )
        #endif
    }

    private func handleStatusTap() {
        // detailKey == data.statusKey
        let accountType = UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest()

        let statusKey = item.createMicroBlogKey()

        router.navigate(to: .statusDetailV2(
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
            guard !state.isTranslating else {
                return
            }

            guard !item.content.raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                return
            }

            state.isTranslating = true
            let provider = appSettings.otherSettings.translationProvider

            if provider == .systemOffline {
                state.currentSheet = .appleTranslation
            } else {
                state.enableGoogleTranslation = true
            }

            DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                state.isTranslating = false
            }
            return
        }
    }

    private func handleMoreAction(_ actionType: MoreActionType) {
        switch actionType {
        case .sharePost:
            handleSharePost()
        case .shareAsImage:
            state.currentSheet = .shareAsImage
        case .showTextForSelection:
            state.currentSheet = .textSelection
        case .translate:
            state.currentSheet = .manualTranslation
        case .copyMediaLink, .copyMediaURLs:
            state.currentSheet = .urlSelection
        case .copyText:
            UIPasteboard.general.string = item.content.raw
            showSuccessToast()
        case .copyMarkdown:
            UIPasteboard.general.string = item.content.markdown
            showSuccessToast()
        case .copyTweetLink:
            if !item.url.isEmpty {
                UIPasteboard.general.string = item.url
                showSuccessToast()
            }
        case .openInBrowser:
            if let url = URL(string: item.url) {
                router.handleDeepLink(url)
            }
        case .report:
            showSuccessToast(message: "Report Success")
        case .saveMedia:
            showSuccessToast(message: "download to App \n Download Manager")
        }
    }

    private func showSuccessToast(message: String = "Copy Success") {
        ToastView(
            icon: UIImage(systemName: "checkmark.circle"),
            message: NSLocalizedString(message, comment: "")
        ).show()
    }

    private func handleSharePost() {
        state.isPreparingShare = true
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
            state.isPreparingShare = false
        }
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
            width: UIScreen.main.bounds.width,
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
            .padding(.vertical, 8)
    }
}
