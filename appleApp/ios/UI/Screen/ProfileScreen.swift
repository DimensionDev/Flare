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
            horizontalSizeClass == .compact ? Visibility.hidden : Visibility.automatic,
            for: .navigationBar
        )
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: isBlockedProfile) { _, isBlocked in
            if !isBlocked {
                showBlockedProfileContent = false
            }
        }
        .toolbar {
            if horizontalSizeClass == .compact && !isProfileHeaderVisible && !shouldGateBlockedProfile, case .success(let userState) = onEnum(of: presenter.state.userState) {
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
                } errorContent: { error in
                    ListErrorView(error: error) {
                        presenter.state.retryTabs()
                    }
                } loadingContent: {
                    GeometryReader { proxy in
                        ScrollView {
                            LazyVStack(spacing: 0) {
                                ProfileTabsLoadingPlaceholder()
                                ProfileTimelineUIKitLoadingPlaceholder(
                                    columnCount: max(Int((proxy.size.width / 320).rounded(.down)), 1)
                                )
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    }
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
            } errorContent: { error in
                ListErrorView(error: error) {
                    presenter.state.retryTabs()
                }
            } loadingContent: {
                ScrollView {
                    LazyVStack(spacing: 0) {
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
                        ProfileTabsLoadingPlaceholder()
                        ProfileTimelineUIKitLoadingPlaceholder(columnCount: 1)
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

private struct ProfileTimelineUIKitLoadingPlaceholder: View {
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode

    let columnCount: Int

    private let placeholderCount = TimelineUIKitLayoutMetrics.timelinePlaceholderCount

    var body: some View {
        let resolvedColumnCount = max(columnCount, 1)
        let isPlainTimelineDisplayMode = timelineDisplayMode == .plain

        if resolvedColumnCount > 1 {
            LazyVGrid(
                columns: Array(
                    repeating: GridItem(
                        .flexible(),
                        spacing: TimelineUIKitLayoutMetrics.columnSpacing
                    ),
                    count: resolvedColumnCount
                ),
                spacing: 0
            ) {
                placeholderCards(
                    isPlainTimelineDisplayMode: isPlainTimelineDisplayMode,
                    isMultipleColumn: true
                )
            }
            .padding(.horizontal, TimelineUIKitLayoutMetrics.horizontalInset)
        } else {
            LazyVStack(spacing: TimelineUIKitLayoutMetrics.rowSpacing) {
                placeholderCards(
                    isPlainTimelineDisplayMode: isPlainTimelineDisplayMode,
                    isMultipleColumn: false
                )
            }
            .padding(
                .horizontal,
                isPlainTimelineDisplayMode ? 0 : TimelineUIKitLayoutMetrics.horizontalInset
            )
        }
    }

    @ViewBuilder
    private func placeholderCards(
        isPlainTimelineDisplayMode: Bool,
        isMultipleColumn: Bool
    ) -> some View {
        ForEach(0..<placeholderCount, id: \.self) { index in
            TimelineUIKitPlaceholderCard(
                index: index,
                totalCount: placeholderCount,
                isPlainTimelineDisplayMode: isPlainTimelineDisplayMode,
                isMultipleColumn: isMultipleColumn
            )
        }
    }
}

private struct TimelineUIKitPlaceholderCard: UIViewRepresentable {
    let index: Int
    let totalCount: Int
    let isPlainTimelineDisplayMode: Bool
    let isMultipleColumn: Bool

    func makeUIView(context: Context) -> AdaptiveTimelineCardUIView {
        makeTimelinePlaceholderCardUIView()
    }

    func updateUIView(_ view: AdaptiveTimelineCardUIView, context: Context) {
        view.isPlainTimelineDisplayMode = isPlainTimelineDisplayMode
        view.isMultipleColumn = isMultipleColumn
        view.configure(index: index, totalCount: totalCount)
    }

    func sizeThatFits(
        _ proposal: ProposedViewSize,
        uiView: AdaptiveTimelineCardUIView,
        context: Context
    ) -> CGSize? {
        guard let width = proposal.width, width > 0 else { return nil }
        return uiView.sizeThatFits(
            CGSize(width: width, height: .greatestFiniteMagnitude)
        )
    }
}

private final class ProfileTabSelectionProgress: ObservableObject {
    @Published private(set) var pagePosition: CGFloat = 0

    func update(_ pagePosition: CGFloat) {
        guard abs(self.pagePosition - pagePosition) > 0.0001 else { return }
        self.pagePosition = pagePosition
    }
}

private struct ProfileProgressTabBar: View {
    let tabs: [ProfileState.Tab]
    @Binding var selectedTab: Int
    @ObservedObject var progress: ProfileTabSelectionProgress

    var body: some View {
        ProfileTabBar(
            tabs: tabs,
            selectedTab: $selectedTab,
            selectionProgress: progress.pagePosition
        )
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

    func makeUIViewController(context: Context) -> ProfileTimelinePagerViewController {
        let controller = ProfileTimelinePagerViewController()
        apply(to: controller, context: context)
        return controller
    }

    func updateUIViewController(_ controller: ProfileTimelinePagerViewController, context: Context) {
        apply(to: controller, context: context)
    }

    static func dismantleUIViewController(_ controller: ProfileTimelinePagerViewController, coordinator: Coordinator) {
        controller.onPagePositionChanged = nil
        coordinator.close()
    }

    private func apply(to controller: ProfileTimelinePagerViewController, context: Context) {
        let appearance = TimelineUIKitAppearance(
            timeline: timelineAppearance,
            fontSizeDiff: globalAppearance.fontSizeDiff,
            showOriginalWithTranslation: translateConfig.showOriginalWithTranslation
        )
        let accessories = context.coordinator.updateAccessories(
            showsProfileAccessories: showsProfileAccessories,
            profileState: profileState,
            tabs: tabs,
            selectedTab: $selectedTab,
            timelineAppearance: timelineAppearance,
            openURL: openURL,
            horizontalSizeClass: horizontalSizeClass,
            onFollowClick: onFollowClick,
            onFollowingClick: onFollowingClick,
            onFansClick: onFansClick
        )
        controller.onHeaderVisibilityChanged = onHeaderVisibilityChanged
        controller.onPickerVisibilityChanged = onPickerVisibilityChanged
        let tabSelectionProgress = context.coordinator.tabSelectionProgress
        controller.onPagePositionChanged = { [weak tabSelectionProgress] pagePosition in
            tabSelectionProgress?.update(pagePosition)
        }
        controller.setAccessories(header: accessories.header, picker: accessories.picker)
        controller.setBackgroundColor(
            appearance.usesCardBackground || timelineColumnCount > 1
                ? .systemGroupedBackground
                : .systemBackground
        )

        let selectedTabBinding = $selectedTab
        controller.onSelectedIndexChanged = { index in
            guard selectedTabBinding.wrappedValue != index else { return }
            selectedTabBinding.wrappedValue = index
        }

        guard !tabs.isEmpty else {
            controller.setPages([], selectedIndex: 0)
            context.coordinator.prunePages(validIDs: [])
            return
        }

        let clampedIndex = min(max(selectedTab, 0), tabs.count - 1)
        if clampedIndex != selectedTab {
            DispatchQueue.main.async {
                selectedTab = clampedIndex
            }
        }
        let validIDs = Set(tabs.map(profileTimelineID(for:)))
        let coordinator = context.coordinator
        let pageConfigurations = tabs.map { tab in
            ProfileTimelinePageConfiguration(
                id: profileTimelineID(for: tab),
                makeController: {
                    coordinator.pageController(
                        for: tab,
                        timelineColumnCount: timelineColumnCount,
                        appearance: appearance,
                        networkKind: networkKind,
                        extendsContentUnderTopBars: showsProfileAccessories,
                        openURL: openURL
                    )
                }
            )
        }
        controller.setPages(pageConfigurations, selectedIndex: clampedIndex)
        context.coordinator.prunePages(validIDs: validIDs)
    }

    final class Coordinator {
        private final class PageRecord {
            enum Kind {
                case timeline
                case media
            }

            let controller = UITimelineCollectionViewController(detailStatusKey: nil)
            var cancellable: AnyCancellable?
            var columnCount: Int?
            var kind: Kind?
            var presenterSource: AnyObject?
            var timelinePresenter: KotlinPresenter<TimelineState>?
            var mediaPresenter: KotlinPresenter<ProfileMediaState>?

            func prepare(kind: Kind, columnCount: Int, presenterSource: AnyObject) -> Bool {
                let kindChanged = self.kind != kind
                let presenterChanged = self.presenterSource !== presenterSource
                if kindChanged || presenterChanged {
                    cancellable = nil
                    timelinePresenter = nil
                    mediaPresenter = nil
                }
                let needsBinding = cancellable == nil || self.columnCount != columnCount
                self.kind = kind
                self.columnCount = columnCount
                self.presenterSource = presenterSource
                return needsBinding
            }

            func close() {
                cancellable = nil
                controller.onContentOffsetChanged = nil
                controller.onScrollInteractionBegan = nil
                controller.refreshCallback = nil
                columnCount = nil
                kind = nil
                presenterSource = nil
                timelinePresenter = nil
                mediaPresenter = nil
            }
        }

        private let headerView = ProfileHostedAccessoryView(ignoresSafeArea: true)
        private let pickerView = ProfileHostedAccessoryView(ignoresSafeArea: false)
        let tabSelectionProgress = ProfileTabSelectionProgress()
        private var pageRecords: [String: PageRecord] = [:]
        private var headerSignature: ProfileHeaderAccessorySignature?
        private var pickerSignature: ProfilePickerAccessorySignature?

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
            onFansClick: @escaping (MicroBlogKey) -> Void
        ) -> (header: UIView?, picker: UIView?) {
            guard showsProfileAccessories else {
                headerSignature = nil
                pickerSignature = nil
                return (nil, nil)
            }

            let newHeaderSignature = ProfileHeaderAccessorySignature(
                profileState: profileState,
                timelineAppearance: timelineAppearance,
                horizontalSizeClass: horizontalSizeClass
            )
            if headerSignature != newHeaderSignature {
                headerSignature = newHeaderSignature
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

            if tabs.count > 1 {
                let newPickerSignature = ProfilePickerAccessorySignature(
                    tabs: tabs,
                    timelineAppearance: timelineAppearance,
                    horizontalSizeClass: horizontalSizeClass
                )
                if pickerSignature != newPickerSignature {
                    pickerSignature = newPickerSignature
                    pickerView.update(
                        AnyView(
                            ProfileProgressTabBar(
                                tabs: tabs,
                                selectedTab: selectedTab,
                                progress: tabSelectionProgress
                            )
                                .environment(\.timelineAppearance, timelineAppearance)
                                .environment(\.openURL, openURL)
                                .environment(\.horizontalSizeClass, horizontalSizeClass)
                        )
                    )
                }
            } else {
                pickerSignature = nil
            }
            return (headerView, tabs.count > 1 ? pickerView : nil)
        }

        func pageController(
            for tab: ProfileState.Tab,
            timelineColumnCount: Int,
            appearance: TimelineUIKitAppearance,
            networkKind: NetworkKind,
            extendsContentUnderTopBars: Bool,
            openURL: OpenURLAction
        ) -> UITimelineCollectionViewController {
            let tabID = profileTimelineID(for: tab)
            let record = pageRecords[tabID] ?? PageRecord()
            pageRecords[tabID] = record
            let controller = record.controller
            controller.appearance = appearance
            controller.usesGroupedBackgroundOverride = appearance.usesCardBackground || timelineColumnCount > 1
            controller.networkKind = networkKind
            controller.extendsContentUnderTopBars = extendsContentUnderTopBars
            controller.topScrollIndicatorInset = 0
            controller.suppressInitialRefreshIndicator = true
            controller.restoresScrollAnchorOnSnapshotChanges = false
            controller.accessoryItems = []
            controller.openURL = { url in
                openURL.callAsFunction(url)
            }

            switch onEnum(of: tab) {
            case .timeline(let tab):
                let needsBinding = record.prepare(
                    kind: .timeline,
                    columnCount: timelineColumnCount,
                    presenterSource: tab.presenter
                )
                if needsBinding {
                    controller.resetInitialRefreshIndicatorSuppression()
                }
                let presenter: KotlinPresenter<TimelineState>
                if let cached = record.timelinePresenter {
                    presenter = cached
                } else {
                    presenter = KotlinPresenter<TimelineState>(presenter: tab.presenter)
                    record.timelinePresenter = presenter
                }
                controller.refreshCallback = { [weak presenter] in
                    guard let presenter else { return }
                    try? await presenter.state.refresh()
                }
                if needsBinding {
                    record.cancellable = presenter.$state
                        .sink { [weak controller] state in
                            controller?.update(data: state.listState, columnCount: timelineColumnCount)
                        }
                }
            case .media(let tab):
                let needsBinding = record.prepare(
                    kind: .media,
                    columnCount: timelineColumnCount,
                    presenterSource: tab.presenter
                )
                if needsBinding {
                    controller.resetInitialRefreshIndicatorSuppression()
                }
                let presenter: KotlinPresenter<ProfileMediaState>
                if let cached = record.mediaPresenter {
                    presenter = cached
                } else {
                    presenter = KotlinPresenter<ProfileMediaState>(presenter: tab.presenter)
                    record.mediaPresenter = presenter
                }
                controller.refreshCallback = { [weak presenter] in
                    guard let presenter else { return }
                    try? await presenter.state.refreshSuspend()
                }
                if needsBinding {
                    record.cancellable = presenter.$state
                        .sink { [weak controller] state in
                            controller?.update(profileMediaData: state.mediaState)
                        }
                }
            }
            return controller
        }

        func removePage(id: String) {
            pageRecords.removeValue(forKey: id)?.close()
        }

        func prunePages(validIDs: Set<String>) {
            for key in pageRecords.keys.filter({ !validIDs.contains($0) }) {
                removePage(id: key)
            }
        }

        func close() {
            pageRecords.values.forEach { $0.close() }
            pageRecords.removeAll()
        }
    }
}

private struct ProfileTimelinePageConfiguration {
    let id: String
    let makeController: () -> UITimelineCollectionViewController
}

private final class ProfileTimelinePageViewController: UIViewController {
    let id: String
    private(set) var timelineController: UITimelineCollectionViewController?
    private var makeController: () -> UITimelineCollectionViewController
    private var pageBackgroundColor: UIColor

    init(configuration: ProfileTimelinePageConfiguration, backgroundColor: UIColor) {
        id = configuration.id
        makeController = configuration.makeController
        pageBackgroundColor = backgroundColor
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func loadView() {
        let view = UIView()
        view.backgroundColor = pageBackgroundColor
        self.view = view
    }

    func update(configuration: ProfileTimelinePageConfiguration) {
        makeController = configuration.makeController
    }

    func setBackgroundColor(_ color: UIColor) {
        pageBackgroundColor = color
        if isViewLoaded {
            view.backgroundColor = color
        }
    }

    func activate() -> UITimelineCollectionViewController {
        let controller = makeController()
        guard timelineController !== controller else { return controller }

        if let timelineController {
            timelineController.willMove(toParent: nil)
            timelineController.view.removeFromSuperview()
            timelineController.removeFromParent()
        }

        timelineController = controller
        addChild(controller)
        controller.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(controller.view)
        NSLayoutConstraint.activate([
            controller.view.topAnchor.constraint(equalTo: view.topAnchor),
            controller.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            controller.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            controller.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        controller.didMove(toParent: self)
        return controller
    }
}

private final class ProfileAccessoryScrollView: UIScrollView {
    weak var headerView: UIView?
    weak var pickerView: UIView?

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        guard super.point(inside: point, with: event) else { return false }
        return [headerView, pickerView].compactMap({ $0 }).contains { accessory in
            guard !accessory.isHidden, accessory.alpha > 0.01 else { return false }
            let pointInAccessory = accessory.convert(point, from: self)
            return accessory.point(inside: pointInAccessory, with: event)
        }
    }

    override func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard gestureRecognizer === panGestureRecognizer else {
            return super.gestureRecognizerShouldBegin(gestureRecognizer)
        }
        let velocity = panGestureRecognizer.velocity(in: self)
        return abs(velocity.y) > abs(velocity.x)
    }

    override func touchesShouldCancel(in view: UIView) -> Bool {
        true
    }
}

private final class ProfileTimelinePagerViewController: UIViewController,
    UIPageViewControllerDataSource,
    UIPageViewControllerDelegate,
    UIScrollViewDelegate {
    private struct PageTransition {
        let fromIndex: Int
        let toIndex: Int
        let idleOffsetX: CGFloat
        let isProgrammatic: Bool
        var maximumFraction: CGFloat = 0
    }

    private let pageViewController = UIPageViewController(
        transitionStyle: .scroll,
        navigationOrientation: .horizontal
    )
    private let accessoryScrollView = ProfileAccessoryScrollView()
    private let collapsedGlassView = ProfileGlassBackgroundView()
    private var pages: [ProfileTimelinePageViewController] = []
    private var currentIndex = 0
    private var headerView: UIView?
    private var pickerView: UIView?
    private var pageBackgroundColor = UIColor.clear
    private var headerHeight: CGFloat = 0
    private var pickerHeight: CGFloat = 0
    private var collapseDistance: CGFloat = 0
    private var isApplyingTimelineOffsetToAccessories = false
    private var isApplyingAccessoryOffsetToTimeline = false
    private var isStoppingAccessoryScroll = false
    private weak var externallyScrollingPage: UITimelineCollectionViewController?
    private weak var pageScrollView: UIScrollView?
    private var pageScrollObservation: NSKeyValueObservation?
    private var idlePageOffsetX: CGFloat?
    private var pageTransition: PageTransition?
    private var lastReportedPagePosition: CGFloat?
    private var lastHeaderVisibility: Bool?
    private var lastPickerVisibility: Bool?
    var onSelectedIndexChanged: ((Int) -> Void)?
    var onPagePositionChanged: ((CGFloat) -> Void)?
    var onHeaderVisibilityChanged: ((Bool) -> Void)?
    var onPickerVisibilityChanged: ((Bool) -> Void)?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        pageViewController.dataSource = self
        pageViewController.delegate = self
        addChild(pageViewController)
        pageViewController.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(pageViewController.view)
        NSLayoutConstraint.activate([
            pageViewController.view.topAnchor.constraint(equalTo: view.topAnchor),
            pageViewController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            pageViewController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            pageViewController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        pageViewController.didMove(toParent: self)
        observePageScrollView()

        accessoryScrollView.backgroundColor = .clear
        accessoryScrollView.contentInsetAdjustmentBehavior = .never
        accessoryScrollView.showsHorizontalScrollIndicator = false
        accessoryScrollView.showsVerticalScrollIndicator = false
        accessoryScrollView.alwaysBounceVertical = true
        accessoryScrollView.isDirectionalLockEnabled = true
        accessoryScrollView.isScrollEnabled = false
        accessoryScrollView.scrollsToTop = false
        accessoryScrollView.delegate = self
        accessoryScrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(accessoryScrollView)
        NSLayoutConstraint.activate([
            accessoryScrollView.topAnchor.constraint(equalTo: view.topAnchor),
            accessoryScrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            accessoryScrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            accessoryScrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        installAccessoryViews()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        layoutAccessories()
        updateIdlePageOffsetIfNeeded()
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        view.setNeedsLayout()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        externallyScrollingPage?.endExternalScrollInteraction()
        externallyScrollingPage = nil
    }

    deinit {
        pageScrollObservation?.invalidate()
    }

    func setAccessories(header: UIView?, picker: UIView?) {
        if headerView !== header {
            headerView?.removeFromSuperview()
            headerView = header
        }
        if pickerView !== picker {
            pickerView?.removeFromSuperview()
            pickerView = picker
        }
        guard isViewLoaded else { return }
        installAccessoryViews()
        view.setNeedsLayout()
        reportVisibility(effectiveOffsetY: currentPage?.effectiveContentOffsetY ?? 0)
    }

    func setBackgroundColor(_ color: UIColor) {
        loadViewIfNeeded()
        pageBackgroundColor = color
        view.backgroundColor = color
        pageViewController.view.backgroundColor = color
        pages.forEach { $0.setBackgroundColor(color) }
        setBaseBackgroundColor(color, for: headerView)
        setBaseBackgroundColor(color, for: pickerView)
        updateAccessoryAppearance(navigationTitleVisible: lastHeaderVisibility == false)
    }

    func setPages(_ configurations: [ProfileTimelinePageConfiguration], selectedIndex: Int) {
        loadViewIfNeeded()
        let previousIDs = pages.map(\.id)
        let existingPages = Dictionary(uniqueKeysWithValues: pages.map { ($0.id, $0) })
        let newIDs = Set(configurations.map(\.id))
        for page in pages where !newIDs.contains(page.id) {
            page.timelineController?.onContentOffsetChanged = nil
            page.timelineController?.onScrollInteractionBegan = nil
        }
        pages = configurations.map { configuration in
            if let page = existingPages[configuration.id] {
                page.update(configuration: configuration)
                return page
            }
            return ProfileTimelinePageViewController(
                configuration: configuration,
                backgroundColor: pageBackgroundColor
            )
        }

        guard !pages.isEmpty else {
            currentIndex = 0
            accessoryScrollView.isScrollEnabled = false
            pageViewController.setViewControllers(nil, direction: .forward, animated: false)
            finishPageTransition(at: 0)
            reportVisibility(effectiveOffsetY: 0)
            return
        }

        accessoryScrollView.isScrollEnabled = true
        let targetIndex = min(max(selectedIndex, 0), pages.count - 1)
        view.setNeedsLayout()
        let pagesChanged = previousIDs != pages.map(\.id)
        selectPage(at: targetIndex, animated: !pagesChanged && view.window != nil)
    }

    func pageViewController(
        _ pageViewController: UIPageViewController,
        viewControllerBefore viewController: UIViewController
    ) -> UIViewController? {
        guard let page = viewController as? ProfileTimelinePageViewController,
              let index = index(of: page), index > 0 else { return nil }
        return pages[index - 1]
    }

    func pageViewController(
        _ pageViewController: UIPageViewController,
        viewControllerAfter viewController: UIViewController
    ) -> UIViewController? {
        guard let page = viewController as? ProfileTimelinePageViewController,
              let index = index(of: page), index + 1 < pages.count else { return nil }
        return pages[index + 1]
    }

    func pageViewController(
        _ pageViewController: UIPageViewController,
        didFinishAnimating finished: Bool,
        previousViewControllers: [UIViewController],
        transitionCompleted completed: Bool
    ) {
        guard let visiblePage = pageViewController.viewControllers?.first as? ProfileTimelinePageViewController,
              let index = index(of: visiblePage) else { return }
        currentIndex = index
        let timeline = activate(visiblePage)
        updateAccessoryFrames(effectiveOffsetY: timeline.effectiveContentOffsetY)
        finishPageTransition(at: index)
        if completed {
            onSelectedIndexChanged?(index)
        }
    }

    func pageViewController(
        _ pageViewController: UIPageViewController,
        willTransitionTo pendingViewControllers: [UIViewController]
    ) {
        guard let source = pageViewController.viewControllers?.first as? ProfileTimelinePageViewController,
              let sourceIndex = index(of: source) else {
            return
        }
        for case let target as ProfileTimelinePageViewController in pendingViewControllers {
            if let targetIndex = index(of: target) {
                beginPageTransition(
                    from: sourceIndex,
                    to: targetIndex,
                    isProgrammatic: false
                )
            }
            let isFirstActivation = target.timelineController == nil
            let timeline = activate(target)
            if isFirstActivation {
                synchronizeInitialOffset(from: source, to: timeline)
            }
        }
    }

    private var currentPage: UITimelineCollectionViewController? {
        guard pages.indices.contains(currentIndex) else { return nil }
        return pages[currentIndex].timelineController
    }

    private func selectPage(at index: Int, animated: Bool) {
        guard pages.indices.contains(index) else { return }
        let target = pages[index]
        let isFirstActivation = target.timelineController == nil
        let targetTimeline = activate(target)
        let visiblePage = pageViewController.viewControllers?.first as? ProfileTimelinePageViewController
        let sourceIndex = visiblePage.flatMap(index(of:)) ?? currentIndex
        if visiblePage === target {
            currentIndex = index
            updateAccessoryFrames(effectiveOffsetY: targetTimeline.effectiveContentOffsetY)
            if pageTransition == nil {
                reportPagePosition(CGFloat(index))
            }
            return
        }
        if isFirstActivation, let visiblePage {
            synchronizeInitialOffset(from: visiblePage, to: targetTimeline)
        }
        let direction: UIPageViewController.NavigationDirection = index >= sourceIndex ? .forward : .reverse
        if animated {
            beginPageTransition(from: sourceIndex, to: index, isProgrammatic: true)
        } else {
            pageTransition = nil
            reportPagePosition(CGFloat(index))
        }
        currentIndex = index
        pageViewController.setViewControllers(
            [target],
            direction: direction,
            animated: animated
        ) { [weak self] _ in
            guard let self,
                  let visiblePage = self.pageViewController.viewControllers?.first
                    as? ProfileTimelinePageViewController else { return }
            let visibleTimeline = self.activate(visiblePage)
            self.currentIndex = self.index(of: visiblePage) ?? index
            self.updateAccessoryFrames(effectiveOffsetY: visibleTimeline.effectiveContentOffsetY)
            self.finishPageTransition(at: self.currentIndex)
        }
    }

    private func observePageScrollView() {
        guard let scrollView = pageViewController.view.subviews
            .compactMap({ $0 as? UIScrollView })
            .first else { return }
        pageScrollView = scrollView
        idlePageOffsetX = scrollView.contentOffset.x
        pageScrollObservation = scrollView.observe(\.contentOffset, options: [.new]) {
            [weak self, weak scrollView] _, _ in
            guard let self, let scrollView else { return }
            MainActor.assumeIsolated {
                self.pageScrollViewDidScroll(scrollView)
            }
        }
    }

    private func updateIdlePageOffsetIfNeeded() {
        guard pageTransition == nil,
              let scrollView = pageScrollView,
              !scrollView.isTracking,
              !scrollView.isDragging,
              !scrollView.isDecelerating else { return }
        idlePageOffsetX = scrollView.contentOffset.x
    }

    private func beginPageTransition(
        from fromIndex: Int,
        to toIndex: Int,
        isProgrammatic: Bool
    ) {
        guard fromIndex != toIndex else {
            finishPageTransition(at: toIndex)
            return
        }
        let idleOffsetX = idlePageOffsetX ?? pageScrollView?.contentOffset.x ?? 0
        pageTransition = PageTransition(
            fromIndex: fromIndex,
            toIndex: toIndex,
            idleOffsetX: idleOffsetX,
            isProgrammatic: isProgrammatic
        )
        reportPagePosition(CGFloat(fromIndex))
        if let pageScrollView {
            pageScrollViewDidScroll(pageScrollView)
        }
    }

    private func pageScrollViewDidScroll(_ scrollView: UIScrollView) {
        guard var transition = pageTransition else {
            updateIdlePageOffsetIfNeeded()
            return
        }
        let width = scrollView.bounds.width
        guard width > 0 else { return }

        var fraction = min(
            max(abs(scrollView.contentOffset.x - transition.idleOffsetX) / width, 0),
            1
        )
        if transition.isProgrammatic {
            fraction = max(fraction, transition.maximumFraction)
        }
        transition.maximumFraction = max(transition.maximumFraction, fraction)
        pageTransition = transition

        let pagePosition = CGFloat(transition.fromIndex) +
            CGFloat(transition.toIndex - transition.fromIndex) * fraction
        reportPagePosition(pagePosition)
    }

    private func finishPageTransition(at index: Int) {
        pageTransition = nil
        reportPagePosition(CGFloat(index))
        DispatchQueue.main.async { [weak self] in
            guard let self, self.pageTransition == nil, let pageScrollView = self.pageScrollView else {
                return
            }
            self.idlePageOffsetX = pageScrollView.contentOffset.x
        }
    }

    private func reportPagePosition(_ pagePosition: CGFloat) {
        let clampedPosition: CGFloat
        if pages.isEmpty {
            clampedPosition = 0
        } else {
            clampedPosition = min(max(pagePosition, 0), CGFloat(pages.count - 1))
        }
        guard lastReportedPagePosition.map({ abs($0 - clampedPosition) > 0.0001 }) ?? true else {
            return
        }
        lastReportedPagePosition = clampedPosition
        onPagePositionChanged?(clampedPosition)
    }

    private func activate(_ page: ProfileTimelinePageViewController) -> UITimelineCollectionViewController {
        let timeline = page.activate()
        configure(timeline)
        timeline.onContentOffsetChanged = { [weak self, weak timeline] offsetY in
            guard let timeline else { return }
            self?.pageDidScroll(timeline, effectiveOffsetY: offsetY)
        }
        timeline.onScrollInteractionBegan = { [weak self, weak timeline] in
            guard let timeline else { return }
            self?.pageWillBeginDragging(timeline)
        }
        return timeline
    }

    private func synchronizeInitialOffset(
        from source: ProfileTimelinePageViewController,
        to target: UITimelineCollectionViewController
    ) {
        guard let source = source.timelineController else { return }
        let offsetY = min(max(source.effectiveContentOffsetY, 0), collapseDistance)
        target.loadViewIfNeeded()
        target.restoreEffectiveContentOffset(offsetY, animated: false)
        target.restoreEffectiveContentOffsetAfterNextSnapshot(offsetY)
    }

    private func index(of viewController: ProfileTimelinePageViewController) -> Int? {
        pages.firstIndex { $0 === viewController }
    }

    private func pageDidScroll(_ page: UITimelineCollectionViewController, effectiveOffsetY: CGFloat) {
        guard let visiblePage = pageViewController.viewControllers?.first as? ProfileTimelinePageViewController,
              visiblePage.timelineController === page else { return }
        if isApplyingAccessoryOffsetToTimeline || accessoryScrollView.isTracking ||
            accessoryScrollView.isDragging || accessoryScrollView.isDecelerating {
            updateAccessoryTransforms(offsetY: accessoryScrollView.contentOffset.y)
            reportVisibility(effectiveOffsetY: accessoryScrollView.contentOffset.y)
            return
        }
        updateAccessoryFrames(effectiveOffsetY: effectiveOffsetY)
    }

    private func pageWillBeginDragging(_ page: UITimelineCollectionViewController) {
        guard let visiblePage = pageViewController.viewControllers?.first as? ProfileTimelinePageViewController,
              visiblePage.timelineController === page else { return }
        stopAccessoryScroll(handingOffTo: page)
    }

    private func installAccessoryViews() {
        accessoryScrollView.headerView = headerView
        accessoryScrollView.pickerView = pickerView
        if collapsedGlassView.superview !== accessoryScrollView {
            accessoryScrollView.addSubview(collapsedGlassView)
        }
        accessoryScrollView.bringSubviewToFront(collapsedGlassView)
        for accessory in [headerView, pickerView].compactMap({ $0 }) {
            if accessory.superview !== accessoryScrollView {
                accessory.removeFromSuperview()
                accessoryScrollView.addSubview(accessory)
            }
            accessoryScrollView.bringSubviewToFront(accessory)
        }
        view.bringSubviewToFront(accessoryScrollView)
    }

    private func layoutAccessories() {
        let width = view.bounds.width
        guard width > 0 else { return }
        let activePages = pages.compactMap(\.timelineController)
        let effectiveOffsets = activePages.reduce(into: [ObjectIdentifier: CGFloat]()) { result, page in
            guard page.isViewLoaded else { return }
            result[ObjectIdentifier(page)] = page.effectiveContentOffsetY
        }

        headerHeight = fittingHeight(of: headerView, width: width)
        pickerHeight = fittingHeight(of: pickerView, width: width)
        collapseDistance = max(headerHeight - view.safeAreaInsets.top, 0)

        headerView?.transform = .identity
        headerView?.frame = CGRect(x: 0, y: 0, width: width, height: headerHeight)
        collapsedGlassView.transform = .identity
        collapsedGlassView.frame = CGRect(
            x: 0,
            y: collapseDistance,
            width: width,
            height: max(headerHeight - collapseDistance, 0) + pickerHeight
        )
        pickerView?.transform = .identity
        pickerView?.frame = CGRect(x: 0, y: headerHeight, width: width, height: pickerHeight)

        for page in activePages {
            configure(page, oldEffectiveOffset: effectiveOffsets[ObjectIdentifier(page)])
        }

        updateAccessoryScrollRange(for: currentPage)
        updateAccessoryFrames(effectiveOffsetY: currentPage?.effectiveContentOffsetY ?? 0)
    }

    private func fittingHeight(of accessory: UIView?, width: CGFloat) -> CGFloat {
        guard let accessory else { return 0 }
        return ceil(
            accessory.systemLayoutSizeFitting(
                CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
                withHorizontalFittingPriority: .required,
                verticalFittingPriority: .fittingSizeLevel
            ).height
        )
    }

    private func configure(
        _ page: UITimelineCollectionViewController,
        oldEffectiveOffset: CGFloat? = nil
    ) {
        let topInset = headerHeight + pickerHeight
        let insetChanged = abs(page.topContentInset - topInset) > 0.5
        if insetChanged {
            page.topContentInset = topInset
        }
        if abs(page.minimumVerticalScrollDistance - collapseDistance) > 0.5 {
            page.minimumVerticalScrollDistance = collapseDistance
        }
        if insetChanged, let oldEffectiveOffset {
            page.restoreEffectiveContentOffset(oldEffectiveOffset, animated: false)
        }
    }

    private func updateAccessoryFrames(effectiveOffsetY: CGFloat) {
        updateAccessoryScrollRange(for: currentPage)
        let accessoryIsScrolling = accessoryScrollView.isTracking ||
            accessoryScrollView.isDragging || accessoryScrollView.isDecelerating
        if !isApplyingAccessoryOffsetToTimeline && !accessoryIsScrolling {
            applyTimelineOffsetToAccessories(effectiveOffsetY)
        }
        let displayedOffsetY = accessoryIsScrolling
            ? accessoryScrollView.contentOffset.y
            : effectiveOffsetY
        updateAccessoryTransforms(offsetY: displayedOffsetY)
        reportVisibility(effectiveOffsetY: displayedOffsetY)
    }

    private func updateAccessoryScrollRange(for page: UITimelineCollectionViewController?) {
        let width = accessoryScrollView.bounds.width
        let height = accessoryScrollView.bounds.height
        guard width > 0, height > 0 else { return }
        let maximumOffsetY = max(page?.maximumEffectiveContentOffsetY ?? 0, collapseDistance)
        let contentSize = CGSize(width: width, height: height + maximumOffsetY)
        if abs(accessoryScrollView.contentSize.width - contentSize.width) > 0.5 ||
            abs(accessoryScrollView.contentSize.height - contentSize.height) > 0.5 {
            // Shrinking contentSize can synchronously clamp contentOffset and notify the delegate.
            let wasApplyingTimelineOffset = isApplyingTimelineOffsetToAccessories
            isApplyingTimelineOffsetToAccessories = true
            accessoryScrollView.contentSize = contentSize
            isApplyingTimelineOffsetToAccessories = wasApplyingTimelineOffset
        }
        if let page {
            accessoryScrollView.decelerationRate = page.scrollDecelerationRate
        }
    }

    private func applyTimelineOffsetToAccessories(_ offsetY: CGFloat) {
        guard abs(accessoryScrollView.contentOffset.y - offsetY) > 0.5 else { return }
        isApplyingTimelineOffsetToAccessories = true
        accessoryScrollView.setContentOffset(
            CGPoint(x: 0, y: offsetY),
            animated: false
        )
        isApplyingTimelineOffsetToAccessories = false
    }

    private func updateAccessoryTransforms(offsetY: CGFloat) {
        let collapsedOffsetY = min(max(offsetY, 0), collapseDistance)
        let compensationY = offsetY - collapsedOffsetY
        let transform = CGAffineTransform(translationX: 0, y: compensationY)
        headerView?.transform = transform
        collapsedGlassView.transform = transform
        pickerView?.transform = transform
    }

    private func stopAccessoryScroll(handingOffTo page: UITimelineCollectionViewController) {
        guard externallyScrollingPage != nil || accessoryScrollView.isTracking ||
            accessoryScrollView.isDragging || accessoryScrollView.isDecelerating else { return }

        isStoppingAccessoryScroll = true
        isApplyingTimelineOffsetToAccessories = true
        if #available(iOS 17.4, *) {
            accessoryScrollView.stopScrollingAndZooming()
        } else {
            let wasScrollEnabled = accessoryScrollView.isScrollEnabled
            accessoryScrollView.setContentOffset(accessoryScrollView.contentOffset, animated: false)
            accessoryScrollView.isScrollEnabled = false
            accessoryScrollView.isScrollEnabled = wasScrollEnabled
        }
        isApplyingTimelineOffsetToAccessories = false
        if externallyScrollingPage !== page {
            externallyScrollingPage?.endExternalScrollInteraction()
        }
        externallyScrollingPage = nil
        isStoppingAccessoryScroll = false

        applyTimelineOffsetToAccessories(page.effectiveContentOffsetY)
        updateAccessoryTransforms(offsetY: accessoryScrollView.contentOffset.y)
        reportVisibility(effectiveOffsetY: page.effectiveContentOffsetY)
    }

    func scrollViewWillBeginDragging(_ scrollView: UIScrollView) {
        guard scrollView === accessoryScrollView, let page = currentPage else { return }
        page.view.layoutIfNeeded()
        updateAccessoryScrollRange(for: page)
        if externallyScrollingPage !== page {
            externallyScrollingPage?.endExternalScrollInteraction()
            externallyScrollingPage = page
            page.beginExternalScrollInteraction()
        }
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
        guard scrollView === accessoryScrollView else { return }
        let offsetY = scrollView.contentOffset.y
        updateAccessoryTransforms(offsetY: offsetY)
        reportVisibility(effectiveOffsetY: offsetY)
        guard !isApplyingTimelineOffsetToAccessories, let page = currentPage else { return }
        isApplyingAccessoryOffsetToTimeline = true
        page.setEffectiveContentOffset(offsetY, animated: false)
        isApplyingAccessoryOffsetToTimeline = false
    }

    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
        guard scrollView === accessoryScrollView, !decelerate, !isStoppingAccessoryScroll else { return }
        finishAccessoryScrollInteraction()
    }

    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) {
        guard scrollView === accessoryScrollView, !isStoppingAccessoryScroll else { return }
        finishAccessoryScrollInteraction()
    }

    func scrollViewDidEndScrollingAnimation(_ scrollView: UIScrollView) {
        guard scrollView === accessoryScrollView, !isStoppingAccessoryScroll else { return }
        finishAccessoryScrollInteraction()
    }

    private func finishAccessoryScrollInteraction() {
        externallyScrollingPage?.endExternalScrollInteraction()
        externallyScrollingPage = nil
        guard let page = currentPage else { return }
        updateAccessoryScrollRange(for: page)
        applyTimelineOffsetToAccessories(page.effectiveContentOffsetY)
        updateAccessoryTransforms(offsetY: accessoryScrollView.contentOffset.y)
        reportVisibility(effectiveOffsetY: page.effectiveContentOffsetY)
    }

    private func reportVisibility(effectiveOffsetY: CGFloat) {
        let headerVisible = headerView != nil && (
            collapseDistance <= 0.5 || effectiveOffsetY < collapseDistance - 0.5
        )
        updateAccessoryAppearance(navigationTitleVisible: !headerVisible)
        if lastHeaderVisibility != headerVisible {
            lastHeaderVisibility = headerVisible
            onHeaderVisibilityChanged?(headerVisible)
        }
        let pickerVisible = pickerView != nil
        if lastPickerVisibility != pickerVisible {
            lastPickerVisibility = pickerVisible
            onPickerVisibilityChanged?(pickerVisible)
        }
    }

    private func setBaseBackgroundColor(_ color: UIColor, for view: UIView?) {
        if let accessory = view as? ProfileHostedAccessoryView {
            accessory.setBaseBackgroundColor(color)
        } else {
            view?.backgroundColor = color
        }
    }

    private func updateAccessoryAppearance(navigationTitleVisible: Bool) {
        headerView?.alpha = navigationTitleVisible ? 0 : 1
        collapsedGlassView.isHidden = !navigationTitleVisible || headerView == nil
        if let picker = pickerView as? ProfileHostedAccessoryView {
            picker.setBaseBackgroundHidden(navigationTitleVisible)
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
    private let ignoresSafeArea: Bool
    private var baseBackgroundColor = UIColor.clear
    private var hidesBaseBackground = false

    init(ignoresSafeArea: Bool) {
        self.ignoresSafeArea = ignoresSafeArea
        super.init(frame: .zero)
        commonInit()
    }

    override init(frame: CGRect) {
        ignoresSafeArea = false
        super.init(frame: frame)
        commonInit()
    }

    required init?(coder: NSCoder) {
        ignoresSafeArea = false
        super.init(coder: coder)
        commonInit()
    }

    func update(_ rootView: AnyView) {
        host.rootView = rootView
        host.view.invalidateIntrinsicContentSize()
        invalidateIntrinsicContentSize()
        setNeedsLayout()
    }

    func setBaseBackgroundColor(_ color: UIColor) {
        baseBackgroundColor = color
        backgroundColor = hidesBaseBackground ? .clear : color
    }

    func setBaseBackgroundHidden(_ hidden: Bool) {
        guard hidesBaseBackground != hidden else { return }
        hidesBaseBackground = hidden
        backgroundColor = hidden ? .clear : baseBackgroundColor
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
        if ignoresSafeArea, #available(iOS 16.4, *) {
            host.safeAreaRegions = []
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

private final class ProfileGlassBackgroundView: UIVisualEffectView {
    init() {
        let effect: UIVisualEffect
        if #available(iOS 26.0, *) {
            effect = UIGlassEffect(style: .regular)
        } else {
            effect = UIBlurEffect(style: .systemMaterial)
        }
        super.init(effect: effect)
        isHidden = true
        isUserInteractionEnabled = false
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
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
