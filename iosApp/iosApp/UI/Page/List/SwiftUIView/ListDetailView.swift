import Generated
import Kingfisher
import shared
import SwiftUI

struct ListDetailView: View {
    let list: UiList
    let accountType: AccountType
    @State private var showMembers: Bool = false
    @State private var presenter: ListTimelinePresenter
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject private var router: Router
    private let randomGradient: [Color]

    init(list: UiList, accountType: AccountType) {
        self.list = list
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
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    ZStack(alignment: .center) {
                        headerBackgroundView
                    }

                    Text(list.title)
                        .font(.title)
                        .bold()
                        .padding(.horizontal)
                        .padding(.top, 16)

                    HStack(alignment: .center, spacing: 12) {
                        if let creator = list.creator {
                            creatorProfileView(creator: creator)
                        } else {
                            unknownCreatorView
                        }

                        Spacer()

                        memberCountsView
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 12)

                    // 描述（如果有）
                    if let description = list.description_, !description.isEmpty {
                        Text(description)
                            .font(.body)
                            .padding()
                    }

                    Divider()
                        .padding(.top, 8)

                    // 查看成员按钮
                    Button(action: {
                        showMembers = true
                    }) {
                        HStack {
                            Image(systemName: "person.2")
                                .foregroundColor(.blue)
                            Text("查看成员")
                                .foregroundColor(.primary)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .foregroundColor(.gray)
                                .font(.caption)
                        }
                        .padding()
                    }
                    
                    // 时间线内容
                    VStack(alignment: .leading) {
                        Text("列表动态")
                            .font(.headline)
                            .padding(.horizontal)
                            .padding(.top)
                        
                        // 使用 StatusTimelineBuilder 模式
                        StatusTimelineComponent(
                            data: state.listState,
                            detailKey: nil
                        )
                    }

                    Spacer()
                }
            }
            .edgesIgnoringSafeArea(.top)
            .navigationBarTitle("", displayMode: .inline)
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
                        listId: list.id,
                        title: list.title
                    ),
                    isActive: $showMembers
                ) {
                    EmptyView()
                }
            )
        }
    }

    // 头部 banner
    private var headerBackgroundView: some View {
        ZStack {
            if let avatarString = list.avatar,
               let url = URL(string: avatarString)
            {
                KFImage(url)
//                    .placeholder { gradientBackground }
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .clipped()
//                    .blur(radius: 3)
                    .overlay(Color.black.opacity(0.2))
            } else {
                // 显示渐变背景
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
                Text("\(Int(list.likedCount))")
                    .font(.headline)
                    .bold()
                Text("members")
                    .font(.caption)
                    .foregroundColor(.gray)
            }
        }
    }
}
