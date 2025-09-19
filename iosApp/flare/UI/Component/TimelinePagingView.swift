import SwiftUI
import KotlinSharedUI

struct TimelinePagingView: View {
    let data: PagingState<UiTimeline>
    let detailStatusKey: MicroBlogKey?
    var body: some View {
        switch onEnum(of: data) {
        case .empty: EmptyView()
        case .error(let error): EmptyView()
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
        }
    }
}

extension TimelinePagingView {
    init(data: PagingState<UiTimeline>) {
        self.data = data
        self.detailStatusKey = nil
    }
}

struct TimelineData: Identifiable {
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
