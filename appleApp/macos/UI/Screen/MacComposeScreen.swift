import AppKit
import AppleFontAwesome
import FlareAppleCore
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import SwiftUI
import UniformTypeIdentifiers

struct MacComposeScreen: View {
    @Environment(\.dismiss) private var dismiss

    let request: MacComposeWindowRequest
    @StateObject private var presenter: KotlinPresenter<ComposeState>

    @State private var text = ""
    @State private var contentWarning = ""
    @State private var enableContentWarning = false
    @State private var sensitive = false
    @State private var languages: [String] = MacComposeDefaults.languages
    @State private var mediaItems: [MacComposeMediaItem] = []
    @State private var pollEnabled = false
    @State private var pollMultiple = false
    @State private var pollChoices = ["", ""]
    @State private var pollExpiration = MacComposePollExpiration.minutes5
    @State private var showFileImporter = false
    @State private var showCloseConfirmation = false
    @State private var showEmojiPopover = false
    @State private var showAccountPopover = false
    @State private var initialTextApplied = false

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
            if enableContentWarning {
                TextField("compose_cw_placeholder", text: $contentWarning)
                Divider()
            }
            
            TextEditor(text: $text)
                .font(.body)
                .scrollDisabled(true)
                .frame(maxWidth: .infinity, minHeight: 60, maxHeight: .infinity)
            
            if !mediaItems.isEmpty {
                mediaSection
            }

