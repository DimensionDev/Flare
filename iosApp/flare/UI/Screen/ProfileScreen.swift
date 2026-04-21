import SwiftUI
import SwiftUIBackports
@preconcurrency import KotlinSharedUI
import Combine

struct ProfileScreen: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.openURL) private var openURL
    let accountType: AccountType
    let userKey: MicroBlogKey?
    let onFollowingClick: (MicroBlogKey) -> Void
    let onFansClick: (MicroBlogKey) -> Void
    @StateObject private var presenter: KotlinPresenter<ProfileState>
    @State private var selectedTab: Int = 0
    @State private var showToolbarTabPicker = false
    @State private var isProfileHeaderVisible = true
    @State private var isInlineTabPickerVisible = true
    @Environment(\.appearanceSettings.timelineDisplayMode) private var timelineDisplayMode
    
    var body: some View {
        ZStack {
            if horizontalSizeClass == .regular {
                regularBody
            }
            else {
                compatBody
            }
        }
        .background(Color(timelineDisplayMode == .plain && horizontalSizeClass == .compact ? .clear : .systemGroupedBackground))
        .toolbarBackground(
            horizontalSizeClass == .compact && isProfileHeaderVisible ? Visibility.hidden : Visibility.automatic,
            for: .navigationBar
        )
        .toolbar {
            if horizontalSizeClass == .compact && showToolbarTabPicker, case .success(let userState) = onEnum(of: presenter.state.userState) {
                ToolbarItem(placement: .principal) {
                    RichText(text: userState.data.name)
                }
            }
            
            if horizontalSizeClass == .regular, case .success(let tabState) = onEnum(of: presenter.state.tabs) {
                let tabs = tabState.data.cast(ProfileState.Tab.self)
                if tabs.count > 1 {
                    ToolbarItemGroup {
                        ForEach(0..<tabs.count, id: \.self) { index in
                            let tab = tabs[index]
                            Button {
                                withAnimation(.spring) {
                                    selectedTab = index
                                }
                            } label: {
                                Text(profileTabTitle(for: tab))
                                    .foregroundStyle(selectedTab == index ? Color.accentColor : .primary)
                                    .fontWeight(selectedTab == index ? .bold : .regular)
                            }
                        }
                    }
                }
                if #available(iOS 26.0, *) {
                    ToolbarSpacer()
                }
            } else if horizontalSizeClass == .compact, case .success(let tabState) = onEnum(of: presenter.state.tabs) {
                let tabs = tabState.data.cast(ProfileState.Tab.self)
                if tabs.count > 1 && showToolbarTabPicker {
                    ToolbarItem(placement: .primaryAction) {
                        profileTabPicker(tabs: tabs)
                            .pickerStyle(.menu)
                            .fixedSize()
                    }
                }
            }
            if !presenter.state.actions.isEmpty {
                ToolbarItem(
                    placement: .primaryAction
                ) {
                    StatusActionsView(data: presenter.state.actions, useText: false, allowSpacer: false)
                }
            }
        }
    }
    
    var regularBody: some View {
        HStack(
            spacing: nil,
        ) {
            ScrollView {
                ListCardView {
                    ProfileHeader(
                        user: presenter.state.userState,
                        relation: presenter.state.relationState,
                        isMe: presenter.state.isMe,
                        onFollowClick: { user, relation in
                            handleFollowAction(user: user, relation: relation)
                        },
                        onFollowingClick: onFollowingClick,
                        onFansClick: onFansClick
                    )
                    .padding(.bottom)
                }
            }
            .padding(.leading)
            .frame(width: 400)
            StateView(state: presenter.state.tabs) { tabsArray in
                let tabs = tabsArray.cast(ProfileState.Tab.self)
                let selectedTabItem = tabs[selectedTab]
                switch onEnum(of: selectedTabItem) {
                case .timeline(let timeline):
                    ProfileTimelineWaterFallView(presenter: timeline.presenter)
                        .id(timeline.type.name)
                case .media(let media):
                    ProfileTimelineWaterFallView(presenter: media.presenter.getMediaTimelinePresenter())
                        .id("media")
                }
            }
        }
    }
    
    var compatBody: some View {
        StateView(state: presenter.state.tabs) { tabsArray in
            let tabs = tabsArray.cast(ProfileState.Tab.self)
            ProfileCompatTimelineView(
                profileState: presenter.state,
                tabs: tabs,
                selectedTab: $selectedTab,
                onFollowClick: { user, relation in
                    handleFollowAction(user: user, relation: relation)
                },
                onFollowingClick: onFollowingClick,
                onFansClick: onFansClick,
                onHeaderVisibilityChanged: { visible in
                    DispatchQueue.main.async {
                        guard isProfileHeaderVisible != visible else { return }
                        isProfileHeaderVisible = visible
                        updateToolbarTabPickerVisibility()
                    }
                },
                onPickerVisibilityChanged: { visible in
                    DispatchQueue.main.async {
                        guard isInlineTabPickerVisible != visible else { return }
                        isInlineTabPickerVisible = visible
                        updateToolbarTabPickerVisibility()
                    }
                }
            )
        }
        .detectScrolling()
        .ignoresSafeArea(edges: .vertical)
    }

    @ViewBuilder
    private func profileTabPicker(tabs: [ProfileState.Tab]) -> some View {
        ProfileTabPicker(tabs: tabs, selectedTab: $selectedTab)
    }

    private func updateToolbarTabPickerVisibility() {
        let shouldShowToolbarPicker = !isProfileHeaderVisible && !isInlineTabPickerVisible
        if showToolbarTabPicker != shouldShowToolbarPicker {
            withAnimation {
                showToolbarTabPicker = shouldShowToolbarPicker
            }
        }
    }

    private func handleFollowAction(user: UiProfile, relation: UiRelation) {
        if relation.blocking {
            if case .success(let state) = onEnum(of: presenter.state.myAccountKey) {
                let route = DeeplinkRoute.UnblockUser(accountKey: state.data, userKey: user.key)
                if let url = URL(string: route.toUri()) {
                    openURL(url)
                }
            }
        } else if relation.following || relation.hasPendingFollowRequestFromYou {
            presenter.state.unfollow(userKey: user.key)
        } else {
            presenter.state.follow(userKey: user.key)
        }
    }
}

