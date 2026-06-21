import AppKit
import AppleFontAwesome
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
//                        Image(fontAwesome: pane.icon)
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
                .navigationTitle("login_button")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button {
                            isLoginSheetPresented = false
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
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    @Environment(\.globalAppearance) private var globalAppearance
    @Environment(\.timelineAppearance) private var timelineAppearance

    var body: some View {
        MacSettingsForm(
            title: "macos_settings_section_appearance",
            subtitle: "appearance_description"
        ) {
            Section("appearance_theme_group_title") {
                Picker(selection: Binding(get: {
                    globalAppearance.theme
                }, set: { newValue in
                    presenter.state.updateTheme(value: newValue)
                })) {
                    Text("appearance_theme_system").tag(Theme.system)
                    Text("appearance_theme_light").tag(Theme.light)
                    Text("appearance_theme_dark").tag(Theme.dark)
                } label: {
                    MacSettingLabel("appearance_theme", subtitle: "appearance_theme_description")
                }

                Picker(selection: Binding(get: {
                    timelineAppearance.avatarShape
                }, set: { newValue in
                    presenter.state.updateAvatarShape(value: newValue)
                })) {
                    Text("appearance_avatar_shape_circle").tag(AvatarShape.circle)
                    Text("appearance_avatar_shape_square").tag(AvatarShape.square)
                } label: {
                    MacSettingLabel("appearance_avatar_shape", subtitle: "appearance_avatar_shape_description")
                }
            }

            Section("appearance_layout_group_title") {
                Picker(selection: Binding(get: {
                    timelineAppearance.timelineDisplayMode
                }, set: { newValue in
                    presenter.state.updateTimelineDisplayMode(value: newValue)
                })) {
                    Text("appearance_timeline_display_mode_card").tag(TimelineDisplayMode.card)
                    Text("appearance_timeline_display_mode_plain").tag(TimelineDisplayMode.plain)
                    Text("appearance_timeline_display_mode_gallery").tag(TimelineDisplayMode.gallery)
                } label: {
                    MacSettingLabel(
                        "appearance_timeline_display_mode",
                        subtitle: "appearance_timeline_display_mode_description"
                    )
                }

                Toggle(isOn: Binding(get: {
                    globalAppearance.showBottomBarLabels
                }, set: { newValue in
                    presenter.state.updateShowBottomBarLabels(value: newValue)
                })) {
                    MacSettingLabel(
                        "appearance_show_bottom_bar_labels",
                        subtitle: "appearance_show_bottom_bar_labels_description"
                    )
                }

                Toggle(isOn: Binding(get: {
                    globalAppearance.deckMode
                }, set: { newValue in
                    presenter.state.updateDeckMode(value: newValue)
                })) {
                    MacSettingLabel("appearance_deck_mode", subtitle: "appearance_deck_mode_description")
                }

                Toggle(isOn: Binding(get: {
                    timelineAppearance.fullWidthPost
                }, set: { newValue in
                    presenter.state.updateFullWidthPost(value: newValue)
                })) {
                    MacSettingLabel("appearance_fullWidthPost", subtitle: "appearance_fullWidthPost_description")
                }

                Picker(selection: Binding(get: {
                    timelineAppearance.postActionStyle
                }, set: { newValue in
                    presenter.state.updatePostActionStyle(value: newValue)
                })) {
                    Text("appearance_post_action_style_hidden").tag(PostActionStyle.hidden)
                    Text("appearance_post_action_style_left_aligned").tag(PostActionStyle.leftAligned)
                    Text("appearance_post_action_style_right_aligned").tag(PostActionStyle.rightAligned)
                    Text("appearance_post_action_style_stretch").tag(PostActionStyle.stretch)
                } label: {
                    MacSettingLabel(
                        "appearance_post_action_style",
                        subtitle: "appearance_post_action_style_description"
                    )
                }

                if timelineAppearance.postActionStyle != .hidden {
                    Toggle(isOn: Binding(get: {
                        timelineAppearance.showNumbers
                    }, set: { newValue in
                        presenter.state.updateShowNumbers(value: newValue)
                    })) {
                        MacSettingLabel("appearance_show_numbers", subtitle: "appearance_show_numbers_description")
                    }
                }
            }

            Section("appearance_display_group_title") {
                Toggle(isOn: Binding(get: {
                    timelineAppearance.absoluteTimestamp
                }, set: { newValue in
                    presenter.state.updateAbsoluteTimestamp(value: newValue)
                })) {
                    MacSettingLabel(
                        "appearance_absolute_timestamp",
                        subtitle: "appearance_absolute_timestamp_description"
                    )
                }

                Toggle(isOn: Binding(get: {
                    timelineAppearance.showPlatformLogo
                }, set: { newValue in
                    presenter.state.updateShowPlatformLogo(value: newValue)
                })) {
                    MacSettingLabel(
                        "appearance_show_platform_logo",
                        subtitle: "appearance_show_platform_logo_description"
                    )
                }

                Toggle(isOn: Binding(get: {
                    timelineAppearance.showLinkPreview
                }, set: { newValue in
                    presenter.state.updateShowLinkPreview(value: newValue)
                })) {
                    MacSettingLabel(
                        "appearance_show_link_preview",
                        subtitle: "appearance_show_link_preview_description"
                    )
                }

                if timelineAppearance.showLinkPreview {
                    Toggle(isOn: Binding(get: {
                        timelineAppearance.compatLinkPreview
                    }, set: { newValue in
                        presenter.state.updateCompatLinkPreview(value: newValue)
                    })) {
                        MacSettingLabel(
                            "appearance_compat_link_preview",
                            subtitle: "appearance_compat_link_preview_description"
                        )
                    }
                }

                Toggle(isOn: Binding(get: {
                    globalAppearance.inAppBrowser
                }, set: { newValue in
                    presenter.state.updateInAppBrowser(value: newValue)
                })) {
                    MacSettingLabel("appearance_in_app_browser", subtitle: "appearance_in_app_browser_description")
                }
            }

            Section("appearance_media_group_title") {
                Toggle(isOn: Binding(get: {
                    timelineAppearance.showMedia
                }, set: { newValue in
                    presenter.state.updateShowMedia(value: newValue)
                })) {
                    MacSettingLabel("appearance_show_media", subtitle: "appearance_show_media_description")
                }

                if timelineAppearance.showMedia {
                    Toggle(isOn: Binding(get: {
                        timelineAppearance.expandMediaSize
                    }, set: { newValue in
                        presenter.state.updateExpandMediaSize(value: newValue)
                    })) {
                        MacSettingLabel(
                            "appearance_expand_media_size",
                            subtitle: "appearance_expand_media_size_description"
                        )
                    }

                    Toggle(isOn: Binding(get: {
                        timelineAppearance.showSensitiveContent
                    }, set: { newValue in
                        presenter.state.updateShowSensitiveContent(value: newValue)
                    })) {
                        MacSettingLabel(
                            "appearance_show_sensitive_content",
                            subtitle: "appearance_show_sensitive_content_description"
                        )
                    }

                    Toggle(isOn: Binding(get: {
                        timelineAppearance.expandContentWarning
                    }, set: { newValue in
                        presenter.state.updateExpandContentWarning(value: newValue)
                    })) {
                        MacSettingLabel(
                            "appearance_expand_content_warning",
                            subtitle: "appearance_expand_content_warning_description"
                        )
                    }

                    Picker(selection: Binding(get: {
                        timelineAppearance.videoAutoplay
                    }, set: { newValue in
                        presenter.state.updateVideoAutoplay(value: newValue)
                    })) {
                        Text("appearance_video_autoplay_never").tag(VideoAutoplay.never)
                        Text("appearance_video_autoplay_wifi").tag(VideoAutoplay.wifi)
                        Text("appearance_video_autoplay_always").tag(VideoAutoplay.always)
                    } label: {
                        MacSettingLabel("appearance_video_autoplay", subtitle: "appearance_video_autoplay_description")
                    }
                }
            }
        }
    }
}

