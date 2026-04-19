import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

struct EmojiPopup: View {
    @StateObject private var presenter: KotlinPresenter<EmojiHistoryPresenterState>
    @State private var filterText: String = ""
    let data: EmojiData
    let onItemClicked: (UiEmoji) -> Void
    
    var body: some View {
        List {
            StateView(state: presenter.state.history) { history in
                let items = history.cast(UiEmoji.self)
                if !items.isEmpty {
                    Section {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 48))], spacing: 8) {
                            ForEach(items, id: \.shortcode) { item in
                                NetworkImage(data: item.url)
                                    .scaledToFit()
                                    .frame(width: 32, height: 32)
                                    .onTapGesture {
                                        onItemClicked(item)
                                        presenter.state.addHistory(emoji: item)
                                    }
                            }
                        }
                    } header: {
                        Text("emoji_recently_used")
                            .font(.body)
                    }
                }
            }
            
            ForEach(
                data.data.sorted(by: { $0.key < $1.key }).filter { key, value in
                    filterText.isEmpty || key.localizedCaseInsensitiveContains(filterText) || value.contains(where: { item in
                        item.searchKeywords.contains(where: { keyword in
                            keyword.contains(filterText)
                        })
                    })
                },
                id: \ .key
            ) { key, value in
                if data.data.count == 1 {
                    Section {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 48))], spacing: 8) {
                            ForEach(value, id: \.shortcode) { item in
                                NetworkImage(data: item.url)
                                    .scaledToFit()
                                    .frame(width: 32, height: 32)
                                    .onTapGesture {
                                        onItemClicked(item)
                                    }
                            }
                        }
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
            TextField("emoji_search_placeholder", text: $filterText)
                .padding()
                .backport
                .glassEffect(.regularInteractive, in: .capsule, fallbackBackground: .regularMaterial)
                .padding()
        }
    }
}

struct EmojiSection: View {
    let value: [UiEmoji]
    let key: String
    let onItemClicked: (UiEmoji) -> Void
    @State private var isExpanded: Bool = false
    var body: some View {
        Section(isExpanded: $isExpanded) {
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 48))], spacing: 8) {
                ForEach(value, id: \.shortcode) { item in
                    NetworkImage(data: item.url)
                        .scaledToFit()
                        .frame(width: 32, height: 32)
                        .onTapGesture {
                            onItemClicked(item)
                        }
                }
            }
        } header: {
            Text(key)
                .font(.body)
        }
    }
}
extension EmojiPopup {
    init(
        data: EmojiData,
        onItemClicked: @escaping (UiEmoji) -> Void
    ) {
        self.data = data
        self.onItemClicked = onItemClicked
        self._presenter = .init(wrappedValue: .init(presenter: EmojiHistoryPresenter(accountType: data.accountType, emojis: data.data.values.flatMap { $0 })))
    }
}
