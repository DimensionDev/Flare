import SwiftUI
import KotlinSharedUI

struct LocalFilterScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: LocalFilterPresenter())
    @State private var selectedFilter: UiKeywordFilter? = nil
    @State private var showingAddFilter = false
    var body: some View {
        List {
            StateView(state: presenter.state.items) { filters in
                let list = filters.cast(UiKeywordFilter.self)
                ForEach(list, id: \.keyword) { item in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.keyword)
                        if item.isRegex {
                            Text("local_filter_regex")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .swipeActions {
                        Button(role: .destructive) {
                            presenter.state.delete(keyword: item.keyword)
                        } label: {
                            Label {
                                Text("local_filter_delete")
                            } icon: {
                                Image("fa-trash")
                            }
                        }
                        Button {
                            selectedFilter = item
                            showingAddFilter = true
                        } label: {
                            Label {
                                Text("local_filter_edit")
                            } icon: {
                                Image("fa-pen")
                            }
                        }
                    }
                }
                if list.isEmpty {
                    ListEmptyView()
                }
            }
        }
        .sheet(
            isPresented: $showingAddFilter,
            onDismiss: {
                selectedFilter = nil
            }
        ) {
            NavigationStack {
                LocalFilterEditSheet(filter: selectedFilter) { keyword, forTimeline, forNotification, forSearch, isRegex in
                    presenter.state.add(
                        item: .init(keyword: keyword, forTimeline: forTimeline, forNotification: forNotification, forSearch: forSearch, expiredAt: nil, isRegex: isRegex)
                    )
                }
            }
        }
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    showingAddFilter = true
                } label: {
                    Image("fa-plus")
                }
            }
        }
        .navigationTitle("local_filter_title")
    }
}

struct LocalFilterEditSheet: View {
    let onConfirm: (String, Bool, Bool, Bool, Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var keyword: String = ""
    @State private var forTimeline: Bool = true
    @State private var forNotification: Bool = true
    @State private var forSearch: Bool = true
    @State private var isRegex: Bool = false
    var body: some View {
        Form {
            Section {
                TextField("local_filter_keyword_placeholder", text: $keyword)
            } header: {
                Text("local_filter_keyword_header")
            }
            Section {
                Toggle("local_filter_timeline", isOn: $forTimeline)
                Toggle("local_filter_notification", isOn: $forNotification)
                Toggle("local_filter_search", isOn: $forSearch)
                Toggle("local_filter_regex", isOn: $isRegex)
            } header: {
                Text("local_filter_scope_header")
            }
        }
        .navigationTitle("local_filter_edit_title")
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Label {
                        Text("Cancel")
                    } icon: {
                        Image("fa-xmark")
                    }
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(
//                    role: .confirm
                ) {
                    onConfirm(keyword, forTimeline, forNotification, forSearch, isRegex)
                    dismiss()
                } label: {
                    Label {
                        Text("Done")
                    } icon: {
                        Image("fa-check")
                    }
                }
                .disabled(keyword.isEmpty)
            }
        }
    }
}

extension LocalFilterEditSheet {
    init(
        filter: UiKeywordFilter?,
        onConfirm: @escaping (String, Bool, Bool, Bool, Bool) -> Void
    ) {
        self.onConfirm = onConfirm
        if let filter = filter {
            self._keyword = .init(initialValue: filter.keyword)
            self._forTimeline = .init(initialValue: filter.forTimeline)
            self._forNotification = .init(initialValue: filter.forNotification)
            self._forSearch = .init(initialValue: filter.forSearch)
            self._isRegex = .init(initialValue: filter.isRegex)
        }
    }
}