private struct ProfileTabPicker: View {
    let tabs: [ProfileState.Tab]
    @Binding var selectedTab: Int

    var body: some View {
        Picker(selection: $selectedTab) {
            ForEach(0..<tabs.count, id: \.self) { index in
                Text(profileTabTitle(for: tabs[index]))
                    .tag(index)
            }
        } label: {
            EmptyView()
        }
    }
}

private func profileTabTitle(for tab: ProfileState.Tab) -> LocalizedStringResource {
    switch onEnum(of: tab) {
    case .media:
        LocalizedStringResource(stringLiteral: "profile_tab_media")
    case .timeline(let timeline):
        switch timeline.type {
        case .likes:
            LocalizedStringResource(stringLiteral: "profile_tab_likes")
        case .status:
            LocalizedStringResource(stringLiteral: "profile_tab_status")
        case .statusWithReplies:
            LocalizedStringResource(stringLiteral: "profile_tab_replies")
        }
    }
}

private func profileTimelineID(for tab: ProfileState.Tab) -> String {
    switch onEnum(of: tab) {
    case .timeline(let timeline):
        timeline.type.name
    case .media:
        "media"
    }
}

private func profileTimelinePresenter(for tab: ProfileState.Tab) -> TimelinePresenter {
    switch onEnum(of: tab) {
    case .timeline(let timeline):
        timeline.presenter
    case .media(let media):
        media.presenter.getMediaTimelinePresenter()
    }
}

private struct ProfileCompatTimelineView: UIViewControllerRepresentable {
    let profileState: ProfileState
    let tabs: [ProfileState.Tab]
    @Binding var selectedTab: Int
    let onFollowClick: (UiProfile, UiRelation) -> Void
    let onFollowingClick: (MicroBlogKey) -> Void
    let onFansClick: (MicroBlogKey) -> Void
    let onHeaderVisibilityChanged: (Bool) -> Void
    let onPickerVisibilityChanged: (Bool) -> Void

