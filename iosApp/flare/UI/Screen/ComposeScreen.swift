import SwiftUI
import KotlinSharedUI
import PhotosUI
import SwiftUIIntrospect

struct ComposeScreen: View {
    @Environment(\.dismiss) var dismiss
    let accountType: AccountType
    let composeStatus: ComposeStatus?
    @FocusState private var keyboardFocused: Bool
    @FocusState private var cwKeyboardFocused: Bool
    @StateObject private var presenter: KotlinPresenter<ComposeState>
    @State private var viewModel = ComposeInputViewModel()
    @State private var uiTextView: UITextView?
    @State private var pendingCursor: Int?

    var body: some View {
        VStack(
            spacing: 8
        ) {
            ScrollView(.horizontal) {
                HStack {
                    StateView(state: presenter.state.selectedUsers) { users in
                        ForEach(0..<users.size, id: \.self) { index in
                            let item = users.get(index: index)
                            if let userState = item.first, let account = item.second {
                                StateView(state: userState) { user in
                                    UserOnelineView(data: user)
                                        .padding(.horizontal)
                                        .padding(.vertical, 8)
                                        .background(Color(.secondarySystemBackground))
                                        .clipShape(.rect(cornerRadius: 16))
                                        .onTapGesture {
                                            presenter.state.selectAccount(account: account)
                                        }
                                }
                            }
                        }
                    }
                    StateView(state: presenter.state.otherAccounts) { others in
                        if (others.size > 0) {
                            Menu {
                                ForEach(0..<others.size, id: \.self) { index in
                                    let item = others.get(index: index)
                                    if let userState = item.first, let account = item.second {
                                        StateView(state: userState) { user in
                                            Button {
                                                presenter.state.selectAccount(account: account)
                                            } label: {
                                                Label {
                                                    Text(user.handle)
                                                } icon: {
                                                    AvatarView(data: user.avatar)
                                                        .frame(width: 20, height: 20)
                                                }
                                            }
                                        }
                                    }
                                }
                            } label: {
                                Image("fa-plus")
                            }

                        }
                    }
                }
                .padding(.horizontal)
            }
            .scrollIndicators(.hidden)
            ScrollView {
                VStack(
                    spacing: 8
                ) {
                    if viewModel.enableCW {
                        TextField(text: $viewModel.contentWarning) {
                            Text("compose_cw_placeholder")
                        }
                        .textFieldStyle(.plain)
                        .focused($cwKeyboardFocused)
                        Divider()
                    }
                    
                    TextField(text: $viewModel.text, axis: .vertical) {
                        Text("compose_placeholder")
                    }
                    .introspect(.textField(axis: .vertical), on: .iOS(.v16, .v17, .v18, .v26)) { textField in
                        self.uiTextView = textField
                        applyCursorIfPossible()
                    }
                    .textFieldStyle(.plain)
                    .focused($keyboardFocused)
                    .onAppear {
                        keyboardFocused = true
                    }
                    Spacer()
                    if viewModel.mediaViewModel.items.count > 0 {
                        ScrollView(.horizontal) {
                            HStack {
                                ForEach(viewModel.mediaViewModel.items.indices, id: \.self) { index in
                                    let item = viewModel.mediaViewModel.items[index]
                                    if let image = item.image {
                                        Image(uiImage: image)
                                            .resizable()
                                            .scaledToFill()
                                            .frame(width: 128, height: 128)
                                            .cornerRadius(8)
                                            .contextMenu {
                                                Button(action: {
                                                    withAnimation {
                                                        viewModel.mediaViewModel.remove(item: item)
                                                    }
                                                }, label: {
                                                    Label {
                                                        Text("delete")
                                                    } icon: {
                                                        Image("fa-trash")
                                                    }
                                                })
                                            }
                                    }
                                }
                            }
                        }
                        Toggle(isOn: $viewModel.mediaViewModel.sensitive, label: {
                            Text("compose_media_mark_sensitive")
                        })
                    }
                    if viewModel.pollViewModel.enabled {
                        HStack(
                            spacing: 8
                        ) {
                            Picker("compose_poll_type", selection: $viewModel.pollViewModel.pollType) {
                                Text("compose_poll_type_single")
                                    .tag(ComposePollType.single)
                                Text("compose_poll_type_multiple")
                                    .tag(ComposePollType.multiple)
                            }
                            .pickerStyle(.segmented)
                            Button {
                                withAnimation {
                                    viewModel.pollViewModel.add()
                                }
                            } label: {
                                Image("fa-plus")
                            }.disabled(viewModel.pollViewModel.choices.count >= 4)
                        }
                        ForEach($viewModel.pollViewModel.choices) { $choice in
                            HStack(
                                spacing: 8
                            ) {
                                TextField(text: $choice.text) {
                                    Text("compose_poll_choice_placeholder")
                                }
                                .textFieldStyle(.roundedBorder)
                                Button {
                                    withAnimation {
                                        viewModel.pollViewModel.remove(choice: choice)
                                    }
                                } label: {
                                    Image("fa-delete-left")
                                }
                                .disabled(viewModel.pollViewModel.choices.count <= 2)
                            }
                        }
                        HStack {
                            Spacer()
                            Menu {
                                ForEach(viewModel.pollViewModel.allExpiration, id: \.self) { expiration in
                                    Button(action: {
                                        viewModel.pollViewModel.expired = expiration
                                    }, label: {
                                        Text(expiration.rawValue)
                                    })
                                }
                            } label: {
                                Text("compose_poll_expiration")
                                Text(viewModel.pollViewModel.expired.rawValue)
                            }
                        }
                    }
                    if let replyState = presenter.state.replyState,
                       case .success(let reply) = onEnum(of: replyState),
                       case .status(let content) = onEnum(of: reply.data.content) {
                        StatusView(data: content, isQuote: true, showMedia: false)
                            .padding()
                            .clipShape(.rect(cornerRadius: 16))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color(.separator), lineWidth: 1)
                            )
                    }
                }
                .padding(.horizontal)
            }
            .scrollIndicators(.hidden)
            .safeAreaInset(edge: .bottom) {
                HStack {
                    ScrollView(.horizontal) {
                        HStack(
                            spacing: 16,
                        ) {
                            if !viewModel.pollViewModel.enabled {
                                PhotosPicker(selection: Binding(get: {
                                    viewModel.mediaViewModel.selectedItems
                                }, set: { value in
                                    viewModel.mediaViewModel.selectedItems = value
                                    viewModel.mediaViewModel.update()
                                }), matching: .any(of: [.images, .videos, .livePhotos])) {
                                    Image("fa-image")
                                }
                            }
                            if viewModel.mediaViewModel.selectedItems.count == 0 {
                                StateView(state: presenter.state.composeConfig) { config in
                                    if config.poll != nil {
                                         Button(action: {
                                             withAnimation {
                                                 viewModel.togglePoll()
                                             }
                                         }, label: {
                                             Image("fa-square-poll-horizontal")
                                         })
                                    }
                                }
                            }
                            StateView(state: presenter.state.visibilityState) { visibilityState in
                                Menu {
                                    ForEach(visibilityState.allVisibilities, id: \.self) { visibility in
                                        Button {
                                            visibilityState.setVisibility(value: visibility)
                                            viewModel.visibility = visibility
                                        } label: {
                                            Label {
                                                Text(visibility.name)
                                            } icon: {
                                                StatusVisibilityView(data: visibility)
                                            }
                                        }
                                    }
                                } label: {
                                    StatusVisibilityView(data: viewModel.visibility)
                                }
                            }
                            StateView(state: presenter.state.composeConfig) { config in
                                if config.contentWarning != nil {
                                    Button(action: {
                                        withAnimation {
                                            viewModel.toggleCW()
                                            if viewModel.enableCW {
                                                cwKeyboardFocused = true
                                            } else {
                                                keyboardFocused = true
                                            }
                                        }
                                    }, label: {
                                        Image("fa-circle-exclamation")
                                    })
                                }
                            }
                            StateView(state: presenter.state.emojiState) { emojis in
                                Button(action: {
                                    viewModel.showEmojiPanel()
                                }, label: {
                                    Image("fa-face-smile")
                                })
                                .popover(isPresented: $viewModel.showEmoji) {
                                    EmojiPopup(data: emojis) { item in
                                        viewModel.addEmoji(emoji: item)
                                        insert(item.insertText)
                                    }
                                }
                            }
                        }
                        .scrollIndicators(.hidden)
                        .font(.title)
                        .buttonStyle(.plain)
                    }
                    Spacer()
                    StateView(state: presenter.state.composeConfig) { config in
                        if let maxLength = config.text?.maxLength {
                            Text("\(viewModel.text.count)/\(maxLength)")
                                .foregroundStyle(viewModel.text.count > maxLength ? .red : .gray)
                        }
                    }
                }
                .padding()
                .glassEffect()
                .padding()
            }
            
        }
        .onChange(of: presenter.state.initialTextState) { oldValue, newValue in
            if case .success(let initialText) = onEnum(of: newValue) {
                viewModel.text = initialText.data.text
                pendingCursor = Int(initialText.data.cursorPosition)
                applyCursorIfPossible()
            }
        }
        .toolbarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                switch onEnum(of: composeStatus) {
                case .none:
                    Text("compose_title_new")
                case .quote:
                    Text("compose_title_quote")
                case .reply:
                    Text("compose_title_reply")
                }
            }
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Label {
                        Text("compose_button_cancel")
                    } icon: {
                        Image(systemName: "xmark")
                    }
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button {
                    send()
                } label: {
                    Label {
                        Text("compose_button_send")
                    } icon: {
                        Image(systemName: "paperplane.fill")
                    }
                }
                .disabled(viewModel.text.isEmpty)
            }
        }
    }
    
    private func applyCursorIfPossible() {
        guard let textView = uiTextView, textView.isFirstResponder, let pendingCursor else { return }
        let length = textView.text.count
        let clamped = NSRange(location: max(0, min(pendingCursor, length)),
                              length: 0)
        textView.selectedRange = clamped
        textView.scrollRangeToVisible(clamped)
        self.pendingCursor = nil
    }
    
    private func insert(_ s: String) {
        guard let textView = uiTextView else { return }

        if textView.markedTextRange != nil { return }

        let sel = textView.selectedRange
        let current = textView.text ?? ""
        let ns = current as NSString
        let newText = ns.replacingCharacters(in: sel, with: s)

        textView.text = newText
        viewModel.text = newText

        let newLocation = sel.location + (s as NSString).length
        textView.selectedRange = NSRange(location: newLocation, length: 0)
        textView.scrollRangeToVisible(NSRange(location: max(0, newLocation - 1), length: 1))
    }
    
    private func getComposeData() -> [ComposeData] {
        return presenter.state.selectedAccounts.map { account in
            ComposeData(
                account: account,
                content: viewModel.text,
                visibility: getVisibility(),
                language: ["en"],
                medias: getMedia(),
                sensitive: viewModel.mediaViewModel.sensitive,
                spoilerText: viewModel.contentWarning,
                poll: getPoll(),
                localOnly: false,
                referenceStatus: getReferenceStatus()
            )
        }
    }
    
    private func send() {
        let datas = getComposeData()
        for data in datas {
            presenter.state.send(data: data)
        }
        dismiss()
    }
    
    private func getMedia() -> [FileItem] {
        return viewModel.mediaViewModel.items.map { item in
            FileItem(name: item.item.itemIdentifier, data: KotlinByteArray.from(data: item.data!))
        }
    }
    private func getReferenceStatus() -> ComposeData.ReferenceStatus? {
        return if let data = composeStatus, let replyState = presenter.state.replyState, case .success(let timeline) = onEnum(of: replyState) {
            ComposeData.ReferenceStatus(data: timeline.data, composeStatus: data)
        } else {
            nil
        }
    }
    private func getPoll() -> ComposeData.Poll? {
        return if viewModel.pollViewModel.enabled {
            ComposeData.Poll(options: viewModel.pollViewModel.choices.map { item in item.text }, expiredAfter: viewModel.pollViewModel.expired.inWholeMilliseconds, multiple: viewModel.pollViewModel.pollType == ComposePollType.multiple)
        } else {
            nil
        }
    }
    private func getVisibility() -> UiTimeline.ItemContentStatusTopEndContentVisibilityType {
        return viewModel.visibility
    }

}

