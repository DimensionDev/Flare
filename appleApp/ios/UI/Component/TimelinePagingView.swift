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
    let accessoryItems: [UITimelineCollectionViewAccessoryItem]
    let suppressInitialRefreshIndicator: Bool
    let onIsAtTopChanged: (Bool) -> Void

    init(
        data: PagingState<UiTimelineV2>,
        detailStatusKey: MicroBlogKey?,
        key: String,
        topContentInset: CGFloat = 0,
        allowGalleryMode: Bool = false,
        accessoryItems: [UITimelineCollectionViewAccessoryItem] = [],
        suppressInitialRefreshIndicator: Bool = false,
        onIsAtTopChanged: @escaping (Bool) -> Void = { _ in }
    ) {
        self.data = data
        self.detailStatusKey = detailStatusKey
        self.key = key
        self.topContentInset = topContentInset
        self.allowGalleryMode = allowGalleryMode
        self.accessoryItems = accessoryItems
        self.suppressInitialRefreshIndicator = suppressInitialRefreshIndicator
        self.onIsAtTopChanged = onIsAtTopChanged
    }

    var body: some View {
        if allowGalleryMode && timelineDisplayMode == .gallery {
            UIGalleryTimelinePagingView(
                data: data,
                accessoryItems: accessoryItems,
                onIsAtTopChanged: onIsAtTopChanged
            )
                .ignoresSafeArea(edges: .vertical)
        } else if UIDevice.current.userInterfaceIdiom == .phone ||
            horizontalSizeClass == .compact {
            singleListView
        } else {
            GeometryReader { proxy in
                UITimelineCollectionView(
                    data: data,
                    detailStatusKey: detailStatusKey,
                    topContentInset: topContentInset,
                    columnCount: max(Int((proxy.size.width / 320).rounded(.down)), 1),
                    accessoryItems: accessoryItems,
                    suppressInitialRefreshIndicator: suppressInitialRefreshIndicator,
                    onIsAtTopChanged: onIsAtTopChanged
                )
                .ignoresSafeArea(edges: .vertical)
            }
        }
    }

    var singleListView: some View {
        UITimelineCollectionView(
            data: data,
            detailStatusKey: detailStatusKey,
            topContentInset: topContentInset,
            accessoryItems: accessoryItems,
            suppressInitialRefreshIndicator: suppressInitialRefreshIndicator,
            onIsAtTopChanged: onIsAtTopChanged
        )
        .ignoresSafeArea(edges: .vertical)
    }
}
