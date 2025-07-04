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

struct TimelineStatusView: View {
    let data: UiTimelineItemContentStatus
    let isDetail: Bool
    let enableTranslation: Bool
    @State private var showMedia: Bool = false
    @State private var showShareMenu: Bool = false

    @Environment(\.openURL) private var openURL
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareRouter.self) private var router
    @Environment(FlareTheme.self) private var theme

    let onMediaClick: (Int, UiMedia) -> Void

    init(data: UiTimelineItemContentStatus, onMediaClick: @escaping (Int, UiMedia) -> Void, isDetail: Bool, enableTranslation: Bool = true) {
        self.data = data
        self.isDetail = isDetail
        self.enableTranslation = enableTranslation
        self.onMediaClick = onMediaClick
    }

    //  每次都要算，性能堪忧，无解，后期想办法
    private var viewModel: StatusViewModel {
        StatusViewModel(data: data, isDetail: isDetail, enableTranslation: enableTranslation)
    }

    var body: some View {
        VStack(alignment: .leading) {
            Spacer().frame(height: 2)

            StatusHeaderView(viewModel: viewModel)

            StatusContentView(
                viewModel: viewModel,
                appSettings: appSettings,
                theme: theme,
                openURL: openURL,
                onMediaClick: onMediaClick,
                onPodcastCardTap: handlePodcastCardTap
            )

            StatusActionsView(
                viewModel: viewModel,
                appSettings: appSettings,
                openURL: openURL,
                parentView: self
            )

            // Spacer().frame(height: 3)
        }
        .frame(alignment: .leading)
        .contentShape(Rectangle())
        .onTapGesture {
            handleStatusTap()
        }
    }

    private func handleStatusTap() {
        // if let tapLocation = UIApplication.shared.windows.first?.hitTest(
        //     UIApplication.shared.windows.first?.convert(CGPoint(x: 0, y: 0), to: nil) ?? .zero,
        //     with: nil
        // ) {
        //     let bottomActionBarFrame = CGRect(
        //         x: 16, y: tapLocation.frame.height - 44,
        //         width: tapLocation.frame.width - 32, height: 44
        //     )
        // if !bottomActionBarFrame.contains(tapLocation.frame.origin) {
        router.navigate(to: .statusDetail(
            accountType: UserManager.shared.getCurrentAccountType() ?? AccountTypeGuest(),
            statusKey: viewModel.statusData.statusKey
        ))
        // }
        // }
    }

    private func handlePodcastCardTap(card: UiCard) {
        if let route = AppDeepLinkHelper().parse(url: card.url) as? AppleRoute.Podcast {
            FlareLog.debug("TimelineStatusView Podcast Card Tapped, navigating via router to: podcastSheet(accountType: \(route.accountType), podcastId: \(route.id))")
            router.navigate(to: .podcastSheet(accountType: route.accountType, podcastId: route.id))
        } else {
            let parsedRoute = AppDeepLinkHelper().parse(url: card.url)
            FlareLog.error("TimelineStatusView Error: Could not parse Podcast URL from card: \(card.url). Parsed type: \(type(of: parsedRoute)) Optional value: \(String(describing: parsedRoute))")
        }
    }
}
