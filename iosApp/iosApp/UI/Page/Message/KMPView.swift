import shared
import SwiftUI

/// KMPView是一个用于简化SwiftUI与KMP Presenter集成的协议
/// 它可以自动处理Presenter状态观察和类型转换
protocol KMPView: View {
    associatedtype P: AnyObject
    associatedtype S
    associatedtype V: View

    var presenter: P { get }

    /// 使用类型安全的状态构建视图内容
    @ViewBuilder func body(state: S) -> V
}

extension KMPView where P: PresenterBase<S> {
    var body: some View {
        ObservePresenter(presenter: presenter) { anyState in
            if let state = anyState as? S {
                self.body(state: state)
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
    }
}

/// 处理可能的加载、错误和成功状态的KMPView扩展
extension KMPView {
    /// 辅助方法：处理PagingState类型的结果
    @ViewBuilder
    func handlePagingState<T>(
        _ state: PagingState<T>,
        @ViewBuilder content: @escaping (PagingStateSuccess<T>) -> some View
    ) -> some View {
        switch onEnum(of: state) {
        case let .success(success):
            content(success)
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity)
                .padding()
        case let .error(error):
            VStack {
                Text("Error loading content")
                    .foregroundColor(.red)
                    .padding()
                Button("Retry") {
                    // TODO: 实现重试逻辑
                }
                .padding()
            }
        @unknown default:
            EmptyView()
        }
    }

    /// 辅助方法：处理UiState类型的结果
    @ViewBuilder
    func handleUiState<T>(
        _ state: UiState<T>,
        @ViewBuilder content: @escaping (T) -> some View
    ) -> some View {
        switch onEnum(of: state) {
        case let .success(data):
            let unwrappedData = data.data
            content(unwrappedData)
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity)
                .padding()
        case .error:
            Text("Error loading content")
                .foregroundColor(.red)
                .padding()
        @unknown default:
            EmptyView()
        }
    }
}

// 自定义ForEach替代视图，处理Int32类型
struct ForEachWithIndex<Content: View>: View {
    let startIndex: Int32
    let count: Int32
    let content: (Int32) -> Content

    init(_ startIndex: Int32, count: Int32, @ViewBuilder content: @escaping (Int32) -> Content) {
        self.startIndex = startIndex
        self.count = count
        self.content = content
    }

    var body: some View {
        ForEach(0 ..< Int(count), id: \.self) { index in
            content(Int32(index) + startIndex)
        }
    }
}
