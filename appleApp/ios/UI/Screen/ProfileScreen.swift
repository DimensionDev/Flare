import SwiftUI
import FlareAppleUI
import SwiftUIBackports
@preconcurrency import KotlinSharedUI
import FlareAppleCore
import Combine

struct ProfileScreen: View {
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(\.openURL) private var openURL
    @Environment(\.timelineAppearance.aiConfig.agent) private var agentEnabled
    let accountType: AccountType
    let userKey: MicroBlogKey?
    let onFollowingClick: (MicroBlogKey) -> Void
    let onFansClick: (MicroBlogKey) -> Void
    let onProfileInsight: (MicroBlogKey) -> Void
    @StateObject private var presenter: KotlinPresenter<ProfileState>
    @State private var selectedTab: Int = 0
    @State private var showToolbarTabPicker = false
    @State private var isProfileHeaderVisible = true
    @State private var isInlineTabPickerVisible = true
    @State private var showBlockedProfileContent = false
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode
    
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
        .onChange(of: isBlockedProfile) { _, isBlocked in
            if !isBlocked {
                showBlockedProfileContent = false
            }
        }
        .toolbar {
            if horizontalSizeClass == .compact && showToolbarTabPicker && !shouldGateBlockedProfile, case .success(let userState) = onEnum(of: presenter.state.userState) {
                ToolbarItem(placement: .principal) {
                    RichText(text: userState.data.name)
                }
            }
            
            if !shouldGateBlockedProfile && horizontalSizeClass == .regular, case .success(let tabState) = onEnum(of: presenter.state.tabs) {
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
            } else if !shouldGateBlockedProfile && horizontalSizeClass == .compact, case .success(let tabState) = onEnum(of: presenter.state.tabs) {
                let tabs = tabState.data.cast(ProfileState.Tab.self)
                if tabs.count > 1 && showToolbarTabPicker {
                    ToolbarItem(placement: .primaryAction) {
                        profileTabPicker(tabs: tabs)
                            .pickerStyle(.menu)
                            .fixedSize()
                    }
                }
            }
            if agentEnabled || !presenter.state.actions.isEmpty {
                ToolbarItemGroup(placement: .primaryAction) {
                    if agentEnabled, case .success(let userState) = onEnum(of: presenter.state.userState) {
                        Button {
                            onProfileInsight(userState.data.key)
                        } label: {
                            Image(fontAwesome: .robot)
                        }
                        .accessibilityLabel(Text(String(localized: "profile_insight_title", defaultValue: "Profile insight")))
                    }
                    if !presenter.state.actions.isEmpty {
                        StatusActionsView(data: presenter.state.actions, useText: false, allowSpacer: false)
                    }
                }
            }
        }
    }
    
    var regularBody: some View {
        HStack(spacing: nil) {
            ScrollView {
                ListCardView {
                    ProfileHeader(
                        user: presenter.state.userState,
                        relation: presenter.state.relationState,
                        followButtonState: presenter.state.followButtonState,
                        isMe: presenter.state.isMe,
                        onFollowClick: { user, followButtonState in
                            handleFollowAction(user: user, followButtonState: followButtonState)
                        },
                        onFollowingClick: onFollowingClick,
                        onFansClick: onFansClick
                    )
                    .padding(.bottom)
                }
            }
            .padding(.leading)
            .frame(width: 400)

            if shouldGateBlockedProfile {
                BlockedProfileGate {
                    showBlockedProfileContent = true
                }
                .padding(.horizontal)
                .padding(.top, 24)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            } else {
                StateView(state: presenter.state.tabs) { tabsArray in
                    let tabs = tabsArray.cast(ProfileState.Tab.self)
                    profileTimelineCollection(
                        tabs: tabs,
                        showsProfileAccessories: false
                    )
                } loadingContent: {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ProfileTabsLoadingPlaceholder()
                            ProfileTimelineLoadingPlaceholder()
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                }
            }
        }
    }
    
    @ViewBuilder
    var compatBody: some View {
        if shouldGateBlockedProfile {
            ScrollView {
                ProfileHeader(
                    user: presenter.state.userState,
                    relation: presenter.state.relationState,
                    followButtonState: presenter.state.followButtonState,
                    isMe: presenter.state.isMe,
                    onFollowClick: { user, followButtonState in
                        handleFollowAction(user: user, followButtonState: followButtonState)
                    },
                    onFollowingClick: onFollowingClick,
                    onFansClick: onFansClick
                )
                .padding(.bottom)
                BlockedProfileGate {
                    showBlockedProfileContent = true
                }
                .padding(.horizontal)
                .padding(.bottom, 24)
            }
            .detectScrolling()
            .ignoresSafeArea(edges: .vertical)
            .onAppear {
                isProfileHeaderVisible = true
                isInlineTabPickerVisible = false
                updateToolbarTabPickerVisibility()
            }
        } else {
            StateView(state: presenter.state.tabs) { tabsArray in
                let tabs = tabsArray.cast(ProfileState.Tab.self)
                profileTimelineCollection(
                    tabs: tabs,
                    showsProfileAccessories: true
                )
            } loadingContent: {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ProfileTabsLoadingPlaceholder()
                        ProfileTimelineLoadingPlaceholder()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            }
            .detectScrolling()
            .ignoresSafeArea(edges: .vertical)
        }
    }

    private func profileTimelineCollection(
        tabs: [ProfileState.Tab],
        showsProfileAccessories: Bool
    ) -> some View {
        GeometryReader { proxy in
            ProfileTimelineCollectionView(
                profileState: presenter.state,
                tabs: tabs,
                selectedTab: $selectedTab,
                showsProfileAccessories: showsProfileAccessories,
                timelineColumnCount: showsProfileAccessories
                    ? 1
                    : max(Int((proxy.size.width / 320).rounded(.down)), 1),
                onFollowClick: { user, followButtonState in
                    handleFollowAction(user: user, followButtonState: followButtonState)
                },
                onFollowingClick: onFollowingClick,
                onFansClick: onFansClick,
                onHeaderVisibilityChanged: { visible in
                    guard showsProfileAccessories else { return }
                    DispatchQueue.main.async {
                        guard isProfileHeaderVisible != visible else { return }
                        isProfileHeaderVisible = visible
                        updateToolbarTabPickerVisibility()
                    }
                },
                onPickerVisibilityChanged: { visible in
                    guard showsProfileAccessories else { return }
                    DispatchQueue.main.async {
                        guard isInlineTabPickerVisible != visible else { return }
                        isInlineTabPickerVisible = visible
                        updateToolbarTabPickerVisibility()
                    }
                }
            )
        }
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

    private func handleFollowAction(user: UiProfile, followButtonState: FollowButtonState) {
        switch onEnum(of: followButtonState) {
        case .blocked:
            if case .success(let state) = onEnum(of: presenter.state.myAccountKey) {
                let route = DeeplinkRoute.UnblockUser(accountKey: state.data, userKey: user.key)
                if let url = URL(string: route.toUri()) {
                    openURL(url)
                }
            }
        case .following, .requested:
            presenter.state.unfollow(userKey: user.key)
        case .follow, .requestFollow:
            presenter.state.follow(userKey: user.key)
        }
    }

    private var isBlockedProfile: Bool {
        if case .success(let relationState) = onEnum(of: presenter.state.relationState) {
            return relationState.data.blocking
        }
        return false
    }

    private var shouldGateBlockedProfile: Bool {
        isBlockedProfile && !showBlockedProfileContent
    }
}

