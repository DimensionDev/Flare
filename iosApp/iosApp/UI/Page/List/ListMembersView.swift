import Generated
import Kingfisher
import shared
import SwiftUI

struct ListMembersView: View {
    @State private var presenter: ListMembersPresenter
    @EnvironmentObject private var router: Router
    @Environment(\.appSettings) private var appSettings
    @State private var lastKnownItemCount: Int = 0
    private let title: String
    private let accountType: AccountType

    init(accountType: AccountType, listId: String, title: String = "列表成员") {
        presenter = .init(accountType: accountType, listId: listId)
        self.title = title
        self.accountType = accountType
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                switch onEnum(of: state.memberInfo) {
                case .loading:
                    loadingView
                case let .success(successData):

                    // 成员列表
                    ForEach(0 ..< Int(successData.itemCount), id: \.self) { index in
                        if successData.itemCount > index {
                            if let member = successData.peek(index: Int32(index)) {
                                memberRow(index: index, member: member)
                                    .onAppear {
                                        // 打印调试信息
                                        print("🟢 调试信息: itemCount=\(successData.itemCount), index=\(index), lastKnownItemCount=\(lastKnownItemCount)")

                                        successData.get(index: Int32(index))
                                        // 更新计数
                                        lastKnownItemCount = Int(successData.itemCount)

                                        // 打印更新后的计数
                                        print("🟡 更新后: itemCount=\(successData.itemCount), index=\(index), lastKnownItemCount=\(lastKnownItemCount)")
                                    }
                            }
                        }
                    }
                case .empty:
                    emptyStateView
                case let .error(errorData):
                    let detailedError = NSError(
                        domain: "ListMembers",
                        code: -1,
                        userInfo: [NSLocalizedDescriptionKey: errorData.error.message ?? "加载错误"]
                    )
                    errorView(error: detailedError)
                default:
                    emptyStateView
                }
            }
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Text("总数: \(lastKnownItemCount)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
        }
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

    private func statusInfoView(itemCount: Int) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text("已加载成员: \(itemCount)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text("服务端总数: \(itemCount)")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Spacer()
        }
        .padding()
        .background(Color.gray.opacity(0.1))
        .cornerRadius(8)
        .padding(.horizontal)
        .padding(.top)
    }

    // 单个成员行
    private func memberRow(index: Int, member: UiUserV2) -> some View {
        Button(action: {
            // 导航到用户个人资料 - 暂时注释掉依赖 router 的代码
            // if let key = member.key as? MicroBlogKey {
            //     router.navigate(to: AppleRoute.Profile(accountType: accountType, userKey: key))
            // }
        }) {
            HStack {
                // 显示序号
                Text("#\(index + 1)")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.secondary)
                    .frame(width: 40, alignment: .leading)

                //
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
