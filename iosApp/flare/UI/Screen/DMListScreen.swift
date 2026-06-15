import SwiftUI
import SwiftUIBackports
@preconcurrency import KotlinSharedUI

struct DMListScreen: View {
    let accountType: AccountType
    @State private var pinCode: String = ""
    @StateObject private var presenter: KotlinPresenter<DMListState>
    var body: some View {
        Group {
            if presenter.state.pinCodePromptVisible {
                XChatPinCodeGate(
                    isVerifying: presenter.state.pinCodeVerifying,
                    errorMessage: presenter.state.pinCodeErrorMessage,
                    pinCode: $pinCode
                ) { pinCode in
                    presenter.state.submitPinCode(pinCode: pinCode)
                }
            } else {
                List {
                    PagingView(data: presenter.state.items) { item in
                        NavigationLink(value: Route.dmConversation(accountType, item.key, item.users.count == 1 ? item.users.first?.name.innerText ?? String(localized: "direct_messages_title") : String(localized: "direct_messages_title"))) {
                            HStack {
                                if item.hasUser, let image = item.users.first?.avatar {
                                    AvatarView(data: image.url, customHeader: image.customHeaders)
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
                                                    Text(user.handle.canonical)
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
            }
        }
        .navigationTitle("dm_list_title")
        .onChange(of: presenter.state.pinCodePromptVisible) { _, newValue in
            if !newValue {
                pinCode = ""
            }
        }
    }
}

extension DMListScreen {
    init(accountType: AccountType) {
        self.accountType = accountType
        self._presenter = .init(wrappedValue: .init(presenter: DMListPresenter(accountType: accountType)))
    }
}

struct UserDMConversationScreen: View {
    @StateObject private var presenter: KotlinPresenter<UserDMConversationPresenterState>
    private let accountType: AccountType
    
    init(accountType: AccountType, userKey: MicroBlogKey) {
        self.accountType = accountType
        self._presenter = .init(wrappedValue: .init(presenter: UserDMConversationPresenter(accountType: accountType, userKey: userKey)))
    }
    
    var body: some View {
        StateView(state: presenter.state.roomKey) { roomKey in
            DMConversationScreen(accountType: accountType, roomKey: roomKey)
        } errorContent: { error in
            ListErrorView(error: error) {
                
            }
        } loadingContent: {
            ProgressView()
        }
    }
}

struct DMConversationScreen: View {
    @State private var inputText: String = ""
    @State private var pinCode: String = ""
    @Environment(\.openURL) private var openURL
    @StateObject private var presenter: KotlinPresenter<DMConversationState>
    var body: some View {
        Group {
            if presenter.state.pinCodePromptVisible {
                XChatPinCodeGate(
                    isVerifying: presenter.state.pinCodeVerifying,
                    errorMessage: presenter.state.pinCodeErrorMessage,
                    pinCode: $pinCode
                ) { pinCode in
                    presenter.state.submitPinCode(pinCode: pinCode)
                }
                .background(Color(.systemGroupedBackground))
            } else {
                DMConversationMessagesView(
                    data: presenter.state.items,
                    onRetry: { key in
                        presenter.state.retry(key: key)
                    },
                    onOpenURL: { url in
                        openURL(url)
                    }
                )
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
                        .disabled(inputText.isEmpty)
                    }
                    .padding([.horizontal, .bottom])
                    .backport
                    .glassEffectContainer()
                }
            }
        }
        .onChange(of: presenter.state.pinCodePromptVisible) { _, newValue in
            if !newValue {
                pinCode = ""
            }
        }
    }
}

extension DMConversationScreen {
    init(accountType: AccountType, roomKey: MicroBlogKey) {
        self._presenter = .init(wrappedValue: .init(presenter: DMConversationPresenter(accountType: accountType, roomKey: roomKey)))
    }
}

private struct XChatPinCodeGate: View {
    let isVerifying: Bool
    let errorMessage: String?
    @Binding var pinCode: String
    let onSubmit: (String) -> Void

    private var trimmedPinCode: String {
        pinCode.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func submitIfReady() {
        let value = trimmedPinCode
        guard !isVerifying, !value.isEmpty else { return }
        onSubmit(value)
    }

    var body: some View {
        VStack(spacing: 12) {
            Text("dm_pin_code_title")
                .font(.headline)
            Text("dm_pin_code_message")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            SecureField("dm_pin_code_label", text: $pinCode)
                .textContentType(.oneTimeCode)
                .keyboardType(.numberPad)
                .textFieldStyle(.roundedBorder)
                .disabled(isVerifying)
                .onSubmit(submitIfReady)
            if let errorMessage, !errorMessage.isEmpty {
                Text(errorMessage)
                    .font(.caption)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
            }
            Button(action: submitIfReady) {
                if isVerifying {
                    ProgressView()
                } else {
                    Text("OK")
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(isVerifying || trimmedPinCode.isEmpty)
        }
        .frame(maxWidth: 360)
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
    }
}
