import AppKit
import FlareAppleCore
import FlareAppleUI
import Kingfisher
@preconcurrency import KotlinSharedUI
import SwiftUI
import UniformTypeIdentifiers
import WebKit

struct MacSettingsScreen: View {
    @State private var selectedPane: MacSettingsPane = .accountManagement

    var body: some View {
        TabView(selection: $selectedPane) {
            ForEach(MacSettingsPane.allCases) { pane in
                NavigationStack {
                    pane.detail
                }
                .tabItem {
                    Label {
                        Text(pane.title)
                    } icon: {
                        Image(fontAwesome: pane.icon)
                    }
                }
                .tag(pane)
            }
        }
        .frame(minWidth: 880, minHeight: 620)
    }
}

private enum MacSettingsPane: String, CaseIterable, Identifiable, Hashable {
    case accountManagement
    case appearance
    case localFilter
    case storage
    case aiConfig
    case translationConfig
    case about

    var id: String { rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .accountManagement:
            "account_management_title"
        case .appearance:
            "macos_settings_section_appearance"
        case .localFilter:
            "local_filter_title"
        case .storage:
            "storage_title"
        case .aiConfig:
            "ai_config_title"
        case .translationConfig:
            "settings_translation_title"
        case .about:
            "about_title"
        }
    }

    var subtitle: LocalizedStringKey {
        switch self {
        case .accountManagement:
            "account_management_description"
        case .appearance:
            "appearance_description"
        case .localFilter:
            "local_filter_description"
        case .storage:
            "storage_description"
        case .aiConfig:
            "ai_config_description"
        case .translationConfig:
            "settings_translation_description"
        case .about:
            "about_description"
        }
    }

    var icon: FontAwesomeIcon {
        switch self {
        case .accountManagement:
            .circleUser
        case .appearance:
            .palette
        case .localFilter:
            .filter
        case .storage:
            .database
        case .aiConfig:
            .robot
        case .translationConfig:
            .language
        case .about:
            .circleInfo
        }
    }

    @ViewBuilder
    var detail: some View {
        switch self {
        case .accountManagement:
            MacAccountManagementSettingsPane()
        case .appearance:
            MacAppearanceSettingsPane()
        case .localFilter:
            MacLocalFilterSettingsPane()
        case .storage:
            MacStorageSettingsPane()
        case .aiConfig:
            MacAiConfigSettingsPane()
        case .translationConfig:
            MacTranslationConfigSettingsPane()
        case .about:
            MacAboutSettingsPane()
        }
    }
}

private struct MacSettingsForm<Content: View>: View {
    let title: LocalizedStringKey
    let subtitle: LocalizedStringKey
    @ViewBuilder let content: () -> Content

    var body: some View {
        Form {
            content()
        }
        .formStyle(.grouped)
        .navigationTitle(title)
        .navigationSubtitle(Text(subtitle))
    }
}

private struct MacSettingsEmptyState: View {
    let pageTitle: LocalizedStringKey
    let pageSubtitle: LocalizedStringKey
    let title: LocalizedStringKey
    let systemImage: String
    let description: LocalizedStringKey?

    init(
        pageTitle: LocalizedStringKey,
        pageSubtitle: LocalizedStringKey,
        title: LocalizedStringKey,
        systemImage: String,
        description: LocalizedStringKey? = nil
    ) {
        self.pageTitle = pageTitle
        self.pageSubtitle = pageSubtitle
        self.title = title
        self.systemImage = systemImage
        self.description = description
    }

    var body: some View {
        if let description {
            ContentUnavailableView(
                title,
                systemImage: systemImage,
                description: Text(description)
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .navigationTitle(pageTitle)
            .navigationSubtitle(Text(pageSubtitle))
        } else {
            ContentUnavailableView(title, systemImage: systemImage)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .navigationTitle(pageTitle)
                .navigationSubtitle(Text(pageSubtitle))
        }
    }
}

private struct MacSettingsLoadingState: View {
    let title: LocalizedStringKey
    let subtitle: LocalizedStringKey

    var body: some View {
        ProgressView()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .navigationTitle(title)
            .navigationSubtitle(Text(subtitle))
    }
}

private struct MacSettingLabel: View {
    let title: LocalizedStringKey
    let subtitleKey: LocalizedStringKey?
    let value: String?

    init(_ title: LocalizedStringKey, subtitle: LocalizedStringKey? = nil) {
        self.title = title
        self.subtitleKey = subtitle
        self.value = nil
    }

