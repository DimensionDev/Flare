import SwiftUI
import KotlinSharedUI

struct PagingView<T: AnyObject, EmptyContent: View, ErrorContent: View, LoadingContent: View, SuccessContent: View>: View {
    let data: PagingState<T>
    @ViewBuilder
    let emptyContent: () -> EmptyContent
    @ViewBuilder
    let errorContent: (KotlinThrowable, @escaping () -> Void) -> ErrorContent
    @ViewBuilder
    let loadingContent: (Int, Int) -> LoadingContent
    let loadingCount = 5
    @ViewBuilder
    let successContent: (T, Int, Int) -> SuccessContent
    var body: some View {
        switch onEnum(of: data) {
        case .empty: emptyContent()
        case .error(let error): errorContent(error.error) {
            _ = error.onRetry()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .loading: ForEach(0..<loadingCount, id: \.self) { index in
            loadingContent(index, loadingCount)
        }
        case .success(let success):
            ForEach(0..<success.itemCount, id: \.self) { index in
                ZStack {
                    if index < success.itemCount, let item = success.peek(index: index) {
                        successContent(item, Int(index), Int(success.itemCount))
                    } else {
                        loadingContent(Int(index), Int(success.itemCount))
                    }
                }
                .onAppear {
                    _ = success.get(index: index)
                }
            }
            
            switch onEnum(of: success.appendState) {
            case .error(let error):
                errorContent(error.error) {
                    _ = success.retry()
                }
                .frame(maxWidth: .infinity, alignment: .center)
            case .loading:
                ProgressView()
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .center)
            case .notLoading(let notLoading):
                EmptyView()
            }
        }
    }
}

extension PagingView {
    init(
        data: PagingState<T>,
        @ViewBuilder
        successContent: @escaping (T) -> SuccessContent,
        @ViewBuilder
        loadingContent: @escaping () -> LoadingContent
    ) where ErrorContent == ListErrorView, EmptyContent == ListEmptyView {
        self.init(
            data: data,
            emptyContent: { ListEmptyView() },
            errorContent: { error, retry in
                ListErrorView(error: error) {
                    retry()
                }
            },
            loadingContent: { index, loadingCount in
                loadingContent()
            },
            successContent: { item, index, itemCount in
                successContent(item)
            }
        )
    }
    
    
    init(
        data: PagingState<T>,
        @ViewBuilder
        successContent: @escaping (T) -> SuccessContent,
        @ViewBuilder
        loadingContent: @escaping () -> LoadingContent,
        @ViewBuilder
        errorContent: @escaping (KotlinThrowable, @escaping () -> Void) -> ErrorContent
    ) where EmptyContent == ListEmptyView {
        self.init(
            data: data,
            emptyContent: { ListEmptyView() },
            errorContent: { error, retry in
                errorContent(error, retry)
            },
            loadingContent: { index, loadingCount in
                loadingContent()
            },
            successContent: { item, index, itemCount in
                successContent(item)
            }
        )
    }
    
    
    init(
        data: PagingState<T>,
        @ViewBuilder
        successContent: @escaping (T) -> SuccessContent,
        @ViewBuilder
        loadingContent: @escaping () -> LoadingContent,
        @ViewBuilder
        errorContent: @escaping (KotlinThrowable, @escaping () -> Void) -> ErrorContent,
        @ViewBuilder
        emptyContent: @escaping () -> EmptyContent
    ) {
        self.init(
            data: data,
            emptyContent: { emptyContent() },
            errorContent: { error, retry in
                errorContent(error, retry)
            },
            loadingContent: { index, loadingCount in
                loadingContent()
            },
            successContent: { item, index, itemCount in
                successContent(item)
            }
        )
    }
    
}


struct UserPagingView: View {
    @Environment(\.openURL) private var openURL
    let data: PagingState<UiProfile>
    var body: some View {
        PagingView(data: data) { user in
            UserCompatView(data: user)
                .onTapGesture {
                    user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                }
        } loadingContent: {
            UserLoadingView()
                .padding(.vertical, 8)
        }
    }
}
