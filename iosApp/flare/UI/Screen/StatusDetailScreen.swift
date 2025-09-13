import SwiftUI
import KotlinSharedUI

struct StatusDetailScreen : View {
    @Environment(\.openURL) private var openURL
    @State private var presenter: KotlinPresenter<TimelineState>
    private let statusKey: MicroBlogKey
    
    init(accountType: AccountType, statusKey: MicroBlogKey) {
        self.statusKey = statusKey
        self._presenter = .init(initialValue: .init(presenter: StatusContextPresenter(accountType: accountType, statusKey: statusKey)))
    }
    
    var body: some View {
        List {
            PagingView(data: presenter.state.listState) { item in
                TimelineView(data: item)
            }
        }
        .navigationTitle("Detail")
    }
}
