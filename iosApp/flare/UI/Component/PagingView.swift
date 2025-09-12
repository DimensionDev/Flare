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
        case .loading(_): ForEach(0..<5) { _ in
            loadingContent()
        }
        case .success(let success):
            ForEach(0..<success.itemCount, id: \.self) { index in
                if let data = success.peek(index: index) {
                    successContent(data)
                        .onAppear {
                            success.get(index: index)
                        }
                } else {
                    loadingContent()
                }
            }
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
    
}
