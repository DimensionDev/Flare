import SwiftUI
import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI
import PhotosUI
import SwiftUIIntrospect
import SwiftUIBackports

final class ComposePrefill: Hashable {
    let id = UUID()
    let text: String
    let cursorPosition: Int
    let imageData: Data
    let fileName: String

    init(
        text: String,
        cursorPosition: Int,
        imageData: Data,
        fileName: String
    ) {
        self.text = text
        self.cursorPosition = cursorPosition
        self.imageData = imageData
        self.fileName = fileName
    }

    static func == (lhs: ComposePrefill, rhs: ComposePrefill) -> Bool {
        lhs.id == rhs.id
    }

    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}

struct ComposeScreen: View {
    @Environment(\.dismiss) var dismiss
    let accountType: AccountType?
    let composeStatus: ComposeStatus?
    let draftGroupId: String?
    let prefill: ComposePrefill?
    @FocusState private var keyboardFocused: Bool
    @FocusState private var cwKeyboardFocused: Bool
    @StateObject private var presenter: KotlinPresenter<ComposeState>
    @State private var viewModel = ComposeContentViewModel()
    @State private var mediaViewModel = MediaViewModel()
    @State private var uiTextView: UITextView?
    @State private var pendingCursor: Int?
    @State private var initialTextApplied = false
    @State private var prefillApplied = false
    @State private var defaultFocusRequested = false
    @State private var showDraftSheet = false
    @State private var showDraftConfirmation = false
    @State private var showAccountPicker = false

