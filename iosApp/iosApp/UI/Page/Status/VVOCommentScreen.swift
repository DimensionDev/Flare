import shared
import SwiftUI

struct VVOCommentScreen: View {
    @State private var presenter: VVOCommentPresenter
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    private let commentKey: MicroBlogKey

    init(accountType: AccountType, commentKey: MicroBlogKey) {
        presenter = .init(accountType: accountType, commentKey: commentKey)
        self.commentKey = commentKey
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                switch onEnum(of: state.root) {
                case let .success(data): StatusItemView(data: data.data, detailKey: nil)
                case .loading: StatusPlaceHolder()
                case .error: EmptyView()
                }
                StatusTimelineComponent(
                    data: state.list,
                    detailKey: nil
                )
            }
            .listStyle(.plain)
            .refreshable {
                try? await state.refresh()
            }
        }
    }
}
