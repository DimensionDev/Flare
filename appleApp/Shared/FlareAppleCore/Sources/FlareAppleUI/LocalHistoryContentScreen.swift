import FlareAppleCore
@preconcurrency import KotlinSharedUI
import SwiftUI

public struct LocalHistoryContentScreen<AskAiOverlay: View>: View {
    @Environment(\.timelineListRenderer) private var listRenderer
    @State private var listScope = UUID().uuidString
    @State private var searchGeneration = UUID().uuidString
    @Environment(\.timelineAppearance.aiConfig.agent) private var agentEnabled
    @State private var presenter = KotlinPresenter(presenter: LocalCacheSearchPresenter())
    @State private var searchText = ""
    @State private var committedQuery = ""
    @State private var isSearchPresented = false
    @State private var selection: LocalHistorySelection = .status

    private let onAskAi: (String?, String) -> Void
    private let askAiOverlay: (Bool, @escaping () -> Void) -> AskAiOverlay

    public init(
        onAskAi: @escaping (String?, String) -> Void = { _, _ in },
        @ViewBuilder askAiOverlay: @escaping (Bool, @escaping () -> Void) -> AskAiOverlay
    ) {
        self.onAskAi = onAskAi
        self.askAiOverlay = askAiOverlay
    }

    public var body: some View {
        scrollingContent
        .modifier(LocalHistoryListStyle(selection: selection))
        .toolbar {
            #if os(macOS)
            askAiToolbarItem
            #endif

            #if os(macOS)
            ToolbarItem(placement: .principal) {
                Picker("local_history_title", selection: $selection) {
                    Text("local_history_status", bundle: .main).tag(LocalHistorySelection.status)
                    Text("local_history_user", bundle: .main).tag(LocalHistorySelection.user)
                }
                .modifier(LocalHistoryPickerStyle())
                .fixedSize()
            }
            #else
            ToolbarItem(placement: .primaryAction) {
                Picker("local_history_title", selection: $selection) {
                    Text("local_history_status", bundle: .main).tag(LocalHistorySelection.status)
                    Text("local_history_user", bundle: .main).tag(LocalHistorySelection.user)
                }
                .modifier(LocalHistoryPickerStyle())
                .fixedSize()
            }
            #endif
        }
        .detectScrolling()
        .background(Color.flareSystemGroupedBackground)
        .navigationTitle(Text("local_history_title", bundle: .main))
        .searchable(
            text: $searchText,
            isPresented: $isSearchPresented,
            prompt: Text("local_history_search_prompt", bundle: .main)
        )
        .overlay(alignment: .bottom) {
            askAiOverlay(isSearchPresented, askAi)
        }
        .onSubmit(of: .search) {
            submitSearch()
        }
    }

    @ViewBuilder
    private var scrollingContent: some View {
        if let listRenderer {
            listRenderer(timelineListRequest)
        } else {
            List {
                if selection == .status {
                    statusContent
                } else {
                    userContent
                }
            }
        }
    }

    private var timelineListRequest: TimelineListRequest {
        let content: TimelineListRequest.Content
        if selection == .status {
            content = .posts(committedQuery.isEmpty ? presenter.state.history : presenter.state.data)
        } else {
            content = .users(committedQuery.isEmpty ? presenter.state.userHistory : presenter.state.searchUser)
        }
        return TimelineListRequest(
            key: "\(listScope):\(searchGeneration):\(selection)",
            content: content
        )
    }

    @ViewBuilder
    private var statusContent: some View {
        if normalizedSearchText.isEmpty {
            TimelinePagingListContent(data: presenter.state.history)
        } else if !presenter.state.data.isError {
            TimelinePagingListContent(data: presenter.state.data)
        }
    }

    @ViewBuilder
    private var userContent: some View {
        if normalizedSearchText.isEmpty {
            LocalHistoryUserPagingView(data: presenter.state.userHistory)
        } else if !presenter.state.searchUser.isError {
            LocalHistoryUserPagingView(data: presenter.state.searchUser)
        }
    }

    private var normalizedSearchText: String {
        searchText.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func submitSearch() {
        committedQuery = normalizedSearchText
        searchGeneration = UUID().uuidString
        presenter.state.setQuery(value: normalizedSearchText)
    }

    private func askAi() {
        let query = normalizedSearchText
        isSearchPresented = false
        onAskAi(query.isEmpty ? nil : query, selection.agentTargetRouteValue)
    }

    #if os(macOS)
    @ToolbarContentBuilder
    private var askAiToolbarItem: some ToolbarContent {
        if agentEnabled {
            ToolbarItem(placement: .primaryAction) {
                AskAiSearchToolbarButton(action: askAi)
            }
        }
    }
    #endif
}

public extension LocalHistoryContentScreen where AskAiOverlay == EmptyView {
    init() {
        self.init(
            onAskAi: { _, _ in },
            askAiOverlay: { _, _ in
            EmptyView()
            }
        )
    }
}

private enum LocalHistorySelection {
    case status
    case user

    var agentTargetRouteValue: String {
        switch self {
        case .status:
            return "posts"
        case .user:
            return "users"
        }
    }
}

private struct LocalHistoryUserPagingView: View {
    @Environment(\.openURL) private var openURL

    let data: PagingState<UiProfile>

    var body: some View {
        PagingView(data: data) { user in
            UserCompatView(data: user)
                .contentShape(Rectangle())
                .onTapGesture {
                    user.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
                }
        } loadingContent: {
            UserLoadingView()
                .padding(.vertical, 8)
        }
    }
}

private struct LocalHistoryListStyle: ViewModifier {
    let selection: LocalHistorySelection

    @ViewBuilder
    func body(content: Content) -> some View {
        #if os(iOS)
        if selection == .status {
            content
                .scrollContentBackground(.hidden)
                .listRowSpacing(2)
                .listStyle(.plain)
        } else {
            content
        }
        #else
        if selection == .status {
            content
                .scrollContentBackground(.hidden)
                .listStyle(.plain)
        } else {
            content
                .listStyle(.inset)
        }
        #endif
    }
}

private struct LocalHistoryPickerStyle: ViewModifier {
    @ViewBuilder
    func body(content: Content) -> some View {
        #if os(iOS)
        content
            .pickerStyle(.menu)
        #else
        content
            .pickerStyle(.segmented)
        #endif
    }
}
