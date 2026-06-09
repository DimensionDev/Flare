import SwiftUI
import KotlinSharedUI

struct LocalHistoryScreen: View {
    @Environment(\.timelineAppearance.aiConfig.agent) private var agentEnabled
    @StateObject private var presenter = KotlinPresenter(presenter: LocalCacheSearchPresenter())
    @State private var searchText = ""
    @State private var isSearchPresented = false
    @State private var selection: HistorySelection = .status
    let onAskAi: (String?, String) -> Void

    init(onAskAi: @escaping (String?, String) -> Void = { _, _ in }) {
        self.onAskAi = onAskAi
    }

    var body: some View {
        List {
            if selection == .status {
                if searchText.isEmpty {
                    TimelinePagingView(data: presenter.state.history)
                        .listRowSeparator(.hidden)
                        .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                        .padding(.horizontal)
                        .listRowBackground(Color.clear)
                } else if !presenter.state.data.isError {
                    TimelinePagingView(data: presenter.state.data)
                        .listRowSeparator(.hidden)
                        .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                        .padding(.horizontal)
                        .listRowBackground(Color.clear)
                }
            } else {
                if searchText.isEmpty {
                    UserPagingView(data: presenter.state.userHistory)
                } else if !presenter.state.searchUser.isError {
                    UserPagingView(data: presenter.state.searchUser)
                }
            }
        }
        .if(selection == .status, transform: { view in
            view
                .scrollContentBackground(.hidden)
                .listRowSpacing(2)
                .listStyle(.plain)
                .background(Color(.systemGroupedBackground))
        })
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Picker("local_history_title", selection: $selection) {
                    Text("local_history_status").tag(HistorySelection.status)
                    Text("local_history_user").tag(HistorySelection.user)
                }
                .pickerStyle(.menu)
                .fixedSize()
            }
        }
        .detectScrolling()
        .background(Color(.systemGroupedBackground))
        .navigationTitle("local_history_title")
        .searchable(text: $searchText, isPresented: $isSearchPresented, prompt: Text("local_history_search_prompt"))
        .askAiSearchOverlay(
            agentEnabled: agentEnabled,
            isSearchPresented: isSearchPresented
        ) {
            askAi()
        }
        .onSubmit(of: .search) {
            presenter.state.setQuery(value: searchText)
        }
    }

    private func askAi() {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        isSearchPresented = false
        onAskAi(query.isEmpty ? nil : query, selection.agentTargetRouteValue)
    }
}

enum HistorySelection {
    case status
    case user
}

private extension HistorySelection {
    var agentTargetRouteValue: String {
        switch self {
        case .status:
            return "posts"
        case .user:
            return "users"
        }
    }
}
