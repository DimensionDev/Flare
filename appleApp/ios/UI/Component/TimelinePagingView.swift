import SwiftUI
import UIKit
import KotlinSharedUI
import FlareAppleUI

struct UITimelinePagingView: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode
    @Environment(\.refresh) private var refreshAction: RefreshAction?
    let data: PagingState<UiTimelineV2>
    let detailStatusKey: MicroBlogKey?
    let key: String
    let topContentInset: CGFloat
    let allowGalleryMode: Bool
    let suppressInitialRefreshIndicator: Bool

    init(
        data: PagingState<UiTimelineV2>,
        detailStatusKey: MicroBlogKey?,
        key: String,
        topContentInset: CGFloat = 0,
        allowGalleryMode: Bool = false,
        suppressInitialRefreshIndicator: Bool = false
    ) {
        self.data = data
        self.detailStatusKey = detailStatusKey
        self.key = key
        self.topContentInset = topContentInset
        self.allowGalleryMode = allowGalleryMode
        self.suppressInitialRefreshIndicator = suppressInitialRefreshIndicator
    }

    var body: some View {
        if allowGalleryMode && timelineDisplayMode == .gallery {
            UIGalleryTimelinePagingView(data: data)
                .ignoresSafeArea(edges: .vertical)
        } else if horizontalSizeClass == .compact {
            singleListView
        } else {
            GeometryReader { proxy in
                if isIPhoneLandscape(size: proxy.size) {
                    singleListView
                } else {
                    UITimelineCollectionView(
                        data: data,
                        detailStatusKey: detailStatusKey,
                        topContentInset: topContentInset,
                        columnCount: max(Int((proxy.size.width / 320).rounded(.down)), 1),
                        suppressInitialRefreshIndicator: suppressInitialRefreshIndicator
                    )
                    .ignoresSafeArea(edges: .vertical)
                }
            }
        }
    }

    private func isIPhoneLandscape(size: CGSize) -> Bool {
        UIDevice.current.userInterfaceIdiom == .phone && size.width > size.height
    }

    var singleListView: some View {
        UITimelineCollectionView(
            data: data,
            detailStatusKey: detailStatusKey,
            topContentInset: topContentInset,
            suppressInitialRefreshIndicator: suppressInitialRefreshIndicator
        )
        .ignoresSafeArea(edges: .vertical)
    }
}
