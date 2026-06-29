import AppKit
import FlareAppleCore
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import SwiftUI
import SwiftUIBackports

struct MacDirectMessagesScreen: View {
    @ObservedObject private var windowCoordinator: MacDirectMessageWindowCoordinator
    @StateObject private var presenter = KotlinPresenter(presenter: DirectMessageUserListPresenter())
    @State private var selectedAccountKey: MicroBlogKey?
    @State private var selectedRoomID: String?
    @State private var selectedRoom: MacDMRoomSelection?
    @State private var roomResolveRequest: MacDMRoomResolveRequest?

    init(windowCoordinator: MacDirectMessageWindowCoordinator = .shared) {
        self.windowCoordinator = windowCoordinator
    }

    var body: some View {
        NavigationSplitView {
            MacDMUserListColumn(
                state: presenter.state,
                selectedAccountKey: $selectedAccountKey
            )
        } content: {
            if let selectedAccountType {
                MacDMRoomListColumn(
                    accountType: selectedAccountType,
                    selectedRoomID: $selectedRoomID,
                    selectedRoom: $selectedRoom
                )
                .id(selectedAccountKey)
            } else {
                MacDMPlaceholder(
                    icon: .message,
                    title: "direct_messages_title",
                    message: "dm_list_empty"
                )
            }
        } detail: {
            if let selectedRoom {
                MacDMConversationColumn(
                    selection: selectedRoom
                )
                .id(selectedRoom.id)
            } else {
                MacDMPlaceholder(
                    icon: .commentDots,
                    title: "dm_conversation",
                    message: "dm_list_empty"
                )
            }
        }
        .background {
            if let roomResolveRequest {
                MacDMRoomResolver(request: roomResolveRequest) { roomKey in
                    selectedRoomID = roomKey.description()
                    selectedRoom = MacDMRoomSelection(
                        accountType: roomResolveRequest.accountType,
                        roomKey: roomKey,
                        title: String(localized: "dm_conversation", bundle: .main)
                    )
                    self.roomResolveRequest = nil
                } onFailed: {
                    self.roomResolveRequest = nil
                }
                .id(roomResolveRequest.id)
            }
        }
        .onAppear {
            reconcileAccountSelection()
            if let request = windowCoordinator.request {
                open(request.route)
            }
        }
        .onChange(of: userItemsSignature) { _, _ in
            reconcileAccountSelection()
        }
        .onChange(of: selectedAccountKey) { _, _ in
            clearSelectedRoom()
        }
        .onChange(of: windowCoordinator.request?.id) { _, _ in
            if let request = windowCoordinator.request {
                open(request.route)
            }
        }
    }

    private var userItems: [DirectMessageUserListItem] {
        switch onEnum(of: presenter.state.items) {
        case .success(let success):
            success.data.cast(DirectMessageUserListItem.self)
        case .loading, .error:
            []
        }
    }

    private var userItemsSignature: String {
        userItems.map { $0.accountKey.description() }.joined(separator: "|")
    }

    private var selectedAccountType: AccountType? {
        guard let selectedAccountKey else {
            return nil
        }
        return userItems.first { $0.accountKey == selectedAccountKey }?.accountType
    }

    private func reconcileAccountSelection() {
        let keys = userItems.map(\.accountKey)
        guard !keys.isEmpty else {
            selectedAccountKey = nil
            clearSelectedRoom()
            return
        }

        if let selectedAccountKey, keys.contains(selectedAccountKey) {
            return
        }

        selectedAccountKey = keys.first
    }

    private func open(_ route: Route) {
        switch route {
        case .directMessages:
            reconcileAccountSelection()
        case .allDirectMessages(let accountType):
            if let accountKey = accountType.specificAccountKey {
                selectedAccountKey = accountKey
            }
        case .dmConversation(let accountType, let roomKey, let title):
            if let accountKey = accountType.specificAccountKey {
                selectedAccountKey = accountKey
            }
            selectedRoomID = roomKey.description()
            selectedRoom = MacDMRoomSelection(
                accountType: accountType,
                roomKey: roomKey,
                title: title
            )
        case .userDirectMessages(let accountType, let userKey):
            if let accountKey = accountType.specificAccountKey {
                selectedAccountKey = accountKey
            }
            clearSelectedRoom()
            roomResolveRequest = MacDMRoomResolveRequest(
                accountType: accountType,
                userKey: userKey
            )
        default:
            break
        }
    }

