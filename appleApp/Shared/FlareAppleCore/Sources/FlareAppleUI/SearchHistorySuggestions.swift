import SwiftUI
@preconcurrency import KotlinSharedUI

public struct SearchHistorySuggestions: View {
    private let state: SearchHistoryState
    private let searchText: String
    private let onSelect: (String) -> Void
    private let onDelete: (String) -> Void

    public init(
        state: SearchHistoryState,
        searchText: String,
        onSelect: @escaping (String) -> Void,
        onDelete: @escaping (String) -> Void
    ) {
        self.state = state
        self.searchText = searchText
        self.onSelect = onSelect
        self.onDelete = onDelete
    }

    public var body: some View {
        if case .success(let data) = onEnum(of: state.searchHistories) {
            ForEach(filteredHistories(data.data), id: \.keyword) { item in
                SearchHistorySuggestionRow(
                    item: item,
                    onSelect: onSelect,
                    onDelete: onDelete
                )
            }
        }
    }

    private func filteredHistories(_ histories: ImmutableListWrapper<UiSearchHistory>) -> [UiSearchHistory] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        var result: [UiSearchHistory] = []

        for index in 0..<Int(histories.size) {
            let history = histories.get(index: Int32(index))
            if query.isEmpty || history.keyword.localizedCaseInsensitiveContains(query) {
                result.append(history)
            }
        }

        return result
    }
}

private struct SearchHistorySuggestionRow: View {
    let item: UiSearchHistory
    let onSelect: (String) -> Void
    let onDelete: (String) -> Void

    var body: some View {
        Button {
            onSelect(item.keyword)
        } label: {
            Label {
                Text(item.keyword)
            } icon: {
                Image(systemName: "clock.arrow.circlepath")
            }
        }
        .modifier(SearchHistoryDeleteAction(keyword: item.keyword, onDelete: onDelete))
    }
}

private struct SearchHistoryDeleteAction: ViewModifier {
    let keyword: String
    let onDelete: (String) -> Void

    @ViewBuilder
    func body(content: Content) -> some View {
        #if os(iOS)
        content
            .swipeActions(edge: .trailing) {
                deleteButton
            }
        #else
        content
            .contextMenu {
                deleteButton
            }
        #endif
    }

    private var deleteButton: some View {
        Button(role: .destructive) {
            onDelete(keyword)
        } label: {
            Label {
                Text("delete", bundle: FlareAppleUILocalization.bundle)
            } icon: {
                Image(systemName: "trash")
            }
        }
    }
}
