import Generated
import Kingfisher
import shared
import SwiftUI

struct ListMembersView: View {
    @StateObject var viewModel: ListMembersViewModel
    @EnvironmentObject private var router: Router
    @Environment(\.appSettings) private var appSettings

    init(accountType: AccountType, listId: String) {
        _viewModel = StateObject(wrappedValue:
            ListMembersViewModel(accountType: accountType, listId: listId))
    }

    var body: some View {
        ZStack {
            switch viewModel.membersState {
            case .loading:
                loadingView
            case let .loaded(members):
                loadedView(members: members)
            case .empty:
                emptyStateView
            case let .error(error):
                errorView(error: error)
            }
        }
        .errorAlert(error: $viewModel.error)
    }

    private var loadingView: some View {
        VStack {
            ForEach(0 ..< 5, id: \.self) { _ in
                HStack(spacing: 16) {
                    Circle()
                        .fill(Color.gray.opacity(0.2))
                        .frame(width: 50, height: 50)

                    VStack(alignment: .leading, spacing: 8) {
                        RoundedRectangle(cornerRadius: 4)
                            .fill(Color.gray.opacity(0.2))
                            .frame(height: 14)
                            .frame(width: 120)

                        RoundedRectangle(cornerRadius: 4)
                            .fill(Color.gray.opacity(0.2))
                            .frame(height: 12)
                            .frame(width: 80)
                    }

                    Spacer()
                }
                .padding(.vertical, 8)
                .padding(.horizontal)
                .shimmering()
            }
        }
    }

    private func loadedView(members: [UiUserV2]) -> some View {
        ScrollView {
            VStack(spacing: 0) {
                if members.isEmpty {
                    Text("没有成员")
                        .font(.headline)
                        .foregroundColor(.secondary)
                        .padding()
                } else {
                    memberRows(members: members)
                }
            }
        }
    }

    // 成员行视图
    private func memberRows(members: [UiUserV2]) -> some View {
        VStack(spacing: 0) {
            // 手动列出最多前10个成员，避免嵌套循环
            if members.count > 0 {
                memberRow(member: members[0])
            }
            if members.count > 1 {
                memberRow(member: members[1])
            }
            if members.count > 2 {
                memberRow(member: members[2])
            }
            if members.count > 3 {
                memberRow(member: members[3])
            }
            if members.count > 4 {
                memberRow(member: members[4])
            }
            if members.count > 5 {
                memberRow(member: members[5])
            }
            if members.count > 6 {
                memberRow(member: members[6])
            }
            if members.count > 7 {
                memberRow(member: members[7])
            }
            if members.count > 8 {
                memberRow(member: members[8])
            }
            if members.count > 9 {
                memberRow(member: members[9])
            }
            if members.count > 10 {
                Text("更多 \(members.count - 10) 名成员...")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .padding()
            }
        }
    }

    // 单个成员行
    private func memberRow(member: UiUserV2) -> some View {
        Button(action: {
            // 导航到用户个人资料
            if let key = member.key as? MicroBlogKey {
                router.navigate(to: AppleRoute.Profile(accountType: viewModel.accountType, userKey: key))
            }
        }) {
            HStack {
                // 用户头像
                UserAvatar(data: member.avatar, size: 48)

                // 用户信息
                VStack(alignment: .leading, spacing: 4) {
                    Text(member.name.raw)
                        .font(.headline)

                    Text(member.handle)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }

                Spacer()
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 16)
        }
        .buttonStyle(PlainButtonStyle())
    }

    // 空状态视图
    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.3.sequence")
                .font(.system(size: 50))
                .foregroundColor(.gray)

            Text("没有成员")
                .font(.title3)
                .fontWeight(.semibold)

            Text("当前列表中没有任何成员")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Button(action: {
                viewModel.refresh()
            }) {
                Text("刷新")
                    .fontWeight(.semibold)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Color.accentColor)
                    .foregroundColor(.white)
                    .cornerRadius(8)
            }
        }
        .padding()
    }

    // 错误视图
    private func errorView(error: Error) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 50))
                .foregroundColor(.orange)

            Text("加载失败")
                .font(.title3)
                .fontWeight(.bold)

            Text(error.localizedDescription)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Button(action: {
                viewModel.refresh()
            }) {
                Text("重试")
                    .fontWeight(.semibold)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(Color.accentColor)
                    .foregroundColor(.white)
                    .cornerRadius(8)
            }
        }
        .padding()
    }
}

// 用于预览的UserProfileView存根
struct UserProfileView: View {
    let user: UiUserV2

    var body: some View {
        Text("用户资料: \(user.name)")
            .navigationTitle("\(user.name)")
    }
}