    init(_ title: LocalizedStringKey, value: String) {
        self.title = title
        self.subtitleKey = nil
        self.value = value
    }

    var body: some View {
        MacSettingLabelContent(title: title, subtitleKey: subtitleKey, value: value)
    }
}

private struct MacSettingActionRow: View {
    let title: LocalizedStringKey
    let subtitleKey: LocalizedStringKey?
    let value: String?
    let buttonTitle: LocalizedStringKey
    let icon: FontAwesomeIcon
    let role: ButtonRole?
    let action: () -> Void

    init(
        _ title: LocalizedStringKey,
        subtitle: LocalizedStringKey? = nil,
        buttonTitle: LocalizedStringKey,
        icon: FontAwesomeIcon,
        role: ButtonRole? = nil,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.subtitleKey = subtitle
        self.value = nil
        self.buttonTitle = buttonTitle
        self.icon = icon
        self.role = role
        self.action = action
    }

    init(
        _ title: LocalizedStringKey,
        value: String,
        buttonTitle: LocalizedStringKey,
        icon: FontAwesomeIcon,
        role: ButtonRole? = nil,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.subtitleKey = nil
        self.value = value
        self.buttonTitle = buttonTitle
        self.icon = icon
        self.role = role
        self.action = action
    }

    var body: some View {
        HStack(spacing: 16) {
            MacSettingLabelContent(title: title, subtitleKey: subtitleKey, value: value)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button(role: role, action: action) {
                Label {
                    Text(buttonTitle)
                } icon: {
                    Image(fontAwesome: icon)
                }
            }
            .buttonStyle(.bordered)
            .controlSize(.small)
        }
    }
}

private struct MacSettingLabelContent: View {
    let title: LocalizedStringKey
    let subtitleKey: LocalizedStringKey?
    let value: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
            if let subtitleKey {
                Text(subtitleKey)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let value {
                Text(verbatim: value)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .truncationMode(.middle)
                    .textSelection(.enabled)
            }
        }
    }
}

private struct MacAccountManagementSettingsPane: View {
    @StateObject private var presenter = KotlinPresenter(presenter: AccountManagementPresenter())
    @State private var accounts: [AccountsStateAccountItem] = []
    @State private var isLoginSheetPresented = false
    @State private var pendingLogoutAccountKey: MicroBlogKey?
    @State private var pendingLogoutAccountName: String?