    private func clearSelectedRoom() {
        selectedRoomID = nil
        selectedRoom = nil
    }
}

private struct MacDMUserListColumn: View {
    let state: DirectMessageUserListState
    @Binding var selectedAccountKey: MicroBlogKey?

    var body: some View {
        List(selection: $selectedAccountKey) {
            StateView(state: state.items) { data in
                let items = data.cast(DirectMessageUserListItem.self)
                if items.isEmpty {
                    MacDMListInlinePlaceholder(
                        icon: .message,
                        title: "dm_list_empty"
                    )
                } else {
                    ForEach(items, id: \.accountKey) { item in
                        MacDMUserRow(item: item)
                            .tag(item.accountKey)
                    }
                }
            } errorContent: { error in
                ListErrorView(error: error) {}
            } loadingContent: {
                ForEach(0..<5, id: \.self) { _ in
                    MacDMUserPlaceholderRow()
                }
            }
        }
        .searchable(
            text: Binding(
                get: { state.query },
                set: { state.setQuery(query: $0) }
            ),
            placement: .sidebar
        )
        .navigationTitle("direct_messages_title")
        .listStyle(.sidebar)
        .navigationSplitViewColumnWidth(min: 220, ideal: 280, max: 360)
    }
}

private struct MacDMUserRow: View {
    let item: DirectMessageUserListItem

    var body: some View {
        StateView(state: item.profile) { user in
            HStack(spacing: 10) {
                AvatarView(data: user.avatar?.url, customHeader: user.avatar?.customHeaders)
                    .frame(width: 34, height: 34)

                VStack(alignment: .leading, spacing: 2) {
                    RichText(text: user.name)
                        .lineLimit(1)
                    Text(user.handle.canonical)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer()

                MacDMUnreadBadge(state: item.unreadCount)
            }
            .padding(.vertical, 2)
        } errorContent: { _ in
            HStack(spacing: 10) {
                Image(fontAwesome: .circleUser)
                    .frame(width: 34, height: 34)
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.accountKey.id)
                        .lineLimit(1)
                    Text(item.accountKey.host)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
        } loadingContent: {
            MacDMUserPlaceholderRow()
        }
    }
}

private struct MacDMUnreadBadge: View {
    let state: UiState<KotlinInt>

    var body: some View {
        if let count = unreadCount, count > 0 {
            Text(verbatim: "\(count)")
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 6)
                .padding(.vertical, 3)
                .background(Capsule().fill(Color.accentColor))
        }
    }

    private var unreadCount: Int? {
        switch onEnum(of: state) {
        case .success(let success):
            Int(success.data.intValue)
        case .loading, .error:
            nil
        }
    }
}

private struct MacDMRoomListColumn: View {
    let accountType: AccountType
    @Binding var selectedRoomID: String?
    @Binding var selectedRoom: MacDMRoomSelection?
    @StateObject private var presenter: KotlinPresenter<DMListState>

    init(
        accountType: AccountType,
        selectedRoomID: Binding<String?>,
        selectedRoom: Binding<MacDMRoomSelection?>
    ) {
        self.accountType = accountType
        _selectedRoomID = selectedRoomID
        _selectedRoom = selectedRoom
        _presenter = .init(wrappedValue: .init(presenter: DMListPresenter(accountType: accountType)))
    }

