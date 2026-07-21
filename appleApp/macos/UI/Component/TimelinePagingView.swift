import AppKit
import FlareAppleCore
import FlareAppleUI
import KotlinSharedUI
import SwiftUI

struct TimelinePagingView: View {
    @Environment(\.openWindow) private var openWindow
    @Environment(\.refresh) private var refreshAction: RefreshAction?
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode

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
        GeometryReader { proxy in
//            let columnCount = resolvedColumnCount(width: proxy.size.width)
            ScrollView {
                content(columnCount: 1, availableWidth: proxy.size.width)
                    .padding(.top, topContentInset)
                    .padding(.bottom, 12)
            }
            .background(timelineDisplayMode == .card || timelineDisplayMode == .gallery ? Color(.secondarySystemFill) : Color(.windowBackgroundColor))
            .detectScrolling()
            .id(key)
            .refreshable {
                if let refreshAction {
                    await refreshAction()
                }
            }
            .environment(\.timelineMediaOpenAction, timelineMediaOpenAction)
            .environment(\.timelineMediaActionHandler, timelineMediaActionHandler)
        }
    }

    @ViewBuilder
    private func content(columnCount: Int, availableWidth: CGFloat) -> some View {
        if allowGalleryMode && timelineDisplayMode == .gallery {
            MacGalleryTimelineMasonryView(
                data: data,
                columnCount: max(columnCount, 2),
                availableWidth: availableWidth
            )
        } else if columnCount > 1 {
            MacTimelineMasonryView(
                data: data,
                detailStatusKey: detailStatusKey,
                columnCount: columnCount,
                availableWidth: availableWidth
            )
        } else {
            LazyVStack(spacing: 2) {
                TimelinePagingContent(data: data, detailStatusKey: detailStatusKey)
            }
        }
    }

    private func resolvedColumnCount(width: CGFloat) -> Int {
        guard width > 0 else { return 1 }
        if allowGalleryMode && timelineDisplayMode == .gallery {
            return min(max(Int((width / 220).rounded(.down)), 2), 6)
        }
        return min(max(Int((width / 320).rounded(.down)), 1), 4)
    }

    private var timelineMediaOpenAction: TimelineMediaOpenAction {
        { post, media, index in
            MacMediaWindowCoordinator.shared.open(
                post: post,
                media: media,
                index: index,
                openWindow: openWindow
            )
        }
    }

    private var timelineMediaActionHandler: TimelineMediaActionHandler {
        { post, media, action in
            switch action {
            case .download:
                guard let source = MacMediaExportSource(media: media, shareContext: post.macShareContext) else { return }
                Task {
                    _ = try? await MacMediaFileExporter.save(source: source)
                }
            case .downloadAll:
                let mediaByFileName = MediaFileNamePolicy.shared.statusMediaFileNames(
                    statusKey: post.statusKey.description(),
                    userHandle: post.user?.handle.canonical ?? "unknown",
                    medias: Array(post.images)
                )
                Task {
                    _ = try? await MacMediaFileExporter.saveAll(mediaByFileName: mediaByFileName)
                }
            case .shareImage:
                guard let source = MacMediaExportSource(media: media, shareContext: post.macShareContext),
                      source.supportsSharing else {
                    return
                }
                Task {
                    guard let fileURL = try? await MacMediaFileExporter.makeShareFile(source: source) else {
                        return
                    }
                    try? await MainActor.run {
                        try MacMediaFileExporter.presentSharePicker(
                            fileURL: fileURL,
                            relativeTo: NSApp.keyWindow?.contentView
                        )
                    }
                }
            case .copyLink:
                NSPasteboard.general.clearContents()
                NSPasteboard.general.setString(media.url, forType: .string)
            }
        }
    }
}

private extension UiTimelineV2.Post {
    var macShareContext: MacMediaShareContext {
        MacMediaShareContext(
            statusKey: statusKey.description(),
            userHandle: user?.handle.canonical
        )
    }
}

private struct MacTimelineMasonryView: View {
    @Environment(\.translateConfig) private var translateConfig
    let data: PagingState<UiTimelineV2>
    let detailStatusKey: MicroBlogKey?
    let columnCount: Int
    let availableWidth: CGFloat

    private let loadingCount = 8
    private let columnSpacing: CGFloat = 8
    private let rowSpacing: CGFloat = 12

