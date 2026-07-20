import AppKit
import FlareAppleCore
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import SwiftUI
import SwiftUIIntrospect
import UniformTypeIdentifiers

struct MacComposeScreen: View {
    @Environment(\.dismiss) private var dismiss
    @FocusState private var textEditorFocused: Bool

    let request: MacComposeWindowRequest
    @StateObject private var presenter: KotlinPresenter<ComposeState>

    @State private var viewModel = ComposeContentViewModel()
    @State private var sensitive = false
    @State private var mediaItems: [MacComposeMediaItem] = []
    @State private var showFileImporter = false
    @State private var showEmojiPopover = false
    @State private var showAccountPopover = false
    @State private var showDraftBoxPopover = false
    @State private var initialTextApplied = false
    @State private var prefillApplied = false
    @State private var nsTextView: NSTextView?
    @State private var pendingCursor: Int?
    @State private var closeController = MacComposeWindowCloseController()
    @State private var mediaDropTargeted = false

    init(request: MacComposeWindowRequest) {
        self.request = request
        _presenter = .init(
            wrappedValue: .init(
                presenter: ComposePresenter(
                    accountType: request.accountType,
                    status: request.composeStatus,
                    draftGroupId: request.draftGroupId
                )
            )
        )
    }

    var body: some View {
        VStack(spacing: 0) {
            VStack(spacing: 0) {
                if viewModel.enableContentWarning {
                    TextField("compose_cw_placeholder", text: $viewModel.contentWarning)
                    Divider()
                }
            }
            .background {
                GeometryReader { proxy in
                    Color.clear.preference(
                        key: MacComposeWindowLayoutPreferenceKey.self,
                        value: MacComposeWindowLayoutMetrics(
                            dynamicContentHeight: proxy.size.height
                        )
                    )
                }
            }
            
            TextEditor(text: $viewModel.text)
                .font(.body)
                .scrollDisabled(true)
                .onDrop(of: Self.mediaTransferTypes, isTargeted: $mediaDropTargeted) { providers in
                    importMediaProviders(providers)
                }
                .introspect(.textEditor, on: .macOS(.v13, .v14, .v15, .v26, .v27)) { textView in
                    nsTextView = textView
                    applyCursorIfPossible()
                }
                .focused($textEditorFocused)
                .frame(maxWidth: .infinity, minHeight: 60, maxHeight: .infinity)
                .background {
                    GeometryReader { proxy in
                        Color.clear.preference(
                            key: MacComposeWindowLayoutPreferenceKey.self,
                            value: MacComposeWindowLayoutMetrics(
                                editorHeight: proxy.size.height
                            )
                        )
                    }
                }

            VStack(spacing: 0) {
                if !mediaItems.isEmpty {
                    mediaSection
                }

                if viewModel.pollViewModel.enabled {
                    pollSection
                }

                referencePostSection
            }
            .background {
                GeometryReader { proxy in
                    Color.clear.preference(
                        key: MacComposeWindowLayoutPreferenceKey.self,
                        value: MacComposeWindowLayoutMetrics(
                            dynamicContentHeight: proxy.size.height
                        )
                    )
                }
            }
            Divider()

            bottomBar
        }
        .frame(minWidth: 400, idealWidth: 440, minHeight: 160)
        .navigationTitle(windowTitle)
        .toolbar {
            ToolbarItem(placement: .navigation) {
                accountToolbarButton
            }

            if presenter.state.showDraft {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showDraftBoxPopover.toggle()
                    } label: {
                        Label {
                            Text("Drafts")
                        } icon: {
                            Image(fontAwesome: .inbox)
                        }
                    }
                    .popover(isPresented: $showDraftBoxPopover, arrowEdge: .top) {
                        draftBoxPopover
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
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.image, .movie, .data],
            allowsMultipleSelection: true,
            onCompletion: importFiles
        )
        .onDrop(of: Self.mediaTransferTypes, isTargeted: $mediaDropTargeted) { providers in
            importMediaProviders(providers)
        }
        .onPasteCommand(of: Self.mediaTransferTypes) { providers in
            _ = importMediaProviders(providers)
        }
        .overlay {
            mediaDropOverlay
        }
        .background {
            MacComposeMediaInputBridge(
                canAcceptMedia: canAcceptMediaInput,
                onPasteMedia: pasteMediaFromPasteboard
            )
        }
        .background {
            MacComposeWindowCloseInterceptor(
                closeController: closeController,
                hasDraftContent: {
                    hasDraftContent
                },
                onSaveDraft: {
                    saveDraft(shouldDismiss: true)
                },
                onCloseWithoutSaving: {
                    dismissComposeWindow()
                }
            )
        }
        .onPreferenceChange(MacComposeWindowLayoutPreferenceKey.self) { metrics in
            closeController.updateLayoutMetrics(metrics)
        }
        .onAppear {
            applyPrefillIfNeeded()
            presenter.state.setText(value: viewModel.text)
            presenter.state.setMediaSize(value: Int32(mediaItems.count))
        }
        .onChange(of: viewModel.text) { _, newValue in
            presenter.state.setText(value: newValue)
        }
        .onChange(of: mediaItems.count) { _, newValue in
            presenter.state.setMediaSize(value: Int32(newValue))
        }
        .onChange(of: presenter.state.initialTextState) { _, newValue in
            guard !initialTextApplied else { return }
            if case .success(let initialText) = onEnum(of: newValue) {
                initialTextApplied = true
                viewModel.text = initialText.data.text
                pendingCursor = Int(initialText.data.cursorPosition)
                requestComposerFocus()
                applyCursorIfPossible()
            }
        }
        .onChange(of: presenter.state.loadedDraftState) { _, newValue in
            guard let newValue, case .success(let loadedDraft) = onEnum(of: newValue) else { return }
            applyDraft(loadedDraft.data)
            presenter.state.consumeLoadedDraft()
        }
        .onSuccessOf(of: presenter.state.composeConfig) { config in
            if let media = config.media {
                mediaItems = Array(mediaItems.prefix(Int(media.maxCount)))
            }
        }
    }