    var body: some View {
        StateView(state: presenter.state.accounts) { data in
            VStack {
                Button {
                    isLoginSheetPresented = true
                } label: {
                    Label {
                        Text("login_button")
                    } icon: {
                        Image(fontAwesome: .plus)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
                .padding()
                let currentAccounts = accounts.isEmpty ? data.cast(AccountsStateAccountItem.self) : accounts
                if currentAccounts.isEmpty {
                    MacSettingsEmptyState(
                        pageTitle: "account_management_title",
                        pageSubtitle: "account_management_description",
                        title: "macos_account_unavailable",
                        systemImage: "person.crop.circle.badge.exclamationmark",
                        description: "macos_account_add"
                    ).onTapGesture {
                        isLoginSheetPresented = true
                    }
                } else {
                    MacSettingsForm(
                        title: "account_management_title",
                        subtitle: "account_management_description"
                    ) {
                        Section {
                            ForEach(Array(currentAccounts.enumerated()), id: \.element.account.accountKey) { index, item in
                                accountRow(item: item, index: index, count: currentAccounts.count)
                            }
                        }
                    }
                }
            }
        } loadingContent: {
            MacSettingsLoadingState(title: "account_management_title", subtitle: "account_management_description")
        }
        .onSuccessOf(of: presenter.state.accounts) { data in
            accounts = data.cast(AccountsStateAccountItem.self)
        }
        .sheet(isPresented: $isLoginSheetPresented) {
            NavigationStack {
                ServiceSelectionScreen {
                    isLoginSheetPresented = false
                }
            }
            .frame(width: 420, height: 540)
        }
        .alert("logout_title", isPresented: Binding(get: {
            pendingLogoutAccountKey != nil
        }, set: { value in
            if !value {
                clearPendingLogout()
            }
        })) {
            Button("cancel_button", role: .cancel) {
                clearPendingLogout()
            }
            Button("delete_button", role: .destructive) {
                confirmLogout()
            }
        } message: {
            Text(
                pendingLogoutAccountName.map { "Are you sure you want to remove \($0) from this device?" } ??
                    "Are you sure you want to remove this account from this device?"
            )
        }
    }

    @ViewBuilder
    private func accountRow(item: AccountsStateAccountItem, index: Int, count: Int) -> some View {
        StateView(state: item.profile) { user in
            UserCompatView(data: user) {
                HStack(spacing: 8) {
                    Image(systemName: activeAccountKey == user.key ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(activeAccountKey == user.key ? Color.accentColor : Color.secondary)

                    MacAccountOrderButtons(
                        canMoveUp: index > 0,
                        canMoveDown: index < count - 1,
                        moveUp: { move(item: item, by: -1) },
                        moveDown: { move(item: item, by: 1) }
                    )
                }
            }
            .contentShape(Rectangle())
            .onTapGesture {
                presenter.state.setActiveAccount(accountKey: user.key)
            }
            .contextMenu {
                accountMenu(item: item, accountName: user.handle.canonical)
            }
        } errorContent: { error in
            UserErrorView(error: error)
                .contextMenu {
                    accountMenu(item: item, accountName: item.account.accountKey.id)
                }
        } loadingContent: {
            UserLoadingView()
        }
    }

    @ViewBuilder
    private func accountMenu(item: AccountsStateAccountItem, accountName: String?) -> some View {
        Button(role: .destructive) {
            requestLogoutConfirmation(
                accountKey: item.account.accountKey,
                accountName: accountName
            )
        } label: {
            Label {
                Text("logout_title")
            } icon: {
                Image(fontAwesome: .trash)
            }
        }
    }

    private var activeAccountKey: MicroBlogKey? {
        if case .success(let active) = onEnum(of: presenter.state.activeAccount) {
            active.data.accountKey
        } else {
            nil
        }
    }

    private func move(item: AccountsStateAccountItem, by offset: Int) {
        guard let currentIndex = accounts.firstIndex(where: { $0.account.accountKey == item.account.accountKey }) else {
            return
        }
        let targetIndex = currentIndex + offset
        guard accounts.indices.contains(targetIndex) else {
            return
        }
        accounts.move(
            fromOffsets: IndexSet(integer: currentIndex),
            toOffset: offset > 0 ? targetIndex + 1 : targetIndex
        )
        presenter.state.setOrder(value: accounts.map { $0.account.accountKey })
    }

    private func requestLogoutConfirmation(accountKey: MicroBlogKey, accountName: String?) {
        pendingLogoutAccountKey = accountKey
        pendingLogoutAccountName = accountName
    }

    private func confirmLogout() {
        guard let accountKey = pendingLogoutAccountKey else {
            return
        }
        accounts.removeAll { item in
            item.account.accountKey == accountKey
        }
        presenter.state.logout(accountKey: accountKey)
        presenter.state.setOrder(value: accounts.map { $0.account.accountKey })
        clearPendingLogout()
    }

    private func clearPendingLogout() {
        pendingLogoutAccountKey = nil
        pendingLogoutAccountName = nil
    }
}

private struct MacAccountOrderButtons: View {
    let canMoveUp: Bool
    let canMoveDown: Bool
    let moveUp: () -> Void
    let moveDown: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Button(action: moveUp) {
                Label {
                    Text("macos_action_move_up")
                } icon: {
                    Image(systemName: "chevron.up")
                }
            }
            .buttonStyle(.borderless)
            .disabled(!canMoveUp)
            .help(Text("macos_action_move_up"))

            Button(action: moveDown) {
                Label {
                    Text("macos_action_move_down")
                } icon: {
                    Image(systemName: "chevron.down")
                }
            }
            .buttonStyle(.borderless)
            .disabled(!canMoveDown)
            .help(Text("macos_action_move_down"))
        }
        .font(.caption)
    }
}

private struct MacAppearanceSettingsPane: View {
    var body: some View {
        MacSettingsForm(
            title: "macos_settings_section_appearance",
            subtitle: "appearance_description"
        ) {
            AppearanceThemeSettingsSection()
            AppearanceLayoutSettingsSection()
            AppearanceDisplaySettingsSection()
            AppearanceMediaSettingsSection()
        }
    }
}

private struct MacLocalFilterSettingsPane: View {
    var body: some View {
        LocalFilterSettingsView()
            .navigationTitle("local_filter_title")
            .navigationSubtitle(Text("local_filter_description"))
    }
}

private struct MacStorageSettingsPane: View {
    @StateObject private var presenter: KotlinPresenter<StorageState>
    @State private var showDatabaseClearAlert = false
    @State private var showImageClearAlert = false
    @State private var showFileExporter = false
    @State private var showFileImporter = false
    @State private var showImportConfirmation = false
    @State private var pendingImportJson: String?
    @State private var jsonFile = MacJSONFile(text: "")
    @State private var notice: MacStorageNotice?
    @State private var isClearingImageCache = false
    @State private var isClearingDatabaseCache = false
    @State private var showingAppLog = false

