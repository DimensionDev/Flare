import SwiftUI
import KotlinSharedUI

public struct TimelinePagingView: View {
    private let data: PagingState<UiTimelineV2>
    private let detailStatusKey: MicroBlogKey?
    private let loadingCount = 5

    public init(data: PagingState<UiTimelineV2>, detailStatusKey: MicroBlogKey? = nil) {
        self.data = data
        self.detailStatusKey = detailStatusKey
    }

    public var body: some View {
        switch onEnum(of: data) {
        case .empty:
            ListEmptyView()
        case .error(let error):
            ListErrorView(error: error.error) {
                _ = error.onRetry()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .loading:
            LazyVStack(spacing: 0) {
                ForEach(0..<loadingCount, id: \.self) { index in
                    loadingRow(index: index, totalCount: loadingCount)
                }
            }
        case .success(let success):
            successContent(success)
        }
    }

    @ViewBuilder
    private func successContent(_ success: PagingStateSuccess<UiTimelineV2>) -> some View {
        let count = Int(success.itemCount)
        let rows = TimelinePagingRows(success: success, count: count)
        LazyVStack(spacing: 0) {
            ForEach(rows) { row in
                TimelinePagingRowView(
                    row: row,
                    totalCount: count,
                    detailStatusKey: detailStatusKey,
                    onDisplay: { index in
                        _ = success.get(index: Int32(index))
                    }
                )
            }

            switch onEnum(of: success.appendState) {
            case .error(let error):
                ListErrorView(error: error.error) {
                    success.retry()
                }
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

    private func loadingRow(index: Int, totalCount: Int) -> some View {
        AdaptiveTimelineCard(index: index, totalCount: totalCount) {
            TimelinePlaceholderView()
                .padding(.horizontal)
                .padding(.vertical, 12)
        }
    }
}

private struct TimelinePagingRow: Identifiable {
    let id: String
    let index: Int
    let item: UiTimelineV2?
}

private struct TimelinePagingRowView: View {
    let row: TimelinePagingRow
    let totalCount: Int
    let detailStatusKey: MicroBlogKey?
    let onDisplay: (Int) -> Void

    var body: some View {
        Group {
            if let item = row.item {
                AdaptiveTimelineCard(index: row.index, totalCount: totalCount) {
                    TimelineView(data: item, detailStatusKey: detailStatusKey)
                        .padding(.horizontal)
                        .padding(.vertical, 12)
                }
            } else {
                AdaptiveTimelineCard(index: row.index, totalCount: totalCount) {
                    TimelinePlaceholderView()
                        .padding(.horizontal)
                        .padding(.vertical, 12)
                }
            }
        }
        .onAppear {
            onDisplay(row.index)
        }
    }
}

private struct TimelinePagingRows: @MainActor RandomAccessCollection {
    let success: PagingStateSuccess<UiTimelineV2>
    let count: Int

    var startIndex: Int { 0 }
    var endIndex: Int { count }

    func index(after index: Int) -> Int { index + 1 }
    func index(before index: Int) -> Int { index - 1 }
    func index(_ index: Int, offsetBy distance: Int) -> Int { index + distance }
    func distance(from start: Int, to end: Int) -> Int { end - start }

    subscript(position: Int) -> TimelinePagingRow {
        let item = success.peek(index: Int32(position))
        return TimelinePagingRow(
            id: item?.itemKey ?? "placeholder-\(position)",
            index: position,
            item: item
        )
    }
}
