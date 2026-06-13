import AppKit
import FlareAppleCore
import FlareAppleUI
import KotlinSharedUI
import SwiftUI

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
        } else {
            GeometryReader { proxy in
                if proxy.size.width < 640 {
                    singleListView
                } else {
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

struct TimelineView: View {
    let data: UiTimelineV2
    let detailStatusKey: MicroBlogKey?
    @Environment(\.timelineAppearance) private var timelineAppearance
    @Environment(\.globalAppearance) private var globalAppearance
    @Environment(\.aiConfig) private var aiConfig
    @Environment(\.openURL) private var openURL

    var body: some View {
        TimelineRepresentedView(
            data: data,
            detailStatusKey: detailStatusKey,
            appearance: StatusAppKitAppearance(
                timeline: timelineAppearance,
                fontSizeDiff: globalAppearance.fontSizeDiff
            ),
            aiTldrEnabled: aiConfig.tldr,
            onOpenURL: { url in openURL.callAsFunction(url) }
        )
    }
}

extension TimelineView {
    init(data: UiTimelineV2) {
        self.data = data
        self.detailStatusKey = nil
    }
}

struct TimelinePlaceholderView: NSViewRepresentable {
    func makeNSView(context: Context) -> TimelinePlaceholderUIView {
        TimelinePlaceholderUIView()
    }

    func updateNSView(_ nsView: TimelinePlaceholderUIView, context: Context) {}
}

private struct TimelineRepresentedView: NSViewRepresentable {
    let data: UiTimelineV2
    let detailStatusKey: MicroBlogKey?
    let appearance: StatusAppKitAppearance
    let aiTldrEnabled: Bool
    let onOpenURL: (URL) -> Void

    func makeNSView(context: Context) -> TimelineUIView {
        let view = TimelineUIView()
        configure(view)
        return view
    }

    func updateNSView(_ nsView: TimelineUIView, context: Context) {
        configure(nsView)
    }

    private func configure(_ view: TimelineUIView) {
        view.configure(
            data: data,
            appearance: appearance,
            detailStatusKey: detailStatusKey,
            aiTldrEnabled: aiTldrEnabled,
            onOpenURL: onOpenURL
        )
    }
}
