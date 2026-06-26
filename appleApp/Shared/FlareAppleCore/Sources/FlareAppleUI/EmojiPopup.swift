import FlareAppleCore
@preconcurrency import KotlinSharedUI
import SwiftUI
import SwiftUIBackports

public struct EmojiPopup: View {
    @StateObject private var presenter: KotlinPresenter<EmojiHistoryPresenterState>
    @State private var filterText = ""

    private let data: EmojiData
    private let onItemClicked: (UiEmoji) -> Void

    public var body: some View {
        List {
            StateView(state: presenter.state.history) { history in
                let items = history.cast(UiEmoji.self)
                if !items.isEmpty {
                    Section {
                        EmojiGrid(items: items) { item in
                            onItemClicked(item)
                            presenter.state.addHistory(emoji: item)
                        }
                    } header: {
                        Text("emoji_recently_used", bundle: FlareAppleUILocalization.bundle)
                            .font(.body)
                    }
                }
            }

            ForEach(filteredSections, id: \.key) { key, value in
                if data.data.count == 1 {
                    Section {
                        EmojiGrid(items: value, onItemClicked: onItemClicked)
                    } header: {
                        Text(key)
                            .font(.body)
                    }
                } else {
                    EmojiSection(value: value, key: key) { item in
                        onItemClicked(item)
                        presenter.state.addHistory(emoji: item)
                    }
                }
            }
        }
        .listStyle(.sidebar)
        .safeAreaInset(edge: .bottom) {
            TextField(
                FlareAppleUILocalization.string(
                    "emoji_search_placeholder",
                    fallback: "Search emoji"
                ),
                text: $filterText
            )
            #if os(iOS)
            .padding()
            .backport
            .glassEffect(.regularInteractive, in: .capsule, fallbackBackground: .regularMaterial)
            #endif
            .padding()
        }
    }

    public init(
        data: EmojiData,
        onItemClicked: @escaping (UiEmoji) -> Void
    ) {
        self.data = data
        self.onItemClicked = onItemClicked
        _presenter = .init(
            wrappedValue: .init(
                presenter: EmojiHistoryPresenter(
                    accountType: data.accountType,
                    emojis: data.data.values.flatMap { $0 }
                )
            )
        )
    }

    private var filteredSections: [(key: String, value: [UiEmoji])] {
        data.data
            .sorted { $0.key < $1.key }
            .filter { key, value in
                filterText.isEmpty ||
                    key.localizedCaseInsensitiveContains(filterText) ||
                    value.contains { item in
                        item.searchKeywords.contains { keyword in
                            keyword.localizedCaseInsensitiveContains(filterText)
                        }
                    }
            }
    }
}

private struct EmojiSection: View {
    let value: [UiEmoji]
    let key: String
    let onItemClicked: (UiEmoji) -> Void

    @State private var isExpanded = false

    var body: some View {
        Section(isExpanded: $isExpanded) {
            EmojiGrid(items: value, onItemClicked: onItemClicked)
        } header: {
            Text(key)
                .font(.body)
        }
    }
}

private struct EmojiGrid: View {
    let items: [UiEmoji]
    let onItemClicked: (UiEmoji) -> Void

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 48))], spacing: 8) {
            ForEach(items, id: \.shortcode) { item in
                NetworkImage(data: item.url)
                    .scaledToFit()
                    .frame(width: 32, height: 32)
                    .onTapGesture {
                        onItemClicked(item)
                    }
            }
        }
    }
}
