import SwiftUI
import shared

struct TimelineScreen: View {
    let presenter: TimelinePresenter
    @State private var state: TimelineState
    init(presenter: TimelinePresenter) {
        self.presenter = presenter
        self.state = self.presenter.models.value
    }
    var body: some View {
        List {
            StatusTimelineComponent(
                data: state.listState,
                detailKey: nil
            )
        }
        .listStyle(.plain)
        .refreshable {
            try? await presenter.models.value.refresh()
        }
        .collect(flow: presenter.models, into: $state)
    }
}

struct HomeTimelineScreen: View {
    @Environment(\.openURL) private var openURL
    @State private var presenter: HomeTimelinePresenter
    private let accountType: AccountType
    private let toCompose: () -> Void
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    init(accountType: AccountType, toCompose: @escaping () -> Void) {
        presenter = .init(accountType: accountType)
        self.accountType = accountType
        self.toCompose = toCompose
    }
    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            ZStack(alignment: .bottomTrailing) {
                TimelineScreen(presenter: presenter)
                if !(accountType is AccountTypeGuest), horizontalSizeClass == .compact {
                    Button {
                        toCompose()
                    } label: {
                        Image(systemName: "plus")
                            .font(.title.weight(.semibold))
                            .padding()
                            .background(Color.accentColor)
                            .foregroundColor(.white)
                            .clipShape(RoundedRectangle(cornerRadius: 16.0))
                            .shadow(radius: 8, x: 4, y: 4)
                    }
                    .padding()
                }
            }
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #else
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: {
                        Task {
                            try? await state.refresh()
                        }
                    }, label: {
                        Image(systemName: "arrow.clockwise.circle")
                    })
                }
            }
            #endif
        }
        .navigationTitle("home_timeline_title")
    }
}