    private static let mediaTransferTypes: [UTType] = [
        .fileURL,
        .image,
        .movie,
        .png,
        .jpeg,
        .tiff,
        .gif,
        .heic,
        .mpeg4Movie,
        .quickTimeMovie,
    ]

    private var windowTitle: LocalizedStringKey {
        switch onEnum(of: presenter.state.composeStatus) {
        case .none:
            "compose_title_new"
        case .quote:
            "compose_title_quote"
        case .reply:
            "compose_title_reply"
        }
    }

    @ViewBuilder
    private var accountToolbarButton: some View {
        let selected = successProfiles(from: presenter.state.selectedUsers)
        let accounts = successProfiles(from: presenter.state.accountUsers)

        if accounts.isEmpty {
            ProgressView()
                .controlSize(.small)
        } else {
            Button {
                showAccountPopover.toggle()
            } label: {
                HStack(spacing: 6) {
                    if selected.isEmpty {
                        Image(systemName: "person.crop.circle.badge.plus")
                            .font(.title3)
                            .foregroundStyle(.secondary)
                    } else {
                        MacComposeAccountAvatarStack(users: selected, size: 26)
                    }

                    Image(systemName: "chevron.down")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .help(selected.isEmpty ? String(localized: "deep_link_account_picker_title") : selected.map { $0.handle.canonical }.joined(separator: ", "))
            .popover(isPresented: $showAccountPopover, arrowEdge: .top) {
                MacComposeAccountPopover(
                    accounts: accounts,
                    selected: selected,
                    onToggle: { account in
                        presenter.state.selectAccount(accountKey: account.key)
                    }
                )
            }
        }
    }

    private var mediaSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            ScrollView(.horizontal, content: {
                LazyHStack(spacing: 8) {
                    ForEach(mediaItems) { item in
                        MacComposeMediaTile(
                            item: item,
                            onAltTextChange: { value in
                                updateAltText(for: item.id, value: value)
                            },
                            onRemove: {
                                removeMedia(item)
                            }
                        )
                    }
                }
                .padding(.horizontal)
            })
            .frame(height: 75)
            HStack {
                if presenter.state.mediaCanSensitive {
                    Toggle("compose_media_mark_sensitive", isOn: $sensitive)
                        .toggleStyle(.checkbox)
                }
            }
        }
    }

    private var pollSection: some View {
        ComposePollSection(viewModel: viewModel.pollViewModel, maxChoices: maxPollOptions)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
    }

    @ViewBuilder
    private var referencePostSection: some View {
        if let replyState = presenter.state.replyState,
           case .success(let reply) = onEnum(of: replyState),
           let content = reply.data.timelineContentPost {
            ComposeReferenceStatusPreview(data: content)
        }
    }

