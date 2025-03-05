import Combine
import shared
import SwiftUI

struct AllListsView: View {
    @StateObject var allListsViewModel: AllListsViewModel
    @StateObject var pinnableListsViewModel: PinnableListViewModel

    init(accountType: AccountType) {
        _allListsViewModel = StateObject(wrappedValue: AllListsViewModel(accountType: accountType))
        _pinnableListsViewModel = StateObject(wrappedValue: PinnableListViewModel(accountType: accountType))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // 常规列表区域
                VStack(alignment: .leading) {
                    Text("我的列表")
                        .font(.headline)
                        .padding(.horizontal)

                    switch allListsViewModel.listsState {
                    case .loading:
                        RegularListLoadingView()
                    case let .loaded(lists):
                        RegularListContentView(lists: lists)
                    case .empty:
                        EmptyStateView(
                            icon: "list.bullet",
                            title: "暂无列表",
                            message: "您当前没有创建或关注的列表",
                            actionTitle: "刷新",
                            action: { allListsViewModel.refresh() }
                        )
                    case let .error(error):
                        ErrorView(error: error) {
                            allListsViewModel.refresh()
                        }
                    }
                }

                Divider()
                    .padding(.vertical, 8)

                // 可固定列表区域
                VStack(alignment: .leading) {
                    Text("可固定列表")
                        .font(.headline)
                        .padding(.horizontal)

                    switch pinnableListsViewModel.listsState {
                    case .loading:
                        PinnableListLoadingView()
                    case let .loaded(lists):
                        PinnableListContentView(lists: lists)
                    case .empty:
                        EmptyStateView(
                            icon: "pin.fill",
                            title: "暂无可固定列表",
                            message: "当前没有可固定的列表",
                            actionTitle: "刷新",
                            action: { pinnableListsViewModel.refresh() }
                        )
                    case let .error(error):
                        ErrorView(error: error) {
                            pinnableListsViewModel.refresh()
                        }
                    }
                }
            }
            .padding(.vertical)
        }
        .navigationTitle("列表")
        .refreshable {
            await refreshAsync()
        }
        .overlay(Group {
            if allListsViewModel.isRefreshing || pinnableListsViewModel.isRefreshing {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
                    .background(Color.black.opacity(0.1))
            }
        })
        .errorAlert(error: $allListsViewModel.error)
    }

    func refreshAsync() async {
        allListsViewModel.refresh()
        pinnableListsViewModel.refresh()
        // 等待一段时间以确保刷新显示
        try? await Task.sleep(nanoseconds: 1_500_000_000)
    }
}

// 常规列表加载视图
private struct RegularListLoadingView: View {
    var body: some View {
        VStack {
            ForEach(0 ..< 3, id: \.self) { _ in
                ListRowSkeletonView()
                    .padding(.horizontal)
            }
        }
    }
}

// 可固定列表加载视图
private struct PinnableListLoadingView: View {
    var body: some View {
        VStack {
            ForEach(0 ..< 2, id: \.self) { _ in
                ListRowSkeletonView()
                    .padding(.horizontal)
            }
        }
    }
}


private struct RegularListContentView: View {
    let lists: [UiList]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(lists, id: \.id) { list in
                NavigationLink(destination: ListDetailView(list: list)) {
                    ListRowView(list: list)
                        .padding(.horizontal)
                }
                .buttonStyle(PlainButtonStyle())

                Divider()
                    .padding(.leading)
            }
        }
    }
}


private struct PinnableListContentView: View {
    let lists: [UiList]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(lists, id: \.id) { list in
                NavigationLink(destination: ListDetailView(list: list)) {
                    ListRowView(list: list)
                        .padding(.horizontal)
                }
                .buttonStyle(PlainButtonStyle())

                Divider()
                    .padding(.leading)
            }
        }
    }
}
