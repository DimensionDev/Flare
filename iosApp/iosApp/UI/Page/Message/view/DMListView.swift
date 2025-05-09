import shared
import SwiftUI

struct DMListView: View {
    let accountType: AccountType
    @State private var presenter: DMListPresenter

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
                }
            } else if case .loading = onEnum(of: state.items) {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding()
            } else if case .error = onEnum(of: state.items) {
                Text("Loading error")
                    .foregroundColor(.red)
                    .padding()
            }
        }
        .listStyle(PlainListStyle())
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
    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(Color.gray.opacity(0.3))
                .frame(width: 50, height: 50)

            VStack(alignment: .leading, spacing: 4) {
                Rectangle()
                    .fill(Color.gray.opacity(0.3))
                    .frame(height: 18)
                    .frame(maxWidth: 120)

                Rectangle()
                    .fill(Color.gray.opacity(0.3))
                    .frame(height: 14)
                    .frame(maxWidth: 200)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .redacted(reason: .placeholder)
    }
}
