import Foundation
import KotlinSharedUI
import SwiftUI


struct StateView<T: AnyObject, SuccessContent: View, ErrorContent: View, LoadingContent: View>: View {
    let state: UiState<T>
    @ViewBuilder var successContent: (T) -> SuccessContent
    @ViewBuilder var errorContent: (KotlinThrowable) -> ErrorContent
    @ViewBuilder var loadingContent: () -> LoadingContent
    var body: some View {
        switch onEnum(of: state) {
        case .error(let error):
            errorContent(error.throwable)
        case .loading(_):
            loadingContent()
        case .success(let data):
            successContent(data.data)
        }
    }
}


extension StateView {
    
    init(
        state: UiState<T>,
        @ViewBuilder successContent: @escaping (T) -> SuccessContent
    ) where LoadingContent == EmptyView, ErrorContent == EmptyView {
        self.init(
            state: state,
            successContent: successContent,
            errorContent: { _ in EmptyView() },
            loadingContent: { EmptyView() }
        )
    }
    
    init(
        state: UiState<T>,
        @ViewBuilder successContent: @escaping (T) -> SuccessContent,
        @ViewBuilder loadingContent: @escaping () -> LoadingContent
    ) where ErrorContent == EmptyView {
        self.init(
            state: state,
            successContent: successContent,
            errorContent: { _ in EmptyView() },
            loadingContent: loadingContent
        )
    }
    
    init(
        state: UiState<T>,
        @ViewBuilder successContent: @escaping (T) -> SuccessContent,
        @ViewBuilder errorContent: @escaping (KotlinThrowable) -> ErrorContent
    ) where LoadingContent == EmptyView {
        self.init(
            state: state,
            successContent: successContent,
            errorContent: errorContent,
            loadingContent: { EmptyView() }
        )
    }
}
