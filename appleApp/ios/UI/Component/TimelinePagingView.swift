import SwiftUI
import KotlinSharedUI
import FlareAppleUI

struct TimelinePagingContent: View {
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
            GalleryTimelinePagingView(data: data)
                .ignoresSafeArea(edges: .vertical)
        } else if horizontalSizeClass == .compact {
            singleListView
        } else {
            GeometryReader { proxy in
                let columnCount = max(Int((proxy.size.width / 320).rounded(.down)), 1)
                CollectionViewTimelineView(
                    data: data,
                    detailStatusKey: detailStatusKey,
                    topContentInset: topContentInset,
                    columnCount: columnCount,
                    suppressInitialRefreshIndicator: suppressInitialRefreshIndicator
                )
                .ignoresSafeArea(edges: .vertical)
            }
        }
    }

    var singleListView: some View {
        CollectionViewTimelineView(
            data: data,
            detailStatusKey: detailStatusKey,
            topContentInset: topContentInset,
            suppressInitialRefreshIndicator: suppressInitialRefreshIndicator
        )
        .ignoresSafeArea(edges: .vertical)
    }
}
