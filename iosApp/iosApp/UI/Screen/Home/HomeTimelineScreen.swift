import SwiftUI
import shared

struct HomeTimelineScreen: View {
    @Environment(\.openURL) private var openURL
    @State
    var presenter: HomeTimelinePresenter

    init(accountType: AccountType) {
        presenter = .init(accountType: accountType)
    }

    var body: some View {
        Observing(presenter.models) { state in
            List {
                StatusTimelineComponent(
                    data: state.listState
                )
            }
            .listStyle(.plain)
            .refreshable {
                try? await presenter.models.value.refresh()
            }
            .navigationTitle("home_timeline_title")
    #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
    #endif
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: {
//                        openURL(URL(string: AppDeepLink.Compose.shared.invoke())!)
                    }, label: {
                        Image(systemName: "square.and.pencil")
                    })
                    Button(action: {
                        Task {
                            try? await state.refresh()
                        }
                    }, label: {
                        Image(systemName: "arrow.clockwise.circle")
                    })
                }
            }
        }
    }
}