    var body: some View {
        List(selection: $selectedRoomID) {
            content
        }
        .listStyle(.plain)
        .onChange(of: selectedRoomID) { _, newValue in
            syncSelection(id: newValue)
        }
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
        .navigationTitle("direct_messages_title")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    Task {
                        try? await presenter.state.refreshSuspend()
                    }
                } label: {
                    Label {
                        Text("Refresh")
                    } icon: {
                        if presenter.state.isRefreshing {
                            ProgressView()
                                .progressViewStyle(.circular)
                                .scaleEffect(0.5)
                                .frame(width: 12, height: 12)
                        } else {
                            Image(fontAwesome: .arrowsRotate)
                        }
                    }
                }
                .disabled(presenter.state.isRefreshing)
            }
        }
        .navigationSplitViewColumnWidth(min: 320, ideal: 380, max: 520)
    }

    @ViewBuilder
    private var content: some View {
        switch onEnum(of: presenter.state.items) {
        case .empty:
            MacDMListInlinePlaceholder(
                icon: .message,
                title: "dm_list_empty"
            )
        case .error(let error):
            ListErrorView(error: error.error) {
                _ = error.onRetry()
            }
        case .loading:
            ForEach(0..<5, id: \.self) { _ in
                MacDMRoomPlaceholderRow()
            }
        case .success(let success):
            successContent(success)
        }
    }

    @ViewBuilder
    private func successContent(_ success: PagingStateSuccess<UiDMRoom>) -> some View {
        let count = Int(success.itemCount)
        if count == 0 {
            MacDMListInlinePlaceholder(
                icon: .message,
                title: "dm_list_empty"
            )
        } else {
            let rows = rows(success: success, count: count)
            ForEach(rows) { row in
                if let room = row.room {
                    MacDMRoomRow(room: room)
                        .contentShape(Rectangle())
                        .tag(row.id)
                        .onAppear {
                            _ = success.get(index: Int32(row.index))
                        }
                } else {
                    MacDMRoomPlaceholderRow()
                        .onAppear {
                            _ = success.get(index: Int32(row.index))
                        }
                }
            }

            switch onEnum(of: success.appendState) {
            case .error(let error):
                ListErrorView(error: error.error) {
                    success.retry()
                }
            case .loading:
                ProgressView()
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .center)
            case .notLoading:
                EmptyView()
            }
        }
    }

    private func rows(
        success: PagingStateSuccess<UiDMRoom>,
        count: Int
    ) -> [MacDMRoomListRow] {
        (0..<count).map { index in
            let room = success.peek(index: Int32(index))
            return MacDMRoomListRow(
                id: room?.key.description() ?? "loading-\(index)",
                index: index,
                room: room
            )
        }
    }

    @MainActor
    private func syncSelection(id: String?) {
        guard let id else {
            selectedRoom = nil
            return
        }

        if selectedRoom?.roomKey.description() == id {
            return
        }

        if let selection = selection(for: id) {
            selectedRoom = selection
        }
    }

    @MainActor
    private func selection(for id: String) -> MacDMRoomSelection? {
        switch onEnum(of: presenter.state.items) {
        case .success(let success):
            let count = Int(success.itemCount)
            for index in 0..<count {
                guard let room = success.peek(index: Int32(index)),
                      room.key.description() == id else {
                    continue
                }
                return MacDMRoomSelection(
                    accountType: accountType,
                    roomKey: room.key,
                    title: room.dmTitle
                )
            }
            return nil
        case .empty, .error, .loading:
            return nil
        }
    }
}

private struct MacDMRoomListRow: Identifiable {
    let id: String
    let index: Int
    let room: UiDMRoom?
}

private struct MacDMRoomRow: View {
    let room: UiDMRoom

    var body: some View {
        HStack(spacing: 10) {
            MacDMRoomAvatar(room: room)

            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 6) {
                    Text(room.dmTitle)
                        .font(.headline)
                        .lineLimit(1)

                    Spacer(minLength: 8)

                    if let lastMessage = room.lastMessage {
                        DateTimeText(data: lastMessage.timestamp)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }

                Text(room.lastMessageText)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }

            if room.unreadCount > 0 {
                Text(verbatim: "\(room.unreadCount)")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(Capsule().fill(Color.accentColor))
            }
        }
        .padding(.vertical, 5)
    }
}

private struct MacDMConversationColumn: View {
    let selection: MacDMRoomSelection
    @Environment(\.openURL) private var openURL
    @StateObject private var presenter: KotlinPresenter<DMConversationState>
    @State private var inputText = ""

    init(selection: MacDMRoomSelection) {
        self.selection = selection
        _presenter = .init(
            wrappedValue: .init(
                presenter: DMConversationPresenter(
                    accountType: selection.accountType,
                    roomKey: selection.roomKey
                )
            )
        )
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                PagingView(
                    data: presenter.state.items,
                    reversed: true,
                    id: { AnyHashable($0.id) },
                    emptyContent: {
                        MacDMListInlinePlaceholder(
                            icon: .commentDots,
                            title: "dm_list_empty"
                        )
                    },
                    errorContent: { error, retry in
                        ListErrorView(error: error) {
                            retry()
                        }
                    },
                    loadingContent: { _, _ in
                        ProgressView()
                    }
                ) { item, _, _ in
                    MacDMMessageRow(
                        item: item,
                        onRetry: {
                            presenter.state.retry(key: item.key)
                        },
                        onOpenURL: { url in
                            openURL(url)
                        }
                    )
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity)
        }
        .defaultScrollAnchor(.bottom)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            MacDMChatInputBar(
                draft: $inputText,
                canSend: canSend,
                onSend: send
            )
        }
        .navigationTitle(dynamicTitle)
    }

    private var canSend: Bool {
        !inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var dynamicTitle: String {
        switch onEnum(of: presenter.state.users) {
        case .success(let success):
            let users = success.data.cast(UiProfile.self)
            if users.count == 1, let user = users.first {
                return user.name.raw.isEmpty ? user.handle.canonical : user.name.raw
            }
            return String(localized: "dm_conversation", bundle: .main)
        case .loading, .error:
            return selection.title
        }
    }

    private func send() {
        let message = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !message.isEmpty else {
            return
        }
        presenter.state.send(message: message)
        inputText = ""
    }
}