    var body: some View {
        VStack(
            spacing: 8
        ) {
            accountSelectionView
            ScrollView {
                VStack(
                    spacing: 8
                ) {
                    if viewModel.enableContentWarning {
                        TextField(text: $viewModel.contentWarning) {
                            Text("compose_cw_placeholder")
                        }
                        .textFieldStyle(.plain)
                        .textInputAutocapitalization(.sentences)
                        .keyboardType(.twitter)
                        .focused($cwKeyboardFocused)
                        Divider()
                    }
                    
                    TextField(text: $viewModel.text, axis: .vertical) {
                        Text("compose_placeholder")
                    }
                    .textInputAutocapitalization(.sentences)
                    .keyboardType(.twitter)
                    .introspect(.textField(axis: .vertical), on: .iOS(.v16, .v17, .v18, .v26, .v27)) { textField in
                        self.uiTextView = textField
                        applyCursorIfPossible()
                    }
                    .textFieldStyle(.plain)
                    .focused($keyboardFocused)
                    .onAppear {
                        requestDefaultFocus()
                    }
                    Spacer()
                    if mediaViewModel.items.count > 0 {
                        ScrollView(.horizontal) {
                            HStack {
                                ForEach(mediaViewModel.items) { item in
                                    ComposeMediaItemView(item: item, mediaViewModel: mediaViewModel)
                                }
                            }
                        }
                        StateView(state: presenter.state.composeConfig) { config in
                            if let media = config.media, media.canSensitive {
                                Toggle(isOn: $mediaViewModel.sensitive, label: {
                                    Text("compose_media_mark_sensitive")
                                })
                            }
                        }
                    }
                    if viewModel.pollViewModel.enabled {
                        ComposePollSection(
                            viewModel: viewModel.pollViewModel,
                            maxChoices: maxPollOptions
                        )
                    }
                    if let replyState = presenter.state.replyState,
                       case .success(let reply) = onEnum(of: replyState),
                       let content = reply.data.timelineContentPost {
                        ComposeReferenceStatusPreview(data: content)
                    }
                }
                .padding(.horizontal)
            }
            .scrollIndicators(.hidden)
            .safeAreaInset(edge: .bottom) {
                composeActionBar
                    .padding()
                    .backport
                    .glassEffect(.regular, in: .capsule, fallbackBackground: .regularMaterial)
                    .padding()
            }
            
        }
        .onAppear {
            applyPrefillIfNeeded()
        }
        .onChange(of: presenter.state.initialTextState) { _, newValue in
            guard !initialTextApplied else { return }
            if case .success(let initialText) = onEnum(of: newValue) {
                initialTextApplied = true
                let prefill = initialText.data.text
                viewModel.text = prefill
                pendingCursor = Int(initialText.data.cursorPosition)
                requestComposerFocus()
                applyCursorIfPossible()
            }
        }
        .onSuccessOf(of: presenter.state.composeConfig) { config in
            if let media = config.media {
                mediaViewModel.maxSize = Int(media.maxCount)
                mediaViewModel.enableAltText = media.altTextMaxLength > 0
                mediaViewModel.altTextMaxLength = Int(media.altTextMaxLength)
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
        .onChange(of: keyboardFocused) { _, isFocused in
            if isFocused {
                applyCursorIfPossible()
            }
        }
        .onChange(of: mediaViewModel.items.count) { _, newValue in
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
                        Text("Cancel")
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
                        Text("agent_chat_send")
                    } icon: {
                        Image(systemName: "paperplane.fill")
                    }
                }
                .disabled(!presenter.state.canSend)
            }
        }
    }

    private var composeActionBar: some View {
        ComposeActionBarContent(
            isPollEnabled: viewModel.pollViewModel.enabled,
            hasMedia: !mediaViewModel.items.isEmpty,
            canAddPoll: presenter.state.pollMaxOptions != nil,
            canUseContentWarning: presenter.state.contentWarningEnabled,
            visibility: composeVisibility,
            allVisibilities: composeVisibilities,
            emojiState: presenter.state.emojiState,
            isEmojiPresented: $viewModel.showEmoji,
            languageCodes: Array(presenter.state.languageCodes),
            selectedLanguages: $viewModel.languages,
            maxLanguageSelectionCount: languageMaxCount,
            remainingTextLength: remainingTextLength,
            onTogglePoll: {
                withAnimation {
                    viewModel.togglePoll()
                }
            },
            onToggleContentWarning: {
                withAnimation {
                    viewModel.toggleContentWarning()
                    if viewModel.enableContentWarning {
                        cwKeyboardFocused = true
                    } else {
                        keyboardFocused = true
                    }
                }
            },
            onSelectVisibility: setVisibility,
            onSelectEmoji: { item in
                viewModel.addEmoji(emoji: item)
                insert(item.insertText)
            }
        ) {
            composeMediaPicker
        }
    }

    @ViewBuilder
    private var composeMediaPicker: some View {
        if presenter.state.mediaEnabled {
            PhotosPicker(
                selection: Binding(get: {
                    mediaViewModel.selectedItems
                }, set: { value in
                    mediaViewModel.selectedItems = value
                    mediaViewModel.update()
                }),
                maxSelectionCount: mediaViewModel.maxSize,
                matching: .any(of: [.images, .videos, .livePhotos])
            ) {
                Image(fontAwesome: .image)
            }
        }
    }

    private var accountSelectionView: some View {
        let selected = successProfiles(from: presenter.state.selectedUsers)
        let accounts = successProfiles(from: presenter.state.accountUsers)

        return HStack {
            if !accounts.isEmpty {
                Button {
                    showAccountPicker.toggle()
                } label: {
                    HStack(spacing: 8) {
                        if !selected.isEmpty {
                            accountAvatarStack(users: selected)
                        }
                        Image(fontAwesome: .plus)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 14, height: 14)
                            .padding(8)
                            .background(Circle().fill(Color(.tertiarySystemBackground)))
                    }
                    .padding(.horizontal, 6)
                    .padding(.vertical, 4)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)
                .popover(isPresented: $showAccountPicker, arrowEdge: .top) {
                    accountPickerPopover(selected: selected, accounts: accounts)
                }
            }
            Spacer()
        }
        .padding(.horizontal)
    }

    private func accountPickerPopover(
        selected: [UiProfile],
        accounts: [UiProfile]
    ) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 4) {
                ForEach(accounts, id: \.accountPickerKey) { user in
                    accountPickerButton(
                        user: user,
                        isSelected: selected.containsAccount(user)
                    )
                }
            }
            .padding(8)
        }
        .frame(minWidth: 280, maxWidth: 340, maxHeight: 420)
        .presentationDetents([.medium, .large])
    }

    private func accountPickerButton(
        user: UiProfile,
        isSelected: Bool
    ) -> some View {
        Button {
            presenter.state.selectAccount(accountKey: user.key)
        } label: {
            HStack(spacing: 10) {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
                    .frame(width: 24)
                accountPickerRow(user: user)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 8)
            .background(
                isSelected ? Color.accentColor.opacity(0.12) : Color.clear,
                in: RoundedRectangle(cornerRadius: 8, style: .continuous)
            )
            .contentShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func successProfiles<T>(from state: UiState<T>) -> [UiProfile] {
        guard case .success(let success) = onEnum(of: state) else {
            return []
        }
        let items = (success.data as? NSArray)?.cast(UiState<UiProfile>.self) ?? []
        return items.compactMap { userState in
            guard case .success(let userSuccess) = onEnum(of: userState) else {
                return nil
            }
            return userSuccess.data
        }
    }

    private func accountAvatarStack(users: [UiProfile]) -> some View {
        HStack(spacing: -8) {
            ForEach(Array(users.enumerated()), id: \.element.accountPickerKey) { _, user in
                accountAvatar(user: user, size: 34)
            }
        }
    }

    private func accountAvatar(user: UiProfile, size: CGFloat) -> some View {
        ZStack(alignment: .bottomTrailing) {
            AvatarView(data: user.avatar?.url, customHeader: user.avatar?.customHeaders)
                .scaledToFit()
                .frame(width: size, height: size)
                .clipShape(Circle())
                .overlay(Circle().stroke(Color(.systemBackground), lineWidth: 2))
            Image(fontAwesome: user.platformIcon.fontAwesomeIcon)
                .resizable()
                .scaledToFit()
                .frame(width: size * 0.34, height: size * 0.34)
                .padding(3)
                .background(Color(.systemBackground))
                .clipShape(Circle())
                .shadow(color: Color.black.opacity(0.16), radius: 1, x: 0, y: 1)
        }
        .frame(width: size, height: size)
    }

    private func accountPickerRow(user: UiProfile) -> some View {
        Label {
            VStack(alignment: .leading, spacing: 2) {
                Text(user.handle.canonical)
                Text(user.key.host)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        } icon: {
            accountAvatar(user: user, size: 24)
        }
    }
    
    private func applyCursorIfPossible() {
        guard uiTextView != nil, pendingCursor != nil else { return }
        DispatchQueue.main.async {
            guard let textView = uiTextView, let pendingCursor else { return }
            let length = (textView.text as NSString).length
            let clamped = NSRange(location: max(0, min(pendingCursor, length)),
                                  length: 0)
            textView.selectedRange = clamped
            textView.scrollRangeToVisible(clamped)
            if textView.isFirstResponder {
                self.pendingCursor = nil
            }
        }
    }

    private func applyPrefillIfNeeded() {
        guard !prefillApplied, let prefill else { return }
        prefillApplied = true
        initialTextApplied = true
        viewModel.text = prefill.text
        mediaViewModel.setInitialImage(
            data: prefill.imageData,
            fileName: prefill.fileName
        )
        presenter.state.setText(value: prefill.text)
        presenter.state.setMediaSize(value: Int32(mediaViewModel.items.count))
        pendingCursor = prefill.cursorPosition
        requestComposerFocus()
        applyCursorIfPossible()
    }

    private func requestDefaultFocus() {
        guard !defaultFocusRequested else { return }
        defaultFocusRequested = true
        requestComposerFocus()
    }

    private func requestComposerFocus() {
        DispatchQueue.main.async {
            keyboardFocused = true
            applyCursorIfPossible()
        }
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
        viewModel.makeComposeData(
            visibility: getVisibility(),
            medias: getMedia(),
            sensitive: mediaViewModel.sensitive,
            composeStatus: presenter.state.composeStatus
        )
    }

    private var hasDraftContent: Bool {
        viewModel.hasDraftContent ||
        !mediaViewModel.items.isEmpty
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
        let result = viewModel.applyDraft(draft)
        mediaViewModel.sensitive = result.sensitive
        mediaViewModel.restore(draftMedias: draft.medias)
        setVisibility(result.visibility)
        requestComposerFocus()
    }
    
    private func getMedia() -> [ComposeData.Media] {
        return mediaViewModel.items.compactMap { item in
            guard let data = item.data else {
                return nil
            }
            return .init(
                file: .init(name: item.fileName, data: KotlinByteArray.from(data: data), type: item.type),
                altText: item.altText.isEmpty ? nil : item.altText
            )
        }
    }
    private func getVisibility() -> UiTimelineV2.PostVisibility {
        switch onEnum(of: presenter.state.visibilityState) {
            case .success(let success): return success.data.visibility
            default: return .public
        }
    }

    private func setVisibility(_ visibility: UiTimelineV2.PostVisibility) {
        if case .success(let visibilityState) = onEnum(of: presenter.state.visibilityState) {
            visibilityState.data.setVisibility(value: visibility)
        }
    }

    private var composeVisibility: UiTimelineV2.PostVisibility? {
        guard case .success(let visibilityState) = onEnum(of: presenter.state.visibilityState) else {
            return nil
        }
        return visibilityState.data.visibility
    }

    private var composeVisibilities: [UiTimelineV2.PostVisibility] {
        guard case .success(let visibilityState) = onEnum(of: presenter.state.visibilityState) else {
            return []
        }
        return Array(visibilityState.data.allVisibilities)
    }

    private var remainingTextLength: Int? {
        presenter.state.remainingLength.map { Int(truncating: $0) }
    }

    private var maxPollOptions: Int {
        presenter.state.pollMaxOptions.map { Int(truncating: $0) } ?? ComposePollViewModel.defaultMaxChoiceCount
    }

    private var languageMaxCount: Int {
        guard case .success(let config) = onEnum(of: presenter.state.composeConfig),
              let language = config.data.language else {
            return 1
        }
        return Int(language.maxCount)
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
    func setInitialImage(data: Data, fileName: String) {
        selectedItems = []
        items = MediaItem(data: data, fileName: fileName).map { [$0] } ?? []
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

    init?(data: Data, fileName: String) {
        guard let image = UIImage(data: data) else { return nil }
        self.item = nil
        self.id = UUID().uuidString
        self.fileName = fileName
        self.data = data
        self.image = image
        self.type = .image
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
    init(
        accountType: AccountType?,
        composeStatus: ComposeStatus? = nil,
        draftGroupId: String? = nil,
        prefill: ComposePrefill? = nil
    ) {
        self.accountType = accountType
        self.composeStatus = composeStatus
        self.draftGroupId = draftGroupId
        self.prefill = prefill
        self._presenter = .init(wrappedValue: .init(presenter: ComposePresenter(accountType: accountType, status: composeStatus, draftGroupId: draftGroupId)))
    }
}

private extension UiProfile {
    var accountPickerKey: String {
        "\(key.id)-\(key.host)"
    }

    var platformIcon: UiIcon {
        switch platformType {
        case .mastodon:
            return .mastodon
        case .misskey:
            return .misskey
        case .bluesky:
            return .bluesky
        case .nostr:
            return .nostr
        case .pixiv:
            return .pixiv
        case .fanbox:
            return .fanbox
        case .xQt:
            return .x
        case .vvo:
            return .weibo
        }
    }
}

private extension Array where Element == UiProfile {
    func containsAccount(_ user: UiProfile) -> Bool {
        contains { item in
            item.key.id == user.key.id && item.key.host == user.key.host
        }
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
                            Image(fontAwesome: .trash)
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
