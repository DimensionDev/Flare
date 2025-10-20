import SwiftUI
import SwiftUIBackports
@preconcurrency import KotlinSharedUI

struct DMListScreen: View {
    let accountType: AccountType
    @StateObject private var presenter: KotlinPresenter<DMListState>
    var body: some View {
        List {
            PagingView(data: presenter.state.items) { item in
                NavigationLink {
                    DMConversationScreen(accountType: accountType, roomKey: item.key)
                        .navigationTitle(item.users.count == 1 ? item.users.first?.name.markdown ?? "direct_messages_title" : "direct_messages_title")
                } label: {
                    HStack {
                        if item.hasUser, let image = item.users.first?.avatar {
                            AvatarView(data: image)
                                .frame(width: 48, height: 48)
                        } else {
                            Image("fa-list")
                        }
                        VStack(
                            alignment: .leading,
                            spacing: 8
                        ) {
                            HStack {
                                if item.hasUser {
                                    ForEach(item.users, id: \.key) { user in
                                        RichText(text: user.name)
                                            .lineLimit(1)
                                        if (item.users.count == 1) {
                                            Text(user.handle)
                                                .lineLimit(1)
                                                .font(.caption)
                                                .foregroundStyle(.secondary)
                                        }
                                    }
                                }
                                Spacer()
                                if let lasMessage = item.lastMessage {
                                    DateTimeText(data: lasMessage.timestamp)
                                        .lineLimit(1)
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Text(item.lastMessageText)
                                .lineLimit(2)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        if item.unreadCount > 0 {
                            Spacer()
                            Text("\(item.unreadCount)")
                                .lineLimit(1)
                                .font(.caption)
                                .padding(6)
                                .background(
                                    Circle()
                                        .fill(Color.accentColor)
                                )
                                .foregroundStyle(.white)
                        }
                    }
                }
            } loadingContent: {
                UiListPlaceholder()
            }
        }
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
        .navigationTitle("dm_list_title")
    }
}

extension DMListScreen {
    init(accountType: AccountType) {
        self.accountType = accountType
        self._presenter = .init(wrappedValue: .init(presenter: DMListPresenter(accountType: accountType)))
    }
}


struct DMConversationScreen: View {
    @State private var inputText: String = ""
    @Environment(\.openURL) private var openURL
    @StateObject private var presenter: KotlinPresenter<DMConversationState>
    var body: some View {
        ComposeDMConversationView(key: presenter.key, data: presenter.state, onOpenLink: { url in openURL(.init(string: url)!) })
//            .ignoresSafeArea()
            .background(Color(.systemGroupedBackground))
            .safeAreaInset(edge: .bottom) {
                HStack {
                    TextField("dm_conversation_input_placeholder", text: $inputText)
                        .padding()
                        .backport
                        .glassEffect(.regularInteractive, in: .capsule, fallbackBackground: .regularMaterial)
                    Button(action: {
                        presenter.state.send(message: inputText)
                        inputText = ""
                    }) {
                        Image(systemName: "paperplane.fill")
                            .font(.title2)
                    }
                    .backport
                    .glassProminentButtonStyle()
                }
                .padding([.horizontal, .bottom])
                .backport
                .glassEffectContainer()
            }
    }
}

extension DMConversationScreen {
    init(accountType: AccountType, roomKey: MicroBlogKey) {
        self._presenter = .init(wrappedValue: .init(presenter: DMConversationPresenter(accountType: accountType, roomKey: roomKey)))
    }
}


struct ComposeDMConversationView : UIViewControllerRepresentable {
    let key: String
    let data: DMConversationState
    let state: ComposeUIStateProxy<DMConversationState>
    
    init(key: String, data: DMConversationState, onOpenLink: @escaping (String) -> Void) {
        self.key = key
        self.data = data
        if let state = ComposeUIStateProxyCache.shared.getOrCreate(key: key, factory: {
            .init(initialState: data, onOpenLink: onOpenLink)
        }) as? ComposeUIStateProxy<any DMConversationState> {
            self.state = state
        } else {
            self.state = ComposeUIStateProxy(initialState: data, onOpenLink: onOpenLink)
        }
    }
    
    func makeUIViewController(context: Context) -> some UIViewController {
        return KotlinSharedUI.DMConversationController(state: state)
    }
    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {
        state.update(newState: data)
    }
    
    func dismantleUIViewController(_ uiViewController: UIViewController, coordinator: Coordinator) {
        ComposeUIStateProxyCache.shared.remove(key: key)
    }
}
