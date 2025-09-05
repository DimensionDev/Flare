import Kingfisher
import os
import shared
import SwiftUI

struct HomeAppBarSettingsView: View {
    @Environment(\.dismiss) var dismiss
    var store: AppBarTabSettingStore = .shared

    // 列表 presenter 状态
    @State private var listPresenter: AllListPresenter
    @State private var lastListState: AllListState?
    @State private var isActionInProgress = false

    // Feeds presenter状态（只用于Bluesky）
    @State private var feedsPresenter: PinnableTimelineTabPresenter?
    @State private var lastFeedsState: PinnableTimelineTabPresenterState?
    @State private var isBlueskyPlatform: Bool = false

    // 编辑标题所需的状态
    @State private var isEditingTitle = false
    @State private var editingList: UiList?
    @State private var editedTitle = ""

    // 使用ID和标题更新的状态
    @State private var editingListId: String?
    @State private var editingListTitle: String = ""

    // 新字段以标识编辑的是Feed还是List
    @State private var editingItemIsBlueskyFeed: Bool = false

    // 折叠相关状态
    @State private var isAvailableListsExpanded: Bool = false
    @State private var isAvailableFeedsExpanded: Bool = false
    @State private var availableListsLimit: Int = 5 // 默认显示数量限制

    @Environment(FlareTheme.self) private var theme

    init() {
        let store = AppBarTabSettingStore.shared
        _listPresenter = State(initialValue: AllListPresenter(accountType: store.accountType))

        // 判断当前平台是否为Bluesky
        let isBluesky = store.currentUser?.platformType == .bluesky
        _isBlueskyPlatform = State(initialValue: isBluesky)

        // 如果是Bluesky平台，初始化FeedsPresenter
        if isBluesky {
            _feedsPresenter = State(initialValue: PinnableTimelineTabPresenter(accountType: store.accountType))
        }
    }

    // 保留旧的初始化方法，但标记为deprecated
    @available(*, deprecated, message: "请使用init()代替")
    init(store _: AppBarTabSettingStore) {
        store = AppBarTabSettingStore.shared // 忽略传入的store，始终使用shared实例
        _listPresenter = State(initialValue: AllListPresenter(accountType: AppBarTabSettingStore.shared.accountType))

        // 判断当前平台是否为Bluesky
        let isBluesky = AppBarTabSettingStore.shared.currentUser?.platformType == .bluesky
        _isBlueskyPlatform = State(initialValue: isBluesky)

        // 如果是Bluesky平台，初始化FeedsPresenter
        if isBluesky {
            _feedsPresenter = State(initialValue: PinnableTimelineTabPresenter(accountType: AppBarTabSettingStore.shared.accountType))
        }
    }

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                // 自定义Header
                HStack {
//                    Button(action: {
//                        dismiss()
//                    }) {
//                        Text("Close")
//                            .foregroundColor(.blue)
//                    }
//                    .padding(.leading, 16)

                    Spacer()

                    Text("AppBar Settings")
                        .font(.headline)

                    Spacer()

                    // 平衡布局的空按钮
//                    Button(action: {}) {
//                        Text("")
//                            .frame(width: 40)
//                    }
//                    .opacity(0)
//                    .padding(.trailing, 16)
                }
                .padding(.vertical, 10)
                // .background(Color(UIColor.systemBackground))
                .overlay(
                    Rectangle()
                        .frame(height: 0.5)
                        .foregroundColor(Color(UIColor.separator))
                        .offset(y: 20),
                    alignment: .bottom
                )

