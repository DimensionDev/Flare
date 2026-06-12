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
                Button {
                    onSelect(item.keyword)
                } label: {
                    Label {
                        Text(item.keyword)
                    } icon: {
                        Image(systemName: "clock.arrow.circlepath")
                    }
                }
                .swipeActions(edge: .trailing) {
                    Button(role: .destructive) {
                        onDelete(item.keyword)
                    } label: {
                        Label("delete", systemImage: "trash")
                    }
                }
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
