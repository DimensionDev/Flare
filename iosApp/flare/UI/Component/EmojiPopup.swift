import SwiftUI
import KotlinSharedUI
import SwiftUIBackports

struct EmojiPopup: View {
    @StateObject private var presenter: KotlinPresenter<EmojiHistoryPresenterState>
    @State private var filterText: String = ""
    let data: EmojiData
    let onItemClicked: (UiEmoji) -> Void
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 8) {
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
                                .frame(maxWidth: .infinity, alignment: .leading)
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
                    Section {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 48))], spacing: 8) {
                            ForEach(value, id: \.shortcode) { item in
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
                        Text(key)
                            .font(.body)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
            .padding()
        }
        .safeAreaInset(edge: .bottom) {
            TextField("emoji_search_placeholder", text: $filterText)
                .padding()
                .backport
                .glassEffect(.regularInteractive, in: .capsule, fallbackBackground: .regularMaterial)
                .padding()
        }
    }
}

extension EmojiPopup {
    init(
        accountType: AccountType,
        data: EmojiData,
        onItemClicked: @escaping (UiEmoji) -> Void
    ) {
        self.data = data
        self.onItemClicked = onItemClicked
        self._presenter = .init(wrappedValue: .init(presenter: EmojiHistoryPresenter(accountType: accountType, emojis: data.data.values.flatMap { $0 })))
    }
}