    var body: some View {
        switch onEnum(of: data) {
        case .empty:
            ListEmptyView()
                .padding()
        case .error(let error):
            ListErrorView(error: error.error) {
                _ = error.onRetry()
            }
            .padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .loading:
            columnsView(
                columns: placeholderColumns(count: loadingCount),
                totalCount: loadingCount
            )
        case .success(let success):
            successContent(success)
        }
    }

    @ViewBuilder
    private func successContent(_ success: PagingStateSuccess<UiTimelineV2>) -> some View {
        let count = Int(success.itemCount)
        if count == 0 {
            ListEmptyView()
                .padding()
        } else {
            let columns = MacTimelineMasonryColumnBuilder.makeTimelineColumns(
                success: success,
                count: count,
                columnCount: columnCount,
                columnWidth: columnWidth,
                rowSpacing: rowSpacing,
                showOriginalWithTranslation: translateConfig.showOriginalWithTranslation
            )
            VStack(spacing: 12) {
                columnsView(columns: columns, totalCount: count) { index in
                    _ = success.get(index: Int32(index))
                }
                appendFooter(success)
            }
        }
    }

    private var columnWidth: CGFloat {
        let horizontalPadding: CGFloat = 16
        let totalSpacing = columnSpacing * CGFloat(max(columnCount - 1, 0))
        return max((availableWidth - horizontalPadding - totalSpacing) / CGFloat(max(columnCount, 1)), 1)
    }

    private func placeholderColumns(count: Int) -> [MacTimelineMasonryColumn] {
        MacTimelineMasonryColumnBuilder.makePlaceholderColumns(count: count, columnCount: columnCount)
    }