private struct MacLocalFilterSettingsPane: View {
    @StateObject private var presenter = KotlinPresenter(presenter: LocalFilterPresenter())
    @State private var selectedFilter: UiKeywordFilter?
    @State private var showingEditor = false

    var body: some View {
        StateView(state: presenter.state.items) { filters in
            VStack {
                Button {
                    selectedFilter = nil
                    showingEditor = true
                } label: {
                    Label {
                        Text("local_filter_edit_title")
                    } icon: {
                        Image(fontAwesome: .plus)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
                .padding()
                let list = filters.cast(UiKeywordFilter.self)
                if list.isEmpty {
                    MacSettingsEmptyState(
                        pageTitle: "local_filter_title",
                        pageSubtitle: "local_filter_description",
                        title: "list_empty_title",
                        systemImage: "line.3.horizontal.decrease.circle"
                    )
                } else {
                    MacSettingsForm(
                        title: "local_filter_title",
                        subtitle: "local_filter_description"
                    ) {
                        Section {
                            ForEach(list, id: \.keyword) { item in
                                MacLocalFilterRow(item: item)
                                    .contextMenu {
                                        Button {
                                            selectedFilter = item
                                            showingEditor = true
                                        } label: {
                                            Label {
                                                Text("local_filter_edit")
                                            } icon: {
                                                Image(fontAwesome: .pen)
                                            }
                                        }
                                        Button(role: .destructive) {
                                            presenter.state.delete(keyword: item.keyword)
                                        } label: {
                                            Label {
                                                Text("local_filter_delete")
                                            } icon: {
                                                Image(fontAwesome: .trash)
                                            }
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        } loadingContent: {
            MacSettingsLoadingState(title: "local_filter_title", subtitle: "local_filter_description")
        }
        .sheet(isPresented: $showingEditor, onDismiss: {
            selectedFilter = nil
        }) {
            NavigationStack {
                MacLocalFilterEditSheet(filter: selectedFilter) { keyword, forTimeline, forNotification, forSearch, isRegex in
                    let item = UiKeywordFilter(
                        keyword: keyword,
                        forTimeline: forTimeline,
                        forNotification: forNotification,
                        forSearch: forSearch,
                        expiredAt: nil,
                        isRegex: isRegex
                    )
                    if let selectedFilter {
                        if selectedFilter.keyword == keyword {
                            presenter.state.update(item: item)
                        } else {
                            presenter.state.delete(keyword: selectedFilter.keyword)
                            presenter.state.add(item: item)
                        }
                    } else {
                        presenter.state.add(item: item)
                    }
                }
            }
            .frame(width: 420, height: 340)
        }
    }
}

private struct MacLocalFilterRow: View {
    let item: UiKeywordFilter

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(item.keyword)
            HStack(spacing: 8) {
                if item.forTimeline {
                    Text("local_filter_timeline")
                }
                if item.forNotification {
                    Text("local_filter_notification")
                }
                if item.forSearch {
                    Text("local_filter_search")
                }
                if item.isRegex {
                    Text("local_filter_regex")
                }
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct MacLocalFilterEditSheet: View {
    let onConfirm: (String, Bool, Bool, Bool, Bool) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var keyword = ""
    @State private var forTimeline = true
    @State private var forNotification = true
    @State private var forSearch = true
    @State private var isRegex = false

    var body: some View {
        Form {
            Section("local_filter_keyword_header") {
                TextField("local_filter_keyword_placeholder", text: $keyword)
            }

            Section("local_filter_scope_header") {
                Toggle("local_filter_timeline", isOn: $forTimeline)
                Toggle("local_filter_notification", isOn: $forNotification)
                Toggle("local_filter_search", isOn: $forSearch)
                Toggle("local_filter_regex", isOn: $isRegex)
            }
        }
        .formStyle(.grouped)
        .navigationTitle("local_filter_edit_title")
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button {
                    dismiss()
                } label: {
                    Label {
                        Text("cancel_button")
                    } icon: {
                        Image(fontAwesome: .xmark)
                    }
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button {
                    onConfirm(keyword, forTimeline, forNotification, forSearch, isRegex)
                    dismiss()
                } label: {
                    Label {
                        Text("ok_button")
                    } icon: {
                        Image(fontAwesome: .check)
                    }
                }
                .disabled(keyword.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
    }

    init(
        filter: UiKeywordFilter?,
        onConfirm: @escaping (String, Bool, Bool, Bool, Bool) -> Void
    ) {
        self.onConfirm = onConfirm
        if let filter {
            _keyword = .init(initialValue: filter.keyword)
            _forTimeline = .init(initialValue: filter.forTimeline)
            _forNotification = .init(initialValue: filter.forNotification)
            _forSearch = .init(initialValue: filter.forSearch)
            _isRegex = .init(initialValue: filter.isRegex)
        }
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
    @StateObject private var presenter = KotlinPresenter(presenter: AiConfigPresenter())
    @State private var editingField: MacAiEditableField?
    @State private var editingText = ""

    var body: some View {
        MacSettingsForm(
            title: "ai_config_title",
            subtitle: "ai_config_description"
        ) {
            Section {
                Picker(selection: Binding(get: {
                    presenter.state.aiType
                }, set: { type in
                    presenter.state.selectType(type: type)
                })) {
                    ForEach(presenter.state.supportedTypes, id: \.name) { type in
                        Text(aiTypeOptionTitle(option: type)).tag(type)
                    }
                } label: {
                    MacSettingLabel("AI Type", subtitle: "Select AI provider")
                }

                if presenter.state.aiType == .openAi {
                    editableButton(field: .serverUrl, value: presenter.state.openAIServerUrl)
                    editableButton(field: .apiKey, value: presenter.state.openAIApiKey)

                    Picker(selection: Binding(get: {
                        presenter.state.openAIReasoningEffort
                    }, set: { effort in
                        presenter.state.setOpenAIReasoningEffort(value: effort)
                    })) {
                        ForEach(presenter.state.supportedOpenAIReasoningEfforts, id: \.name) { effort in
                            Text(reasoningEffortTitle(option: effort)).tag(effort)
                        }
                    } label: {
                        MacSettingLabel(
                            "Reasoning Effort",
                            subtitle: "Choose how much effort the model spends on reasoning. Default uses the provider's default behavior."
                        )
                    }

                    editableButton(field: .extraBody, value: presenter.state.openAIExtraBody)

                    if shouldShowManualModelInput {
                        editableButton(field: .model, value: presenter.state.openAIModel)
                    } else {
                        let selectedModel = presenter.state.openAIModel
                        Picker(selection: Binding(get: {
                            selectedModel
                        }, set: { model in
                            if !model.hasPrefix("__meta__") {
                                presenter.state.setOpenAIModel(value: model)
                            }
                        })) {
                            switch onEnum(of: presenter.state.openAIModels) {
                            case .loading:
                                if !selectedModel.isEmpty {
                                    Text(selectedModel).tag(selectedModel)
                                }
                                Text("Loading models...").tag("__meta__loading")
                            case .success(let data):
                                let models = (data.data as NSArray).cast(NSString.self).map(String.init)
                                ForEach(models, id: \.self) { model in
                                    Text(model).tag(model)
                                }
                            case .error:
                                EmptyView()
                            }
                        } label: {
                            MacSettingLabel("Model", subtitle: "AI model used for translation and summary")
                        }
                    }
                }
            }

            Section {
                Toggle(isOn: Binding(get: {
                    presenter.state.aiAgent
                }, set: { newValue in
                    presenter.state.setAIAgent(value: newValue)
                })) {
                    MacSettingLabel("ai_config_post_insight", subtitle: "ai_config_post_insight_description")
                }
            }

            Section {
                Toggle(isOn: Binding(get: {
                    presenter.state.aiTldr
                }, set: { newValue in
                    presenter.state.setAITldr(value: newValue)
                })) {
                    MacSettingLabel("ai_config_summarize", subtitle: "Summarize long text with AI")
                }

                if presenter.state.aiTldr {
                    editableButton(field: .tldrPrompt, value: presenter.state.tldrPrompt)
                }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: presenter.state.aiType == .openAi)
        .animation(.easeInOut(duration: 0.2), value: presenter.state.aiTldr)
        .sheet(item: $editingField) { field in
            MacTextEditSheet(
                title: field.title,
                text: $editingText,
                isMultiline: field.isMultiline,
                placeholder: field.placeholder,
                footer: field == .serverUrl ? serverUrlHint : (field == .extraBody ? extraBodyHint : nil),
                suggestions: field == .serverUrl ? filteredServerSuggestions(query: editingText) : [],
                onSelectSuggestion: { suggestion in
                    editingText = suggestion
                },
                onCancel: {
                    editingField = nil
                },
                onConfirm: {
                    applyEdit(field: field, value: editingText)
                    editingField = nil
                }
            )
        }
    }

    private func editableButton(field: MacAiEditableField, value: String) -> some View {
        MacSettingActionRow(
            field.title,
            value: field.displayValue(value),
            buttonTitle: "macos_action_edit",
            icon: .pen
        ) {
            editingText = value
            editingField = field
        }
    }

    private func aiTypeOptionTitle(option: AiTypeOption) -> LocalizedStringResource {
        switch option {
        case .onDevice:
            "On Device"
        case .openAi:
            "AI-compatible API"
        }
    }

    private func reasoningEffortTitle(option: AiReasoningEffortOption) -> LocalizedStringResource {
        switch option {
        case .default:
            "Default"
        case .low:
            "Low"
        case .medium:
            "Medium"
        case .high:
            "High"
        }
    }

    private var shouldShowManualModelInput: Bool {
        switch onEnum(of: presenter.state.openAIModels) {
        case .loading:
            false
        case .error:
            true
        case .success(let data):
            (data.data as NSArray).cast(NSString.self).isEmpty
        }
    }

    private var serverUrlHint: String {
        "Server URL must end with '/' and support the AI-compatible v1/chat/completions API."
    }

    private var extraBodyHint: String {
        "{\"thinking\": {\"type\": \"enabled\"}}"
    }

    private var serverSuggestions: [String] {
        (presenter.state.serverSuggestions as NSArray).cast(NSString.self).map(String.init)
    }

    private func filteredServerSuggestions(query: String) -> [String] {
        if query.isEmpty {
            serverSuggestions
        } else {
            serverSuggestions.filter { $0.localizedCaseInsensitiveContains(query) }
        }
    }

    private func applyEdit(field: MacAiEditableField, value: String) {
        switch field {
        case .serverUrl:
            presenter.state.setOpenAIServerUrl(value: value)
        case .apiKey:
            presenter.state.setOpenAIApiKey(value: value)
        case .extraBody:
            presenter.state.setOpenAIExtraBody(value: value)
        case .model:
            presenter.state.setOpenAIModel(value: value)
        case .tldrPrompt:
            presenter.state.setTldrPrompt(value: value)
        }
    }
}

private enum MacAiEditableField: String, Identifiable {
    case serverUrl
    case apiKey
    case extraBody
    case model
    case tldrPrompt

    var id: String { rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .serverUrl:
            "Server URL"
        case .apiKey:
            "API Key"
        case .extraBody:
            "Extra Body"
        case .model:
            "Manual Model"
        case .tldrPrompt:
            "Summary Prompt"
        }
    }

    var placeholder: String {
        switch self {
        case .serverUrl:
            "https://api.example.com/v1/"
        case .apiKey:
            "sk-..."
        case .extraBody:
            "{\"thinking\": {\"type\": \"enabled\"}}"
        case .model:
            "model-name"
        case .tldrPrompt:
            ""
        }
    }

    var isMultiline: Bool {
        self == .extraBody || self == .tldrPrompt
    }

    func displayValue(_ value: String) -> String {
        if value.isEmpty {
            switch self {
            case .model:
                String(localized: "Select model")
            default:
                String(localized: "Not set")
            }
        } else {
            value
        }
    }
}

private struct MacTranslationConfigSettingsPane: View {
    @StateObject private var presenter = KotlinPresenter(presenter: AiConfigPresenter())
    @StateObject private var aiTranslationTestPresenter = KotlinPresenter(presenter: AiTranslationTestPresenter())
    @State private var editingField: MacTranslationEditableField?
    @State private var editingText = ""
    @State private var showExcludedLanguagesPicker = false
    @State private var pendingExcludedLanguages: Set<String> = []
    @State private var excludedLanguagesQuery = ""

    var body: some View {
        MacSettingsForm(
            title: "settings_translation_title",
            subtitle: "settings_translation_description"
        ) {
            Section {
                Picker(selection: Binding(get: {
                    presenter.state.translateProvider
                }, set: { provider in
                    presenter.state.selectTranslateProvider(type: provider)
                })) {
                    ForEach(presenter.state.supportedTranslateProviders, id: \.name) { provider in
                        Text(translateProviderOptionTitle(option: provider)).tag(provider)
                    }
                } label: {
                    MacSettingLabel("Translation Provider", subtitle: "Choose which service handles translation")
                }

                Toggle(isOn: Binding(get: {
                    presenter.state.preTranslate
                }, set: { newValue in
                    presenter.state.setPreTranslate(value: newValue)
                })) {
                    MacSettingLabel("ai_config_pre_translate", subtitle: "ai_config_pre_translate_description")
                }

                if presenter.state.preTranslate {
                    MacSettingActionRow(
                        "Auto-translate excluded languages",
                        value: displayExcludedLanguages,
                        buttonTitle: "macos_action_edit",
                        icon: .pen
                    ) {
                        pendingExcludedLanguages = Set(excludedLanguages)
                        excludedLanguagesQuery = ""
                        showExcludedLanguagesPicker = true
                    }
                }

                providerSpecificControls
            }
        }
        .animation(.easeInOut(duration: 0.2), value: presenter.state.translateProvider.name)
        .animation(.easeInOut(duration: 0.2), value: presenter.state.preTranslate)
        .sheet(item: $editingField) { field in
            MacTextEditSheet(
                title: field.title,
                text: $editingText,
                isMultiline: true,
                placeholder: "",
                footer: nil,
                suggestions: [],
                onSelectSuggestion: { _ in },
                onCancel: {
                    editingField = nil
                },
                onConfirm: {
                    applyTranslationEdit(field: field, value: editingText)
                    editingField = nil
                }
            )
        }
        .sheet(isPresented: $showExcludedLanguagesPicker) {
            NavigationStack {
                List(selection: $pendingExcludedLanguages) {
                    ForEach(filteredLanguageOptions) { option in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(option.title)
                            if option.title != option.tag {
                                Text(option.tag)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .tag(option.tag)
                    }
                }
                .searchable(text: $excludedLanguagesQuery, prompt: "Search language")
                .navigationTitle("Auto-translate excluded languages")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button {
                            showExcludedLanguagesPicker = false
                        } label: {
                            Label {
                                Text("cancel_button")
                            } icon: {
                                Image(fontAwesome: .xmark)
                            }
                        }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button {
                            presenter.state.setAutoTranslateExcludedLanguages(
                                value: languageOptions
                                    .map(\.tag)
                                    .filter { pendingExcludedLanguages.contains($0) }
                            )
                            showExcludedLanguagesPicker = false
                        } label: {
                            Label {
                                Text("ok_button")
                            } icon: {
                                Image(fontAwesome: .check)
                            }
                        }
                    }
                }
            }
            .frame(width: 460, height: 560)
        }
    }

    @ViewBuilder
    private var providerSpecificControls: some View {
        switch presenter.state.translateProvider {
        case .ai:
            editableButton(field: .translatePrompt, value: presenter.state.translatePrompt)

            VStack(alignment: .leading, spacing: 8) {
                Text("AI translation test")
                    .font(.headline)
                Text("Run a short rich-text sample through the current AI translation setup.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text("Sample text")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                RichText(text: aiTranslationTestPresenter.state.sampleText)
                MacSettingActionRow(
                    "Test translation",
                    subtitle: "settings_translation_test_description",
                    buttonTitle: "macos_action_run",
                    icon: .play
                ) {
                    aiTranslationTestPresenter.state.runTest()
                }
                if aiTranslationTestPresenter.state.isLoading {
                    ProgressView()
                }
                if let errorMessage = aiTranslationTestPresenter.state.errorMessage {
                    Text(errorMessage)
                        .foregroundStyle(.red)
                        .font(.footnote)
                }
                if let translatedText = aiTranslationTestPresenter.state.translatedText {
                    Text("Translated text")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    RichText(text: translatedText)
                }
            }
            .padding(.vertical, 4)

        case .deepL:
            editableButton(field: .deepLApiKey, value: presenter.state.deepLApiKey)
            Toggle(isOn: Binding(get: {
                presenter.state.deepLUsePro
            }, set: { newValue in
                presenter.state.setDeepLUsePro(value: newValue)
            })) {
                MacSettingLabel("DeepL Pro Endpoint", subtitle: "Use api.deepl.com instead of the free endpoint")
            }

        case .googleCloud:
            editableButton(field: .googleCloudApiKey, value: presenter.state.googleCloudApiKey)

        case .libreTranslate:
            editableButton(field: .libreTranslateBaseUrl, value: presenter.state.libreTranslateBaseUrl)
            editableButton(field: .libreTranslateApiKey, value: presenter.state.libreTranslateApiKey)

        case .googleWeb:
            EmptyView()
        }
    }

    private func editableButton(field: MacTranslationEditableField, value: String) -> some View {
        MacSettingActionRow(
            field.title,
            value: value.isEmpty ? String(localized: "Not set") : value,
            buttonTitle: "macos_action_edit",
            icon: .pen
        ) {
            editingText = value
            editingField = field
        }
    }

    private func translateProviderOptionTitle(option: TranslateProviderOption) -> LocalizedStringResource {
        switch option {
        case .ai:
            "AI"
        case .googleWeb:
            "Google Translate (Web)"
        case .deepL:
            "DeepL"
        case .googleCloud:
            "Google Cloud Translate"
        case .libreTranslate:
            "LibreTranslate"
        }
    }

    private var excludedLanguages: [String] {
        (presenter.state.autoTranslateExcludedLanguages as NSArray).cast(NSString.self).map(String.init)
    }

    private var languageOptions: [MacLanguageOption] {
        let current = Locale.current
        let baseOptions = Locale.LanguageCode.isoLanguageCodes.map { code in
            let tag = code.identifier
            return MacLanguageOption(
                tag: tag,
                title: current.localizedString(forLanguageCode: tag) ?? tag
            )
        }
        let specialOptions = [
            MacLanguageOption(
                tag: "zh-CN",
                title: current.localizedString(forIdentifier: "zh-Hans") ?? "Chinese (Simplified)"
            ),
            MacLanguageOption(
                tag: "zh-TW",
                title: current.localizedString(forIdentifier: "zh-Hant") ?? "Chinese (Traditional)"
            )
        ]
        let knownTags = Set((specialOptions + baseOptions).map(\.tag))
        let customOptions = excludedLanguages
            .filter { !knownTags.contains($0) }
            .map { MacLanguageOption(tag: $0, title: $0) }
        return (specialOptions + baseOptions + customOptions)
            .reduce(into: [String: MacLanguageOption]()) { result, option in
                result[option.tag] = result[option.tag] ?? option
            }
            .values
            .sorted { $0.title.localizedCaseInsensitiveCompare($1.title) == .orderedAscending }
    }

    private var displayExcludedLanguages: String {
        if excludedLanguages.isEmpty {
            return String(localized: "Not set")
        }
        let titles = Dictionary(uniqueKeysWithValues: languageOptions.map { ($0.tag, $0.title) })
        return excludedLanguages.map { titles[$0] ?? $0 }.joined(separator: ", ")
    }

    private var filteredLanguageOptions: [MacLanguageOption] {
        if excludedLanguagesQuery.isEmpty {
            languageOptions
        } else {
            languageOptions.filter { option in
                option.title.localizedCaseInsensitiveContains(excludedLanguagesQuery) ||
                    option.tag.localizedCaseInsensitiveContains(excludedLanguagesQuery)
            }
        }
    }

    private func applyTranslationEdit(field: MacTranslationEditableField, value: String) {
        switch field {
        case .translatePrompt:
            presenter.state.setTranslatePrompt(value: value)
        case .deepLApiKey:
            presenter.state.setDeepLApiKey(value: value)
        case .googleCloudApiKey:
            presenter.state.setGoogleCloudApiKey(value: value)
        case .libreTranslateBaseUrl:
            presenter.state.setLibreTranslateBaseUrl(value: value)
        case .libreTranslateApiKey:
            presenter.state.setLibreTranslateApiKey(value: value)
        }
    }
}

private enum MacTranslationEditableField: String, Identifiable {
    case translatePrompt
    case deepLApiKey
    case googleCloudApiKey
    case libreTranslateBaseUrl
    case libreTranslateApiKey

    var id: String { rawValue }

    var title: LocalizedStringKey {
        switch self {
        case .translatePrompt:
            "Translate Prompt"
        case .deepLApiKey:
            "DeepL API Key"
        case .googleCloudApiKey:
            "Google Cloud API Key"
        case .libreTranslateBaseUrl:
            "LibreTranslate Base URL"
        case .libreTranslateApiKey:
            "LibreTranslate API Key"
        }
    }
}

private struct MacLanguageOption: Identifiable {
    let tag: String
    let title: String

    var id: String { tag }
}

private struct MacTextEditSheet: View {
    let title: LocalizedStringKey
    @Binding var text: String
    let isMultiline: Bool
    let placeholder: String
    let footer: String?
    let suggestions: [String]
    let onSelectSuggestion: (String) -> Void
    let onCancel: () -> Void
    let onConfirm: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if isMultiline {
                        TextEditor(text: $text)
                            .frame(minHeight: 180)
                            .font(.body.monospaced())
                    } else {
                        TextField(placeholder, text: $text)
                            .textFieldStyle(.roundedBorder)
                    }
                } footer: {
                    if let footer {
                        Text(footer)
                            .font(.footnote)
                    }
                }

                if !suggestions.isEmpty {
                    Section("Suggestions") {
                        ForEach(suggestions, id: \.self) { suggestion in
                            Button {
                                onSelectSuggestion(suggestion)
                            } label: {
                                Text(suggestion)
                                    .font(.callout.monospaced())
                                    .lineLimit(1)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
            .formStyle(.grouped)
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(action: onCancel) {
                        Label {
                            Text("cancel_button")
                        } icon: {
                            Image(fontAwesome: .xmark)
                        }
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: onConfirm) {
                        Label {
                            Text("ok_button")
                        } icon: {
                            Image(fontAwesome: .check)
                        }
                    }
                }
            }
        }
        .frame(width: 520, height: 420)
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
