import SwiftUI
import UIKit
import KotlinSharedUI
import FlareAppleUI

struct UITimelinePagingView: View {
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode
    @Environment(\.refresh) private var refreshAction: RefreshAction?
    let readingState: TimelineReadingState?
    let data: PagingState<UiTimelineV2>
    let detailStatusKey: MicroBlogKey?
    let key: String
    @Environment(\.timelineAccountID) private var accountID
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
        readingState: TimelineReadingState? = nil,
        topContentInset: CGFloat = 0,
        allowGalleryMode: Bool = false,
        accessoryItems: [UITimelineCollectionViewAccessoryItem] = [],
        suppressInitialRefreshIndicator: Bool = false,
        columnPolicy: TimelineColumnPolicy = .adaptive,
        onIsAtTopChanged: @escaping (Bool) -> Void = { _ in }
    ) {
        self.readingState = readingState
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
                readingState: readingState,
                suppressInitialRefreshIndicator: suppressInitialRefreshIndicator,
                onIsAtTopChanged: onIsAtTopChanged
            )
                .id("\(accountID):\(key)")
                .ignoresSafeArea(edges: .vertical)
        } else {
            GeometryReader { proxy in
                UITimelineCollectionView(
                    data: data,
                    detailStatusKey: detailStatusKey,
                    readingState: readingState,
                    topContentInset: topContentInset,
                    columnCount: columnPolicy.columnCount(for: proxy.size.width),
                    accessoryItems: accessoryItems,
                    suppressInitialRefreshIndicator: suppressInitialRefreshIndicator,
                    onIsAtTopChanged: onIsAtTopChanged
                )
                .id("\(accountID):\(key)")
                .ignoresSafeArea(edges: .vertical)
            }
            .modifier(TimelineListBackground(columnPolicy: columnPolicy))
        }
    }
}