private struct BlockedProfileGate: View {
    let onShow: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(String(localized: "profile_blocked_gate_title", defaultValue: "Blocked profile"))
                .font(.headline)
            Text(String(localized: "profile_blocked_gate_description", defaultValue: "You blocked this user. Their tabs and timeline are hidden until you choose to show them."))
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button {
                onShow()
            } label: {
                Text(String(localized: "profile_blocked_gate_show", defaultValue: "Show"))
            }
            .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
private struct ProfileTimelineCollectionView: UIViewControllerRepresentable {
    let profileState: ProfileState
    let tabs: [ProfileState.Tab]
    @Binding var selectedTab: Int
    let showsProfileAccessories: Bool
    let timelineColumnCount: Int
    let onFollowClick: (UiProfile, FollowButtonState) -> Void
    let onFollowingClick: (MicroBlogKey) -> Void
    let onFansClick: (MicroBlogKey) -> Void
    let onHeaderVisibilityChanged: (Bool) -> Void
    let onPickerVisibilityChanged: (Bool) -> Void

    @Environment(\.timelineAppearance) private var timelineAppearance
    @Environment(\.globalAppearance) private var globalAppearance
    @Environment(\.translateConfig) private var translateConfig
    @Environment(\.networkKind) private var networkKind
    @Environment(\.openURL) private var openURL
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIViewController(context: Context) -> UITimelineCollectionViewController {
        let controller = UITimelineCollectionViewController(detailStatusKey: nil)
        context.coordinator.controller = controller
        apply(to: controller, context: context)
        return controller
    }

