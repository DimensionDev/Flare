import SwiftUI
import WaterfallGrids
import KotlinSharedUI

struct TimelinePagingView: View {
    let data: PagingState<UiTimeline>
    let detailStatusKey: MicroBlogKey?
    var body: some View {
        switch onEnum(of: data) {
        case .empty: ListEmptyView()
        case .error(let error): ListErrorView(error: error.error) {
            error.onRetry()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .loading: ForEach(0..<5) { index in
            ListCardView(index: index, totalCount: 5) {
                TimelinePlaceholderView()
                    .padding()
            }
        }
        case .success(let success):
            ForEach(TimelineCollection(data: success)) { data in
                if let item = data.data {
                    ListCardView(index: data.index, totalCount: Int(success.itemCount)) {
                        TimelineView(data: item, detailStatusKey: detailStatusKey)
                            .padding()
                            .onAppear {
                                _ = success.get(index: Int32(data.index))
                            }
                    }
                } else {
                    ListCardView(index: data.index, totalCount: Int(success.itemCount)) {
                        TimelinePlaceholderView()
                            .padding()
                            .onAppear {
                                _ = success.get(index: Int32(data.index))
                            }
                    }
                }
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
            case .notLoading(let notLoading):
                if notLoading.endOfPaginationReached {
                    Text("end_of_list")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .center)
                } else {
                    EmptyView()
                }
            }
        }
    }
}

extension TimelinePagingView {
    init(data: PagingState<UiTimeline>) {
        self.data = data
        self.detailStatusKey = nil
    }
}

struct TimelineData: Identifiable, Hashable {
    let id: String
    let data: UiTimeline?
    let index: Int
}

struct TimelineCollection: @MainActor RandomAccessCollection {
    let data: PagingStateSuccess<UiTimeline>
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
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    let data: PagingState<UiTimeline>
    let detailStatusKey: MicroBlogKey?
    var body: some View {
        if horizontalSizeClass == .compact {
            singleListView
        } else {
            GeometryReader { proxy in
                let columnCount = max((proxy.size.width / 320).rounded(.down), 1)
                if columnCount == 1 {
                    singleListView
                } else {
                    let columns: [WaterfallItems.Column] = Array(repeating: .init(spacing: 12), count: Int(columnCount))
                    ScrollView {
                        TimelineWaterFallPagingView(data: data, detailStatusKey: detailStatusKey, columns: columns)
                            .padding()
                    }
                    .environment(\.isMultipleColumn, true)
                    .detectScrolling()
//                    .refreshable {
//                        try? await presenter.state.refreshSuspend()
//                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(.systemGroupedBackground))
                }
            }
        }
    }
    
    var singleListView: some View {
        List {
            TimelinePagingView(data: data)
                .listRowSeparator(.hidden)
                .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                .padding(.horizontal)
                .listRowBackground(Color.clear)
        }
        .detectScrolling()
        .scrollContentBackground(.hidden)
        .listRowSpacing(2)
        .listStyle(.plain)
//        .refreshable {
//            try? await presenter.state.refreshSuspend()
//        }
        .background(Color(.systemGroupedBackground))
    }
}

struct TimelineWaterFallPagingView: View {
    let data: PagingState<UiTimeline>
    let detailStatusKey: MicroBlogKey?
    let columns: [WaterfallItems.Column]
    var body: some View {
        
        switch onEnum(of: data) {
        case .empty: ListEmptyView()
        case .error(let error): ListErrorView(error: error.error) {
            error.onRetry()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .loading: LazyWaterfallGrid(items: .columns(columns), spacing: 12, data: [0, 1, 2, 3, 4]) { index in
            ListCardView(index: index, totalCount: 5) {
                TimelinePlaceholderView()
                    .padding()
            }
        }
        case .success(let success):
            LazyWaterfallGrid(items: .columns(columns), spacing: 12, data: TimelineCollection(data: success)) { data in
                if let item = data.data {
                    ListCardView(index: data.index, totalCount: Int(success.itemCount)) {
                        TimelineView(data: item, detailStatusKey: detailStatusKey)
                            .padding()
                            .onAppear {
                                _ = success.get(index: Int32(data.index))
                            }
                    }
                } else {
                    ListCardView(index: data.index, totalCount: Int(success.itemCount)) {
                        TimelinePlaceholderView()
                            .padding()
                            .onAppear {
                                _ = success.get(index: Int32(data.index))
                            }
                    }
                }
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
            case .notLoading(let notLoading):
                if notLoading.endOfPaginationReached {
                    Text("end_of_list")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .center)
                } else {
                    EmptyView()
                }
            }
        }
    }
}
