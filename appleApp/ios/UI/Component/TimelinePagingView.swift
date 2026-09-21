import SwiftUI
import UIKit
import KotlinSharedUI
import FlareAppleUI

struct UITimelinePagingView: View {
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode
    @Environment(\.refresh) private var refreshAction: RefreshAction?
    let data: PagingState<UiTimelineV2>
    let detailStatusKey: MicroBlogKey?
    let key: String
    let topContentInset: CGFloat
    let allowGalleryMode: Bool
    let accessoryItems: [UITimelineCollectionViewAccessoryItem]
    let suppressInitialRefreshIndicator: Bool
    let columnPolicy: TimelineColumnPolicy
    let onIsAtTopChanged: (Bool) -> Void

    init(
        data: PagingState<UiTimelineV2>,
        detailStatusKey: MicroBlogKey?,
        key: String,
        topContentInset: CGFloat = 0,
        allowGalleryMode: Bool = false,
        accessoryItems: [UITimelineCollectionViewAccessoryItem] = [],
        suppressInitialRefreshIndicator: Bool = false,
        columnPolicy: TimelineColumnPolicy = .adaptive,
        onIsAtTopChanged: @escaping (Bool) -> Void = { _ in }
    ) {
        self.data = data
        self.detailStatusKey = detailStatusKey
        self.key = key
        self.topContentInset = topContentInset
        self.allowGalleryMode = allowGalleryMode
        self.accessoryItems = accessoryItems
        self.suppressInitialRefreshIndicator = suppressInitialRefreshIndicator
        self.columnPolicy = columnPolicy
        self.onIsAtTopChanged = onIsAtTopChanged
    }

    var body: some View {
        if allowGalleryMode && timelineDisplayMode == .gallery {
            UIGalleryTimelinePagingView(
                data: data,
                suppressInitialRefreshIndicator: suppressInitialRefreshIndicator,
                onIsAtTopChanged: onIsAtTopChanged
            )
                .ignoresSafeArea(edges: .vertical)
        } else {
            GeometryReader { proxy in
                UITimelineCollectionView(
                    data: data,
                    detailStatusKey: detailStatusKey,
                    topContentInset: topContentInset,
                    columnCount: columnPolicy.columnCount(for: proxy.size.width),
                    accessoryItems: accessoryItems,
                    suppressInitialRefreshIndicator: suppressInitialRefreshIndicator,
                    readingKey: key,
                    onIsAtTopChanged: onIsAtTopChanged
                )
                .ignoresSafeArea(edges: .vertical)
            }
        }
    }
}
