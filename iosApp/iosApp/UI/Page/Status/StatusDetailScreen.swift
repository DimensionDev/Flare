import Foundation
import os.log
import shared
import SwiftUI

struct StatusDetailScreen: View {
    @State private var presenter: StatusContextPresenter
    private let statusKey: MicroBlogKey

    @Environment(FlareRouter.self) private var router
    @Environment(FlareMenuState.self) private var menuState
    @Environment(FlareTheme.self) private var theme

    init(accountType: AccountType, statusKey: MicroBlogKey) {
        presenter = .init(accountType: accountType, statusKey: statusKey)
        self.statusKey = statusKey
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                TimelineV4Component(
                    data: state.listState
                )
                .listRowBackground(theme.primaryBackgroundColor)
            }
            .listStyle(.plain)
            .refreshable {
                try? await state.refresh()
            }
            .onAppear {
                os_log("[StatusDetailScreen] onAppear - Router: %{public}@, current depth: %{public}d",
                       log: .default, type: .debug,
                       String(describing: ObjectIdentifier(router)),
                       router.navigationDepth)
            }
            .onDisappear {
                presenter.close()
                os_log("[StatusDetailScreen] onDisappear - Router: %{public}@, current depth: %{public}d",
                       log: .default, type: .debug,
                       String(describing: ObjectIdentifier(router)),
                       router.navigationDepth)
            }
        }
        .environment(router)
        .environment(menuState)
    }
}