    @Environment(\.appearanceSettings) private var appearanceSettings
    @Environment(\.networkKind) private var networkKind
    @Environment(\.openURL) private var openURL
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIViewController(context: Context) -> CollectionViewTimelineController {
        let controller = CollectionViewTimelineController(detailStatusKey: nil)
        context.coordinator.controller = controller
        apply(to: controller, context: context)
        return controller
    }

    func updateUIViewController(_ controller: CollectionViewTimelineController, context: Context) {
        context.coordinator.controller = controller
        apply(to: controller, context: context)
    }

    static func dismantleUIViewController(_ controller: CollectionViewTimelineController, coordinator: Coordinator) {
        coordinator.close()
    }

    private func apply(to controller: CollectionViewTimelineController, context: Context) {
        controller.appearance = TimelineUIKitAppearance(settings: appearanceSettings)
        controller.networkKind = networkKind
        controller.columnCount = 1
        controller.extendsContentUnderTopBars = true
        let accessoriesChanged = context.coordinator.updateAccessories(
            profileState: profileState,
            tabs: tabs,
            selectedTab: $selectedTab,
            appearanceSettings: appearanceSettings,
            openURL: openURL,
            horizontalSizeClass: horizontalSizeClass,
            onFollowClick: onFollowClick,
            onFollowingClick: onFollowingClick,
            onFansClick: onFansClick,
            onHeaderVisibilityChanged: onHeaderVisibilityChanged,
            onPickerVisibilityChanged: onPickerVisibilityChanged
        )
        context.coordinator.installAccessoriesIfNeeded(on: controller, changed: accessoriesChanged)

        guard !tabs.isEmpty else {
            onPickerVisibilityChanged(false)
            context.coordinator.prunePresenters(validIDs: [])
            return
        }

        let clampedIndex = min(max(selectedTab, 0), tabs.count - 1)
        if clampedIndex != selectedTab {
            DispatchQueue.main.async {
                selectedTab = clampedIndex
            }
        }
        if tabs.count <= 1 {
            onPickerVisibilityChanged(false)
        }

        let tab = tabs[clampedIndex]
        context.coordinator.prunePresenters(validIDs: Set(tabs.map(profileTimelineID(for:))))
        context.coordinator.show(tab: tab, in: controller, openURL: openURL)
    }

    final class Coordinator {
        fileprivate weak var controller: CollectionViewTimelineController?
        fileprivate var accessoryItems: [CollectionViewTimelineAccessoryItem] = []

        private let headerView = ProfileHostedAccessoryView()
        private let pickerView = ProfileHostedAccessoryView()
        private var presenters: [String: KotlinPresenter<TimelineState>] = [:]
        private var cancellable: AnyCancellable?
        private var currentTabID: String?
        private weak var boundController: CollectionViewTimelineController?
        private weak var installedAccessoryController: CollectionViewTimelineController?
        private var headerSignature: ProfileHeaderAccessorySignature?
        private var pickerSignature: ProfilePickerAccessorySignature?

