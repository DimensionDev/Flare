import Generated
import Kingfisher
import shared
import SwiftUI

struct AllListsView: View {
    @State private var presenter: PinnableListPresenter
    @EnvironmentObject private var router: Router
    @Environment(\.appSettings) private var appSettings
    private let accountType: AccountType

    init(accountType: AccountType) {
        presenter = .init(accountType: accountType)
        self.accountType = accountType
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                switch onEnum(of: state.b)
                     switch onEnum(of: state.items) {
                    case .loading:
                        loadingListsView
                    case let .success(successData):
                        // 直接使用列表内容视图的逻辑
                        VStack(spacing: 0) {
                            ForEach(0 ..< successData.itemCount, id: \.self) { index in
                                if let list = successData.peek(index: Int32(index)) {
                                    EnhancedListRowView(list: list, accountType: accountType)
                                        .onAppear {
                                            // 获取数据并触发加载
                                            successData.get(index: Int32(index))
                                        }
                                }
                            }
                        }
                    case let .error(errorState):

                        ErrorView(error: error) {
                            state.refresh()
                        }
                    }
               
            }
            .navigationTitle("列表")
        }
    }

    // 加载状态视图
    private var loadingListsView: some View {
        VStack {
            ForEach(0 ..< 5, id: \.self) { _ in
                ListRowSkeletonView()
                    .padding(.horizontal)
            }
        }
    }
}

private struct EnhancedListRowView: View {
    let list: UiList
    @State private var isPinned: Bool
    @EnvironmentObject private var router: Router
    let accountType: AccountType

    init(list: UiList, accountType: AccountType) {
        self.list = list
        self.accountType = accountType
        _isPinned = State(initialValue: list.liked)
    }

    var body: some View {
        HStack(spacing: 12) {
            ListAvatarView(list: list)
                .frame(width: 50, height: 50)

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 4) {
                    Text(list.title)
                        .font(.headline)
                        .lineLimit(1)

                    // 如果有成员数，显示成员数量
                    if Int(list.likedCount) > 0 {
                        Text("·\(list.likedCount) members")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                            .lineLimit(1)
                    }
                }

                // 创建者信息
                if let creator = list.creator {
                    HStack(spacing: 4) {
                        let avatarUrl = creator.avatar
                        if avatarUrl != nil, let url = URL(string: avatarUrl ?? "") {
                            KFImage(url)
                                .placeholder {
                                    Circle()
                                        .fill(Color.gray.opacity(0.2))
                                }
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: 16, height: 16)
                                .clipShape(Circle())
                        } else {
                            Image(systemName: "person.circle.fill")
                                .resizable()
                                .frame(width: 16, height: 16)
                                .foregroundColor(.gray)
                        }

                        Text(creator.name.raw)
                            .font(.caption)
                            .foregroundColor(.gray)
                            .lineLimit(1)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            Button(action: {
                isPinned.toggle()
            }) {
                Image(systemName: isPinned ? "pin.fill" : "pin")
                    .foregroundColor(.blue)
                    .font(.system(size: 14))
            }
            .buttonStyle(BorderlessButtonStyle())
        }
        .padding(.vertical, 8)
    }
}

private struct ListAvatarView: View {
    let list: UiList

    var body: some View {
        if let avatarUrl = list.avatar, let url = URL(string: avatarUrl) {
            KFImage(url)
                .placeholder {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color.gray.opacity(0.2))
                }
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(width: 50, height: 50)
                .clipShape(RoundedRectangle(cornerRadius: 12))
        } else {
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(Color.blue.opacity(0.7))
                Image(systemName: "list.bullet")
                    .foregroundColor(.white)
                    .font(.system(size: 24))
            }
            .frame(width: 50, height: 50)
        }
    }
}
