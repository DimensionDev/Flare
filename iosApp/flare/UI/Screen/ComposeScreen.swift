import SwiftUI
import KotlinSharedUI
import PhotosUI
import SwiftUIIntrospect
import SwiftUIBackports

struct ComposeScreen: View {
    @Environment(\.dismiss) var dismiss
    let accountType: AccountType?
    let composeStatus: ComposeStatus?
    let draftGroupId: String?
    @FocusState private var keyboardFocused: Bool
    @FocusState private var cwKeyboardFocused: Bool
    @StateObject private var presenter: KotlinPresenter<ComposeState>
    @State private var viewModel = ComposeInputViewModel()
    @State private var uiTextView: UITextView?
    @State private var pendingCursor: Int?
    @State private var showDraftSheet = false
    @State private var showDraftConfirmation = false

    var body: some View {
        VStack(
            spacing: 8
        ) {
            accountSelectionView
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
                                ForEach(viewModel.mediaViewModel.items) { item in
                                    ComposeMediaItemView(item: item, mediaViewModel: viewModel.mediaViewModel)
                                }
                            }
                        }
                        StateView(state: presenter.state.composeConfig) { config in
                            if let media = config.media, media.canSensitive {
                                Toggle(isOn: $viewModel.mediaViewModel.sensitive, label: {
                                    Text("compose_media_mark_sensitive")
                                })
                            }
                        }
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
                       let content = reply.data as? UiTimelineV2.Post {
                        StatusView(data: content, isQuote: true, showMedia: false, forceHideActions: true)
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
                                StateView(state: presenter.state.composeConfig) { config in
                                    if config.media != nil {
                                        PhotosPicker(
                                            selection: Binding(get: {
                                                viewModel.mediaViewModel.selectedItems
                                            }, set: { value in
                                                viewModel.mediaViewModel.selectedItems = value
                                                viewModel.mediaViewModel.update()
                                            }),
                                            maxSelectionCount: viewModel.mediaViewModel.maxSize,
                                            matching: .any(of: [.images, .videos, .livePhotos])) {
                                            Image("fa-image")
                                        }
                                    }
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
                                        } label: {
                                            Label {
                                                Text(visibility.title)
                                            } icon: {
                                                StatusVisibilityView(data: visibility)
                                            }
                                            Text(visibility.desc)
                                        }
                                    }
                                } label: {
                                    StatusVisibilityView(data: visibilityState.visibility)
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
                                    NavigationStack {
                                        EmojiPopup(data: emojis) { item in
                                            viewModel.addEmoji(emoji: item)
                                            insert(item.insertText)
                                        }
                                        .toolbar {
                                            ToolbarItem(placement: .cancellationAction) {
                                                Button(
                                                    role: .cancel
                                                ) {
                                                    viewModel.showEmoji = false
                                                } label: {
                                                    Label {
                                                        Text("Cancel")
                                                    } icon: {
                                                        Image("fa-xmark")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            StateView(state: presenter.state.composeConfig) { config in
                                if let languageConfig = config.language {
                                    Menu {
                                        ForEach(languageConfig.sortedIsoCodes, id: \.self) { code in
                                            Button {
                                                if languageConfig.maxCount > 1 {
                                                    if viewModel.languages.contains(code) {
                                                        if viewModel.languages.count > 1 {
                                                            viewModel.languages.removeAll { $0 == code }
                                                        }
                                                    } else {
                                                        if viewModel.languages.count < languageConfig.maxCount {
                                                            viewModel.languages.append(code)
                                                        }
                                                    }
                                                } else {
                                                    viewModel.languages = [code]
                                                }
                                            } label: {
                                                HStack {
                                                    Text(Locale.current.localizedString(forLanguageCode: code) ?? code)
                                                    if viewModel.languages.contains(code) {
                                                        Image(systemName: "checkmark")
                                                    }
                                                }
                                            }
                                        }
                                    } label: {
                                        if let first = viewModel.languages.first, viewModel.languages.count == 1 {
                                             Text(first.uppercased())
                                                .font(.caption)
                                                .bold()
                                                .padding(4)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 4)
                                                        .stroke(Color.primary, lineWidth: 1)
                                                )
                                        } else {
                                            Image(systemName: "globe")
                                        }
                                    }
                                }
                            }
                        }
                        .font(.title)
                        .buttonStyle(.plain)
                    }
                    .scrollIndicators(.hidden)
                    Spacer()
                    StateView(state: presenter.state.composeConfig) { config in
                        if let maxLength = config.text?.maxLength {
                            Text("\(viewModel.text.count)/\(maxLength)")
                                .foregroundStyle(viewModel.text.count > maxLength ? .red : .gray)
                        }
                    }
                }
                .padding()
                .backport
                .glassEffect(.regular, in: .capsule, fallbackBackground: .regularMaterial)
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
        .onSuccessOf(of: presenter.state.composeConfig) { config in
            if let media = config.media {
                viewModel.mediaViewModel.maxSize = Int(media.maxCount)
                viewModel.mediaViewModel.enableAltText = media.altTextMaxLength > 0
                viewModel.mediaViewModel.altTextMaxLength = Int(media.altTextMaxLength)
            }
        }
        .onChange(of: presenter.state.loadedDraftState) { _, newValue in
            guard let newValue, case .success(let loadedDraft) = onEnum(of: newValue) else { return }
            applyDraft(loadedDraft.data)
            presenter.state.consumeLoadedDraft()
        }
        .onChange(of: viewModel.text) { oldValue, newValue in
            presenter.state.setText(value: newValue)
        }
        .onChange(of: viewModel.mediaViewModel.items.count) { _, newValue in
            presenter.state.setMediaSize(value: Int32(newValue))
        }
        .sheet(isPresented: $showDraftSheet) {
            draftSheet
        }
        .toolbarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                switch onEnum(of: presenter.state.composeStatus) {
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
                    if hasDraftContent {
                        showDraftConfirmation = true
                    } else {
                        dismiss()
                    }
                } label: {
                    Label {
                        Text("compose_button_cancel")
                    } icon: {
                        Image(systemName: "xmark")
                    }
                }
                .confirmationDialog("Draft", isPresented: $showDraftConfirmation, titleVisibility: .visible) {
                    if #available(iOS 26.0, *) {
                        Button("Save Draft", role: .confirm) {
                            saveDraft(shouldDismiss: true)
                        }
                    } else {
                        Button("Save Draft") {
                            saveDraft(shouldDismiss: true)
                        }
                    }
                    Button("Cancel", role: .cancel) {
                        dismiss()
                    }
                } message: {
                    Text("Save your current content as a draft before leaving?")
                }
            }
            if presenter.state.showDraft {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showDraftSheet = true
                    } label: {
                        Text("Drafts")
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
                .disabled(!presenter.state.canSend)
            }
        }
    }

    private var accountSelectionView: some View {
        ScrollView(.horizontal) {
            HStack {
                StateView(state: presenter.state.selectedUsers) { users in
                    let items = (users as NSArray).cast(UiState<UiProfile>.self)
                    ForEach(Array(items.enumerated()), id: \.offset) { _, userState in
                        StateView(state: userState) { user in
                            Label {
                                Text(user.handle.canonical)
                            } icon: {
                                AvatarView(data: user.avatar)
                                    .scaledToFit()
                                    .frame(width: 20, height: 20)
                            }
                            .padding(.horizontal)
                            .padding(.vertical, 8)
                            .background(Color(.secondarySystemBackground))
                            .clipShape(.rect(cornerRadius: 16))
                            .onTapGesture {
                                presenter.state.selectAccount(accountKey: user.key)
                            }
                        }
                    }
                }
                StateView(state: presenter.state.otherUsers) { others in
                    let items = (others as NSArray).cast(UiState<UiProfile>.self)
                    if !items.isEmpty {
                        Menu {
                            ForEach(Array(items.enumerated()), id: \.offset) { _, userState in
                                StateView(state: userState) { user in
                                    Button {
                                        presenter.state.selectAccount(accountKey: user.key)
                                    } label: {
                                        Label {
                                            Text(user.handle.canonical)
                                        } icon: {
                                            AvatarView(data: user.avatar)
                                                .scaledToFit()
                                                .frame(maxWidth: 20, maxHeight: 20)
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
    
    private func getComposeData() -> ComposeData {
        ComposeData(
            content: viewModel.text,
            visibility: getVisibility(),
            language: viewModel.languages,
            medias: getMedia(),
            sensitive: viewModel.mediaViewModel.sensitive,
            spoilerText: viewModel.contentWarning,
            poll: getPoll(),
            localOnly: false,
            referenceStatus: getReferenceStatus()
        )
    }

    private var hasDraftContent: Bool {
        !viewModel.text.isEmpty ||
        !viewModel.contentWarning.isEmpty ||
        !viewModel.mediaViewModel.items.isEmpty ||
        viewModel.pollViewModel.enabled
    }
    
    private func send() {
        let data = getComposeData()
        presenter.state.send(data: data) { dispatched in
            if dispatched.boolValue {
                dismiss()
            }
        }
    }

    private func saveDraft(shouldDismiss: Bool = false) {
        presenter.state.saveDraft(data: getComposeData()) { dispatched in
            guard dispatched.boolValue else { return }
            if shouldDismiss {
                dismiss()
            }
        }
    }

    private func applyDraft(_ draft: UiDraft) {
        let data = draft.data
        viewModel.text = data.content
        viewModel.contentWarning = data.spoilerText ?? ""
        viewModel.enableCW = !(data.spoilerText ?? "").isEmpty
        if !data.language.isEmpty {
            viewModel.languages = data.language
        }
        viewModel.mediaViewModel.sensitive = data.sensitive
        viewModel.mediaViewModel.restore(draftMedias: draft.medias)
        viewModel.apply(poll: data.poll)
        if case .success(let visibilityState) = onEnum(of: presenter.state.visibilityState) {
            visibilityState.data.setVisibility(value: data.visibility)
        }
    }
    
    private func getMedia() -> [ComposeData.Media] {
        return viewModel.mediaViewModel.items.map { item in
                .init(file: .init(name: item.fileName, data: KotlinByteArray.from(data: item.data!), type: item.type), altText: item.altText.isEmpty ? nil : item.altText)
        }
    }
    private func getReferenceStatus() -> ComposeData.ReferenceStatus? {
        return if let data = presenter.state.composeStatus {
            ComposeData.ReferenceStatus(composeStatus: data)
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
    private func getVisibility() -> UiTimelineV2.PostVisibility {
        switch onEnum(of: presenter.state.visibilityState) {
            case .success(let success): return success.data.visibility
            default: return .public
        }
    }

    private var draftSheet: some View {
        NavigationStack {
            DraftBoxScreen { groupId in
                presenter.state.loadDraft(groupId: groupId)
                showDraftSheet = false
            }
        }
        .presentationDetents([.medium, .large])
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
//    var visibility: UiTimeline.ItemContentStatusTopEndContentVisibilityType = .public
    var languages: [String] = {
        if let code = Locale.current.language.languageCode?.identifier {
            return [code]
        }
        return ["en"]
    }()
    
    
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
    func apply(poll: ComposeData.Poll?) {
        guard let poll else {
            pollViewModel = PollViewModel()
            return
        }

        pollViewModel.enabled = true
        pollViewModel.pollType = poll.multiple ? .multiple : .single
        pollViewModel.expired = ComposePollExpired(milliseconds: poll.expiredAfter) ?? .minutes5
        pollViewModel.choices = poll.options.map(PollChoice.init)
        while pollViewModel.choices.count < 2 {
            pollViewModel.choices.append(PollChoice())
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
    var maxSize = 4
    var enableAltText = true
    var altTextMaxLength = 500
    func update() {
        if selectedItems.count > maxSize {
            selectedItems = Array(selectedItems[(selectedItems.count - 4)...(selectedItems.count - 1)])
        } else {
            selectedItems = selectedItems
        }
        items = selectedItems.map { item in
            MediaItem(item: item)
        }
    }
    func restore(draftMedias: [UiDraftMedia]) {
        selectedItems = []
        items = draftMedias.compactMap(MediaItem.init(draftMedia:))
    }
    func remove(item: MediaItem) {
        if let index = items.firstIndex(of: item) {
            items.remove(at: index)
            if selectedItems.indices.contains(index) {
                selectedItems.remove(at: index)
            }
        }
    }
}

@Observable
class MediaItem: Equatable, Identifiable {
    static func == (lhs: MediaItem, rhs: MediaItem) -> Bool {
        lhs.id == rhs.id
    }
    let item: PhotosPickerItem?
    var image: UIImage?
    var data: Data?
    var altText: String = ""
    let id: String
    let fileName: String
    var type: FileType = .other
    
    init(item: PhotosPickerItem) {
        self.item = item
        self.id = item.itemIdentifier ?? UUID().uuidString
        self.fileName = item.itemIdentifier ?? UUID().uuidString
        
        if let contentType = item.supportedContentTypes.first {
            if contentType.conforms(to: .image) {
                self.type = .image
            } else if contentType.conforms(to: .movie) {
                self.type = .video
            }
        }
        
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

    init?(draftMedia: UiDraftMedia) {
        let fileURL = URL(fileURLWithPath: draftMedia.cachePath)
        guard let data = try? Data(contentsOf: fileURL) else {
            return nil
        }

        self.item = nil
        self.id = draftMedia.cachePath
        self.fileName = draftMedia.fileName ?? fileURL.lastPathComponent
        self.data = data
        self.altText = draftMedia.altText ?? ""

        switch draftMedia.type {
        case .image:
            self.type = .image
            self.image = UIImage(data: data)
        case .video:
            self.type = .video
        default:
            self.type = .other
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

    init(text: String = "") {
        self.text = text
    }
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
    init?(milliseconds: Int64) {
        switch milliseconds {
        case 5 * 60 * 1000:
            self = .minutes5
        case 30 * 60 * 1000:
            self = .minutes30
        case 1 * 60 * 60 * 1000:
            self = .hours1
        case 6 * 60 * 60 * 1000:
            self = .hours6
        case 12 * 60 * 60 * 1000:
            self = .hours12
        case 24 * 60 * 60 * 1000:
            self = .days1
        case 3 * 24 * 60 * 60 * 1000:
            self = .days3
        case 7 * 24 * 60 * 60 * 1000:
            self = .days7
        default:
            return nil
        }
    }
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

extension UiDraftStatus {
    fileprivate var title: String {
        switch self {
        case .draft:
            return "Draft"
        case .failed:
            return "Failed"
        case .sending:
            return "Sending"
        default:
            return "Draft"
        }
    }

    fileprivate var tint: Color {
        switch self {
        case .draft:
            return .secondary
        case .failed:
            return .red
        case .sending:
            return .orange
        default:
            return .secondary
        }
    }
}


extension ComposeScreen {
    init(accountType: AccountType?, composeStatus: ComposeStatus? = nil, draftGroupId: String? = nil) {
        self.accountType = accountType
        self.composeStatus = composeStatus
        self.draftGroupId = draftGroupId
        self._presenter = .init(wrappedValue: .init(presenter: ComposePresenter(accountType: accountType, status: composeStatus, draftGroupId: draftGroupId)))
    }
}

struct ComposeMediaItemView: View {
    let item: MediaItem
    var mediaViewModel: MediaViewModel
    @State private var showAltTextEditor = false
    
    var body: some View {
        if let image = item.image {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .frame(width: 128, height: 128)
                .cornerRadius(8)
                .overlay(alignment: .bottomLeading) {
                    if mediaViewModel.enableAltText && !item.altText.isEmpty {
                        Text("ALT")
                            .font(.caption2)
                            .bold()
                            .foregroundStyle(.black)
                            .padding(.horizontal, 4)
                            .padding(.vertical, 2)
                            .background(.white.opacity(0.8))
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                            .padding(4)
                    }
                }
                .onTapGesture {
                    if mediaViewModel.enableAltText {
                        showAltTextEditor = true
                    }
                }
                .contextMenu {
                    Button(action: {
                        withAnimation {
                            mediaViewModel.remove(item: item)
                        }
                    }, label: {
                        Label {
                            Text("delete")
                        } icon: {
                            Image("fa-trash")
                        }
                    })
                    
                    if mediaViewModel.enableAltText {
                        Button {
                            showAltTextEditor = true
                        } label: {
                            Label("Edit Description", systemImage: "pencil")
                        }
                    }
                }
                .sheet(isPresented: $showAltTextEditor) {
                    AltTextEditSheet(item: item, maxLength: mediaViewModel.altTextMaxLength)
                }
        }
    }
}

extension UiTimelineV2.PostVisibility {
    var title: LocalizedStringResource {
        switch self {
        case .public:
            return LocalizedStringResource("status_visibility_public")
        case .home:
            return LocalizedStringResource("status_visibility_home")
        case .followers:
            return LocalizedStringResource("status_visibility_followers")
        case .specified:
            return LocalizedStringResource("status_visibility_specified")
        case .channel:
            return LocalizedStringResource("status_visibility_public")
        }
    }
    var desc: LocalizedStringResource {
        switch self {
        case .public:
            return LocalizedStringResource("status_visibility_public_description")
        case .home:
            return LocalizedStringResource("status_visibility_home_description")
        case .followers:
            return LocalizedStringResource("status_visibility_followers_description")
        case .specified:
            return LocalizedStringResource("status_visibility_specified_description")
        case .channel:
            return LocalizedStringResource("status_visibility_public_description")
        }
    }
}