        @discardableResult
        func updateAccessories(
            profileState: ProfileState,
            tabs: [ProfileState.Tab],
            selectedTab: Binding<Int>,
            appearanceSettings: AppearanceSettings,
            openURL: OpenURLAction,
            horizontalSizeClass: UserInterfaceSizeClass?,
            onFollowClick: @escaping (UiProfile, UiRelation) -> Void,
            onFollowingClick: @escaping (MicroBlogKey) -> Void,
            onFansClick: @escaping (MicroBlogKey) -> Void,
            onHeaderVisibilityChanged: @escaping (Bool) -> Void,
            onPickerVisibilityChanged: @escaping (Bool) -> Void
        ) -> Bool {
            var changed = false
            let newHeaderSignature = ProfileHeaderAccessorySignature(
                profileState: profileState,
                appearanceSettings: appearanceSettings,
                horizontalSizeClass: horizontalSizeClass
            )
            if headerSignature != newHeaderSignature {
                headerSignature = newHeaderSignature
                changed = true
                headerView.update(
                    AnyView(
                        ProfileHeader(
                            user: profileState.userState,
                            relation: profileState.relationState,
                            isMe: profileState.isMe,
                            onFollowClick: onFollowClick,
                            onFollowingClick: onFollowingClick,
                            onFansClick: onFansClick
                        )
                        .environment(\.appearanceSettings, appearanceSettings)
                        .environment(\.openURL, openURL)
                        .environment(\.horizontalSizeClass, horizontalSizeClass)
                    )
                )
            }

            var newAccessoryItems = [
                CollectionViewTimelineAccessoryItem(
                    id: "profile_header",
                    view: headerView,
                    onVisibilityChanged: onHeaderVisibilityChanged
                ),
            ]

            if tabs.count > 1 {
                let newPickerSignature = ProfilePickerAccessorySignature(
                    tabs: tabs,
                    selectedTab: selectedTab.wrappedValue,
                    appearanceSettings: appearanceSettings,
                    horizontalSizeClass: horizontalSizeClass
                )
                if pickerSignature != newPickerSignature {
                    pickerSignature = newPickerSignature
                    changed = true
                    pickerView.update(
                        AnyView(
                            ProfileTabPicker(tabs: tabs, selectedTab: selectedTab)
                                .pickerStyle(.segmented)
                                .padding()
                                .environment(\.appearanceSettings, appearanceSettings)
                                .environment(\.openURL, openURL)
                                .environment(\.horizontalSizeClass, horizontalSizeClass)
                        )
                    )
                }
                newAccessoryItems.append(
                    CollectionViewTimelineAccessoryItem(
                        id: "profile_picker",
                        view: pickerView,
                        onVisibilityChanged: onPickerVisibilityChanged
                    )
                )
            } else {
                pickerSignature = nil
            }

            if accessoryItems.map(\.id) != newAccessoryItems.map(\.id) {
                changed = true
            }
            accessoryItems = newAccessoryItems
            return changed
        }

        func installAccessoriesIfNeeded(on controller: CollectionViewTimelineController, changed: Bool) {
            guard changed || installedAccessoryController !== controller else { return }
            controller.accessoryItems = accessoryItems
            installedAccessoryController = controller
        }

        func show(tab: ProfileState.Tab, in controller: CollectionViewTimelineController, openURL: OpenURLAction) {
            let tabID = profileTimelineID(for: tab)
            let switchedTabs = currentTabID != nil && currentTabID != tabID
            let offsetBeforeSwitch = controller.currentContentOffset
            let needsBinding = currentTabID != tabID || cancellable == nil || boundController !== controller

            let presenter = presenter(for: tab)
            controller.openURL = { url in
                openURL.callAsFunction(url)
            }
            controller.refreshCallback = { [weak presenter] in
                guard let presenter else { return }
                try? await presenter.state.refresh()
            }

            if needsBinding {
                currentTabID = tabID
                boundController = controller
                cancellable = presenter.$state
                    .receive(on: DispatchQueue.main)
                    .sink { [weak controller] state in
                        controller?.update(data: state.listState)
                    }
                controller.update(data: presenter.state.listState)
            }

            if switchedTabs {
                DispatchQueue.main.async { [weak controller] in
                    controller?.restoreContentOffset(offsetBeforeSwitch, animated: false)
                }
            }
        }

        func prunePresenters(validIDs: Set<String>) {
            for key in presenters.keys where !validIDs.contains(key) {
                presenters.removeValue(forKey: key)
            }
            if let currentTabID, !validIDs.contains(currentTabID) {
                self.currentTabID = nil
                cancellable = nil
            }
        }

        func close() {
            cancellable = nil
            presenters.removeAll()
            currentTabID = nil
            boundController = nil
            installedAccessoryController = nil
        }

        private func presenter(for tab: ProfileState.Tab) -> KotlinPresenter<TimelineState> {
            let id = profileTimelineID(for: tab)
            if let presenter = presenters[id] {
                return presenter
            }
            let presenter = KotlinPresenter<TimelineState>(presenter: profileTimelinePresenter(for: tab))
            presenters[id] = presenter
            return presenter
        }
    }
}

