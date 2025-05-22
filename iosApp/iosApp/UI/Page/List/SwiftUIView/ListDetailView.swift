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

    private let gradientColors: [Color]

    init(list: UiList, accountType: AccountType, defaultUser: UiUserV2? = nil) {
        listInfo = list
        self.accountType = accountType
        self.defaultUser = defaultUser
        _presenter = State(initialValue: ListTimelinePresenter(accountType: accountType, listId: list.id))

        gradientColors = ListFeedHeaderView.getGradientColors(for: list.id)
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
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

                StatusTimelineComponent(
                    data: state.listState,
                    detailKey: nil
                ).listRowBackground(theme.primaryBackgroundColor)
                    .listFeedContentStyle()
            }
            .listStyle(PlainListStyle())
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
        }
    }
}