                List {
                    // 主要标签
                    if let primaryTab = store.primaryHomeItems.first {
                        Section(header: Text("main tab")) {
                            AppBarSettingRow(tab: primaryTab, store: store, isPrimary: true)
                        }.listRowBackground(theme.primaryBackgroundColor)
                    }

                    // 次要标签
                    let secondaryTabs = store.availableAppBarTabsItems.filter { tab in
                        if let primaryTab = store.primaryHomeItems.first, tab.key != primaryTab.key {
                            return !tab.key.starts(with: "list_") && !tab.key.starts(with: "feed_")
                        }
                        return false
                    }

                    if !secondaryTabs.isEmpty {
                        Section(header: Text("used tabs")) {
                            ForEach(secondaryTabs, id: \.key) { tab in
                                AppBarSettingRow(tab: tab, store: store, isPrimary: false, defaultToggleValue: true)
                            }
                            .onMove { source, destination in
                                store.moveTab(from: source, to: destination)
                            }
                        }.listRowBackground(theme.primaryBackgroundColor)
                    }

                    // 未启用的标签
                    let unusedTabs = store.secondaryItems.filter { tab in
                        !store.availableAppBarTabsItems.contains { $0.key == tab.key }
                    }
                    if !unusedTabs.isEmpty {
                        Section(header: Text("unused tabs")) {
                            ForEach(unusedTabs, id: \.key) { tab in
                                AppBarSettingRow(tab: tab, store: store, isPrimary: false, defaultToggleValue: false)
                            }
                        }.listRowBackground(theme.primaryBackgroundColor)
                    }

                    // pinned lists
                    let pinnedListTabs = store.availableAppBarTabsItems.filter { $0.key.starts(with: "list_") }
                    if !pinnedListTabs.isEmpty {
                        Section(header: Text("pinned Lists")) {
                            ForEach(pinnedListTabs, id: \.key) { tab in
                                if let listId = tab.key.split(separator: "_").last {
                                    if let title = store.listTitles[String(listId)] {
                                        ListTabItemRowRow(
                                            listId: String(listId),
                                            title: title,
                                            store: store,
                                            onRequestEdit: { listId, title in
                                                // 直接设置ID和标题，
                                                editingListId = listId
                                                editingListTitle = title
                                                editedTitle = title
                                                // 确定是Feed还是List
                                                editingItemIsBlueskyFeed = tab.key.starts(with: "feed_")
                                                isEditingTitle = true
                                            },
                                            isBlueskyFeed: tab.key.starts(with: "feed_"),
                                            defaultToggleValue: true
                                        )
                                    }
                                }
                            }
                            .onMove { source, destination in
                                store.moveListTab(from: source, to: destination)
                            }
                        }.listRowBackground(theme.primaryBackgroundColor)
                    }

                    // pinned feeds
                    let pinnedFeedTabs = store.availableAppBarTabsItems.filter { $0.key.starts(with: "feed_") }
                    if isBlueskyPlatform, !pinnedFeedTabs.isEmpty {
                        Section(header: Text("pinned feeds")) {
                            ForEach(pinnedFeedTabs, id: \.key) { tab in
                                if let feedId = tab.key.split(separator: "_").last {
                                    if let title = store.listTitles[String(feedId)] {
                                        ListTabItemRowRow(
                                            listId: String(feedId),
                                            title: title,
                                            store: store,
                                            onRequestEdit: { feedId, title in
                                                // 直接设置ID和标题
                                                editingListId = feedId
                                                editingListTitle = title
                                                editedTitle = title
                                                // 明确这是Feed
                                                editingItemIsBlueskyFeed = true
                                                isEditingTitle = true
                                            },
                                            isBlueskyFeed: true,
                                            defaultToggleValue: true
                                        )
                                    }
                                }
                            }

                            .onMove { source, destination in
                                store.moveFeedTab(from: source, to: destination)
                            }
                        }.listRowBackground(theme.primaryBackgroundColor)
                    }

                    // available no pinned lists
                    availableListsSection

                    // 仅当为Bluesky平台时显示：available no pinned feeds
                    if isBlueskyPlatform {
                        availableFeedsSection
                    }
                }
                .navigationBarHidden(true) // 隐藏系统导航栏
                // 删除navigationTitle和navigationBarItems
                .onAppear {
                    // 视图出现时启动对列表状态的观察
                    startListObservation()

                    // 如果是Bluesky平台，启动对Feeds状态的观察
                    if isBlueskyPlatform {
                        startFeedsObservation()
                    }

                    // 隐藏系统的拖动控件但保留拖拽功能
                    UITableViewCell.appearance().showsReorderControl = false
                }
            }

            // if isEditingTitle {
            //     EditAppBarSettingListTitleView(
            //         title: $editedTitle,
            //         listId: editingListId ?? "",
            //         iconUrl: store.listIconUrls[editingListId ?? ""] ?? "",
            //         onSave: { newTitle in
            //             // 更新标题
            //             store.listTitles[editingListId ?? ""] = newTitle

            //             // 发送标题更新通知
            //             NotificationCenter.default.post(
            //                 name: .listTitleDidUpdate,
            //                 object: nil,
            //                 userInfo: [
            //                     "listId": editingListId,
            //                     "newTitle": newTitle,
            //                     "itemType": editingItemIsBlueskyFeed ? "feed" : "list"
            //                 ]
            //             )

            //             // 保存设置
            //             store.savePinnedLists()
            //             store.saveTabs()

            //             isEditingTitle = false
            //         },
            //         onCancel: {
            //             isEditingTitle = false
            //         },
            //         isBlueskyFeed: editingItemIsBlueskyFeed
            //     )
            // }
        }
        .scrollContentBackground(.hidden)
        .background(theme.secondaryBackgroundColor)
        .environment(\.editMode, .constant(.active))
        .sheet(isPresented: $isEditingTitle, onDismiss: {
            editingList = nil
            editingListId = nil
            editingListTitle = ""
        }) {
            if let list = editingList {
                AppBarSettingEditListTitleView(
                    title: $editedTitle,
                    listId: list.id,
                    iconUrl: list.avatar,
                    onSave: { newTitle in
                        updateListTitle(list: list, newTitle: newTitle)
                        isEditingTitle = false
                    },
                    onCancel: {
                        isEditingTitle = false
                    },
                    isBlueskyFeed: false // 假设列表对象不是Feed
                )
            } else if let listId = editingListId, !editingListTitle.isEmpty {
                // 使用 ID 和标题直接编辑
                AppBarSettingEditListTitleView(
                    title: $editedTitle,
                    listId: listId,
                    iconUrl: store.listIconUrls[listId],
                    onSave: { newTitle in
                        updateListTitleById(listId: listId, oldTitle: editingListTitle, newTitle: newTitle)
                        isEditingTitle = false
                        editingListId = nil
                        editingListTitle = ""
                    },
                    onCancel: {
                        isEditingTitle = false
                        editingListId = nil
                        editingListTitle = ""
                    },
                    isBlueskyFeed: editingItemIsBlueskyFeed // 使用我们识别的类型
                )
            }
        }
    }

    @ViewBuilder
    private var availableListsSection: some View {
        if let state = lastListState {
            if let successState = state.items as? PagingStateSuccess<UiList>,
               successState.itemCount > 0
            {
                // 检查是否有未固定的列表
                let hasUnpinnedLists = checkForUnpinnedLists(successState)
                if hasUnpinnedLists {
                    // 计算实际可用列表总数和当前要显示的数量
                    let totalAvailableCount = countUnpinnedLists(successState)
                    let displayCount = isAvailableListsExpanded ?
                        min(Int(successState.itemCount), 50) :
                        min(availableListsLimit, totalAvailableCount)

                    Section(header:
                        HStack {
                            Text("available lists")
                            Spacer()
                            if totalAvailableCount > availableListsLimit {
                                Image(systemName: isAvailableListsExpanded ? "chevron.up" : "chevron.down")
                                    .font(.system(size: 14))
                                    .foregroundColor(.gray)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            if totalAvailableCount > availableListsLimit {
                                FlareHapticManager.shared.selection()
                                withAnimation {
                                    isAvailableListsExpanded.toggle()
                                }
                            }
                        }
                    ) {
                        ForEach(0 ..< min(Int(successState.itemCount), 50), id: \.self) { index in
                            if let list = successState.peek(index: Int32(index)) {
                                // 确保此列表没有被固定，并且没有对应的标签
                                let listTabKey = "list_\(store.accountType)_\(list.id)"
                                if !store.pinnedListIds.contains(list.id),
                                   !store.availableAppBarTabsItems.contains(where: { $0.key == listTabKey })
                                {
                                    // 计算已显示的未固定列表数量，用于确定是否显示当前列表
                                    let displayedCount = countDisplayedItemsBeforeIndex(successState, currentIndex: index)

                                    if displayedCount < displayCount {
                                        listRowItem(for: list)
                                            .buttonStyle(PlainButtonStyle())
                                    }
                                }
                            }
                        }

                        // 添加"显示更多"按钮
                        if !isAvailableListsExpanded, totalAvailableCount > availableListsLimit {
                            Button(action: {
                                FlareHapticManager.shared.buttonPress()
                                withAnimation {
                                    isAvailableListsExpanded = true
                                }
                            }) {
                                HStack {
                                    Spacer()
                                    Text("Load more")
                                        .font(.footnote)
                                        .foregroundColor(.blue)
                                    Spacer()
                                }
                                .padding(.vertical, 8)
                            }
                        }
                    }.listRowBackground(theme.primaryBackgroundColor)
                }
            } else if state.items is PagingStateLoading {
                Section(header: Text("")) {
                    ForEach(0 ..< 3, id: \.self) { _ in
                        ListRowSkeletonView()
                            .padding(.horizontal)
                    }
                }.listRowBackground(theme.primaryBackgroundColor)
            }
        }
    }

    // 辅助方法：计算可用的未固定列表总数
    private func countUnpinnedLists(_ successState: PagingStateSuccess<UiList>) -> Int {
        var count = 0
        for i in 0 ..< min(Int(successState.itemCount), 50) {
            if let list = successState.peek(index: Int32(i)) {
                let listTabKey = "list_\(store.accountType)_\(list.id)"
                if !store.pinnedListIds.contains(list.id),
                   !store.availableAppBarTabsItems.contains(where: { $0.key == listTabKey })
                {
                    count += 1
                }
            }
        }
        return count
    }

    // 辅助方法：计算在当前索引之前显示的项目数
    private func countDisplayedItemsBeforeIndex(_ successState: PagingStateSuccess<UiList>, currentIndex: Int) -> Int {
        var count = 0
        for i in 0 ..< currentIndex {
            if let list = successState.peek(index: Int32(i)) {
                let listTabKey = "list_\(store.accountType)_\(list.id)"
                if !store.pinnedListIds.contains(list.id),
                   !store.availableAppBarTabsItems.contains(where: { $0.key == listTabKey })
                {
                    count += 1
                }
            }
        }
        return count
    }

    @ViewBuilder
    private var availableFeedsSection: some View {
        if let state = lastFeedsState {
            switch onEnum(of: state.tabs) {
            case .loading:
                Section(header: Text("")) {
                    ForEach(0 ..< 3, id: \.self) { _ in
                        ListRowSkeletonView()
                            .padding(.horizontal)
                    }
                }.listRowBackground(theme.primaryBackgroundColor)
            case let .success(tabsData):
                if let feedTab = findFeedTab(in: tabsData.data) {
                    switch onEnum(of: feedTab.data) {
                    case .loading:
                        Section(header: Text("")) {
                            ForEach(0 ..< 3, id: \.self) { _ in
                                ListRowSkeletonView()
                                    .padding(.horizontal)
                            }
                        }.listRowBackground(theme.primaryBackgroundColor)
                    case let .success(feedsData):
                        let hasUnpinnedFeeds = checkForUnpinnedFeeds(feedsData)
                        if hasUnpinnedFeeds {
                            // 计算实际可用Feed总数和当前要显示的数量
                            let totalAvailableCount = countUnpinnedFeeds(feedsData)
                            let displayCount = isAvailableFeedsExpanded ?
                                min(Int(feedsData.itemCount), 30) :
                                min(availableListsLimit, totalAvailableCount)

                            Section(header:
                                HStack {
                                    Text("available feeds")
                                    Spacer()
                                    if totalAvailableCount > availableListsLimit {
                                        Image(systemName: isAvailableFeedsExpanded ? "chevron.up" : "chevron.down")
                                            .font(.system(size: 14))
                                            .foregroundColor(.gray)
                                    }
                                }
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    if totalAvailableCount > availableListsLimit {
                                        FlareHapticManager.shared.selection()
                                        withAnimation {
                                            isAvailableFeedsExpanded.toggle()
                                        }
                                    }
                                }
                            ) {
                                ForEach(0 ..< min(Int(feedsData.itemCount), 30), id: \.self) { index in
                                    if let feed = feedsData.peek(index: Int32(index)) {
                                        // 确保此Feed没有被固定，并且没有对应的标签
                                        let feedTabKey = "feed_\(store.accountType)_\(feed.id)"
                                        if !store.pinnedListIds.contains(feed.id),
                                           !store.availableAppBarTabsItems.contains(where: { $0.key == feedTabKey })
                                        {
                                            // 计算已显示的未固定Feed数量，用于确定是否显示当前Feed
                                            let displayedCount = countDisplayedFeedsBeforeIndex(feedsData, currentIndex: index)

                                            if displayedCount < displayCount {
                                                feedRowItem(for: feed)
                                                    .buttonStyle(PlainButtonStyle())
                                            }
                                        }
                                    }
                                }

                                // 添加"显示更多"按钮
                                if !isAvailableFeedsExpanded, totalAvailableCount > availableListsLimit {
                                    Button(action: {
                                        FlareHapticManager.shared.buttonPress()
                                        withAnimation {
                                            isAvailableFeedsExpanded = true
                                        }
                                    }) {
                                        HStack {
                                            Spacer()
                                            Text("Load more")
                                                .font(.footnote)
                                                .foregroundColor(.blue)
                                            Spacer()
                                        }
                                        .padding(.vertical, 8)
                                    }
                                }
                            }.listRowBackground(theme.primaryBackgroundColor)
                        }
                    default:
                        EmptyView()
                    }
                }
            default:
                EmptyView()
            }
        } else {
            Section(header: Text("")) {
                ForEach(0 ..< 3, id: \.self) { _ in
                    ListRowSkeletonView()
                        .padding(.horizontal)
                }
            }.listRowBackground(theme.primaryBackgroundColor)
        }
    }

    // 辅助方法：计算可用的未固定Feed总数
    private func countUnpinnedFeeds(_ feedsData: PagingStateSuccess<UiList>) -> Int {
        var count = 0
        for i in 0 ..< min(Int(feedsData.itemCount), 30) {
            if let feed = feedsData.peek(index: Int32(i)) {
                let feedTabKey = "feed_\(store.accountType)_\(feed.id)"
                if !store.pinnedListIds.contains(feed.id),
                   !store.availableAppBarTabsItems.contains(where: { $0.key == feedTabKey })
                {
                    count += 1
                }
            }
        }
        return count
    }

    // 辅助方法：计算在当前索引之前显示的Feed项目数
    private func countDisplayedFeedsBeforeIndex(_ feedsData: PagingStateSuccess<UiList>, currentIndex: Int) -> Int {
        var count = 0
        for i in 0 ..< currentIndex {
            if let feed = feedsData.peek(index: Int32(i)) {
                let feedTabKey = "feed_\(store.accountType)_\(feed.id)"
                if !store.pinnedListIds.contains(feed.id),
                   !store.availableAppBarTabsItems.contains(where: { $0.key == feedTabKey })
                {
                    count += 1
                }
            }
        }
        return count
    }

    private func checkForUnpinnedLists(_ successState: PagingStateSuccess<UiList>) -> Bool {
        for i in 0 ..< min(Int(successState.itemCount), 50) {
            if let list = successState.peek(index: Int32(i)) {
                let listTabKey = "list_\(store.accountType)_\(list.id)"
                if !store.pinnedListIds.contains(list.id),
                   !store.availableAppBarTabsItems.contains(where: { $0.key == listTabKey })
                {
                    return true
                }
            }
        }
        return false
    }

    private func checkForUnpinnedFeeds(_ feedsData: PagingStateSuccess<UiList>) -> Bool {
        for i in 0 ..< min(Int(feedsData.itemCount), 30) {
            if let feed = feedsData.peek(index: Int32(i)) {
                let feedTabKey = "feed_\(store.accountType)_\(feed.id)"
                if !store.pinnedListIds.contains(feed.id),
                   !store.availableAppBarTabsItems.contains(where: { $0.key == feedTabKey })
                {
                    return true
                }
            }
        }
        return false
    }

    private func listRowItem(for list: UiList) -> some View {
        ListItemRowView(
            list: list,
            isPinned: store.pinnedListIds.contains(list.id),
            showCreator: true,
            showMemberCount: false,
            onTap: {},
            onPinTap: {
                pinList(list)
            }
        )
        .contentShape(Rectangle())
        .onTapGesture {}
    }

    // Feed列表项
    private func feedRowItem(for feed: UiList) -> some View {
        ListItemRowView(
            list: feed,
            isPinned: store.pinnedListIds.contains(feed.id),
            showCreator: true,
            showMemberCount: false,
            onTap: {},
            onPinTap: {
                pinFeed(feed)
            }
        )
        .contentShape(Rectangle())
        .onTapGesture {}
    }

    private func startListObservation() {
        Task {
            for await state in listPresenter.models {
                if let state = state as? AllListState {
                    await MainActor.run {
                        lastListState = state
                    }
                }
            }
        }
    }

    // 观察Feeds状态
    private func startFeedsObservation() {
        guard let presenter = feedsPresenter else { return }

        Task {
            for await state in presenter.models {
                if let state = state as? PinnableTimelineTabPresenterState {
                    await MainActor.run {
                        lastFeedsState = state
                    }
                }
            }
        }
    }

    // 从Tabs中找到Feed类型的Tab
    private func findFeedTab(in tabs: ImmutableListWrapper<PinnableTimelineTabPresenterStateTab>) -> PinnableTimelineTabPresenterStateTab? {
        for i in 0 ..< tabs.size {
            if let tab = tabs.get(index: Int32(i)) as? PinnableTimelineTabPresenterStateTab {
                if tab is PinnableTimelineTabPresenterStateTabFeed {
                    return tab
                }
            }
        }
        return nil
    }

    private func pinList(_ list: UiList) {
        guard !isActionInProgress else { return }

        isActionInProgress = true

        // 添加轻微延迟，确保UI状态更新
        withAnimation(.easeInOut(duration: 0.2)) {
            var userInfo: [String: Any] = [
                "listId": list.id,
                "listTitle": list.title,
                "isPinned": true,
                "itemType": "list" // 明确指定为list类型
            ]

            if let iconUrl = list.avatar {
                userInfo["listIconUrl"] = iconUrl
            }

            NotificationCenter.default.post(
                name: .listPinStatusChanged,
                object: nil,
                userInfo: userInfo
            )
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            isActionInProgress = false
        }
    }

    // Pin Feed方法
    private func pinFeed(_ feed: UiList) {
        guard !isActionInProgress else { return }

        isActionInProgress = true

        // 添加轻微延迟，确保UI状态更新
        withAnimation(.easeInOut(duration: 0.2)) {
            var userInfo: [String: Any] = [
                "listId": feed.id,
                "listTitle": feed.title,
                "isPinned": true,
                "itemType": "feed" // 明确指定为feed类型
            ]

            if let iconUrl = feed.avatar {
                userInfo["listIconUrl"] = iconUrl
            }

            NotificationCenter.default.post(
                name: .listPinStatusChanged,
                object: nil,
                userInfo: userInfo
            )
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            isActionInProgress = false
        }
    }

    private func updateListTitle(list: UiList, newTitle: String) {
        guard newTitle != list.title, !newTitle.isEmpty else { return }

        // 先设置处理状态，防止并发操作
        isActionInProgress = true
        FlareLog.debug("更新列表标题: \(list.id) 从 '\(list.title)' 到 '\(newTitle)'")

        // 这里只更新本地存储的标题，不调用API
        NotificationCenter.default.post(
            name: .listTitleDidUpdate,
            object: nil,
            userInfo: [
                "listId": list.id,
                "oldTitle": list.title,
                "newTitle": newTitle
            ]
        )

        // 强制在主线程中更新标题显示，这是为了确保UI会即时更新
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            // 确保store的状态更新
            store.saveTabs()

            // 重置状态
            isActionInProgress = false
        }
    }

    // 更新updateListTitleById方法以支持Feed类型
    private func updateListTitleById(listId: String, oldTitle: String, newTitle: String) {
        guard newTitle != oldTitle, !newTitle.isEmpty else { return }

        // 先设置处理状态，防止并发操作
        isActionInProgress = true
        FlareLog.debug("更新标题: \(listId) 从 '\(oldTitle)' 到 '\(newTitle)'")

        // 这里只更新本地存储的标题，不调用API
        NotificationCenter.default.post(
            name: .listTitleDidUpdate,
            object: nil,
            userInfo: [
                "listId": listId,
                "oldTitle": oldTitle,
                "newTitle": newTitle,
                "itemType": editingItemIsBlueskyFeed ? "feed" : "list" // 添加类型标识
            ]
        )

        // 强制在主线程中更新标题显示，这是为了确保UI会即时更新
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            // 确保store的状态更新
            store.saveTabs()

            // 重置状态
            isActionInProgress = false
        }
    }
}
