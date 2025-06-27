import Generated
import Kingfisher
import shared
import SwiftUI

struct ListMembersView: View {
    @State private var presenter: ListMembersPresenter
    @Environment(\.appSettings) private var appSettings
    @State private var lastKnownItemCount: Int = 0
    private let title: String
    private let accountType: AccountType
    @Environment(FlareTheme.self) private var theme

    init(accountType: AccountType, listId: String, title: String = "List Members") {
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
                    ForEach(0 ..< Int(successData.itemCount), id: \.self) { index in
                        if successData.itemCount > index {
                            if let member = successData.peek(index: Int32(index)) {
                                memberRow(index: index, member: member)
                                    .onAppear {
                                        FlareLog.debug("ListMembers Debug Info: itemCount=\(successData.itemCount), index=\(index), lastKnownItemCount=\(lastKnownItemCount)")

                                        successData.get(index: Int32(index))

                                        lastKnownItemCount = Int(successData.itemCount)

                                        FlareLog.debug("ListMembers Update: itemCount=\(successData.itemCount), index=\(index), lastKnownItemCount=\(lastKnownItemCount)")
                                    }
                            }
                        }
                    }.listRowBackground(theme.primaryBackgroundColor)
                case .empty:
                    emptyStateView
                case let .error(errorData):
                    let detailedError = NSError(
                        domain: "ListMembers",
                        code: -1,
                        userInfo: [NSLocalizedDescriptionKey: errorData.error.message ?? "Loading error"]
                    )
                    errorView(error: detailedError)
                default:
                    emptyStateView
                }
            }
            .scrollContentBackground(.hidden)
            .background(theme.secondaryBackgroundColor)
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Text("Total: \(lastKnownItemCount)")
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
                Text("loaded members: \(itemCount)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                Text("server total: \(itemCount)")
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

    // single member row
    private func memberRow(index _: Int, member: UiUserV2) -> some View {
        Button(action: {}) {
            HStack {
                // Text("#\(index + 1)")
                //     .font(.system(size: 14, weight: .bold))
                //     .foregroundColor(.secondary)
                //     .frame(width: 40, alignment: .leading)

                UserAvatar(data: member.avatar, size: 48)

                VStack(alignment: .leading, spacing: 4) {
                    Text(member.name.raw)
                        .font(.headline)

                    Text(member.handleWithoutFirstAt)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }

                Spacer()
            }
            .padding(.vertical, 8)
        }
        .buttonStyle(PlainButtonStyle())
    }

    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: "person.3.sequence")
                .font(.system(size: 50))
                .foregroundColor(.gray)

            Text("No members")
                .font(.title3)
                .fontWeight(.semibold)

            Text("No members in this list")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .padding()
    }

    private func errorView(error: Error) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 50))
                .foregroundColor(.orange)

            Text("Load failed")
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

struct UserProfileView: View {
    let user: UiUserV2

    var body: some View {
        Text("User profile: \(user.name)")
            .navigationTitle("\(user.name)")
    }
}
