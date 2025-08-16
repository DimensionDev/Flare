import Generated
import Kingfisher
import shared
import SwiftUI

struct ListDetailView: View {
    let listInfo: UiList
    let accountType: AccountType
    let defaultUser: UiUserV2?
    @State private var showMembers: Bool = false
    @State private var presenter: ListTimelinePresenter
    @State private var showNavigationTitle: Bool = false
    @Environment(\.presentationMode) var presentationMode
    @Environment(FlareTheme.self) private var theme

    @State private var timelineViewModel = TimelineViewModel()

    private let gradientColors: [Color]

    init(list: UiList, accountType: AccountType, defaultUser: UiUserV2? = nil) {
        listInfo = list
        self.accountType = accountType
        self.defaultUser = defaultUser
        _presenter = State(initialValue: ListTimelinePresenter(accountType: accountType, listId: list.id))

        gradientColors = ListFeedHeaderView.getGradientColors(for: list.id)
    }

    var body: some View {
        ScrollViewReader { _ in
            VStack {
                List {
                    EmptyView()
                        .id("list-timeline-top")
                        .frame(height: 0)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets())

                    Group {
                        ListFeedHeaderView.ListFeedHeaderBackgroundView(listInfo: listInfo, gradientColors: gradientColors)
                            .listFeedHeaderStyle()
                            .onAppear {
                                showNavigationTitle = false
                            }
                            .onDisappear {
                                showNavigationTitle = true
                            }

                        VStack(alignment: .leading, spacing: 0) {
                            Text(listInfo.title)
                                .font(.title)
                                .bold()
                                .padding(.top, 16)

                            HStack(alignment: .center, spacing: 12) {
                                if let user = defaultUser {
                                    ListFeedHeaderView.ListFeedCreatorProfileView(creator: user)
                                } else if let creator = listInfo.creator {
                                    ListFeedHeaderView.ListFeedCreatorProfileView(creator: creator)
                                } else {
                                    ListFeedHeaderView.ListFeedUnknownCreatorView()
                                }

                                Spacer()
                                if defaultUser == nil {
                                    ListFeedHeaderView.ListFeedMemberCountsView(count: listInfo.likedCount, isListView: true)
                                }
                            }
                            .padding(.vertical, 12)
                        }
                        .listFeedContentStyle()

                        if let description = listInfo.description_, !description.isEmpty {
                            Text(description)
                                .font(.body)
                                .padding(.vertical, 8)
                                .listFeedContentStyle()
                        }

                        if defaultUser == nil {
                            Button(action: {
                                showMembers = true
                            }) {
                                HStack {
                                    Image(systemName: "person.2")
                                        .foregroundColor(.blue)
                                    Text("Show Members")
                                        .foregroundColor(.primary)
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .foregroundColor(.gray)
                                        .font(.caption)
                                }
                            }
                            .listFeedContentStyle()
                        }
                        Text("List Timeline")
                            .font(.headline)
                            .padding(.top, 16)
                            .padding(.bottom, 8)
                            .listFeedContentStyle()
                    }.scrollContentBackground(.hidden).listRowBackground(theme.primaryBackgroundColor)

                    switch timelineViewModel.timelineState {
                    case .loading:
                        ForEach(0 ..< 5, id: \.self) { _ in
                            TimelineStatusViewV2(
                                item: createSampleTimelineItem(),
                                timelineViewModel: timelineViewModel
                            ).padding(.horizontal, 16)
                                .redacted(reason: .placeholder)
                                .listRowBackground(theme.primaryBackgroundColor)
                                .listRowInsets(EdgeInsets())
                                .listRowSeparator(.hidden)
                        }

                    case let .loaded(items, hasMore):
                        TimelineItemsView(
                            items: items,
                            hasMore: hasMore,
                            viewModel: timelineViewModel
                        )
                        .listRowBackground(theme.primaryBackgroundColor)
                        .listRowInsets(EdgeInsets())

                    case let .error(error):
                        TimelineErrorView(error: error) {
                            Task {
                                await timelineViewModel.handleRefresh()
                            }
                        }
                        .listRowInsets(EdgeInsets())

                    case .empty:
                        TimelineEmptyView()
                            .listRowBackground(theme.primaryBackgroundColor)
                            .listRowInsets(EdgeInsets())
                    }

                    EmptyView()
                        .id("list-timeline-bottom")
                        .frame(height: 0)
                        .listRowSeparator(.hidden)
                        .listRowInsets(EdgeInsets())
                }
                .listStyle(.plain)
                .refreshable {
                    FlareLog.debug("[ListDetailView] 下拉刷新触发")
                    await timelineViewModel.handleRefresh()
                    FlareLog.debug("[ListDetailView] 下拉刷新完成")
                }
            }
            .edgesIgnoringSafeArea(.top)
            .navigationBarTitle(showNavigationTitle ? listInfo.title : "", displayMode: .inline)
            .navigationBarBackButtonHidden(true)
            .navigationBarItems(leading: Button(action: {
                presentationMode.wrappedValue.dismiss()
            }) {
                HStack(spacing: 4) {
                    Image(systemName: "chevron.left")
                    Text("")
                }
                .foregroundColor(.blue)
            })
            .background(
                NavigationLink(
                    destination:
                    ListMembersView(
                        accountType: accountType,
                        listId: listInfo.id,
                        title: listInfo.title
                    ),
                    isActive: $showMembers
                ) {
                    EmptyView()
                }
            )

            .task {
                FlareLog.debug("🔧 [ListDetailView] 开始初始化 TimelineViewModel")
                await timelineViewModel.setupDataSource(presenter: presenter)
                FlareLog.debug("✅ [ListDetailView] TimelineViewModel 初始化完成")
            }
            .onAppear {
                FlareLog.debug("👁️ [ListDetailView] onAppear - 调用resume()")
                timelineViewModel.resume()
            }
            .onDisappear {
                FlareLog.debug("👁️ [ListDetailView] onDisappear - 调用pause()")
                timelineViewModel.pause()
            }
        }
    }
}