    init() {
        _presenter = StateObject(wrappedValue: KotlinPresenter(presenter: StoragePresenter()))
    }

    var body: some View {
        MacSettingsForm(
            title: "storage_title",
            subtitle: "storage_description"
        ) {
            Section {
                MacSettingActionRow(
                    "storage_clear_image_cache",
                    subtitle: "storage_clear_image_cache_desc",
                    buttonTitle: "macos_action_clear",
                    icon: .trash,
                    role: .destructive
                ) {
                    guard !isClearingStorage else {
                        return
                    }
                    showImageClearAlert = true
                }

                MacSettingActionRow(
                    "storage_clear_database_cache",
                    value: "\(presenter.state.userCount) users, \(presenter.state.statusCount) posts",
                    buttonTitle: "macos_action_clear",
                    icon: .trash,
                    role: .destructive
                ) {
                    guard !isClearingStorage else {
                        return
                    }
                    showDatabaseClearAlert = true
                }
            }

            Section {
                MacSettingActionRow(
                    "storage_view_app_log",
                    subtitle: "storage_view_app_log_desc",
                    buttonTitle: "macos_action_open",
                    icon: .envelope
                ) {
                    showingAppLog = true
                }

                MacSettingActionRow(
                    "settings_storage_export_data",
                    subtitle: "settings_storage_export_data_desc",
                    buttonTitle: "macos_action_export",
                    icon: .fileExport
                ) {
                    exportData()
                }

                MacSettingActionRow(
                    "settings_storage_import_data",
                    subtitle: "settings_storage_import_data_desc",
                    buttonTitle: "macos_action_import",
                    icon: .fileImport
                ) {
                    guard !isClearingStorage else {
                        return
                    }
                    showFileImporter = true
                }
            }
        }
        .disabled(isClearingStorage)
        .overlay {
            if isClearingStorage {
                ZStack {
                    Color.black.opacity(0.12)
                    ProgressView()
                        .controlSize(.large)
                }
            }
        }
        .alert("storage_clear_image_cache_confirm", isPresented: $showImageClearAlert) {
            Button("cancel_button", role: .cancel) {}
            Button("ok_button", role: .destructive) {
                clearImageCache()
            }
        }
        .alert("storage_clear_database_cache_confirm", isPresented: $showDatabaseClearAlert) {
            Button("cancel_button", role: .cancel) {}
            Button("ok_button", role: .destructive) {
                clearDatabaseCache()
            }
        }
        .alert("import_confirmation_title", isPresented: $showImportConfirmation) {
            Button("cancel_button", role: .cancel) {
                pendingImportJson = nil
            }
            Button("ok_button") {
                importData()
            }
        } message: {
            Text("import_confirmation_message")
        }
        .alert(item: $notice) { notice in
            Alert(
                title: Text(notice.title),
                message: Text(notice.message),
                dismissButton: .default(Text("OK"))
            )
        }
        .fileExporter(
            isPresented: $showFileExporter,
            document: jsonFile,
            contentType: .json,
            defaultFilename: "flare_data_export"
        ) { result in
            switch result {
            case .success:
                notice = MacStorageNotice(title: "save_completed", message: "")
            case .failure(let error):
                notice = MacStorageNotice(title: "save_error", message: error.localizedDescription)
            }
        }
        .fileImporter(isPresented: $showFileImporter, allowedContentTypes: [.json]) { result in
            switch result {
            case .success(let url):
                guard url.startAccessingSecurityScopedResource() else {
                    notice = MacStorageNotice(title: "import_error", message: "")
                    return
                }
                defer {
                    url.stopAccessingSecurityScopedResource()
                }

                do {
                    let data = try Data(contentsOf: url)
                    pendingImportJson = String(data: data, encoding: .utf8)
                    showImportConfirmation = pendingImportJson != nil
                } catch {
                    notice = MacStorageNotice(title: "import_error", message: error.localizedDescription)
                }
            case .failure(let error):
                notice = MacStorageNotice(title: "import_error", message: error.localizedDescription)
            }
        }
        .sheet(isPresented: $showingAppLog) {
            NavigationStack {
                MacAppLogSettingsPane()
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button {
                                showingAppLog = false
                            } label: {
                                Label {
                                    Text("cancel_button")
                                } icon: {
                                    Image(fontAwesome: .xmark)
                                }
                            }
                        }
                    }
            }
            .frame(width: 620, height: 560)
        }
    }

    private var isClearingStorage: Bool {
        isClearingImageCache || isClearingDatabaseCache
    }

    private func clearImageCache() {
        isClearingImageCache = true
        URLCache.shared.removeAllCachedResponses()
        KingfisherManager.shared.cache.clearMemoryCache()
        let websiteDataTypes: Set<String> = [
            WKWebsiteDataTypeMemoryCache,
            WKWebsiteDataTypeDiskCache
        ]
        WKWebsiteDataStore.default().removeData(
            ofTypes: websiteDataTypes,
            modifiedSince: .distantPast
        ) {}
        KingfisherManager.shared.cache.clearDiskCache {
            Task { @MainActor in
                isClearingImageCache = false
            }
        }
    }

    private func clearDatabaseCache() {
        isClearingDatabaseCache = true
        presenter.state.clearCache()
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 300_000_000)
            isClearingDatabaseCache = false
        }
    }

    private func exportData() {
        guard !isClearingStorage else {
            return
        }
        Task.detached {
            do {
                let json = try await ExportDataPresenter().export()
                await MainActor.run {
                    jsonFile = MacJSONFile(text: json)
                    showFileExporter = true
                }
            } catch {
                let message = error.localizedDescription
                await MainActor.run {
                    notice = MacStorageNotice(title: "export_error", message: message)
                }
            }
        }
    }

    private func importData() {
        guard let json = pendingImportJson else {
            return
        }
        pendingImportJson = nil
        Task.detached {
            do {
                let importPresenter = ImportDataPresenter(jsonContent: json)
                try await importPresenter.models.value.import()
                await MainActor.run {
                    notice = MacStorageNotice(title: "import_completed", message: "")
                }
            } catch {
                let message = error.localizedDescription
                await MainActor.run {
                    notice = MacStorageNotice(title: "import_error", message: message)
                }
            }
        }
    }
}

