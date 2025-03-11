import Generated
import Kingfisher
import shared
import SwiftUI

struct ListDetailView: View {
    let listInfo: UiList
    let accountType: AccountType
    @State private var showMembers: Bool = false
    @State private var presenter: ListTimelinePresenter
    @State private var showNavigationTitle: Bool = false
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject private var router: Router
    private let randomGradient: [Color]

    init(list: UiList, accountType: AccountType) {
        listInfo = list
        self.accountType = accountType
        _presenter = State(initialValue: ListTimelinePresenter(accountType: accountType, listId: list.id))
        let gradients: [[Color]] = [
            [.blue.opacity(0.7), .purple.opacity(0.5)],
            [.green.opacity(0.6), .blue.opacity(0.4)],
            [.purple.opacity(0.6), .pink.opacity(0.4)],
            [.orange.opacity(0.6), .yellow.opacity(0.4)],
            [.teal.opacity(0.6), .blue.opacity(0.4)],
        ]
        randomGradient = gradients[Int.random(in: 0 ..< gradients.count)]
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                Group {
                    headerBackgroundView
                        .listRowInsets(EdgeInsets())
                        .listRowSeparator(.hidden)
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
                            if let creator = listInfo.creator {
                                creatorProfileView(creator: creator)
                            } else {
                                unknownCreatorView
                            }

                            Spacer()

                            memberCountsView
                        }
                        .padding(.vertical, 12)
                    }
                    .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                    .listRowSeparator(.hidden)

                    if let description = listInfo.description_, !description.isEmpty {
                        Text(description)
                            .font(.body)
                            .padding(.vertical, 8)
                            .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                            .listRowSeparator(.hidden)
                    }

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
                    .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 8, trailing: 16))

                    Text("List Timeline")
                        .font(.headline)
                        .padding(.top, 16)
                        .padding(.bottom, 8)
                        .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                        .listRowSeparator(.hidden)
                }

                StatusTimelineComponent(
                    data: state.listState,
                    detailKey: nil
                )
                .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                .listRowSeparator(.hidden)
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

    private var headerBackgroundView: some View {
        ZStack {
            if let avatarString = listInfo.avatar,
               let url = URL(string: avatarString)
            {
                KFImage(url)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .clipped()
                    .overlay(Color.black.opacity(0.2))
            } else {
                gradientBackground
            }
        }
    }

    // 随机渐变背景
    private var gradientBackground: some View {
        ZStack {
            LinearGradient(
                gradient: Gradient(colors: randomGradient),
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .frame(height: 140)

            ForEach(0 ..< 6, id: \.self) { _ in
                Circle()
                    .fill(Color.white.opacity(Double.random(in: 0.05 ... 0.15)))
                    .frame(width: CGFloat.random(in: 30 ... 80),
                           height: CGFloat.random(in: 30 ... 80))
                    .offset(x: CGFloat.random(in: -150 ... 150),
                            y: CGFloat.random(in: -100 ... 100))
                    .blur(radius: CGFloat.random(in: 3 ... 8))
            }
            Image(systemName: "list.bullet.rectangle")
                .resizable()
                .scaledToFit()
                .frame(width: 80, height: 80)
                .foregroundColor(.white.opacity(0.8))
                .shadow(color: .black.opacity(0.3), radius: 5, x: 0, y: 2)
        }
    }

    private func creatorProfileView(creator: UiUserV2) -> some View {
        HStack(spacing: 8) {
            if let url = URL(string: creator.avatar) {
                KFImage(url)
                    .placeholder {
                        Circle()
                            .fill(Color.gray.opacity(0.2))
                    }
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 45, height: 45)
                    .clipShape(Circle())
            } else {
                Image(systemName: "person.circle.fill")
                    .resizable()
                    .frame(width: 45, height: 45)
                    .foregroundColor(.gray)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(creator.name.raw)
                    .font(.headline)
                    .lineLimit(1)

                Text("\(creator.handle)")
                    .font(.subheadline)
                    .foregroundColor(.gray)
                    .lineLimit(1)
            }
        }
    }

    private var unknownCreatorView: some View {
        HStack(spacing: 8) {
            Image(systemName: "person.circle.fill")
                .resizable()
                .frame(width: 45, height: 45)
                .foregroundColor(.gray)

            Text("")
                .font(.headline)
        }
    }

    private var memberCountsView: some View {
        HStack(spacing: 16) {
            VStack(alignment: .center, spacing: 2) {
                Text("\(Int(listInfo.likedCount))")
                    .font(.headline)
                    .bold()
                Text("members")
                    .font(.caption)
                    .foregroundColor(.gray)
            }
        }
    }
}
