import Kingfisher
import os
import shared
import SwiftUI

private let logger = Logger(subsystem: "com.flare.app", category: "HomeAppBarSettingsView")

struct HomeAppBarSettingsView: View {
    @Environment(\.dismiss) var dismiss
    @ObservedObject var store: AppBarTabSettingStore

    // 添加列表 presenter 状态
    @State private var listPresenter: AllListPresenter
    @State private var lastListState: AllListState?
    @State private var isActionInProgress = false

    // 添加编辑标题所需的状态
    @State private var isEditingTitle = false
    @State private var editingList: UiList?
    @State private var editedTitle = ""

    // 添加使用ID和标题更新的状态
    @State private var editingListId: String?
    @State private var editingListTitle: String = ""

    init(store: AppBarTabSettingStore) {
        self.store = store
        _listPresenter = State(initialValue: AllListPresenter(accountType: store.accountType))
    }

    var body: some View {
        NavigationView {
            List {
                // 主要标签
                if let primaryTab = store.primaryHomeItems.first {
                    Section(header: Text("main tab")) {
                        TabItemRow(tab: primaryTab, store: store, isPrimary: true)
                    }
                }

                // 次要标签
                Section(header: Text("used tabs")) {
                    ForEach(store.availableAppBarTabsItems, id: \.key) { tab in
                        if let primaryTab = store.primaryHomeItems.first, tab.key != primaryTab.key {
                            // 过滤掉列表标签，
                            if !tab.key.starts(with: "list_") {
                                TabItemRow(tab: tab, store: store, isPrimary: false)
                            }
                        }
                    }
                    .onMove { source, destination in
                        store.moveTab(from: source, to: destination)
                    }
                }

                // 未启用的标签
                let unusedTabs = store.secondaryItems.filter { tab in
                    !store.availableAppBarTabsItems.contains { $0.key == tab.key }
                }
                if !unusedTabs.isEmpty {
                    Section(header: Text("unused tabs")) {
                        ForEach(unusedTabs, id: \.key) { tab in
                            TabItemRow(tab: tab, store: store, isPrimary: false)
                        }
                    }
                }

                // pinned lists
                let pinnedListTabs = store.availableAppBarTabsItems.filter { $0.key.starts(with: "list_") }
                if !pinnedListTabs.isEmpty {
                    Section(header: Text("pinned lists")) {
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
                                            isEditingTitle = true
                                        }
                                    )
                                }
                            }
                        }
                        .onMove { source, destination in
                            store.moveTab(from: source, to: destination)
                        }
                    }
                }

                // available no pinned lists
                Section(header: Text("available lists")) {
                    if let state = lastListState,
                       let successState = state.items as? PagingStateSuccess<UiList>
                    {
                        ForEach(0 ..< min(Int(successState.itemCount), 50), id: \.self) { index in
                            if let list = successState.peek(index: Int32(index)) {
                                if !store.pinnedListIds.contains(list.id) {
                                    listRowItem(for: list)
                                        .buttonStyle(PlainButtonStyle())
                                }
                            }
                        }
                    } else if lastListState?.items is PagingStateEmpty {
                        Text("No Lists")
                            .foregroundColor(.gray)
                            .frame(maxWidth: .infinity, alignment: .center)
                            .padding()
                    } else if lastListState?.items is PagingStateError {
                        VStack {
                            Text("Error loading lists")
                                .foregroundColor(.red)
                        }
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding()
                    } else {
                        // 加载中状态或初始状态
                        ForEach(0 ..< 3, id: \.self) { _ in
                            ListRowSkeletonView()
                                .padding(.horizontal)
                        }
                    }
                }
            }
            .navigationTitle("AppBar Settings")
            .navigationBarItems(
                leading: Button("Close") {
                    dismiss()
                }
            )
            .onAppear {
                // 视图出现时启动对列表状态的观察
                startListObservation()
            }
        }
        .environment(\.editMode, .constant(.active))
        .sheet(isPresented: $isEditingTitle, onDismiss: {
            editingList = nil // 清理编辑状态
            editingListId = nil
            editingListTitle = ""
        }) {
            if let list = editingList {
                EditAppBarSettingListTitleView(
                    title: $editedTitle,
                    listId: list.id,
                    iconUrl: list.avatar,
                    onSave: { newTitle in
                        updateListTitle(list: list, newTitle: newTitle)
                        isEditingTitle = false
                    },
                    onCancel: {
                        isEditingTitle = false
                    }
                )
            } else if let listId = editingListId, !editingListTitle.isEmpty {
                // 使用 ID 和标题直接编辑
                EditAppBarSettingListTitleView(
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
                    }
                )
            }
        }
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

    private func pinList(_ list: UiList) {
        guard !isActionInProgress else { return }

        isActionInProgress = true

        // 添加轻微延迟，确保UI状态更新
        withAnimation(.easeInOut(duration: 0.2)) {
            var userInfo: [String: Any] = [
                "listId": list.id,
                "listTitle": list.title,
                "isPinned": true,
            ]

            if let iconUrl = list.avatar {
                userInfo["iconUrl"] = iconUrl
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
        logger.debug("更新列表标题: \(list.id) 从 '\(list.title)' 到 '\(newTitle)'")

        // 这里只更新本地存储的标题，不调用API
        NotificationCenter.default.post(
            name: .listTitleDidUpdate,
            object: nil,
            userInfo: [
                "listId": list.id,
                "oldTitle": list.title,
                "newTitle": newTitle,
            ]
        )

        // 强制在主线程中更新标题显示，这是为了确保UI会即时更新
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            // 确保store的状态更新
            store.saveTabs()
            store.objectWillChange.send()

            // 重置状态
            isActionInProgress = false
        }
    }

    // 添加使用ID和标题更新的方法
    private func updateListTitleById(listId: String, oldTitle: String, newTitle: String) {
        guard newTitle != oldTitle, !newTitle.isEmpty else { return }

        // 先设置处理状态，防止并发操作
        isActionInProgress = true
        logger.debug("使用ID和标题更新列表标题: \(listId) 从 '\(oldTitle)' 到 '\(newTitle)'")

        // 这里只更新本地存储的标题，不调用API
        NotificationCenter.default.post(
            name: .listTitleDidUpdate,
            object: nil,
            userInfo: [
                "listId": listId,
                "oldTitle": oldTitle,
                "newTitle": newTitle,
            ]
        )

        // 强制在主线程中更新标题显示，这是为了确保UI会即时更新
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            // 确保store的状态更新
            store.saveTabs()
            store.objectWillChange.send()

            // 重置状态
            isActionInProgress = false
        }
    }
}