    private var bottomBar: some View {
        ComposeActionBarContent(
            isPollEnabled: viewModel.pollViewModel.enabled,
            hasMedia: !mediaItems.isEmpty,
            canAddPoll: presenter.state.pollMaxOptions != nil,
            canUseContentWarning: presenter.state.contentWarningEnabled,
            visibility: composeVisibility,
            allVisibilities: composeVisibilities,
            emojiState: presenter.state.emojiState,
            isEmojiPresented: $showEmojiPopover,
            languageCodes: Array(presenter.state.languageCodes),
            selectedLanguages: $viewModel.languages,
            maxLanguageSelectionCount: languageMaxCount,
            remainingTextLength: remainingTextLength,
            onTogglePoll: {
                viewModel.togglePoll()
                if viewModel.pollViewModel.enabled {
                    mediaItems = []
                }
            },
            onToggleContentWarning: {
                viewModel.toggleContentWarning()
            },
            onSelectVisibility: setVisibility,
            onSelectEmoji: { emoji in
                viewModel.text += emoji.insertText
                showEmojiPopover = false
            }
        ) {
            Button {
                showFileImporter = true
            } label: {
                Label {
                    Text("macos_compose_add_media")
                } icon: {
                    Image(fontAwesome: .image)
                }
            }
            .disabled(!canAcceptMediaInput)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    @ViewBuilder
    private var mediaDropOverlay: some View {
        if mediaDropTargeted {
            let canAccept = canAcceptMediaInput
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(Color.accentColor.opacity(canAccept ? 0.08 : 0.03))
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .strokeBorder(
                        canAccept ? Color.accentColor : Color.secondary,
                        style: StrokeStyle(lineWidth: 2, dash: [7, 5])
                    )
                Image(systemName: canAccept ? "photo.badge.plus" : "nosign")
                    .font(.system(size: 32, weight: .semibold))
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(canAccept ? Color.accentColor : Color.secondary)
            }
            .padding(8)
            .allowsHitTesting(false)
        }
    }

    private var hasDraftContent: Bool {
        viewModel.hasDraftContent ||
        !mediaItems.isEmpty
    }

    private var canAcceptMediaInput: Bool {
        presenter.state.mediaEnabled &&
        remainingMediaSlots > 0
    }

    private var remainingMediaSlots: Int {
        max(Int(presenter.state.mediaMaxCount) - mediaItems.count, 0)
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

    private func close() {
        if hasDraftContent {
            closeController.presentCloseConfirmation(
                onSaveDraft: {
                    saveDraft(shouldDismiss: true)
                },
                onCloseWithoutSaving: {
                    dismissComposeWindow()
                }
            )
        } else {
            dismissComposeWindow()
        }
    }

    private func send() {
        presenter.state.send(data: composeData) { dispatched in
            if dispatched.boolValue {
                dismissComposeWindow()
            }
        }
    }

    private func saveDraft(shouldDismiss: Bool) {
        presenter.state.saveDraft(data: composeData) { dispatched in
            guard dispatched.boolValue else { return }
            if shouldDismiss {
                dismissComposeWindow()
            }
        }
    }

    private func dismissComposeWindow() {
        closeController.closeWindow {
            dismiss()
        }
    }

    private var composeData: ComposeData {
        viewModel.makeComposeData(
            visibility: currentVisibility,
            medias: mediaItems.map(\.composeMedia),
            sensitive: sensitive,
            composeStatus: presenter.state.composeStatus
        )
    }

    private var draftBoxPopover: some View {
        NavigationStack {
            DraftBoxScreen { groupId in
                presenter.state.loadDraft(groupId: groupId)
                showDraftBoxPopover = false
            }
        }
        .frame(width: 380, height: 480)
    }

    private var currentVisibility: UiTimelineV2.PostVisibility {
        switch onEnum(of: presenter.state.visibilityState) {
        case .success(let success):
            success.data.visibility
        default:
            .public
        }
    }

    private func importFiles(_ result: Result<[URL], Error>) {
        guard case .success(let urls) = result else { return }
        appendMediaItems(urls.compactMap(MacComposeMediaItem.init(url:)))
    }

    private func importMediaProviders(_ providers: [NSItemProvider]) -> Bool {
        guard canAcceptMediaInput, !providers.isEmpty else {
            return false
        }

        MacComposeMediaItem.loadItems(
            from: providers,
            maxCount: remainingMediaSlots
        ) { items in
            appendMediaItems(items)
        }
        return true
    }

    private func pasteMediaFromPasteboard() -> Bool {
        guard canAcceptMediaInput else {
            return false
        }

        let items = MacComposeMediaItem.loadItems(
            from: .general,
            maxCount: remainingMediaSlots
        )
        appendMediaItems(items)
        return !items.isEmpty
    }

    private func appendMediaItems(_ items: [MacComposeMediaItem]) {
        guard presenter.state.mediaEnabled, remainingMediaSlots > 0 else {
            return
        }

        let accepted = Array(items.prefix(remainingMediaSlots))
        guard !accepted.isEmpty else {
            return
        }

        viewModel.pollViewModel.reset()
        mediaItems.append(contentsOf: accepted)
    }

    private func removeMedia(_ item: MacComposeMediaItem) {
        mediaItems.removeAll { $0.id == item.id }
    }

    private func updateAltText(for id: UUID, value: String) {
        guard let index = mediaItems.firstIndex(where: { $0.id == id }) else { return }
        mediaItems[index].altText = value
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

    private func applyPrefillIfNeeded() {
        guard !prefillApplied, let prefill = request.prefill else { return }
        prefillApplied = true
        initialTextApplied = true
        viewModel.text = prefill.text
        mediaItems = MacComposeMediaItem(
            data: prefill.imageData,
            fileName: prefill.fileName,
            contentType: .png
        ).map { [$0] } ?? []
        pendingCursor = prefill.cursorPosition
        requestComposerFocus()
        applyCursorIfPossible()
    }

    private func applyCursorIfPossible(retryCount: Int = 0) {
        guard nsTextView != nil, pendingCursor != nil else { return }
        DispatchQueue.main.async {
            guard let textView = nsTextView, let pendingCursor else { return }
            if textView.string != viewModel.text {
                guard retryCount >= 3 else {
                    applyCursorIfPossible(retryCount: retryCount + 1)
                    return
                }
                textView.string = viewModel.text
            }

            guard textView.string == viewModel.text else {
                return
            }

            let length = (textView.string as NSString).length
            let clamped = NSRange(
                location: max(0, min(pendingCursor, length)),
                length: 0
            )
            textView.setSelectedRange(clamped)
            textView.scrollRangeToVisible(clamped)
            self.pendingCursor = nil
        }
    }

    private func requestComposerFocus() {
        DispatchQueue.main.async {
            textEditorFocused = true
            nsTextView?.window?.makeFirstResponder(nsTextView)
            applyCursorIfPossible()
        }
    }

    private func applyDraft(_ draft: UiDraft) {
        let result = viewModel.applyDraft(draft)
        sensitive = result.sensitive
        mediaItems = draft.medias.compactMap(MacComposeMediaItem.init(draftMedia:))
        setVisibility(result.visibility)
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
}

private struct MacComposeAccountPopover: View {
    let accounts: [UiProfile]
    let selected: [UiProfile]
    let onToggle: (UiProfile) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ForEach(accounts, id: \.accountPickerKey) { account in
                MacComposeAccountRow(
                    account: account,
                    isSelected: selected.containsAccount(account),
                    onToggle: {
                        onToggle(account)
                    }
                )
            }
        }
        .padding(8)
        .frame(width: 300)
    }
}

private struct MacComposeAccountRow: View {
    let account: UiProfile
    let isSelected: Bool
    let onToggle: () -> Void

    var body: some View {
        Toggle(
            isOn: Binding(
                get: { isSelected },
                set: { _ in onToggle() }
            )
        ) {
            HStack(spacing: 10) {
                MacComposeAccountAvatar(user: account, size: 28)

                VStack(alignment: .leading, spacing: 2) {
                    Text(account.handle.canonical)
                        .lineLimit(1)
                    Text(account.key.host)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .toggleStyle(.checkbox)
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(
            isSelected ? Color.accentColor.opacity(0.12) : Color.clear,
            in: RoundedRectangle(cornerRadius: 6, style: .continuous)
        )
    }
}

private struct MacComposeAccountAvatarStack: View {
    let users: [UiProfile]
    let size: CGFloat

    var body: some View {
        HStack(spacing: -size * 0.32) {
            ForEach(Array(users.enumerated()), id: \.element.accountPickerKey) { _, user in
                MacComposeAccountAvatar(user: user, size: size)
            }
        }
    }
}

private struct MacComposeAccountAvatar: View {
    let user: UiProfile
    let size: CGFloat

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            AvatarView(data: user.avatar?.url, customHeader: user.avatar?.customHeaders)
                .scaledToFit()
                .frame(width: size, height: size)
                .clipShape(Circle())
                .overlay(Circle().stroke(Color(nsColor: .windowBackgroundColor), lineWidth: 2))

            Image(fontAwesome: user.platformIcon.fontAwesomeIcon)
                .resizable()
                .scaledToFit()
                .frame(width: size * 0.34, height: size * 0.34)
                .padding(max(2, size * 0.08))
                .background(Color(nsColor: .windowBackgroundColor))
                .clipShape(Circle())
                .shadow(color: Color.black.opacity(0.16), radius: 1, x: 0, y: 1)
        }
        .frame(width: size, height: size)
    }
}

private struct MacComposeMediaTile: View {
    let item: MacComposeMediaItem
    @State private var showPopOver = false
    let onAltTextChange: (String) -> Void
    let onRemove: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let image = item.image {
                Image(nsImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                VStack(spacing: 6) {
                    Image(systemName: item.type == .video ? "film" : "doc")
                        .font(.title2)
                    Text(item.fileName)
                        .font(.caption)
                        .lineLimit(1)
                }
                .foregroundStyle(.secondary)
                .padding(8)
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .contextMenu {
            Button {
                showPopOver = true
            } label: {
                Label {
                    Text("macos_compose_alt_text")
                } icon: {
                    EmptyView()
                }
            }
            
            Button(role: .destructive) {
                onRemove()
            } label: {
                Label {
                    Text("delete")
                } icon: {
                    Image(fontAwesome: .trash)
                }
            }
        }
        .popover(isPresented: $showPopOver) {
            TextField(
                "macos_compose_alt_text",
                text: Binding(
                    get: { item.altText },
                    set: { value in onAltTextChange(value) }
                )
            )
            .padding()
            .frame(minWidth: 300)
            .textFieldStyle(.roundedBorder)
        }
    }
}

private struct MacComposeMediaItem: Identifiable {
    let id = UUID()
    let fileName: String
    let data: Data
    let type: FileType
    var altText = ""

    init?(data: Data, fileName: String?, contentType: UTType?) {
        guard !data.isEmpty else {
            return nil
        }

        self.fileName = Self.normalizedFileName(fileName, contentType: contentType)
        self.data = data
        self.type = Self.fileType(for: contentType)
    }

    init?(url: URL) {
        let didStartAccessing = url.startAccessingSecurityScopedResource()
        defer {
            if didStartAccessing {
                url.stopAccessingSecurityScopedResource()
            }
        }

        guard let data = try? Data(contentsOf: url) else {
            return nil
        }
        self.fileName = url.lastPathComponent
        self.data = data
        self.type = MacComposeMediaItem.fileType(for: url)
    }

    static func loadItems(
        from providers: [NSItemProvider],
        maxCount: Int,
        completion: @escaping ([MacComposeMediaItem]) -> Void
    ) {
        guard maxCount > 0 else {
            completion([])
            return
        }

        guard !providers.isEmpty else {
            completion([])
            return
        }

        let group = DispatchGroup()
        let lock = NSLock()
        var results = Array<MacComposeMediaItem?>(repeating: nil, count: providers.count)

        for (index, provider) in providers.enumerated() {
            group.enter()
            provider.loadMacComposeMediaItem { item in
                lock.lock()
                results[index] = item
                lock.unlock()
                group.leave()
            }
        }

        group.notify(queue: .main) {
            completion(Array(results.compactMap { $0 }.prefix(maxCount)))
        }
    }

    static func loadItems(
        from pasteboard: NSPasteboard,
        maxCount: Int
    ) -> [MacComposeMediaItem] {
        guard maxCount > 0 else {
            return []
        }

        let fileURLItems = pasteboard.fileURLs.compactMap(MacComposeMediaItem.init(url:))
        if !fileURLItems.isEmpty {
            return Array(fileURLItems.prefix(maxCount))
        }

        let dataItems =
            pasteboard
                .pasteboardItems?
                .compactMap(MacComposeMediaItem.init(pasteboardItem:)) ?? []
        return Array(dataItems.prefix(maxCount))
    }

    init?(draftMedia: UiDraftMedia) {
        let fileURL = URL(fileURLWithPath: draftMedia.cachePath)
        guard let data = try? Data(contentsOf: fileURL) else {
            return nil
        }
        self.fileName = draftMedia.fileName ?? fileURL.lastPathComponent
        self.data = data
        self.altText = draftMedia.altText ?? ""
        switch draftMedia.type {
        case .image:
            self.type = .image
        case .video:
            self.type = .video
        default:
            self.type = .other
        }
    }

    var image: NSImage? {
        guard type == .image else { return nil }
        return NSImage(data: data)
    }

    var composeMedia: ComposeData.Media {
        ComposeData.Media(
            file: .init(name: fileName, data: KotlinByteArray.from(data: data), type: type),
            altText: altText.isEmpty ? nil : altText
        )
    }

    private static func fileType(for url: URL) -> FileType {
        let resourceContentType = (try? url.resourceValues(forKeys: [.contentTypeKey]))?.contentType
        let contentType = resourceContentType ?? UTType(filenameExtension: url.pathExtension)
        return fileType(for: contentType)
    }

    private static func fileType(for contentType: UTType?) -> FileType {
        if contentType?.conforms(to: .image) == true {
            return .image
        }
        if contentType?.conforms(to: .movie) == true {
            return .video
        }
        return .other
    }

    private static func normalizedFileName(_ fileName: String?, contentType: UTType?) -> String {
        let fallbackExtension = contentType?.preferredFilenameExtension ?? "dat"
        let fallbackName = "pasted-media-\(UUID().uuidString).\(fallbackExtension)"
        let trimmed = fileName?.trimmingCharacters(in: .whitespacesAndNewlines)
        let lastPathComponent = trimmed.map { URL(fileURLWithPath: $0).lastPathComponent }
        let candidate = lastPathComponent?.isEmpty == false ? lastPathComponent! : fallbackName

        guard URL(fileURLWithPath: candidate).pathExtension.isEmpty,
              let preferredExtension = contentType?.preferredFilenameExtension else {
            return candidate
        }
        return "\(candidate).\(preferredExtension)"
    }
}

private extension MacComposeMediaItem {
    init?(pasteboardItem: NSPasteboardItem) {
        guard let contentType = pasteboardItem.firstMediaContentType,
              let data = pasteboardItem.data(forType: NSPasteboard.PasteboardType(contentType.identifier)) else {
            return nil
        }

        self.init(
            data: data,
            fileName: nil,
            contentType: contentType
        )
    }
}

private extension NSPasteboard {
    var fileURLs: [URL] {
        let objects =
            readObjects(
                forClasses: [NSURL.self],
                options: [.urlReadingFileURLsOnly: true]
            ) ?? []

        return objects.compactMap { object in
            switch object {
            case let url as URL:
                url.isFileURL ? url : nil
            case let url as NSURL:
                (url as URL).isFileURL ? url as URL : nil
            default:
                nil
            }
        }
    }
}

private extension NSPasteboardItem {
    var firstMediaContentType: UTType? {
        let mediaTypes =
            types
                .compactMap { UTType($0.rawValue) }
                .filter(\.isComposeMediaContentType)

        return mediaTypes.first(where: { !$0.isAbstractComposeMediaContentType }) ?? mediaTypes.first
    }
}

private extension UTType {
    var isComposeMediaContentType: Bool {
        conforms(to: .image) || conforms(to: .movie)
    }

    var isAbstractComposeMediaContentType: Bool {
        self == .image || self == .movie
    }
}

private extension NSItemProvider {
    func loadMacComposeMediaItem(completion: @escaping (MacComposeMediaItem?) -> Void) {
        if hasItemConformingToTypeIdentifier(UTType.fileURL.identifier) {
            loadFileURL { url in
                if let url,
                   let item = MacComposeMediaItem(url: url) {
                    completion(item)
                } else {
                    self.loadMediaData(completion: completion)
                }
            }
        } else {
            loadMediaData(completion: completion)
        }
    }

    private func loadFileURL(completion: @escaping (URL?) -> Void) {
        loadItem(forTypeIdentifier: UTType.fileURL.identifier, options: nil) { item, _ in
            switch item {
            case let url as URL:
                completion(url.isFileURL ? url : nil)
            case let url as NSURL:
                let swiftURL = url as URL
                completion(swiftURL.isFileURL ? swiftURL : nil)
            case let data as Data:
                completion(URL(dataRepresentation: data, relativeTo: nil))
            case let data as NSData:
                completion(URL(dataRepresentation: data as Data, relativeTo: nil))
            case let string as String:
                completion(Self.fileURL(from: string))
            case let string as NSString:
                completion(Self.fileURL(from: string as String))
            default:
                completion(nil)
            }
        }
    }

    private func loadMediaData(completion: @escaping (MacComposeMediaItem?) -> Void) {
        guard let contentType = firstRegisteredMediaContentType else {
            completion(nil)
            return
        }

        loadDataRepresentation(forTypeIdentifier: contentType.identifier) { data, _ in
            guard let data,
                  let item =
                    MacComposeMediaItem(
                        data: data,
                        fileName: self.suggestedName,
                        contentType: contentType
                    ) else {
                completion(nil)
                return
            }
            completion(item)
        }
    }

    private var firstRegisteredMediaContentType: UTType? {
        let mediaTypes =
            registeredTypeIdentifiers
            .compactMap(UTType.init)
            .filter(\.isComposeMediaContentType)

        return mediaTypes.first(where: { !$0.isAbstractComposeMediaContentType }) ?? mediaTypes.first
    }

    private static func fileURL(from value: String) -> URL? {
        if let url = URL(string: value), url.isFileURL {
            return url
        }
        return URL(fileURLWithPath: value)
    }
}

private struct MacComposeMediaInputBridge: NSViewRepresentable {
    let canAcceptMedia: Bool
    let onPasteMedia: () -> Bool

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeNSView(context: Context) -> NSView {
        let view = NSView()
        context.coordinator.attach(to: view)
        updateCoordinator(context.coordinator)
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        context.coordinator.attach(to: nsView)
        updateCoordinator(context.coordinator)
    }

    static func dismantleNSView(_ nsView: NSView, coordinator: Coordinator) {
        coordinator.detach()
    }

    private func updateCoordinator(_ coordinator: Coordinator) {
        coordinator.canAcceptMedia = canAcceptMedia
        coordinator.onPasteMedia = onPasteMedia
    }

    final class Coordinator {
        var canAcceptMedia = false
        var onPasteMedia: () -> Bool = { false }
        private weak var view: NSView?
        private var eventMonitor: Any?

        func attach(to view: NSView) {
            self.view = view
            guard eventMonitor == nil else {
                return
            }

            eventMonitor =
                NSEvent.addLocalMonitorForEvents(matching: .keyDown) { [weak self] event in
                    guard let self,
                          self.isPasteShortcut(event),
                          self.isEventInComposeWindow(event),
                          self.canAcceptMedia,
                          self.onPasteMedia() else {
                        return event
                    }
                    return nil
                }
        }

        func detach() {
            if let eventMonitor {
                NSEvent.removeMonitor(eventMonitor)
            }
            eventMonitor = nil
            view = nil
        }

        private func isPasteShortcut(_ event: NSEvent) -> Bool {
            let flags = event.modifierFlags.intersection(.deviceIndependentFlagsMask)
            return flags == .command &&
            event.charactersIgnoringModifiers?.lowercased() == "v"
        }

        private func isEventInComposeWindow(_ event: NSEvent) -> Bool {
            guard let window = view?.window else {
                return false
            }

            if let eventWindow = event.window {
                return eventWindow === window
            }
            return NSApp.keyWindow === window
        }
    }
}

private struct MacComposeWindowLayoutMetrics: Equatable {
    var editorHeight: CGFloat?
    var dynamicContentHeight: CGFloat?

    init(
        editorHeight: CGFloat? = nil,
        dynamicContentHeight: CGFloat? = nil
    ) {
        self.editorHeight = editorHeight
        self.dynamicContentHeight = dynamicContentHeight
    }
}

private struct MacComposeWindowLayoutPreferenceKey: PreferenceKey {
    static let defaultValue = MacComposeWindowLayoutMetrics()

    static func reduce(
        value: inout MacComposeWindowLayoutMetrics,
        nextValue: () -> MacComposeWindowLayoutMetrics
    ) {
        let next = nextValue()
        if let editorHeight = next.editorHeight {
            value.editorHeight = editorHeight
        }
        if let dynamicContentHeight = next.dynamicContentHeight {
            value.dynamicContentHeight = (value.dynamicContentHeight ?? 0) + dynamicContentHeight
        }
    }
}

@MainActor
private final class MacComposeWindowCloseController {
    private weak var window: NSWindow?
    var allowsWindowClose = false
    private var isPresentingCloseConfirmation = false
    private var layoutMetrics: MacComposeWindowLayoutMetrics?
    private var pendingHeightAdjustment: CGFloat = 0
    private var heightAdjustmentScheduled = false

    func attach(to window: NSWindow) {
        self.window = window
        scheduleHeightAdjustment()
    }

    func detach(from window: NSWindow?) {
        guard self.window === window else { return }
        self.window = nil
    }

    func updateLayoutMetrics(_ metrics: MacComposeWindowLayoutMetrics) {
        guard let editorHeight = metrics.editorHeight,
              let dynamicContentHeight = metrics.dynamicContentHeight,
              editorHeight.isFinite,
              dynamicContentHeight.isFinite else {
            return
        }

        defer {
            layoutMetrics = metrics
        }

        guard let previous = layoutMetrics,
              let previousEditorHeight = previous.editorHeight,
              let previousDynamicContentHeight = previous.dynamicContentHeight,
              abs(dynamicContentHeight - previousDynamicContentHeight) > 0.5 else {
            return
        }

        let editorHeightDelta = previousEditorHeight - editorHeight
        guard abs(editorHeightDelta) > 0.5 else { return }

        pendingHeightAdjustment += editorHeightDelta
        scheduleHeightAdjustment()
    }

    func closeWindow(fallback: () -> Void) {
        allowsWindowClose = true
        if let window {
            window.close()
        } else {
            fallback()
        }
    }

    func presentCloseConfirmation(
        onSaveDraft: @escaping () -> Void,
        onCloseWithoutSaving: @escaping () -> Void
    ) {
        guard !isPresentingCloseConfirmation,
              let window else {
            return
        }

        isPresentingCloseConfirmation = true
        let alert = NSAlert()
        alert.messageText = String(localized: "compose_save_draft_prompt")
        alert.alertStyle = .warning
        alert.addButton(withTitle: String(localized: "Save Draft"))
        alert.addButton(withTitle: String(localized: "Cancel"))
        alert.beginSheetModal(for: window) { [weak self] response in
            self?.isPresentingCloseConfirmation = false
            switch response {
            case .alertFirstButtonReturn:
                onSaveDraft()
            case .alertSecondButtonReturn:
                onCloseWithoutSaving()
            default:
                break
            }
        }
    }

    private func scheduleHeightAdjustment() {
        guard !heightAdjustmentScheduled,
              abs(pendingHeightAdjustment) > 0.5 else {
            return
        }

        heightAdjustmentScheduled = true
        DispatchQueue.main.async { [weak self] in
            self?.applyPendingHeightAdjustment()
        }
    }

    private func applyPendingHeightAdjustment() {
        heightAdjustmentScheduled = false
        guard let window else { return }

        let adjustment = pendingHeightAdjustment
        pendingHeightAdjustment = 0

        let currentFrame = window.frame
        var targetHeight = currentFrame.height + adjustment
        targetHeight = max(targetHeight, window.minSize.height)
        targetHeight = min(targetHeight, window.maxSize.height)

        if adjustment > 0,
           let visibleFrame = window.screen?.visibleFrame {
            let availableHeight = currentFrame.maxY - visibleFrame.minY
            targetHeight = min(targetHeight, availableHeight)
        }

        guard abs(targetHeight - currentFrame.height) > 0.5 else { return }

        var targetFrame = currentFrame
        targetFrame.origin.y = currentFrame.maxY - targetHeight
        targetFrame.size.height = targetHeight
        window.setFrame(targetFrame, display: true, animate: true)
    }
}

private struct MacComposeWindowCloseInterceptor: NSViewRepresentable {
    let closeController: MacComposeWindowCloseController
    let hasDraftContent: () -> Bool
    let onSaveDraft: () -> Void
    let onCloseWithoutSaving: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(closeController: closeController)
    }

    func makeNSView(context: Context) -> NSView {
        let view = NSView()
        updateCoordinator(context.coordinator)
        DispatchQueue.main.async {
            context.coordinator.attach(to: view.window)
        }
        return view
    }

    func updateNSView(_ nsView: NSView, context: Context) {
        updateCoordinator(context.coordinator)
        DispatchQueue.main.async {
            context.coordinator.attach(to: nsView.window)
        }
    }

    static func dismantleNSView(_ nsView: NSView, coordinator: Coordinator) {
        coordinator.detach()
    }

    private func updateCoordinator(_ coordinator: Coordinator) {
        coordinator.hasDraftContent = hasDraftContent
        coordinator.onSaveDraft = onSaveDraft
        coordinator.onCloseWithoutSaving = onCloseWithoutSaving
    }

    final class Coordinator: NSObject, NSWindowDelegate {
        let closeController: MacComposeWindowCloseController
        var hasDraftContent: () -> Bool = { false }
        var onSaveDraft: () -> Void = {}
        var onCloseWithoutSaving: () -> Void = {}
        private weak var window: NSWindow?
        private weak var previousDelegate: (any NSWindowDelegate)?

        init(closeController: MacComposeWindowCloseController) {
            self.closeController = closeController
        }

        func attach(to window: NSWindow?) {
            guard let window, self.window !== window else {
                return
            }
            detach()
            self.window = window
            closeController.attach(to: window)
            previousDelegate = window.delegate
            window.delegate = self
        }

        func detach() {
            if window?.delegate === self {
                window?.delegate = previousDelegate
            }
            closeController.detach(from: window)
            window = nil
            previousDelegate = nil
        }

        func windowShouldClose(_ sender: NSWindow) -> Bool {
            if closeController.allowsWindowClose || !hasDraftContent() {
                return true
            }
            closeController.presentCloseConfirmation(
                onSaveDraft: onSaveDraft,
                onCloseWithoutSaving: onCloseWithoutSaving
            )
            return false
        }
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
