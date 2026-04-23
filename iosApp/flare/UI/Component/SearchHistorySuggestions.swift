import SwiftUI
@preconcurrency import KotlinSharedUI

struct SearchHistorySuggestions: View {
    let state: SearchHistoryState
    let searchText: String
    let onSelect: (String) -> Void
    let onDelete: (String) -> Void

    var body: some View {
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
