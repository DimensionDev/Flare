import SwiftUI
import KotlinSharedUI
import PhotosUI
internal import Kingfisher
internal import SwiftUIIntrospect
internal import SwiftUIBackports

public struct ComposeContent<StatusContent: View>: View {
    public let dismiss: () -> Void
    public let composeStatus: ComposeStatus?
    public let state: ComposeState
    public let itemProvider: [NSItemProvider]?
    @ViewBuilder public let statusContent: (UiTimelineV2.Post) -> StatusContent
    @FocusState private var keyboardFocused: Bool
    @FocusState private var cwKeyboardFocused: Bool
    @State private var viewModel = ComposeInputViewModel()
    @State private var uiTextView: UITextView?
    @State private var pendingCursor: Int?
    @State private var showDraftConfirmation = false
    
    public init(
        itemProvider: [NSItemProvider]? = nil,
        composeStatus: ComposeStatus? = nil,
        state: ComposeState,
        dismiss: @escaping () -> Void,
        @ViewBuilder statusContent: @escaping (UiTimelineV2.Post) -> StatusContent
    ) {
        self.itemProvider = itemProvider
        self.composeStatus = composeStatus
        self.state = state
        self.statusContent = statusContent
        self.dismiss = dismiss
    }

    public var body: some View {
        VStack(
            spacing: 8
        ) {
            accountSelectionView
            composeBody
                .safeAreaInset(edge: .bottom) {
                    bottomBar
                }
                .onChange(of: state.loadedDraftState) { _, newValue in
                    if case .success(let loadedDraft) = onEnum(of: newValue) {
                        applyDraft(loadedDraft.data)
                        state.consumeLoadedDraft()
                    }
                }
        }
        .onChange(of: state.initialTextState) { _, newValue in
            if case .success(let initialText) = onEnum(of: newValue) {
                viewModel.text = initialText.data.text
                pendingCursor = Int(initialText.data.cursorPosition)
                applyCursorIfPossible()
            }
        }
        .onChange(of: state.composeConfig) { _, newValue in
            if case .success(let config) = onEnum(of: newValue), let media = config.data.media {
                viewModel.mediaViewModel.maxSize = Int(media.maxCount)
                viewModel.mediaViewModel.enableAltText = media.altTextMaxLength > 0
                viewModel.mediaViewModel.altTextMaxLength = Int(media.altTextMaxLength)
            }
        }
        .onChange(of: viewModel.text) { oldValue, newValue in
            state.setText(value: newValue)
        }
        .onChange(of: viewModel.mediaViewModel.items.count) { _, newValue in
            state.setMediaSize(value: Int32(newValue))
        }
        .toolbarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                switch onEnum(of: state.composeStatus) {
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
                .disabled(!state.canSend)
            }
        }
    }
    
    private var composeBody: some View {
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
                    if case .success(let config) = onEnum(of: state.composeConfig), let media = config.data.media, media.canSensitive {
                        Toggle(isOn: $viewModel.mediaViewModel.sensitive, label: {
                            Text("compose_media_mark_sensitive")
                        })
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
                if let replyState = state.replyState,
                   case .success(let reply) = onEnum(of: replyState),
                   let content = reply.data as? UiTimelineV2.Post {
                    statusContent(content)
                }
            }
            .padding(.horizontal)
        }
        .scrollIndicators(.hidden)
        .task {
            if let itemProvider {
                for item in itemProvider {
                    if item.hasItemConformingToTypeIdentifier(UTType.image.identifier) {
                        if let data = try? await item.loadFileRepresentation(forTypeIdentifier: UTType.image.identifier) {
                            viewModel.mediaViewModel.update(urls: [data])
                        }
                    } else if item.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                        if let url = try? await item.loadItem(forTypeIdentifier: UTType.url.identifier) as? URL {
                            viewModel.text = url.absoluteString
                        }
                    } else if item.hasItemConformingToTypeIdentifier(UTType.text.identifier) {
                        if let text = try? await item.loadItem(forTypeIdentifier: UTType.text.identifier) as? String {
                            viewModel.text = text
                        }
                    }
                }
            }
        }
    }
    
    private var bottomBar: some View {
        HStack {
            ScrollView(.horizontal) {
                HStack(
                    spacing: 16,
                ) {
                    if !viewModel.pollViewModel.enabled, case .success(let config) = onEnum(of: state.composeConfig), config.data.media != nil {
                        PhotosPicker(
                            selection: $viewModel.mediaViewModel.selectedItems,
                            maxSelectionCount: viewModel.mediaViewModel.maxSize,
                            matching: .any(of: [.images, .videos, .livePhotos])
                        ) {
                            Image("fa-image")
                        }
                        .onChange(of: viewModel.mediaViewModel.selectedItems) { oldValue, newValue in
                            viewModel.mediaViewModel.update(selectedItems: newValue)
                        }
                    }
                    if viewModel.mediaViewModel.selectedItems.count == 0, case .success(let config) = onEnum(of: state.composeConfig), config.data.poll != nil {
                        Button(action: {
                            withAnimation {
                                viewModel.togglePoll()
                            }
                        }, label: {
                            Image("fa-square-poll-horizontal")
                        })
                    }
                    if case .success(let visibilityState) = onEnum(of: state.visibilityState), visibilityState.data.allVisibilities.count > 1 {
                        Menu {
                            ForEach(visibilityState.data.allVisibilities, id: \.self) { visibility in
                                Button {
                                    visibilityState.data.setVisibility(value: visibility)
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
                            StatusVisibilityView(data: visibilityState.data.visibility)
                        }
                    }
                    if case .success(let config) = onEnum(of: state.composeConfig), config.data.contentWarning != nil {
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
                    if case .success(let emojiState) = onEnum(of: state.emojiState), emojiState.data.size > 0 {
                        Button(action: {
                            viewModel.showEmojiPanel()
                        }, label: {
                            Image("fa-face-smile")
                        })
                        .popover(isPresented: $viewModel.showEmoji) {
                            NavigationStack {
                                EmojiPopup(data: emojiState.data) { item in
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
                    
                    if case .success(let config) = onEnum(of: state.composeConfig), let languageConfig = config.data.language {
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
                .font(.title)
                .buttonStyle(.plain)
            }
            .scrollIndicators(.hidden)
            Spacer()
            if case .success(let config) = onEnum(of: state.composeConfig), let maxLength = config.data.text?.maxLength {
                Text("\(viewModel.text.count)/\(maxLength)")
                    .foregroundStyle(viewModel.text.count > maxLength ? .red : .gray)
            }
        }
        .padding()
        .backport
        .glassEffect(.regular, in: .capsule, fallbackBackground: .regularMaterial)
        .padding()
    }

    private var accountSelectionView: some View {
        ScrollView(.horizontal) {
            HStack {
                if case .success(let users) = onEnum(of: state.selectedUsers) {
                    ForEach(Array(users.data.enumerated()), id: \.offset) { element in
                        if let userState = element.element as? UiState<UiProfile>, case .success(let userData) = onEnum(of: userState) {
                            let user = userData.data
                            Label {
                                Text(user.handle.canonical)
                            } icon: {
                                KFImage(URL(string: user.avatar))
                                    .resizable()
                                    .scaledToFit()
                                    .frame(width: 20, height: 20)
                            }
                            .padding(.horizontal)
                            .padding(.vertical, 8)
                            .background(Color(.secondarySystemBackground))
                            .clipShape(.rect(cornerRadius: 16))
                            .onTapGesture {
                                state.selectAccount(accountKey: user.key)
                            }
                        }
                    }
                }
                if case .success(let others) = onEnum(of: state.otherUsers) {
                    Menu {
                        ForEach(Array(others.data.enumerated()), id: \.offset) { element in
                            if let userState = element.element as? UiState<UiProfile>, case .success(let userData) = onEnum(of: userState) {
                                let user = userData.data
                                Button {
                                    state.selectAccount(accountKey: user.key)
                                } label: {
                                    Label {
                                        Text(user.handle.canonical)
                                    } icon: {
                                        KFImage(URL(string: user.avatar))
                                            .resizable()
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
        state.send(data: data) { dispatched in
            if dispatched.boolValue {
                dismiss()
            }
        }
    }

    private func saveDraft(shouldDismiss: Bool = false) {
        state.saveDraft(data: getComposeData()) { dispatched in
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
        if case .success(let visibilityState) = onEnum(of: state.visibilityState) {
            visibilityState.data.setVisibility(value: data.visibility)
        }
    }
    
    private func getMedia() -> [ComposeData.Media] {
        return viewModel.mediaViewModel.items.compactMap { item in
            return .init(
                file: .init(name: item.fileName, data: KotlinByteArray.from(data: item.getData()!), type: item.type),
                altText: item.altText.isEmpty ? nil : item.altText
            )
        }
    }
    private func getReferenceStatus() -> ComposeData.ReferenceStatus? {
        return if let data = state.composeStatus {
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
        switch onEnum(of: state.visibilityState) {
            case .success(let success): return success.data.visibility
            default: return .public
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
    func update(selectedItems: [PhotosPickerItem]) {
        items.append(contentsOf: selectedItems.filter { item in
            !self.items.contains { $0.id == item.itemIdentifier }
        }.map { item in
            MediaItem(item: item)
        })
        items = Array(items.prefix(maxSize))
    }
    func update(urls: [URL]) {
        items.append(contentsOf: urls.map { url in
            MediaItem(url: url)
        })
        items = Array(items.prefix(maxSize))
    }
    func update(image: UIImage) {
        items.append(contentsOf: [MediaItem(image: image)])
        items = Array(items.prefix(maxSize))
    }
    func restore(draftMedias: [UiDraftMedia]) {
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
    
    var url: URL?
    var image: UIImage?
    private var data: Data?
    var altText: String = ""
    let id: String
    let fileName: String
    var type: FileType = .other
    
    func getData() -> Data? {
        if let data {
            return data
        } else if let url, let newData = try? Data(contentsOf: url) {
            return newData
        } else if let image, let newData = image.jpegData(compressionQuality: 0.8) {
            return newData
        }
        return nil
    }
    
    init(image: UIImage) {
        self.id = UUID().uuidString
        self.fileName = "image_\(id).jpg"
        self.type = .image
        self.data = nil
        self.image = image
        self.url = nil
    }
    
    init(url: URL) {
        self.id = url.absoluteString
        self.fileName = url.lastPathComponent
        self.type = url.pathExtension.lowercased() == "mp4" ? .video : .image
        self.data = nil
        self.image = nil
        self.url = url
//        if let data = try? Data(contentsOf: url) {
//            self.data = data
//            if let uiImage = UIImage(data: data) {
//                self.image = uiImage
//            }
//        }
    }
    
    init(item: PhotosPickerItem) {
//        self.item = item
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

struct ComposeMediaItemView: View {
    let item: MediaItem
    var mediaViewModel: MediaViewModel
    @State private var showAltTextEditor = false
    
    var body: some View {
        if let url = item.url {
            KFImage(url)
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
        } else if let image = item.image {
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

extension KotlinByteArray {
    static func from(data: Data) -> KotlinByteArray {
        let swiftByteArray = [UInt8](data)
        return swiftByteArray
            .map(Int8.init(bitPattern:))
            .enumerated()
            .reduce(into: KotlinByteArray(size: Int32(swiftByteArray.count))) { result, row in
                result.set(index: Int32(row.offset), value: row.element)
        }
    }
}

extension NSItemProvider {
  func loadFileRepresentation(forTypeIdentifier typeIdentifier: String) async throws -> URL {
    try await withCheckedThrowingContinuation { continuation in
      self.loadFileRepresentation(forTypeIdentifier: typeIdentifier) { url, error in
        if let error = error {
          return continuation.resume(throwing: error)
        }
        
        guard let url = url else {
          return continuation.resume(throwing: NSError())
        }
        
        let localURL = FileManager.default.temporaryDirectory.appendingPathComponent(url.lastPathComponent)
        try? FileManager.default.removeItem(at: localURL)

        do {
          try FileManager.default.copyItem(at: url, to: localURL)
        } catch {
          return continuation.resume(throwing: error)
        }
        
        continuation.resume(returning: localURL)
      }.resume()
    }
  }
}
