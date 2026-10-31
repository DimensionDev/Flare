import SwiftUI
import KotlinSharedUI
import FlareAppleCore
import FlareAppleUI
import PhotosUI
import CoreTransferable
import SwiftUIIntrospect
import SwiftUIBackports
import UniformTypeIdentifiers

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
    @State private var presenter: KotlinPresenter<ComposeState>
    @State private var viewModel = ComposeContentViewModel()
    @State private var mediaViewModel = MediaViewModel()
    @State private var imageInput = ComposeImageInput()
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
                    
                    TextEditor(text: $viewModel.text)
                        .font(.body)
                        .scrollContentBackground(.hidden)
                        .textInputAutocapitalization(.sentences)
                        .keyboardType(.twitter)
                        .introspect(.textEditor, on: .iOS(.v16, .v17, .v18, .v26, .v27)) { textView in
                            self.uiTextView = textView
                            imageInput.receiveImage = { [weak mediaViewModel] in mediaViewModel?.importImage($0) }
                            imageInput.install(on: textView)
                            applyCursorIfPossible()
                        }
                        .frame(minHeight: 120)
                        .focused($keyboardFocused)
                        .overlay(alignment: .topLeading) {
                            if viewModel.text.isEmpty {
                                Text("compose_placeholder")
                                    .foregroundStyle(.tertiary)
                                    .padding(.horizontal, 5)
                                    .padding(.vertical, 8)
                                    .allowsHitTesting(false)
                            }
                        }
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
                        .onDrop(of: [.image, .text], isTargeted: nil) { providers in
                            mediaViewModel.importDrop(providers, insert: insertDroppedText)
                            return true
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
            let currentPresenter = presenter
            let content = viewModel
            mediaViewModel.imagesAllowed = { currentPresenter.state.mediaEnabled && !content.pollViewModel.enabled }
            mediaViewModel.onError = { error in
                SwiftInAppNotification.shared.onError(message: .compose, throwable: KotlinThrowable(message: error.localizedDescription))
            }
            imageInput.receiveImage = { [weak mediaViewModel] in mediaViewModel?.importImage($0) }
            if let uiTextView { imageInput.install(on: uiTextView) }
            applyPrefillIfNeeded()
        }
        .onDisappear {
            mediaViewModel.cancelImports()
            mediaViewModel.imagesAllowed = { false }
            imageInput.receiveImage = nil
            uiTextView?.pasteDelegate = nil
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
            mediaViewModel.maxSize = Int(config.media?.maxCount ?? 0)
            if let media = config.media {
                mediaViewModel.enableAltText = media.altTextMaxLength > 0
                mediaViewModel.altTextMaxLength = Int(media.altTextMaxLength)
            }
        }
        .onChange(of: presenter.state.loadedDraftState) { _, newValue in
            guard let newValue, case .success(let loadedDraft) = onEnum(of: newValue) else { return }
            applyDraft(loadedDraft.data)
            presenter.state.consumeLoadedDraft()
        }
        .onChange(of: viewModel.enableContentWarning ? viewModel.contentWarning : nil, initial: true) { _, newValue in
            presenter.state.setSpoilerText(value: newValue)
        }
        .onChange(of: viewModel.text) { oldValue, newValue in
            presenter.state.setText(value: newValue)
        }
        .onChange(of: keyboardFocused) { _, isFocused in
            if isFocused {
                applyCursorIfPossible()
            }
        }
        .onChange(of: mediaViewModel.items.map { $0.mimeType ?? "" }) { _, mimeTypes in
            presenter.state.setMediaMimeTypes(value: mimeTypes)
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
                .disabled(!presenter.state.canSend || mediaViewModel.items.contains { !$0.isReady })
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
                matching: .any(of: [.images, .videos, .livePhotos]),
                // Preserve original GIFs and videos; destination validation rejects unsupported formats.
                preferredItemEncoding: .current
            ) {
                Image(fontAwesome: .image)
            }
            .accessibilityLabel(
                Text(
                    String(
                        localized: "compose_add_media",
                        defaultValue: "Add image or video"
                    )
                )
            )
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
                .accessibilityLabel(
                    Text(
                        String(
                            localized: "compose_select_accounts",
                            defaultValue: "Select posting accounts"
                        )
                    )
                )
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
        .frame(idealWidth: 340, idealHeight: 420)
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
        .accessibilityValue(
            Text(isSelected ? String(localized: "selected") : String(localized: "not_selected"))
        )
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
            AvatarView(data: user.avatar)
                .scaledToFit()
                .frame(width: size, height: size)
                .clipShape(Circle())
                .overlay(Circle().stroke(Color(.systemBackground), lineWidth: 2))
            TabIcon(icon: user.platformIcon, size: size * 0.34)
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
        presenter.state.setMediaMimeTypes(value: mediaViewModel.items.map { $0.mimeType ?? "" })
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
    
    private func insertDroppedText(_ text: String) {
        guard let textView = uiTextView else {
            viewModel.text += text
            return
        }
        textView.unmarkText()
        textView.insertText(text)
        viewModel.text = textView.text
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
        !mediaViewModel.items.isEmpty || mediaViewModel.hasPendingImports
    }
    
    private func send() {
        mediaViewModel.dispatchWhenReady({ completed in
            presenter.state.send(data: getComposeData()) { completed($0.boolValue) }
        }) { dispatched in
            if dispatched { dismiss() }
        }
    }

    private func saveDraft(shouldDismiss: Bool = false) {
        mediaViewModel.dispatchWhenReady({ completed in
            presenter.state.saveDraft(data: getComposeData()) { completed($0.boolValue) }
        }) { dispatched in
            if dispatched && shouldDismiss { dismiss() }
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
            let file: FileItem
            if let source = item.file {
                file = .init(name: item.fileName, path: source.url.path, type: item.type, mimeType: item.mimeType)
            } else if let data = item.data {
                file = .init(name: item.fileName, data: KotlinByteArray.from(data: data), type: item.type, mimeType: item.mimeType)
            } else {
                return nil
            }
            return .init(file: file, altText: item.altText.isEmpty ? nil : item.altText)
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
        Group {
            if let image = item.image {
                Image(uiImage: image).resizable().scaledToFill()
            } else if let error = item.loadError {
                VStack {
                    Image(systemName: "exclamationmark.triangle")
                    Text(verbatim: error).font(.caption).lineLimit(3)
                }
            } else if item.isReady {
                Image(systemName: item.type == .video ? "video.fill" : "photo").font(.largeTitle)
            } else {
                ProgressView()
            }
        }
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
        .accessibilityLabel(Text(verbatim: mediaDescription))
    }

    private var mediaDescription: String {
        let trimmedAltText = item.altText.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedAltText.isEmpty {
            return trimmedAltText
        }
        if item.type == .video {
            return String(
                localized: "media_video_no_alt",
                defaultValue: "Video, no alternative text provided"
            )
        }
        return String(
            localized: "media_image_no_alt",
            defaultValue: "Image, no alternative text provided"
        )
    }
}