private struct MacStorageNotice: Identifiable {
    let id = UUID()
    let title: LocalizedStringKey
    let message: String
}

private struct MacJSONFile: FileDocument {
    static let readableContentTypes = [UTType.json]
    var text = ""

    init(text: String = "") {
        self.text = text
    }

    init(configuration: ReadConfiguration) throws {
        if let data = configuration.file.regularFileContents {
            text = String(decoding: data, as: UTF8.self)
        }
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: Data(text.utf8))
    }
}

private struct MacAiConfigSettingsPane: View {
    var body: some View {
        MacSettingsForm(
            title: "ai_config_title",
            subtitle: "ai_config_description"
        ) {
            AiConfigSettingsSections()
        }
    }
}

private struct MacTranslationConfigSettingsPane: View {
    var body: some View {
        MacSettingsForm(
            title: "settings_translation_title",
            subtitle: "settings_translation_description"
        ) {
            TranslationConfigSettingsSections()
        }
    }
}

private struct MacAppLogSettingsPane: View {
    @StateObject private var presenter = KotlinPresenter(presenter: DevModePresenter())
    @State private var selectedMessage: MacLogMessage?
    @State private var exportedLogContent: String?

    var body: some View {
        let messages = (presenter.state.messages as NSArray).cast(NSString.self).map(String.init)

        Form {
            Section {
                Toggle(isOn: Binding(get: {
                    presenter.state.enabled
                }, set: { enabled in
                    presenter.state.setEnabled(value: enabled)
                })) {
                    Text("app_log_network_toggle")
                }
            }

            if messages.isEmpty {
                ContentUnavailableView("list_empty_title", systemImage: "doc.text.magnifyingglass")
            } else {
                Section("app_log") {
                    ForEach(messages, id: \.self) { message in
                        Text(message)
                            .lineLimit(3)
                            .onTapGesture {
                                selectedMessage = MacLogMessage(message: message)
                            }
                    }
                }
            }
        }
        .formStyle(.grouped)
        .navigationTitle("app_log")
        .toolbar {
            ToolbarItem {
                Button {
                    presenter.state.clear()
                } label: {
                    Label {
                        Text("macos_action_clear")
                    } icon: {
                        Image(fontAwesome: .trash)
                    }
                }
            }
            ToolbarItem {
                Button {
                    exportedLogContent = presenter.state.printMessageToString()
                } label: {
                    Label {
                        Text("macos_action_save")
                    } icon: {
                        Image(fontAwesome: .floppyDisk)
                    }
                }
            }
        }
        .fileExporter(
            isPresented: Binding(get: {
                exportedLogContent != nil
            }, set: { newValue in
                if !newValue {
                    exportedLogContent = nil
                }
            }),
            document: TextDocument(text: exportedLogContent ?? ""),
            defaultFilename: "flare_log.txt"
        ) { _ in
            exportedLogContent = nil
        }
        .sheet(item: $selectedMessage) { message in
            NavigationStack {
                ScrollView {
                    Text(message.message)
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                }
                .navigationTitle("app_log")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button {
                            selectedMessage = nil
                        } label: {
                            Label {
                                Text("cancel_button")
                            } icon: {
                                Image(fontAwesome: .xmark)
                            }
                        }
                    }
                }
            }
            .frame(width: 560, height: 420)
        }
    }
}