struct EmojiPopup: View {
    let data: EmojiData
    let onItemClicked: (UiEmoji) -> Void
    var body: some View {
        ScrollView {
            LazyVStack(spacing: 8) {
                ForEach(data.data.sorted(by: { $0.key < $1.key }), id: \ .key) { key, value in
                    Section(key) {
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 48))], spacing: 8) {
                            ForEach(value, id: \.shortcode) { item in
                                NetworkImage(data: item.url)
                                    .frame(width: 32, height: 32)
                                    .onTapGesture {
                                        onItemClicked(item)
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Observable
class ComposeInputViewModel {
    var text: String = ""
    var contentWarning: String = ""
    var enableCW = false
    var showEmoji = false
    var pollViewModel = PollViewModel()
    var mediaViewModel = MediaViewModel()
    var visibility: UiTimeline.ItemContentStatusTopEndContentVisibilityType = .public
    
    
    func showEmojiPanel() {
        showEmoji = true
    }
    func toggleCW() {
        enableCW = !enableCW
    }
    func togglePoll() {
        if pollViewModel.enabled {
            pollViewModel = PollViewModel()
        } else {
            pollViewModel.enabled = true
        }
    }
    func addEmoji(emoji: UiEmoji) {
//        text += " :" + emoji.shortcode + ": "
        showEmoji = false
    }
}


@Observable
class MediaViewModel {
    var selectedItems: [PhotosPickerItem] = []
    var items: [MediaItem] = []
    var sensitive = false
    func update() {
        if selectedItems.count > 4 {
            selectedItems = Array(selectedItems[(selectedItems.count - 4)...(selectedItems.count - 1)])
        } else {
            selectedItems = selectedItems
        }
        items = selectedItems.map { item in
            MediaItem(item: item)
        }
    }
    func remove(item: MediaItem) {
        if let index = items.firstIndex(of: item) {
            items.remove(at: index)
            selectedItems.remove(at: index)
        }
    }
}

@Observable
class MediaItem: Equatable {
    static func == (lhs: MediaItem, rhs: MediaItem) -> Bool {
        lhs.item == rhs.item
    }
    let item: PhotosPickerItem
    var image: UIImage?
    var data: Data?
    init(item: PhotosPickerItem) {
        self.item = item
        item.loadTransferable(type: Data.self) { result in
            do {
                if let data = try result.get() {
                    if let uiImage = UIImage(data: data) {
                        DispatchQueue.main.async {
                            self.data = data
                            self.image = uiImage
                        }
                    }
                }
            } catch {
            }
        }
    }
}

@Observable
class PollViewModel {
    var enabled = false
    var pollType = ComposePollType.single
    var choices: [PollChoice] = [PollChoice(), PollChoice()]
    var expired = ComposePollExpired.minutes5
    let allExpiration = [
        ComposePollExpired.minutes5,
        ComposePollExpired.minutes30,
        ComposePollExpired.hours1,
        ComposePollExpired.hours6,
        ComposePollExpired.hours12,
        ComposePollExpired.days1,
        ComposePollExpired.days3,
        ComposePollExpired.days7
    ]
    func add() {
        if choices.count < 4 {
            choices.append(PollChoice())
        }
    }
    func remove(choice: PollChoice) {
        if choices.count > 2 {
            choices.removeAll { value in
                value.id == choice.id
            }
        }
    }
}

@Observable
class PollChoice: Identifiable {
    var text = ""
}

enum ComposePollType {
    case single
    case multiple
}

enum ComposePollExpired: String {
    case minutes5
    case minutes30
    case hours1
    case hours6
    case hours12
    case days1
    case days3
    case days7
    var inWholeMilliseconds: Int64 {
        switch self {
        case .minutes5:
            return 5 * 60 * 1000
        case .minutes30:
            return 30 * 60 * 1000
        case .hours1:
            return 1 * 60 * 60 * 1000
        case .hours6:
            return 6 * 60 * 60 * 1000
        case .hours12:
            return 12 * 60 * 60 * 1000
        case .days1:
            return 24 * 60 * 60 * 1000
        case .days3:
            return 3 * 24 * 60 * 60 * 1000
        case .days7:
            return 7 * 24 * 60 * 60 * 1000
        }
    }
}


extension ComposeScreen {
    init(accountType: AccountType, composeStatus: ComposeStatus? = nil) {
        self.accountType = accountType
        self.composeStatus = composeStatus
        self._presenter = .init(wrappedValue: .init(presenter: ComposePresenter(accountType: accountType, status: composeStatus)))
    }
}