private struct ProfileHeaderAccessorySignature: Equatable {
    let userState: String
    let relationState: String
    let isMeState: String
    let appearance: TimelineUIKitAppearance
    let horizontalSizeClass: UserInterfaceSizeClass?

    init(
        profileState: ProfileState,
        appearanceSettings: AppearanceSettings,
        horizontalSizeClass: UserInterfaceSizeClass?
    ) {
        userState = Self.userStateSignature(profileState.userState)
        relationState = Self.relationStateSignature(profileState.relationState)
        isMeState = Self.isMeStateSignature(profileState.isMe)
        appearance = TimelineUIKitAppearance(settings: appearanceSettings)
        self.horizontalSizeClass = horizontalSizeClass
    }

    private static func userStateSignature(_ state: UiState<UiProfile>) -> String {
        switch onEnum(of: state) {
        case .error:
            "error"
        case .loading:
            "loading"
        case .success(let success):
            [
                "success",
                String(describing: success.data.key),
                success.data.name.raw,
                success.data.handle.canonical,
                success.data.avatar,
                success.data.banner ?? "",
                success.data.description_?.raw ?? "",
                String(describing: success.data.matrices.fansCount),
                String(describing: success.data.matrices.followsCount),
                String(describing: success.data.matrices.statusesCount),
            ].joined(separator: "|")
        }
    }

    private static func relationStateSignature(_ state: UiState<UiRelation>) -> String {
        switch onEnum(of: state) {
        case .error:
            "error"
        case .loading:
            "loading"
        case .success(let success):
            [
                "success",
                String(success.data.following),
                String(success.data.isFans),
                String(success.data.blocking),
                String(success.data.blockedBy),
                String(success.data.muted),
                String(success.data.hasPendingFollowRequestFromYou),
                String(success.data.hasPendingFollowRequestToYou),
            ].joined(separator: "|")
        }
    }

    private static func isMeStateSignature(_ state: UiState<KotlinBoolean>) -> String {
        switch onEnum(of: state) {
        case .error:
            "error"
        case .loading:
            "loading"
        case .success(let success):
            "success|\(success.data.boolValue)"
        }
    }
}

private struct ProfilePickerAccessorySignature: Equatable {
    let tabs: [String]
    let selectedTab: Int
    let appearance: TimelineUIKitAppearance
    let horizontalSizeClass: UserInterfaceSizeClass?

    init(
        tabs: [ProfileState.Tab],
        selectedTab: Int,
        appearanceSettings: AppearanceSettings,
        horizontalSizeClass: UserInterfaceSizeClass?
    ) {
        self.tabs = tabs.map(profileTimelineID(for:))
        self.selectedTab = selectedTab
        appearance = TimelineUIKitAppearance(settings: appearanceSettings)
        self.horizontalSizeClass = horizontalSizeClass
    }
}

private final class ProfileHostedAccessoryView: UIView {
    private let host = UIHostingController(rootView: AnyView(EmptyView()))

    override init(frame: CGRect) {
        super.init(frame: frame)
        commonInit()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        commonInit()
    }

    func update(_ rootView: AnyView) {
        host.rootView = rootView
        host.view.invalidateIntrinsicContentSize()
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil {
            host.willMove(toParent: nil)
            host.removeFromParent()
        } else if host.parent == nil, let parent = findParentViewController() {
            parent.addChild(host)
            host.didMove(toParent: parent)
        }
    }

    private func commonInit() {
        backgroundColor = .clear
        host.view.backgroundColor = .clear
        host.view.translatesAutoresizingMaskIntoConstraints = false
        if #available(iOS 16.0, *) {
            host.sizingOptions = [.intrinsicContentSize]
        }
        addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.topAnchor.constraint(equalTo: topAnchor),
            host.view.leadingAnchor.constraint(equalTo: leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: trailingAnchor),
            host.view.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
    }

    private func findParentViewController() -> UIViewController? {
        var responder: UIResponder? = self
        while let current = responder {
            if let viewController = current as? UIViewController {
                return viewController
            }
            responder = current.next
        }
        return nil
    }
}

