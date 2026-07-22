import FlareAppleCore
import Foundation
import KotlinSharedUI
import SwiftUI

#if os(iOS)
import UIKit
#endif

public struct LocalFilterSettingsView: View {
    @StateObject private var presenter = KotlinPresenter(presenter: LocalFilterPresenter())
    @StateObject private var mxgaPresenter = KotlinPresenter(presenter: MxgaSettingsPresenter())
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
            List {
                MxgaSettingsSection(presenter: mxgaPresenter)
                if list.isEmpty && !mxgaPresenter.state.hasXQtAccount {
                    ListEmptyView()
                } else {
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
                                    Text("edit", bundle: FlareAppleUILocalization.bundle)
                                } icon: {
                                    Image(fontAwesome: .pen)
                                }
                            }
                            Button(role: .destructive) {
                                presenter.state.delete(keyword: item.keyword)
                            } label: {
                                Label {
                                    Text("delete", bundle: FlareAppleUILocalization.bundle)
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
                                    Text("delete", bundle: FlareAppleUILocalization.bundle)
                                } icon: {
                                    Image(fontAwesome: .trash)
                                }
                            }
                            Button {
                                selectedFilter = item
                                showingAddFilter = true
                            } label: {
                                Label {
                                    Text("edit", bundle: FlareAppleUILocalization.bundle)
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

private struct MxgaSettingsSection: View {
    @ObservedObject var presenter: KotlinPresenter<MxgaSettingsState>
    @Environment(\.openURL) private var openURL

    private let projectURL = URL(string: "https://github.com/foru17/make-x-great-again")!

    var body: some View {
        if presenter.state.hasXQtAccount {
            Section {
                Toggle(isOn: Binding(get: {
                    presenter.state.isEnabled
                }, set: { value in
                    presenter.state.setEnabled(value: value)
                })) {
                    Text("settings_mxga_filter_title", bundle: FlareAppleUILocalization.bundle)
                    Text("settings_mxga_filter_description", bundle: FlareAppleUILocalization.bundle)
                }

                Button {
                    presenter.state.refresh()
                } label: {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("settings_mxga_refresh_title", bundle: FlareAppleUILocalization.bundle)
                                .foregroundStyle(.primary)
                            refreshStatus
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        if presenter.state.isRefreshing {
                            ProgressView()
                        }
                    }
                }
                .disabled(presenter.state.isRefreshing)
            } header: {
                Text(verbatim: "MXGA")
            } footer: {
                Button {
                    #if os(iOS)
                    UIApplication.shared.open(projectURL)
                    #else
                    openURL(projectURL)
                    #endif
                } label: {
                    Text("settings_mxga_learn_more", bundle: FlareAppleUILocalization.bundle)
                }
            }
        }
    }

    @ViewBuilder
    private var refreshStatus: some View {
        if presenter.state.isRefreshing {
            Text("settings_mxga_refreshing", bundle: FlareAppleUILocalization.bundle)
        } else if presenter.state.lastCheckedAt > 0 {
            HStack(spacing: 4) {
                Text("settings_mxga_last_refreshed", bundle: FlareAppleUILocalization.bundle)
                Text(
                    Date(timeIntervalSince1970: TimeInterval(presenter.state.lastCheckedAt) / 1_000),
                    style: .relative
                )
            }
        } else {
            Text("settings_mxga_never_refreshed", bundle: FlareAppleUILocalization.bundle)
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
                        Text("done", bundle: FlareAppleUILocalization.bundle)
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