            if pollEnabled {
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

            ToolbarItem(placement: .primaryAction) {
                Button {
                    saveDraft(shouldDismiss: false)
                } label: {
                    Label {
                        Text("compose_save_draft")
                    } icon: {
                        Image(systemName: "tray.and.arrow.down")
                    }
                }
                .disabled(!hasDraftContent)
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
        .confirmationDialog(
            "compose_save_draft_prompt",
            isPresented: $showCloseConfirmation,
            titleVisibility: .visible
        ) {
            Button("compose_save_draft") {
                saveDraft(shouldDismiss: true)
            }
            Button("compose_discard_draft", role: .destructive) {
                dismiss()
            }
            Button("compose_button_cancel", role: .cancel) {}
        }
        .onAppear {
            presenter.state.setText(value: text)
            presenter.state.setMediaSize(value: Int32(mediaItems.count))
        }
        .onChange(of: text) { _, newValue in
            presenter.state.setText(value: newValue)
        }
        .onChange(of: mediaItems.count) { _, newValue in
            presenter.state.setMediaSize(value: Int32(newValue))
        }
        .onChange(of: presenter.state.initialTextState) { _, newValue in
            guard !initialTextApplied else { return }
            if case .success(let initialText) = onEnum(of: newValue) {
                initialTextApplied = true
                text = initialText.data.text
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
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("compose_poll_type")
                    .font(.headline)

                Picker("compose_poll_type", selection: $pollMultiple) {
                    Text("compose_poll_type_single").tag(false)
                    Text("compose_poll_type_multiple").tag(true)
                }
                .pickerStyle(.segmented)
                .frame(width: 260)

                Spacer()

                Button {
                    pollEnabled = false
                    pollChoices = ["", ""]
                } label: {
                    Image(systemName: "xmark")
                }
                .buttonStyle(.borderless)
            }

            ForEach(pollChoices.indices, id: \.self) { index in
                HStack {
                    TextField(
                        "compose_poll_choice_placeholder",
                        text: Binding(
                            get: { pollChoices[index] },
                            set: { pollChoices[index] = $0 }
                        )
                    )
                    .textFieldStyle(.roundedBorder)

                    Button {
                        pollChoices.remove(at: index)
                    } label: {
                        Image(fontAwesome: .deleteLeft)
                    }
                    .buttonStyle(.borderless)
                    .disabled(pollChoices.count <= 2)
                }
            }

            HStack {
                Button {
                    pollChoices.append("")
                } label: {
                    Label {
                        Text("macos_compose_add_poll_choice")
                    } icon: {
                        Image(fontAwesome: .plus)
                    }
                }
                .disabled(pollChoices.count >= max(2, maxPollOptions))

                Spacer()

                Picker("compose_poll_expiration", selection: $pollExpiration) {
                    ForEach(MacComposePollExpiration.allCases) { expiration in
                        Text(expiration.title).tag(expiration)
                    }
                }
                .frame(width: 180)
            }
        }
        .padding(12)
        .background(Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
    }

    @ViewBuilder
    private var referencePostSection: some View {
        if let replyState = presenter.state.replyState,
           case .success(let reply) = onEnum(of: replyState),
           let content = reply.data as? UiTimelineV2.Post {
            StatusView(data: content, isQuote: true, showMedia: false, forceHideActions: true)
                .padding(12)
                .background(Color(nsColor: .controlBackgroundColor), in: RoundedRectangle(cornerRadius: 8))
        }
    }

    private var bottomBar: some View {
        ComposeActionBarContent(
            isPollEnabled: pollEnabled,
            hasMedia: !mediaItems.isEmpty,
            canAddPoll: presenter.state.pollMaxOptions != nil,
            canUseContentWarning: presenter.state.contentWarningEnabled,
            visibility: composeVisibility,
            allVisibilities: composeVisibilities,
            emojiState: presenter.state.emojiState,
            isEmojiPresented: $showEmojiPopover,
            languageCodes: Array(presenter.state.languageCodes),
            selectedLanguages: $languages,
            maxLanguageSelectionCount: languageMaxCount,
            textCount: text.count,
            maxTextLength: textMaxLength,
            onTogglePoll: {
                pollEnabled.toggle()
                if pollEnabled {
                    mediaItems = []
                }
            },
            onToggleContentWarning: {
                enableContentWarning.toggle()
            },
            onSelectVisibility: setVisibility,
            onSelectEmoji: { emoji in
                text += emoji.insertText
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
        !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
        !contentWarning.isEmpty ||
        !mediaItems.isEmpty ||
        pollEnabled
    }

    private var textMaxLength: Int? {
        presenter.state.textMaxLength.map { Int(truncating: $0) }
    }

    private var maxPollOptions: Int {
        presenter.state.pollMaxOptions.map { Int(truncating: $0) } ?? 4
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
            showCloseConfirmation = true
        } else {
            dismiss()
        }
    }

    private func send() {
        presenter.state.send(data: composeData) { dispatched in
            if dispatched.boolValue {
                dismiss()
            }
        }
    }

    private func saveDraft(shouldDismiss: Bool) {
        presenter.state.saveDraft(data: composeData) { dispatched in
            guard dispatched.boolValue else { return }
            if shouldDismiss {
                dismiss()
            }
        }
    }

    private var composeData: ComposeData {
        ComposeData(
            content: text,
            visibility: currentVisibility,
            language: languages,
            medias: mediaItems.map(\.composeMedia),
            sensitive: sensitive,
            spoilerText: enableContentWarning ? contentWarning : nil,
            poll: poll,
            localOnly: false,
            referenceStatus: referenceStatus
        )
    }

    private var currentVisibility: UiTimelineV2.PostVisibility {
        switch onEnum(of: presenter.state.visibilityState) {
        case .success(let success):
            success.data.visibility
        default:
            .public
        }
    }

    private var referenceStatus: ComposeData.ReferenceStatus? {
        if let composeStatus = presenter.state.composeStatus {
            ComposeData.ReferenceStatus(composeStatus: composeStatus)
        } else {
            nil
        }
    }

    private var poll: ComposeData.Poll? {
        guard pollEnabled else { return nil }
        let options = pollChoices
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        guard options.count >= 2 else { return nil }
        return ComposeData.Poll(
            options: options,
            expiredAfter: pollExpiration.milliseconds,
            multiple: pollMultiple
        )
    }

    private func importFiles(_ result: Result<[URL], Error>) {
        guard case .success(let urls) = result else { return }
        let remaining = max(Int(presenter.state.mediaMaxCount) - mediaItems.count, 0)
        let items = urls.prefix(remaining).compactMap(MacComposeMediaItem.init(url:))
        if !items.isEmpty {
            pollEnabled = false
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

    private func applyDraft(_ draft: UiDraft) {
        let data = draft.data
        text = data.content
        contentWarning = data.spoilerText ?? ""
        enableContentWarning = !(data.spoilerText ?? "").isEmpty
        if !data.language.isEmpty {
            languages = data.language
        }
        sensitive = data.sensitive
        mediaItems = draft.medias.compactMap(MacComposeMediaItem.init(draftMedia:))
        applyPoll(data.poll)
        if case .success(let visibilityState) = onEnum(of: presenter.state.visibilityState) {
            visibilityState.data.setVisibility(value: data.visibility)
        }
    }

    private func applyPoll(_ data: ComposeData.Poll?) {
        guard let data else {
            pollEnabled = false
            pollChoices = ["", ""]
            pollMultiple = false
            pollExpiration = .minutes5
            return
        }

        pollEnabled = true
        pollChoices = data.options
        while pollChoices.count < 2 {
            pollChoices.append("")
        }
        pollMultiple = data.multiple
        pollExpiration = MacComposePollExpiration(milliseconds: data.expiredAfter) ?? .minutes5
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

private enum MacComposePollExpiration: String, CaseIterable, Identifiable {
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

    var id: String { rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .minutes5:
            "macos_compose_poll_5_minutes"
        case .minutes30:
            "macos_compose_poll_30_minutes"
        case .hours1:
            "macos_compose_poll_1_hour"
        case .hours6:
            "macos_compose_poll_6_hours"
        case .hours12:
            "macos_compose_poll_12_hours"
        case .days1:
            "macos_compose_poll_1_day"
        case .days3:
            "macos_compose_poll_3_days"
        case .days7:
            "macos_compose_poll_7_days"
        }
    }

    var milliseconds: Int64 {
        switch self {
        case .minutes5:
            5 * 60 * 1000
        case .minutes30:
            30 * 60 * 1000
        case .hours1:
            1 * 60 * 60 * 1000
        case .hours6:
            6 * 60 * 60 * 1000
        case .hours12:
            12 * 60 * 60 * 1000
        case .days1:
            24 * 60 * 60 * 1000
        case .days3:
            3 * 24 * 60 * 60 * 1000
        case .days7:
            7 * 24 * 60 * 60 * 1000
        }
    }
}

private enum MacComposeDefaults {
    static var languages: [String] {
        if let code = Locale.current.language.languageCode?.identifier {
            return [code]
        }
        return ["en"]
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
