import SwiftUI
import KotlinSharedUI


struct TimelinePagingView : View {
    let data: PagingState<UiTimeline>
    var body: some View {
        switch onEnum(of: data) {
        case .empty(_): EmptyView()
        case .error(let error): EmptyView()
        case .loading(_): ForEach(0..<5) { index in
            ListCardView(index: index, totalCount: 5) {
                TimelinePlaceholderView()
                    .padding()
            }
        }
        case .success(let success):
            ForEach(TimelineCollection(data: success)) { data in
                if let item = data.data {
                    ListCardView(index: data.index, totalCount: Int(success.itemCount)) {
                        TimelineView(data: item)
//                            .id(item.itemKey)
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
                
//                PagingItemView(loadingContent: {
//                    ListCardView(index: data.index, totalCount: Int(success.itemCount)) {
//                        TimelinePlaceholderView()
//                            .padding()
//                    }
//                }, successContent: { item in
//                    ListCardView(index: data.index, totalCount: Int(success.itemCount)) {
//                        TimelineView(data: item)
//                            .id(item.itemKey)
//                            .padding()
//                    }
//                }, getData: {
//                    success.get(index: Int32(data.index))
//                }, data: data.data)
            }
        }
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

    public func index(after i: Int) -> Int { i + 1 }
    public func index(before i: Int) -> Int { i - 1 }
    public func index(_ i: Int, offsetBy distance: Int) -> Int { i + distance }
    public func distance(from start: Int, to end: Int) -> Int { end - start }

    public subscript(position: Int) -> TimelineData {
        let item = data.peek(index: Int32(position))
        return TimelineData(id: item?.itemKey ?? "\(position)", data: item, index: position)
    }

    public var count: Int { Int(data.itemCount) }
}