    func updateUIViewController(_ controller: UITimelineCollectionViewController, context: Context) {
        context.coordinator.controller = controller
        apply(to: controller, context: context)
    }

    static func dismantleUIViewController(_ controller: UITimelineCollectionViewController, coordinator: Coordinator) {
        coordinator.close()
    }

    private func apply(to controller: UITimelineCollectionViewController, context: Context) {
        let appearance = TimelineUIKitAppearance(
            timeline: timelineAppearance,
            fontSizeDiff: globalAppearance.fontSizeDiff,
            showOriginalWithTranslation: translateConfig.showOriginalWithTranslation
        )
        controller.appearance = appearance
        controller.usesGroupedBackgroundOverride = appearance.usesCardBackground || timelineColumnCount > 1
        controller.networkKind = networkKind
        controller.extendsContentUnderTopBars = true
        controller.suppressInitialRefreshIndicator = true
        let accessoriesChanged = context.coordinator.updateAccessories(
            showsProfileAccessories: showsProfileAccessories,
            profileState: profileState,
            tabs: tabs,
            selectedTab: $selectedTab,
            timelineAppearance: timelineAppearance,
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
        context.coordinator.show(
            tab: tab,
            timelineColumnCount: timelineColumnCount,
            in: controller,
            openURL: openURL
        )
    }

    final class Coordinator {
        fileprivate weak var controller: UITimelineCollectionViewController?
        fileprivate var accessoryItems: [UITimelineCollectionViewAccessoryItem] = []

        private let headerView = ProfileHostedAccessoryView()
        private let pickerView = ProfileHostedAccessoryView()
        private var timelinePresenters: [String: KotlinPresenter<TimelineState>] = [:]
        private var mediaPresenters: [String: KotlinPresenter<ProfileMediaState>] = [:]
        private var cancellable: AnyCancellable?
        private var currentTabID: String?
        private var currentTimelineColumnCount: Int?
        private weak var boundController: UITimelineCollectionViewController?
        private weak var installedAccessoryController: UITimelineCollectionViewController?
        private var headerSignature: ProfileHeaderAccessorySignature?
        private var pickerSignature: ProfilePickerAccessorySignature?

        @discardableResult
        func updateAccessories(
            showsProfileAccessories: Bool,
            profileState: ProfileState,
            tabs: [ProfileState.Tab],
            selectedTab: Binding<Int>,
            timelineAppearance: TimelineAppearance,
            openURL: OpenURLAction,
            horizontalSizeClass: UserInterfaceSizeClass?,
            onFollowClick: @escaping (UiProfile, FollowButtonState) -> Void,
            onFollowingClick: @escaping (MicroBlogKey) -> Void,
            onFansClick: @escaping (MicroBlogKey) -> Void,
            onHeaderVisibilityChanged: @escaping (Bool) -> Void,
            onPickerVisibilityChanged: @escaping (Bool) -> Void
        ) -> Bool {
            guard showsProfileAccessories else {
                let changed = !accessoryItems.isEmpty
                accessoryItems = []
                headerSignature = nil
                pickerSignature = nil
                onHeaderVisibilityChanged(false)
                onPickerVisibilityChanged(false)
                return changed
            }

            var changed = false
            let newHeaderSignature = ProfileHeaderAccessorySignature(
                profileState: profileState,
                timelineAppearance: timelineAppearance,
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
                            followButtonState: profileState.followButtonState,
                            isMe: profileState.isMe,
                            onFollowClick: onFollowClick,
                            onFollowingClick: onFollowingClick,
                            onFansClick: onFansClick
                        )
                        .environment(\.timelineAppearance, timelineAppearance)
                        .environment(\.openURL, openURL)
                        .environment(\.horizontalSizeClass, horizontalSizeClass)
                    )
                )
            }

