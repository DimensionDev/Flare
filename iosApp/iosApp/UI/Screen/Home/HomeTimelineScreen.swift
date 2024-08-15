import SwiftUI
import shared

struct TimelineScreen: View {
    @State
    var presenter: TimelinePresenter
    var body: some View {
        Observing(presenter.models) { state in
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
        }
    }
}

struct HomeTimelineScreen: View {
    @Environment(\.openURL) private var openURL
    private let presenter: HomeTimelinePresenter
    private let accountType: AccountType
    private let toCompose: () -> Void
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    init(accountType: AccountType, toCompose: @escaping () -> Void) {
        presenter = .init(accountType: accountType)
        self.accountType = accountType
        self.toCompose = toCompose
    }
    var body: some View {
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
            .navigationTitle("home_timeline_title")
#if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
#endif
    }
}