    private func columnsView(
        columns: [MacTimelineMasonryColumn],
        totalCount: Int,
        onDisplay: @escaping (Int) -> Void = { _ in }
    ) -> some View {
        HStack(alignment: .top, spacing: columnSpacing) {
            ForEach(columns) { column in
                LazyVStack(spacing: rowSpacing) {
                    ForEach(column.rows) { row in
                        MacTimelineMasonryRowView(
                            row: row,
                            totalCount: totalCount,
                            detailStatusKey: detailStatusKey,
                            onDisplay: onDisplay
                        )
                    }
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 8)
        .environment(\.isMultipleColumn, true)
    }

    @ViewBuilder
    private func appendFooter(_ success: PagingStateSuccess<UiTimelineV2>) -> some View {
        switch onEnum(of: success.appendState) {
        case .error(let error):
            ListErrorView(error: error.error) {
                success.retry()
            }
            .padding(.horizontal)
            .frame(maxWidth: .infinity, alignment: .center)
        case .loading:
            ProgressView()
                .padding()
                .frame(maxWidth: .infinity, alignment: .center)
        case .notLoading:
            EmptyView()
        }
    }
}

private struct MacTimelineMasonryRowView: View {
    let row: MacTimelineMasonryRow
    let totalCount: Int
    let detailStatusKey: MicroBlogKey?
    let onDisplay: (Int) -> Void

    var body: some View {
        ListCardView(index: row.index, totalCount: totalCount) {
            Group {
                if let item = row.item {
                    TimelineView(data: item, detailStatusKey: detailStatusKey)
                } else {
                    TimelinePlaceholderView()
                }
            }
            .padding(.horizontal)
            #if os(macOS)
            .padding(.top, 12)
            .padding(.bottom, 8)
            #else
            .padding(.vertical, 12)
            #endif
        }
        .onAppear {
            onDisplay(row.index)
        }
    }
}

private struct MacGalleryTimelineMasonryView: View {
    @Environment(\.translateConfig) private var translateConfig
    let data: PagingState<UiTimelineV2>
    let columnCount: Int
    let availableWidth: CGFloat

    private let loadingCount = 12
    private let spacing: CGFloat = 8

    var body: some View {
        switch onEnum(of: data) {
        case .empty:
            ListEmptyView()
                .padding()
        case .error(let error):
            ListErrorView(error: error.error) {
                _ = error.onRetry()
            }
            .padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .loading:
            columnsView(columns: placeholderColumns(count: loadingCount), totalCount: loadingCount)
        case .success(let success):
            successContent(success)
        }
    }

    @ViewBuilder
    private func successContent(_ success: PagingStateSuccess<UiTimelineV2>) -> some View {
        let count = Int(success.itemCount)
        if count == 0 {
            ListEmptyView()
                .padding()
        } else {
            let columns = MacTimelineMasonryColumnBuilder.makeGalleryColumns(
                success: success,
                count: count,
                columnCount: columnCount,
                columnWidth: columnWidth,
                rowSpacing: spacing,
                showOriginalWithTranslation: translateConfig.showOriginalWithTranslation
            )
            VStack(spacing: 12) {
                columnsView(columns: columns, totalCount: count) { index in
                    _ = success.get(index: Int32(index))
                }
                appendFooter(success)
            }
        }
    }

    private var columnWidth: CGFloat {
        let horizontalPadding: CGFloat = 16
        let totalSpacing = spacing * CGFloat(max(columnCount - 1, 0))
        return max((availableWidth - horizontalPadding - totalSpacing) / CGFloat(max(columnCount, 1)), 1)
    }

    private func placeholderColumns(count: Int) -> [MacTimelineMasonryColumn] {
        MacTimelineMasonryColumnBuilder.makePlaceholderColumns(count: count, columnCount: columnCount)
    }

    private func columnsView(
        columns: [MacTimelineMasonryColumn],
        totalCount: Int,
        onDisplay: @escaping (Int) -> Void = { _ in }
    ) -> some View {
        HStack(alignment: .top, spacing: spacing) {
            ForEach(columns) { column in
                LazyVStack(spacing: spacing) {
                    ForEach(column.rows) { row in
                        TimelineGalleryItemView(item: row.item, placeholderVariant: row.index)
                            .onAppear {
                                onDisplay(row.index)
                            }
                    }
                }
                .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 8)
    }

    @ViewBuilder
    private func appendFooter(_ success: PagingStateSuccess<UiTimelineV2>) -> some View {
        switch onEnum(of: success.appendState) {
        case .error(let error):
            ListErrorView(error: error.error) {
                success.retry()
            }
            .padding(.horizontal)
            .frame(maxWidth: .infinity, alignment: .center)
        case .loading:
            ProgressView()
                .padding()
                .frame(maxWidth: .infinity, alignment: .center)
        case .notLoading:
            EmptyView()
        }
    }
}

private struct MacTimelineMasonryColumn: Identifiable {
    let id: Int
    var rows: [MacTimelineMasonryRow]
}

private struct MacTimelineMasonryRow: Identifiable {
    let index: Int
    let item: UiTimelineV2?

    var id: String {
        item?.itemKey ?? "placeholder-\(index)"
    }
}

private enum MacTimelineMasonryColumnBuilder {
    static func makePlaceholderColumns(count: Int, columnCount: Int) -> [MacTimelineMasonryColumn] {
        var rows = Array(repeating: [MacTimelineMasonryRow](), count: max(columnCount, 1))
        for index in 0..<count {
            rows[index % rows.count].append(MacTimelineMasonryRow(index: index, item: nil))
        }
        return rows.enumerated().map { MacTimelineMasonryColumn(id: $0.offset, rows: $0.element) }
    }

    static func makeTimelineColumns(
        success: PagingStateSuccess<UiTimelineV2>,
        count: Int,
        columnCount: Int,
        columnWidth: CGFloat,
        rowSpacing: CGFloat,
        showOriginalWithTranslation: Bool
    ) -> [MacTimelineMasonryColumn] {
        makeColumns(
            success: success,
            count: count,
            columnCount: columnCount,
            rowSpacing: rowSpacing,
            estimateHeight: { item in
                timelineEstimatedHeight(
                    for: item,
                    columnWidth: columnWidth,
                    showOriginalWithTranslation: showOriginalWithTranslation
                )
            }
        )
    }

    static func makeGalleryColumns(
        success: PagingStateSuccess<UiTimelineV2>,
        count: Int,
        columnCount: Int,
        columnWidth: CGFloat,
        rowSpacing: CGFloat,
        showOriginalWithTranslation: Bool
    ) -> [MacTimelineMasonryColumn] {
        makeColumns(
            success: success,
            count: count,
            columnCount: columnCount,
            rowSpacing: rowSpacing,
            estimateHeight: { item in
                galleryEstimatedHeight(
                    for: item,
                    columnWidth: columnWidth,
                    showOriginalWithTranslation: showOriginalWithTranslation
                )
            }
        )
    }

    private static func makeColumns(
        success: PagingStateSuccess<UiTimelineV2>,
        count: Int,
        columnCount: Int,
        rowSpacing: CGFloat,
        estimateHeight: (UiTimelineV2?) -> CGFloat
    ) -> [MacTimelineMasonryColumn] {
        let resolvedColumnCount = max(columnCount, 1)
        var columnHeights = Array(repeating: CGFloat.zero, count: resolvedColumnCount)
        var rows = Array(repeating: [MacTimelineMasonryRow](), count: resolvedColumnCount)

        for index in 0..<count {
            let item = success.peek(index: Int32(index))
            let targetColumn = columnHeights.enumerated().min { $0.element < $1.element }?.offset ?? 0
            rows[targetColumn].append(MacTimelineMasonryRow(index: index, item: item))
            columnHeights[targetColumn] += estimateHeight(item) + rowSpacing
        }

        return rows.enumerated().map { MacTimelineMasonryColumn(id: $0.offset, rows: $0.element) }
    }

    private static func timelineEstimatedHeight(
        for item: UiTimelineV2?,
        columnWidth: CGFloat,
        showOriginalWithTranslation: Bool
    ) -> CGFloat {
        guard let item else { return 260 }
        switch onEnum(of: item) {
        case .post(let post):
            let mediaHeight = post.images.first.map { columnWidth / CGFloat(max($0.aspectRatio ?? 1, 0.3)) } ?? 0
            let textHeight = min(CGFloat(visibleContentLength(post, showOriginalWithTranslation)) * 0.45, 160)
            return 120 + mediaHeight + textHeight
        case .timelinePostItem(let item):
            let post = item.presentation.repost ?? item.post
            let mediaHeight = post.images.first.map { columnWidth / CGFloat(max($0.aspectRatio ?? 1, 0.3)) } ?? 0
            let textHeight = min(CGFloat(visibleContentLength(post, showOriginalWithTranslation)) * 0.45, 160)
            let parentPenalty = CGFloat(item.presentation.inlineParents.count) * 140
            return 120 + mediaHeight + textHeight + parentPenalty
        case .feed(let feed):
            if let media = feed.media {
                return 96 + columnWidth / CGFloat(max(media.aspectRatio, 0.3))
            }
            let titleHeight = CGFloat(feed.title?.count ?? 0) * 0.45
            let descriptionHeight = CGFloat(feed.description_?.count ?? 0) * 0.28
            return 110 + min(titleHeight + descriptionHeight, 180)
        default:
            return 180
        }
    }

    private static func galleryEstimatedHeight(
        for item: UiTimelineV2?,
        columnWidth: CGFloat,
        showOriginalWithTranslation: Bool
    ) -> CGFloat {
        guard let item else { return 220 }
        switch onEnum(of: item) {
        case .post(let post):
            if let media = post.images.first {
                return 48 + columnWidth / CGFloat(max(media.aspectRatio ?? 1, 0.3))
            }
            return 140 + min(CGFloat(visibleContentLength(post, showOriginalWithTranslation)) * 0.35, 120)
        case .timelinePostItem:
            guard let post = item.timelineContentPost else {
                return 180
            }
            if let media = post.images.first {
                return 48 + columnWidth / CGFloat(max(media.aspectRatio ?? 1, 0.3))
            }
            return 140 + min(CGFloat(visibleContentLength(post, showOriginalWithTranslation)) * 0.35, 120)
        case .feed(let feed):
            if let media = feed.media {
                return 48 + columnWidth / CGFloat(max(media.aspectRatio, 0.3))
            }
            let titleHeight = CGFloat(feed.title?.count ?? 0) * 0.45
            let descriptionHeight = CGFloat(feed.description_?.count ?? 0) * 0.28
            return 96 + min(titleHeight + descriptionHeight, 150)
        default:
            return 180
        }
    }

    private static func visibleContentLength(
        _ post: UiTimelineV2.Post,
        _ showOriginalWithTranslation: Bool
    ) -> Int {
        guard post.translationDisplayState == .translated, let translation = post.content.translation else {
            return post.content.original.raw.count
        }
        return showOriginalWithTranslation
            ? post.content.original.raw.count + translation.raw.count
            : translation.raw.count
    }
}