            var newAccessoryItems = [
                UITimelineCollectionViewAccessoryItem(
                    id: "profile_header",
                    view: headerView,
                    onVisibilityChanged: onHeaderVisibilityChanged
                ),
            ]

            if tabs.count > 1 {
                let newPickerSignature = ProfilePickerAccessorySignature(
                    tabs: tabs,
                    timelineAppearance: timelineAppearance,
                    horizontalSizeClass: horizontalSizeClass
                )
                if pickerSignature != newPickerSignature {
                    pickerSignature = newPickerSignature
                    changed = true
                    pickerView.update(
                        AnyView(
                            ProfileTabBar(tabs: tabs, selectedTab: selectedTab)
                                .environment(\.timelineAppearance, timelineAppearance)
                                .environment(\.openURL, openURL)
                                .environment(\.horizontalSizeClass, horizontalSizeClass)
                        )
                    )
                }
                newAccessoryItems.append(
                    UITimelineCollectionViewAccessoryItem(
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

        func installAccessoriesIfNeeded(on controller: UITimelineCollectionViewController, changed: Bool) {
            guard changed || installedAccessoryController !== controller else { return }
            controller.accessoryItems = accessoryItems
            installedAccessoryController = controller
        }

        func show(
            tab: ProfileState.Tab,
            timelineColumnCount: Int,
            in controller: UITimelineCollectionViewController,
            openURL: OpenURLAction
        ) {
            let tabID = profileTimelineID(for: tab)
            let switchedTabs = currentTabID != nil && currentTabID != tabID
            let offsetBeforeSwitch = controller.currentContentOffset
            let needsBinding = currentTabID != tabID ||
                cancellable == nil ||
                boundController !== controller ||
                currentTimelineColumnCount != timelineColumnCount

            if switchedTabs {
                controller.restoreContentOffsetAfterNextSnapshot(offsetBeforeSwitch)
            }

            controller.openURL = { url in
                openURL.callAsFunction(url)
            }

            switch onEnum(of: tab) {
            case .timeline(let tab):
                let presenter: KotlinPresenter<TimelineState>
                if let cached = timelinePresenters[tabID] {
                    presenter = cached
                } else {
                    presenter = KotlinPresenter<TimelineState>(presenter: tab.presenter)
                    timelinePresenters[tabID] = presenter
                }
                controller.refreshCallback = { [weak presenter] in
                    guard let presenter else { return }
                    try? await presenter.state.refresh()
                }
                if needsBinding {
                    currentTabID = tabID
                    currentTimelineColumnCount = timelineColumnCount
                    boundController = controller
                    controller.resetInitialRefreshIndicatorSuppression()
                    cancellable = presenter.$state
                        .sink { [weak controller] state in
                            controller?.update(data: state.listState, columnCount: timelineColumnCount)
                        }
                }
            case .media(let tab):
                let presenter: KotlinPresenter<ProfileMediaState>
                if let cached = mediaPresenters[tabID] {
                    presenter = cached
                } else {
                    presenter = KotlinPresenter<ProfileMediaState>(presenter: tab.presenter)
                    mediaPresenters[tabID] = presenter
                }
                controller.refreshCallback = { [weak presenter] in
                    guard let presenter else { return }
                    switch onEnum(of: presenter.state.mediaState) {
                    case .success(let success):
                        try? await skie(success).refreshSuspend()
                    case .empty(let empty):
                        empty.refresh()
                    default:
                        break
                    }
                }
                if needsBinding {
                    currentTabID = tabID
                    currentTimelineColumnCount = timelineColumnCount
                    boundController = controller
                    controller.resetInitialRefreshIndicatorSuppression()
                    cancellable = presenter.$state
                        .sink { [weak controller] state in
                            controller?.update(profileMediaData: state.mediaState)
                        }
                }
            }
        }

        func prunePresenters(validIDs: Set<String>) {
            for key in timelinePresenters.keys where !validIDs.contains(key) {
                timelinePresenters.removeValue(forKey: key)
            }
            for key in mediaPresenters.keys where !validIDs.contains(key) {
                mediaPresenters.removeValue(forKey: key)
            }
            if let currentTabID, !validIDs.contains(currentTabID) {
                self.currentTabID = nil
                currentTimelineColumnCount = nil
                cancellable = nil
            }
        }

        func close() {
            cancellable = nil
            timelinePresenters.removeAll()
            mediaPresenters.removeAll()
            currentTabID = nil
            currentTimelineColumnCount = nil
            boundController = nil
            installedAccessoryController = nil
        }
    }
}

private struct ProfileHeaderAccessorySignature: Equatable {
    let userState: String
    let relationState: String
    let followButtonState: String
    let isMeState: String
    let appearance: TimelineUIKitAppearance
    let horizontalSizeClass: UserInterfaceSizeClass?

    init(
        profileState: ProfileState,
        timelineAppearance: TimelineAppearance,
        horizontalSizeClass: UserInterfaceSizeClass?
    ) {
        userState = Self.userStateSignature(profileState.userState)
        relationState = Self.relationStateSignature(profileState.relationState)
        followButtonState = Self.followButtonStateSignature(profileState.followButtonState)
        isMeState = Self.isMeStateSignature(profileState.isMe)
        appearance = TimelineUIKitAppearance(timeline: timelineAppearance)
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
                success.data.avatar?.url ?? "",
                success.data.banner?.url ?? "",
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

    private static func followButtonStateSignature(_ state: UiState<FollowButtonState>) -> String {
        switch onEnum(of: state) {
        case .error:
            "error"
        case .loading:
            "loading"
        case .success(let success):
            "success|\(success.data.id)"
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
    let appearance: TimelineUIKitAppearance
    let horizontalSizeClass: UserInterfaceSizeClass?

    init(
        tabs: [ProfileState.Tab],
        timelineAppearance: TimelineAppearance,
        horizontalSizeClass: UserInterfaceSizeClass?
    ) {
        self.tabs = tabs.map(profileTimelineID(for:))
        appearance = TimelineUIKitAppearance(timeline: timelineAppearance)
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
        onFansClick: @escaping (MicroBlogKey) -> Void,
        onProfileInsight: @escaping (MicroBlogKey) -> Void = { _ in }
    ) {
        self.init(
            accountType: accountType,
            userKey: userKey,
            onFollowingClick: onFollowingClick,
            onFansClick: onFansClick,
            onProfileInsight: onProfileInsight,
            presenter: .init(presenter: ProfilePresenter(accountType: accountType, userKey: userKey))
        )
    }
}

struct ProfileWithUserNameAndHostScreen: View {
    @StateObject private var presenter: KotlinPresenter<UserState>
    let accountType: AccountType
    let onFollowingClick: (MicroBlogKey) -> Void
    let onFansClick: (MicroBlogKey) -> Void
    let onProfileInsight: (MicroBlogKey) -> Void
    
    init(
        userName: String,
        host: String,
        accountType: AccountType,
        onFollowingClick: @escaping (MicroBlogKey) -> Void,
        onFansClick: @escaping (MicroBlogKey) -> Void,
        onProfileInsight: @escaping (MicroBlogKey) -> Void = { _ in }
    ) {
        self.accountType = accountType
        self.onFollowingClick = onFollowingClick
        self.onFansClick = onFansClick
        self.onProfileInsight = onProfileInsight
        self._presenter = .init(wrappedValue: .init(presenter: ProfileWithUserNameAndHostPresenter(userName: userName, host: host, accountType: accountType)))
    }
    var body: some View {
        StateView(state: presenter.state.user) { user in
            ProfileScreen(
                accountType: accountType,
                userKey: user.key,
                onFollowingClick: onFollowingClick,
                onFansClick: onFansClick,
                onProfileInsight: onProfileInsight
            )
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
        TimelinePagingListContent(data: presenter.state.listState)
//            .refreshable {
//                try? await presenter.state.refresh()
//            }
    }
}
