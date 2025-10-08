import SwiftUI
import KotlinSharedUI

struct LocalHistoryScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: LocalCacheSearchPresenter())
    @State private var searchText = ""
    @State private var selection: HistorySelection = .status
    var body: some View {
        VStack {
            Picker("local_history_title", selection: $selection) {
                Text("local_history_status").tag(HistorySelection.status)
                Text("local_history_user").tag(HistorySelection.user)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
            .padding(.vertical, 8)
            if selection == .status {
                List {
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
                }
                .scrollContentBackground(.hidden)
                .listRowSpacing(2)
                .listStyle(.plain)
                .background(Color(.systemGroupedBackground))
            } else {
                List {
                    if searchText.isEmpty {
                        UserPagingView(data: presenter.state.userHistory)
                    } else if !presenter.state.searchUser.isError {
                        UserPagingView(data: presenter.state.searchUser)
                    }
                }
            }
        }
        .detectScrolling()
        .background(Color(.systemGroupedBackground))
        .navigationTitle("local_history_title")
        .searchable(text: $searchText, prompt: Text("local_history_search_prompt"))
        .onSubmit(of: .search) {
            presenter.state.setQuery(value: searchText)
        }
    }
}

enum HistorySelection {
    case status
    case user
}