private struct MacLogMessage: Identifiable {
    let id = UUID()
    let message: String
}

private struct MacAboutSettingsPane: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        MacSettingsForm(
            title: "about_title",
            subtitle: "about_description"
        ) {
            Section {
                VStack(spacing: 14) {
                    Image(nsImage: NSApp.applicationIconImage)
                        .resizable()
                        .aspectRatio(1, contentMode: .fit)
                        .frame(width: 96, height: 96)
                        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                        .shadow(color: .black.opacity(0.12), radius: 16, y: 8)

                    VStack(spacing: 6) {
                        Text("Flare")
                            .font(.largeTitle.weight(.semibold))
                        Text("settings_about_description")
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                        if !version.isEmpty {
                            Text(verbatim: version)
                                .font(.footnote.monospacedDigit())
                                .foregroundStyle(.tertiary)
                        }
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
            }

            Section {
                ForEach(MacAboutLink.all) { item in
                    MacSettingActionRow(
                        item.titleKey,
                        value: item.subtitle,
                        buttonTitle: "macos_action_open",
                        icon: item.icon
                    ) {
                        openURL(item.url)
                    }
                }
            }
        }
    }

    private var version: String {
        let versionName = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? ""

        switch (versionName.isEmpty, buildNumber.isEmpty) {
        case (false, false):
            return "\(versionName) (\(buildNumber))"
        case (false, true):
            return versionName
        case (true, false):
            return buildNumber
        case (true, true):
            return ""
        }
    }
}

private struct MacAboutLink: Identifiable {
    let id: String
    let titleKey: LocalizedStringKey
    let subtitle: String
    let icon: FontAwesomeIcon
    let url: URL

    static let all: [MacAboutLink] = [
        MacAboutLink(
            id: "source-code",
            titleKey: "settings_about_source_code",
            subtitle: "https://github.com/DimensionDev/Flare",
            icon: .github,
            url: URL(string: "https://github.com/DimensionDev/Flare")!
        ),
        MacAboutLink(
            id: "telegram",
            titleKey: "settings_about_telegram",
            subtitle: String(localized: "settings_about_telegram_description"),
            icon: .telegram,
            url: URL(string: "https://t.me/+VZ63fqNQXIA0MzVl")!
        ),
        MacAboutLink(
            id: "discord",
            titleKey: "settings_about_discord",
            subtitle: String(localized: "settings_about_discord_description"),
            icon: .discord,
            url: URL(string: "https://discord.gg/De9NhXBryT")!
        ),
        MacAboutLink(
            id: "localization",
            titleKey: "settings_about_localization",
            subtitle: String(localized: "settings_about_localization_description"),
            icon: .language,
            url: URL(string: "https://crowdin.com/project/flareapp")!
        ),
        MacAboutLink(
            id: "privacy-policy",
            titleKey: "settings_privacy_policy",
            subtitle: "https://legal.mask.io/maskbook",
            icon: .lock,
            url: URL(string: "https://legal.mask.io/maskbook/")!
        )
    ]
}
