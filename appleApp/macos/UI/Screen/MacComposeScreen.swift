import AppKit
import AppleFontAwesome
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
    @State private var nsTextView: NSTextView?
    @State private var pendingCursor: Int?
    @State private var closeController = MacComposeWindowCloseController()

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
            if viewModel.enableContentWarning {
                TextField("compose_cw_placeholder", text: $viewModel.contentWarning)
                Divider()
            }
            
            TextEditor(text: $viewModel.text)
                .font(.body)
                .scrollDisabled(true)
                .introspect(.textEditor, on: .macOS(.v13, .v14, .v15, .v26, .v27)) { textView in
                    nsTextView = textView
                    applyCursorIfPossible()
                }
                .focused($textEditorFocused)
                .frame(maxWidth: .infinity, minHeight: 60, maxHeight: .infinity)
            
            if !mediaItems.isEmpty {
                mediaSection
            }

            if viewModel.pollViewModel.enabled {
                pollSection
            }

            referencePostSection
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
                        Text("compose_button_send")
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
        .onAppear {
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
            .help(selected.isEmpty ? String(localized: "macos_compose_select_account") : selected.map { $0.handle.canonical }.joined(separator: ", "))
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
            HStack {
                Text("macos_compose_media")
                    .font(.headline)

                Spacer()

                if presenter.state.mediaCanSensitive {
                    Toggle("compose_media_mark_sensitive", isOn: $sensitive)
                        .toggleStyle(.checkbox)
                }
            }

            LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 10)], spacing: 10) {
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
           let content = reply.data as? UiTimelineV2.Post {
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
            textCount: viewModel.text.count,
            maxTextLength: textMaxLength,
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
            .disabled(!presenter.state.mediaEnabled || mediaItems.count >= Int(presenter.state.mediaMaxCount))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private var hasDraftContent: Bool {
        viewModel.hasDraftContent ||
        !mediaItems.isEmpty
    }

    private var textMaxLength: Int? {
        presenter.state.textMaxLength.map { Int(truncating: $0) }
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
        let remaining = max(Int(presenter.state.mediaMaxCount) - mediaItems.count, 0)
        let items = urls.prefix(remaining).compactMap(MacComposeMediaItem.init(url:))
        if !items.isEmpty {
            viewModel.pollViewModel.reset()
            mediaItems.append(contentsOf: items)
        }
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
    let onAltTextChange: (String) -> Void
    let onRemove: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(nsColor: .controlBackgroundColor))

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
            .frame(height: 96)
            .clipShape(RoundedRectangle(cornerRadius: 8))
            .overlay(alignment: .topTrailing) {
                Button(action: onRemove) {
                    Image(systemName: "xmark.circle.fill")
                        .symbolRenderingMode(.hierarchical)
                }
                .buttonStyle(.plain)
                .padding(5)
            }

            TextField(
                "macos_compose_alt_text",
                text: Binding(
                    get: { item.altText },
                    set: { value in onAltTextChange(value) }
                )
            )
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
        let contentType = (try? url.resourceValues(forKeys: [.contentTypeKey]))?.contentType
        if contentType?.conforms(to: .image) == true {
            return .image
        }
        if contentType?.conforms(to: .movie) == true {
            return .video
        }
        return .other
    }
}

@MainActor
private final class MacComposeWindowCloseController {
    weak var window: NSWindow?
    var allowsWindowClose = false
    private var isPresentingCloseConfirmation = false

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
        alert.addButton(withTitle: String(localized: "compose_save_draft"))
        alert.addButton(withTitle: String(localized: "compose_button_cancel"))
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
            closeController.window = window
            previousDelegate = window.delegate
            window.delegate = self
        }

        func detach() {
            if window?.delegate === self {
                window?.delegate = previousDelegate
            }
            if closeController.window === window {
                closeController.window = nil
            }
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