extension ProfileScreen {
    init(
        accountType: AccountType,
        userKey: MicroBlogKey?,
        onFollowingClick: @escaping (MicroBlogKey) -> Void,
        onFansClick: @escaping (MicroBlogKey) -> Void
    ) {
        self.init(
            accountType: accountType,
            userKey: userKey,
            onFollowingClick: onFollowingClick,
            onFansClick: onFansClick,
            presenter: .init(presenter: ProfilePresenter(accountType: accountType, userKey: userKey))
        )
    }
}

struct ProfileHeader: View {
    let user: UiState<UiProfile>
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiProfile, UiRelation) -> Void
    let onFollowingClick: (MicroBlogKey) -> Void
    let onFansClick: (MicroBlogKey) -> Void
    var body: some View {
        switch onEnum(of: user) {
        case .error:
            Text("error")
        case .loading:
            CommonProfileHeader(
                user: createSampleUser(),
                relation: relation,
                isMe: isMe,
                onFollowClick: { _ in },
                onFollowingClick: {},
                onFansClick: {}
            )
                .redacted(reason: .placeholder)
        case .success(let data):
            ProfileHeaderSuccess(
                user: data.data,
                relation: relation,
                isMe: isMe,
                onFollowClick: { relation in onFollowClick(data.data, relation) },
                onFollowingClick: onFollowingClick,
                onFansClick: onFansClick
            )
        }
    }
}

struct ProfileHeaderSuccess: View {
    let user: UiProfile
    let relation: UiState<UiRelation>
    let isMe: UiState<KotlinBoolean>
    let onFollowClick: (UiRelation) -> Void
    let onFollowingClick: (MicroBlogKey) -> Void
    let onFansClick: (MicroBlogKey) -> Void
    var body: some View {
        CommonProfileHeader(
            user: user,
            relation: relation,
            isMe: isMe,
            onFollowClick: onFollowClick,
            onFollowingClick: {
                onFollowingClick(user.key)
            },
            onFansClick: {
                onFansClick(user.key)
            }
        )
    }
}

struct ProfileWithUserNameAndHostScreen: View {
    @StateObject private var presenter: KotlinPresenter<UserState>
    let accountType: AccountType
    let onFollowingClick: (MicroBlogKey) -> Void
    let onFansClick: (MicroBlogKey) -> Void
    
    init(userName: String, host: String, accountType: AccountType, onFollowingClick: @escaping (MicroBlogKey) -> Void, onFansClick: @escaping (MicroBlogKey) -> Void) {
        self.accountType = accountType
        self.onFollowingClick = onFollowingClick
        self.onFansClick = onFansClick
        self._presenter = .init(wrappedValue: .init(presenter: ProfileWithUserNameAndHostPresenter(userName: userName, host: host, accountType: accountType)))
    }
    var body: some View {
        StateView(state: presenter.state.user) { user in
            ProfileScreen(accountType: accountType, userKey: user.key, onFollowingClick: onFollowingClick, onFansClick: onFansClick)
        } loadingContent: {
            ProgressView()
        }
    }
}

struct ProfileTimelineView: View {
    @StateObject private var presenter: KotlinPresenter<TimelineState>
    
    init(presenter: TimelinePresenter) {
        self._presenter = .init(wrappedValue: .init(presenter: presenter))
    }
    
    var body: some View {
        TimelinePagingView(data: presenter.state.listState)
            .listRowSeparator(.hidden)
            .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
            .listRowBackground(Color.clear)
//            .refreshable {
//                try? await presenter.state.refresh()
//            }
    }
}

struct ProfileTimelineWaterFallView: View {
    @StateObject private var presenter: KotlinPresenter<TimelineState>
    
    init(presenter: TimelinePresenter) {
        self._presenter = .init(wrappedValue: .init(presenter: presenter))
    }
    
    var body: some View {
        TimelinePagingContent(data: presenter.state.listState, detailStatusKey: nil, key: presenter.key)
            .refreshable {
                try? await presenter.state.refresh()
            }
    }
}
