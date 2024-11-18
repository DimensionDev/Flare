import SwiftUI
import shared

struct StatusDetailScreen: View {
    @State private var presenter: StatusContextPresenter
    private let statusKey: MicroBlogKey
    @Environment(\.colorScheme) var colorScheme: ColorScheme
    @Environment(\.openURL) private var openURL

    init(accountType: AccountType, statusKey: MicroBlogKey) {
        presenter = .init(accountType: accountType, statusKey: statusKey)
        self.statusKey = statusKey
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            ComposeTimelineListController(
                pagingState: state.listState,
                onRefresh: {  },
                detailStatusKey: statusKey,
                darkMode: colorScheme == .dark,
                onOpenLink: { openURL(.init(string: $0)!) }
            )
        }
    }
}
