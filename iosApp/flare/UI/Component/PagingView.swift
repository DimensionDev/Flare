import SwiftUI
import KotlinSharedUI

struct PagingView<T: AnyObject, EmptyContent: View, ErrorContent: View, LoadingContent: View, SuccessContent: View>: View {
    let data: PagingState<T>
    @ViewBuilder
    let emptyContent: () -> EmptyContent
    @ViewBuilder
    let errorContent: (KotlinThrowable, @escaping () -> Void) -> ErrorContent
    @ViewBuilder
    let loadingContent: () -> LoadingContent
    let loadingCount = 5
    @ViewBuilder
    let successContent: (T) -> SuccessContent
    var body: some View {
        switch onEnum(of: data) {
        case .empty: emptyContent()
        case .error(let error): errorContent(error.error) {
            _ = error.onRetry()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .loading: ForEach(0..<loadingCount) { index in
            loadingContent()
        }
        case .success(let success):
            ForEach(0..<success.itemCount, id: \.self) { index in
                if let item = success.peek(index: index) {
                    successContent(item)
                        .onAppear {
                            _ = success.get(index: index)
                        }
                } else {
                    loadingContent()
                        .onAppear {
                            _ = success.get(index: index)
                        }
                }
            }
            
            switch onEnum(of: success.appendState) {
            case .error(let error):
                errorContent(error.error) {
                    _ = success.retry()
                }
                .frame(maxWidth: .infinity, alignment: .center)
            case .loading:
                ForEach(0..<loadingCount) { index in
                    loadingContent()
               }
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
            loadingContent: loadingContent, successContent: successContent)
    }
}


struct UserPagingView: View {
    @Environment(\.openURL) private var openURL
    let data: PagingState<UiUserV2>
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