private struct MacDMChatInputBar: View {
    @Binding var draft: String
    let canSend: Bool
    let onSend: () -> Void

    var body: some View {
        TextField("dm_conversation_input_placeholder", text: $draft, axis: .vertical)
            .lineLimit(1...5)
            .textFieldStyle(.plain)
            .padding()
            .onSubmit(onSend)
            .safeAreaInset(edge: .trailing, spacing: 8) {
                Button(action: onSend) {
                    Image(systemName: "paperplane.fill")
                        .frame(width: 24, height: 24)
                }
                .buttonBorderShape(.circle)
                .backport
                .glassButtonStyle()
                .disabled(!canSend)
                .help(Text("send_message"))
                .accessibilityLabel(Text("send_message"))
                .padding(.trailing, 8)
            }
            .backport
            .glassEffect()
            .padding(.horizontal)
            .padding(.bottom)
    }
}

private struct MacDMMessageRow: View {
    let item: UiDMItem
    let onRetry: () -> Void
    let onOpenURL: (URL) -> Void

    var body: some View {
        VStack(alignment: item.isFromMe ? .trailing : .leading, spacing: 4) {
            HStack(alignment: .bottom, spacing: 8) {
                if item.isFromMe {
                    Spacer(minLength: 56)
                } else if item.showSender {
                    AvatarView(data: item.user.avatar?.url, customHeader: item.user.avatar?.customHeaders)
                        .frame(width: 32, height: 32)
                }

                if item.sendState == .failed {
                    Button(action: onRetry) {
                        Image(fontAwesome: .circleExclamation)
                            .foregroundStyle(.red)
                    }
                    .buttonStyle(.plain)
                    .help(Text("send_message"))
                }

                messageContent
                    .frame(maxWidth: 520, alignment: item.isFromMe ? .trailing : .leading)

                if !item.isFromMe {
                    Spacer(minLength: 56)
                }
            }

            footer
        }
        .frame(maxWidth: .infinity, alignment: item.isFromMe ? .trailing : .leading)
    }

    @ViewBuilder
    private var messageContent: some View {
        switch onEnum(of: item.content) {
        case .text(let message):
            RichText(text: message.text)
                .padding(.horizontal, 13)
                .padding(.vertical, 9)
                .foregroundStyle(item.isFromMe ? Color.white : Color.primary)
                .background(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(item.isFromMe ? Color.accentColor : Color(nsColor: .controlBackgroundColor))
                )
        case .deleted:
            Text("dm_deleted")
                .font(.caption)
                .foregroundStyle(.secondary)
                .padding(.horizontal, 13)
                .padding(.vertical, 9)
        case .media(let message):
            MediaView(data: message.media)
                .frame(width: 260, height: 180)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .onTapGesture {
                    if let url = message.media.primaryURL {
                        onOpenURL(url)
                    }
                }
        case .status(let message):
            StatusView(
                data: message.status,
                maxLine: 5,
                forceHideActions: true
            )
            .padding(10)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color(nsColor: .controlBackgroundColor))
            )
        }
    }

    @ViewBuilder
    private var footer: some View {
        HStack(spacing: 6) {
            if item.showSender && !item.isFromMe {
                Text(item.user.name.raw)
                    .lineLimit(1)
            }

            if item.sendState == .sending {
                Text("dm_sending")
            } else if item.sendState != .failed {
                DateTimeText(data: item.timestamp)
            }
        }
        .font(.caption2)
        .foregroundStyle(.secondary)
        .padding(.leading, item.showSender && !item.isFromMe ? 40 : 0)
        .padding(.trailing, item.isFromMe ? 4 : 0)
    }
}

private struct MacDMRoomResolver: View {
    let request: MacDMRoomResolveRequest
    let onResolved: (MicroBlogKey) -> Void
    let onFailed: () -> Void
    @StateObject private var presenter: KotlinPresenter<UserDMConversationPresenterState>

