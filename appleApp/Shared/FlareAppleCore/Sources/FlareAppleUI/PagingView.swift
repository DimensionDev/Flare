import SwiftUI
import KotlinSharedUI

public struct PagingView<
    T: AnyObject,
    EmptyContent: View,
    ErrorContent: View,
    LoadingContent: View,
    SuccessContent: View
>: View {
    private let data: PagingState<T>
    private let emptyContent: () -> EmptyContent
    private let errorContent: (KotlinThrowable, @escaping () -> Void) -> ErrorContent
    private let loadingContent: (Int, Int) -> LoadingContent
    private let loadingCount = 5
    private let maxCount: Int?
    private let reversed: Bool
    private let itemID: ((T) -> AnyHashable)?
    private let successContent: (T, Int, Int) -> SuccessContent

    public init(
        data: PagingState<T>,
        maxCount: Int? = nil,
        reversed: Bool = false,
        id itemID: ((T) -> AnyHashable)? = nil,
        @ViewBuilder emptyContent: @escaping () -> EmptyContent,
        @ViewBuilder errorContent: @escaping (KotlinThrowable, @escaping () -> Void) -> ErrorContent,
        @ViewBuilder loadingContent: @escaping (Int, Int) -> LoadingContent,
        @ViewBuilder successContent: @escaping (T, Int, Int) -> SuccessContent
    ) {
        self.data = data
        self.maxCount = maxCount
        self.reversed = reversed
        self.itemID = itemID
        self.emptyContent = emptyContent
        self.errorContent = errorContent
        self.loadingContent = loadingContent
        self.successContent = successContent
    }

    public var body: some View {
        switch onEnum(of: data) {
        case .empty:
            emptyContent()
        case .error(let error):
            errorContent(error.error) {
                _ = error.onRetry()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
        case .loading:
            let visibleLoadingCount = cappedCount(loadingCount)
            ForEach(0..<visibleLoadingCount, id: \.self) { index in
                loadingContent(index, visibleLoadingCount)
            }
        case .success(let success):
            let itemCount = Int(success.itemCount)
            let visibleItemCount = cappedCount(itemCount)
            if reversed {
                appendStateContent(success: success, itemCount: itemCount)
            }
            ForEach(
                rows(
                    success: success,
                    itemCount: itemCount,
                    visibleItemCount: visibleItemCount
                )
            ) { row in
                let kotlinIndex = Int32(row.pagingIndex)
                ZStack {
                    if let item = row.item {
                        successContent(item, row.displayIndex, visibleItemCount)
                    } else {
                        loadingContent(row.displayIndex, visibleItemCount)
                    }
                }
                .onAppear {
                    _ = success.get(index: kotlinIndex)
                }
            }

            if !reversed {
                appendStateContent(success: success, itemCount: itemCount)
            }
        }
    }

    private func rows(
        success: PagingStateSuccess<T>,
        itemCount: Int,
        visibleItemCount: Int
    ) -> [PagingViewRow<T>] {
        (0..<visibleItemCount).map { displayIndex in
            let pagingIndex = pagingIndex(displayIndex: displayIndex, visibleItemCount: visibleItemCount)
            let item: T?
            if pagingIndex < itemCount {
                item = success.peek(index: Int32(pagingIndex))
            } else {
                item = nil
            }
            let id: AnyHashable
            if let item, let itemID {
                id = itemID(item)
            } else {
                id = AnyHashable(pagingIndex)
            }
            return PagingViewRow(
                id: id,
                displayIndex: displayIndex,
                pagingIndex: pagingIndex,
                item: item
            )
        }
    }

    private func cappedCount(_ count: Int) -> Int {
        guard let maxCount else {
            return count
        }
        return min(count, max(0, maxCount))
    }

    private func isMaxCountReached(_ count: Int) -> Bool {
        guard let maxCount else {
            return false
        }
        return count >= max(0, maxCount)
    }

    private func pagingIndex(displayIndex: Int, visibleItemCount: Int) -> Int {
        if reversed {
            visibleItemCount - 1 - displayIndex
        } else {
            displayIndex
        }
    }

    @ViewBuilder
    private func appendStateContent(success: PagingStateSuccess<T>, itemCount: Int) -> some View {
        if isMaxCountReached(itemCount) {
            EmptyView()
        } else {
            switch onEnum(of: success.appendState) {
            case .error(let error):
                errorContent(error.error) {
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
}

private struct PagingViewRow<T: AnyObject>: Identifiable {
    let id: AnyHashable
    let displayIndex: Int
    let pagingIndex: Int
    let item: T?
}

public extension PagingView {
    init(
        data: PagingState<T>,
        maxCount: Int? = nil,
        reversed: Bool = false,
        @ViewBuilder successContent: @escaping (T) -> SuccessContent,
        @ViewBuilder loadingContent: @escaping () -> LoadingContent
    ) where ErrorContent == ListErrorView, EmptyContent == ListEmptyView {
        self.init(
            data: data,
            maxCount: maxCount,
            reversed: reversed,
            emptyContent: { ListEmptyView() },
            errorContent: { error, retry in
                ListErrorView(error: error) {
                    retry()
                }
            },
            loadingContent: { _, _ in
                loadingContent()
            },
            successContent: { item, _, _ in
                successContent(item)
            }
        )
    }

    init(
        data: PagingState<T>,
        maxCount: Int? = nil,
        reversed: Bool = false,
        @ViewBuilder successContent: @escaping (T) -> SuccessContent,
        @ViewBuilder loadingContent: @escaping () -> LoadingContent,
        @ViewBuilder errorContent: @escaping (KotlinThrowable, @escaping () -> Void) -> ErrorContent
    ) where EmptyContent == ListEmptyView {
        self.init(
            data: data,
            maxCount: maxCount,
            reversed: reversed,
            emptyContent: { ListEmptyView() },
            errorContent: { error, retry in
                errorContent(error, retry)
            },
            loadingContent: { _, _ in
                loadingContent()
            },
            successContent: { item, _, _ in
                successContent(item)
            }
        )
    }

    init(
        data: PagingState<T>,
        maxCount: Int? = nil,
        reversed: Bool = false,
        @ViewBuilder successContent: @escaping (T) -> SuccessContent,
        @ViewBuilder loadingContent: @escaping () -> LoadingContent,
        @ViewBuilder errorContent: @escaping (KotlinThrowable, @escaping () -> Void) -> ErrorContent,
        @ViewBuilder emptyContent: @escaping () -> EmptyContent
    ) {
        self.init(
            data: data,
            maxCount: maxCount,
            reversed: reversed,
            emptyContent: { emptyContent() },
            errorContent: { error, retry in
                errorContent(error, retry)
            },
            loadingContent: { _, _ in
                loadingContent()
            },
            successContent: { item, _, _ in
                successContent(item)
            }
        )
    }
}
