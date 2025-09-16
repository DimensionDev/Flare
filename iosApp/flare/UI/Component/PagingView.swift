import SwiftUI
import KotlinSharedUI

struct PagingView<T: AnyObject, EmptyContent: View, ErrorContent: View, LoadingContent: View, SuccessContent: View> : View {
    let data: PagingState<T>
    @ViewBuilder
    let emptyContent: () -> EmptyContent
    @ViewBuilder
    let errorContent: (KotlinThrowable) -> ErrorContent
    @ViewBuilder
    let loadingContent: () -> LoadingContent
    let loadingCount = 5
    @ViewBuilder
    let successContent: (T) -> SuccessContent
    var body: some View {
        switch onEnum(of: data) {
        case .empty(_): emptyContent()
        case .error(let error): errorContent(error.error)
        case .loading(_): ForEach(0..<5) { index in
            ListCardView(index: index, totalCount: 5) {
                loadingContent()
                    .padding()
            }
        }
        case .success(let success):
            ForEach(0..<success.itemCount, id: \.self) { index in
                if let item = success.peek(index: index) {
                    ListCardView(index: Int(index), totalCount: Int(success.itemCount)) {
                        successContent(item)
                            .padding()
                            .onAppear {
                                _ = success.get(index: index)
                            }
                    }
                } else {
                    ListCardView(index: Int(index), totalCount: Int(success.itemCount)) {
                        loadingContent()
                            .padding()
                    }
                    
                }
//                PagingItemView(loadingContent: {
//                    ListCardView(index: Int(index), totalCount: Int(success.itemCount)) {
//                        loadingContent()
//                            .padding()
//                    }
//                }, successContent: { data in
//                    ListCardView(index: Int(index), totalCount: Int(success.itemCount)) {
//                        successContent(data)
//                            .padding()
//                    }
//                }, getData: {
//                    success.get(index: index)
//                }, data: success.peek(index: index))
            }
        }
    }
}



struct PagingItemView<T: AnyObject, LoadingContent: View, SuccessContent: View> : View {
    @ViewBuilder
    let loadingContent: () -> LoadingContent
    let loadingCount = 5
    @ViewBuilder
    let successContent: (T) -> SuccessContent
    let getData: () -> T?
    @State var data: T?
    
    var body: some View {
        if let data = data {
            successContent(data)
                .onAppear {
                    self.data = getData()
                }
        } else {
            loadingContent()
        }
    }
    
}

extension PagingView {
    init(
        data: PagingState<T>,
        @ViewBuilder
        successContent: @escaping (T) -> SuccessContent
    ) where ErrorContent == EmptyView, LoadingContent == EmptyView, EmptyContent == EmptyView {
        self.init(data: data, emptyContent: { EmptyView() }, errorContent: {_ in EmptyView()}, loadingContent: {EmptyView()}, successContent: successContent)
    }
    
    init(
        data: PagingState<UiTimeline>,
    ) where ErrorContent == EmptyView, EmptyContent == EmptyView, LoadingContent == TimelinePlaceholderView, SuccessContent == TimelineView, T == UiTimeline {
        self.init(
            data: data,
            emptyContent: { EmptyView() },
            errorContent: { _ in EmptyView() },
            loadingContent: { TimelinePlaceholderView() },
            successContent: { item in TimelineView(data: item) }
        )
    }
    
    init(
        data: PagingState<UiTimeline>,
        detailStatusKey: MicroBlogKey?
    ) where ErrorContent == EmptyView, EmptyContent == EmptyView, LoadingContent == TimelinePlaceholderView, SuccessContent == TimelineView, T == UiTimeline {
        self.init(
            data: data,
            emptyContent: { EmptyView() },
            errorContent: { _ in EmptyView() },
            loadingContent: { TimelinePlaceholderView() },
            successContent: { item in TimelineView(data: item, detailStatusKey: detailStatusKey) }
        )
    }
}