    init(
        request: MacDMRoomResolveRequest,
        onResolved: @escaping (MicroBlogKey) -> Void,
        onFailed: @escaping () -> Void
    ) {
        self.request = request
        self.onResolved = onResolved
        self.onFailed = onFailed
        _presenter = .init(
            wrappedValue: .init(
                presenter: UserDMConversationPresenter(
                    accountType: request.accountType,
                    userKey: request.userKey
                )
            )
        )
    }

    var body: some View {
        Color.clear
            .frame(width: 0, height: 0)
            .onSuccessOf(of: presenter.state.roomKey) { roomKey in
                onResolved(roomKey)
            }
            .onChange(of: presenter.state.roomKey) { _, newValue in
                if case .error = onEnum(of: newValue) {
                    onFailed()
                }
            }
    }
}

private struct MacDMRoomAvatar: View {
    let room: UiDMRoom

    var body: some View {
        if room.hasUser, let firstUser = room.users.first {
            AvatarView(data: firstUser.avatar?.url, customHeader: firstUser.avatar?.customHeaders)
                .frame(width: 42, height: 42)
        } else {
            Image(fontAwesome: .circleUser)
                .frame(width: 42, height: 42)
                .foregroundStyle(.secondary)
        }
    }
}

private struct MacDMPlaceholder: View {
    let icon: FontAwesomeIcon
    let title: LocalizedStringKey
    let message: LocalizedStringKey

    var body: some View {
        VStack(spacing: 10) {
            Image(fontAwesome: icon)
                .font(.system(size: 34))
                .foregroundStyle(.secondary)
            Text(title)
                .font(.headline)
            Text(message)
                .font(.callout)
                .foregroundStyle(.secondary)
        }
        .multilineTextAlignment(.center)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct MacDMListInlinePlaceholder: View {
    let icon: FontAwesomeIcon
    let title: LocalizedStringKey

    var body: some View {
        VStack(spacing: 8) {
            Image(fontAwesome: icon)
                .font(.title2)
                .foregroundStyle(.secondary)
            Text(title)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
    }
}

private struct MacDMUserPlaceholderRow: View {
    var body: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(.placeholder)
                .frame(width: 34, height: 34)
            VStack(alignment: .leading, spacing: 5) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(.placeholder)
                    .frame(width: 120, height: 10)
                RoundedRectangle(cornerRadius: 4)
                    .fill(.placeholder)
                    .frame(width: 86, height: 8)
            }
        }
        .redacted(reason: .placeholder)
        .padding(.vertical, 3)
    }
}

private struct MacDMRoomPlaceholderRow: View {
    var body: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(.placeholder)
                .frame(width: 42, height: 42)
            VStack(alignment: .leading, spacing: 6) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(.placeholder)
                    .frame(width: 150, height: 11)
                RoundedRectangle(cornerRadius: 4)
                    .fill(.placeholder)
                    .frame(width: 220, height: 9)
            }
        }
        .redacted(reason: .placeholder)
        .padding(.vertical, 6)
    }
}

private struct MacDMMessagePlaceholderRow: View {
    var body: some View {
        HStack {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(.placeholder)
                .frame(width: 260, height: 42)
            Spacer()
        }
        .redacted(reason: .placeholder)
    }
}

private struct MacDMRoomSelection: Identifiable {
    let accountType: AccountType
    let roomKey: MicroBlogKey
    let title: String

    var id: String {
        "\(String(describing: accountType)):\(roomKey.description())"
    }
}

private struct MacDMRoomResolveRequest: Identifiable {
    let id = UUID()
    let accountType: AccountType
    let userKey: MicroBlogKey
}

private extension UiDMRoom {
    var dmTitle: String {
        if users.count == 1, let user = users.first {
            let name = user.name.raw
            return name.isEmpty ? user.handle.canonical : name
        }
        return String(localized: "direct_messages_title", bundle: .main)
    }
}

private extension AccountType {
    var specificAccountKey: MicroBlogKey? {
        (self as? AccountType.Specific)?.accountKey
    }
}

private extension UiMedia {
    var primaryURL: URL? {
        switch onEnum(of: self) {
        case .image(let image):
            URL(string: image.url)
        case .gif(let gif):
            URL(string: gif.url)
        case .video(let video):
            URL(string: video.url)
        case .audio:
            nil
        }
    }
}
