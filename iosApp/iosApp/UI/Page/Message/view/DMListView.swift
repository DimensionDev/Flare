import shared
import SwiftUI

struct DMListView: View {
    let accountType: AccountType
    @State private var presenter: DMListPresenter
    @Environment(FlareTheme.self) private var theme

    init(accountType: AccountType) {
        self.accountType = accountType
        _presenter = State(initialValue: DMListPresenter(accountType: accountType))
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { anyState in
            if let state = anyState as? DMListState {
                listContent(state: state)
            } else {
                ProgressView()
            }
        }
    }

    @ViewBuilder
    private func listContent(state: DMListState) -> some View {
        List {
            if case let .success(success) = onEnum(of: state.items) {
                ForEachWithIndex(0, count: success.itemCount) { index in
                    if let room = success.peek(index: index) {
                        NavigationLink(
                            destination: DMConversationView(
                                accountType: UserManager.shared.getCurrentAccount() ?? AccountTypeGuest(),
                                roomKey: room.key,
                                title: room.getFormattedTitle()
                            )
                        ) {
                            DMRoomItemView(room: room)
                        }
                        .onAppear {
                            success.get(index: index)
                        }
                    } else {
                        DMRoomPlaceholderView()
                    }
                }.listRowBackground(theme.primaryBackgroundColor)
            } else if case .loading = onEnum(of: state.items) {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding().background(theme.primaryBackgroundColor)
            } else if case .error = onEnum(of: state.items) {
                Text("Loading error")
                    .foregroundColor(.red)
                    .padding().background(theme.primaryBackgroundColor)
            }
        }
        .background(theme.primaryBackgroundColor)
        .scrollContentBackground(.hidden)
        .listRowBackground(theme.primaryBackgroundColor)
        .listStyle(.plain)
        .refreshable {
            try? await state.refreshSuspend()
        }
        .overlay {
            if state.isRefreshing {
                ProgressView()
            }
        }
    }
}

struct DMRoomPlaceholderView: View {
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(theme.primaryBackgroundColor.opacity(0.3))
                .frame(width: 50, height: 50)

            VStack(alignment: .leading, spacing: 4) {
                Rectangle()
                    .fill(theme.primaryBackgroundColor.opacity(0.3))
                    .frame(height: 18)
                    .frame(maxWidth: 120)

                Rectangle()
                    .fill(theme.primaryBackgroundColor.opacity(0.3))
                    .frame(height: 14)
                    .frame(maxWidth: 200)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .redacted(reason: .placeholder)
    }
}
