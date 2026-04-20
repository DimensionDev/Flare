import SwiftUI
import KotlinSharedUI

struct TimelinePagingView: View {
    let data: PagingState<UiTimelineV2>
    let detailStatusKey: MicroBlogKey?
    var body: some View {
        PagingView(data: data) {
            ListEmptyView()
        } errorContent: { error, retry in
            ListErrorView(error: error) {
                retry()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        } loadingContent: { index, totalCount in
            AdaptiveTimelineCard(index: index, totalCount: totalCount) {
                TimelinePlaceholderView()
                    .padding(.horizontal)
                    .padding(.vertical, 12)
            }
        } successContent: { item, index, totalCount in
            AdaptiveTimelineCard(index: index, totalCount: totalCount) {
                TimelineView(data: item, detailStatusKey: detailStatusKey)
                    .padding(.horizontal)
                    .padding(.vertical, 12)
            }
        }
    }
}

extension TimelinePagingView {
    init(data: PagingState<UiTimelineV2>) {
        self.data = data
        self.detailStatusKey = nil
    }
}

struct TimelineData: Identifiable, Hashable {
    let id: String
    let data: UiTimelineV2?
    let index: Int
}

struct TimelineCollection: @MainActor RandomAccessCollection {
    let data: PagingStateSuccess<UiTimelineV2>
    public var startIndex: Int { 0 }
    public var endIndex: Int { Int(data.itemCount) }

    public func index(after index: Int) -> Int { index + 1 }
    public func index(before index: Int) -> Int { index - 1 }
    public func index(_ index: Int, offsetBy distance: Int) -> Int { index + distance }
    public func distance(from start: Int, to end: Int) -> Int { end - start }

    public subscript(position: Int) -> TimelineData {
        let item = data.peek(index: Int32(position))
        return TimelineData(id: item?.itemKey ?? "\(position)", data: item, index: position)
    }

    public var count: Int { Int(data.itemCount) }
}

struct TimelinePagingContent: View {
    @AppStorage("pref_timeline_use_compose_view") private var useComposeView: Bool = false
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.openURL) private var openURL
    @Environment(\.appearanceSettings.timelineDisplayMode) private var timelineDisplayMode
    @Environment(\.refresh) private var refreshAction: RefreshAction?
    let data: PagingState<UiTimelineV2>
    let detailStatusKey: MicroBlogKey?
    let key: String
    let topContentInset: CGFloat
    let allowGalleryMode: Bool

    init(
        data: PagingState<UiTimelineV2>,
        detailStatusKey: MicroBlogKey?,
        key: String,
        topContentInset: CGFloat = 0,
        allowGalleryMode: Bool = false
    ) {
        self.data = data
        self.detailStatusKey = detailStatusKey
        self.key = key
        self.topContentInset = topContentInset
        self.allowGalleryMode = allowGalleryMode
    }

    var body: some View {
        if allowGalleryMode && timelineDisplayMode == .gallery {
            GalleryTimelinePagingView(data: data)
                .ignoresSafeArea(edges: .vertical)
        } else if useComposeView {
            ComposeTimelineView(
                key: key,
                data: data,
                detailStatusKey: detailStatusKey,
                topPadding: 0,
                onOpenLink: { url in
                    if let targetURL = URL(string: url) {
                        openURL.callAsFunction(targetURL)
                    }
                },
                onExpand: {},
                onCollapse: {}
            )
            .ignoresSafeArea(edges: .vertical)
            .background(Color(.systemGroupedBackground))
        } else if horizontalSizeClass == .compact {
            singleListView
        } else {
            GeometryReader { proxy in
                let columnCount = max(Int((proxy.size.width / 320).rounded(.down)), 1)
                CollectionViewTimelineView(
                    data: data,
                    detailStatusKey: detailStatusKey,
                    topContentInset: topContentInset,
                    columnCount: columnCount
                )
                .ignoresSafeArea(edges: .vertical)
            }
        }
    }

    var singleListView: some View {
        CollectionViewTimelineView(
            data: data,
            detailStatusKey: detailStatusKey,
            topContentInset: topContentInset
        )
        .ignoresSafeArea(edges: .vertical)
    }
}

