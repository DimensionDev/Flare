import AppleFontAwesome
import FlareAppleCore
import KotlinSharedUI
import SwiftUI

public struct LocalFilterSettingsView: View {
    @StateObject private var presenter = KotlinPresenter(presenter: LocalFilterPresenter())
    @State private var selectedFilter: UiKeywordFilter?
    @State private var showingAddFilter = false

    public init() {}

    public var body: some View {
        Group {
            #if os(macOS)
            VStack(spacing: 0) {
                addFilterButton
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding()
                content
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            #else
            content
            #endif
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
                        item: .init(
                            keyword: keyword,
                            forTimeline: forTimeline,
                            forNotification: forNotification,
                            forSearch: forSearch,
                            expiredAt: nil,
                            isRegex: isRegex
                        )
                    )
                }
            }
        }
        #if os(iOS)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showingAddFilter = true
                } label: {
                    Image(fontAwesome: .plus)
                }
            }
        }
        #endif
    }

    private var content: some View {
        StateView(state: presenter.state.items) { filters in
            let list = filters.cast(UiKeywordFilter.self)
            if list.isEmpty {
                ListEmptyView()
            } else {
                List {
                    ForEach(list, id: \.keyword) { item in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.keyword)
                            if item.isRegex {
                                Text("local_filter_regex", bundle: FlareAppleUILocalization.bundle)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .contextMenu {
                            Button {
                                selectedFilter = item
                                showingAddFilter = true
                            } label: {
                                Label {
                                    Text("local_filter_edit", bundle: FlareAppleUILocalization.bundle)
                                } icon: {
                                    Image(fontAwesome: .pen)
                                }
                            }
                            Button(role: .destructive) {
                                presenter.state.delete(keyword: item.keyword)
                            } label: {
                                Label {
                                    Text("local_filter_delete", bundle: FlareAppleUILocalization.bundle)
                                } icon: {
                                    Image(fontAwesome: .trash)
                                }
                            }
                        }
                        .swipeActions {
                            Button(role: .destructive) {
                                presenter.state.delete(keyword: item.keyword)
                            } label: {
                                Label {
                                    Text("local_filter_delete", bundle: FlareAppleUILocalization.bundle)
                                } icon: {
                                    Image(fontAwesome: .trash)
                                }
                            }
                            Button {
                                selectedFilter = item
                                showingAddFilter = true
                            } label: {
                                Label {
                                    Text("local_filter_edit", bundle: FlareAppleUILocalization.bundle)
                                } icon: {
                                    Image(fontAwesome: .pen)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private var addFilterButton: some View {
        Button {
            showingAddFilter = true
        } label: {
            Label {
                Text("local_filter_edit_title", bundle: FlareAppleUILocalization.bundle)
            } icon: {
                Image(fontAwesome: .plus)
            }
        }
    }
}

private struct LocalFilterEditSheet: View {
    let onConfirm: (String, Bool, Bool, Bool, Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var keyword = ""
    @State private var forTimeline = true
    @State private var forNotification = true
    @State private var forSearch = true
    @State private var isRegex = false

    var body: some View {
        Form {
            Section {
                TextField(text: $keyword) {
                    Text("local_filter_keyword_placeholder", bundle: FlareAppleUILocalization.bundle)
                }
            } header: {
                Text("local_filter_keyword_header", bundle: FlareAppleUILocalization.bundle)
            }
            Section {
                Toggle(isOn: $forTimeline) {
                    Text("local_filter_timeline", bundle: FlareAppleUILocalization.bundle)
                }
                Toggle(isOn: $forNotification) {
                    Text("local_filter_notification", bundle: FlareAppleUILocalization.bundle)
                }
                Toggle(isOn: $forSearch) {
                    Text("local_filter_search", bundle: FlareAppleUILocalization.bundle)
                }
                Toggle(isOn: $isRegex) {
                    Text("local_filter_regex", bundle: FlareAppleUILocalization.bundle)
                }
            } header: {
                Text("local_filter_scope_header", bundle: FlareAppleUILocalization.bundle)
            }
        }
        .formStyle(.grouped)
        .navigationTitle(Text("local_filter_edit_title", bundle: FlareAppleUILocalization.bundle))
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Label {
                        Text("Cancel", bundle: FlareAppleUILocalization.bundle)
                    } icon: {
                        Image(fontAwesome: .xmark)
                    }
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button {
                    onConfirm(keyword, forTimeline, forNotification, forSearch, isRegex)
                    dismiss()
                } label: {
                    Label {
                        Text("Done", bundle: FlareAppleUILocalization.bundle)
                    } icon: {
                        Image(fontAwesome: .check)
                    }
                }
                .disabled(keyword.isEmpty)
            }
        }
    }

    init(
        filter: UiKeywordFilter?,
        onConfirm: @escaping (String, Bool, Bool, Bool, Bool) -> Void
    ) {
        self.onConfirm = onConfirm
        if let filter {
            _keyword = .init(initialValue: filter.keyword)
            _forTimeline = .init(initialValue: filter.forTimeline)
            _forNotification = .init(initialValue: filter.forNotification)
            _forSearch = .init(initialValue: filter.forSearch)
            _isRegex = .init(initialValue: filter.isRegex)
        }
    }
}
