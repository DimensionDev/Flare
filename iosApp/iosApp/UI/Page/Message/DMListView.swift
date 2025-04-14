import SwiftUI
import shared

/// DM对话列表视图
struct DMListView: View {
    let accountType: AccountType
    
    var body: some View {
        DMListContent(presenter: DMListPresenter(accountType: accountType))
    }
}

/// 使用KMPView协议封装DM列表内容
struct DMListContent: KMPView {
    typealias P = DMListPresenter
    typealias S = DMListState
    
    let presenter: DMListPresenter
    
    func body(state: DMListState) -> some View {
        List {
            if case let .success(success) = onEnum(of: state.items) {
                ForEachWithIndex(0, count: success.itemCount) { index in
                    if let room = success.peek(index: index) {
                        NavigationLink(
                            destination: DMConversationView(
                                accountType: UserManager.shared.getCurrentAccount() ?? AccountTypeGuest(),
                                roomKey: room.key
                            )
                        ) {
                            DMRoomItemView(room: room)
                        }
                        .onAppear {
                            // 当单元格出现时，请求加载该项目数据
                            success.get(index: index)
                            
                            // 不需要手动加载更多，KMP会自动处理
                        }
                    } else {
                        DMRoomPlaceholderView()
                    }
                }
            } else if case .loading = onEnum(of: state.items) {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding()
            } else if case .error = onEnum(of: state.items) {
                Text("加载失败")
                    .foregroundColor(.red)
                    .padding()
            }
        }
        .listStyle(PlainListStyle())
        .refreshable {
            try? await state.refreshSuspend()
        }
        .overlay {
            if state.isRefreshing {
                ProgressView()
            }
        }
    }
}




// 占位视图
struct DMRoomPlaceholderView: View {
    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(Color.gray.opacity(0.3))
                .frame(width: 50, height: 50)
            
            VStack(alignment: .leading, spacing: 4) {
                Rectangle()
                    .fill(Color.gray.opacity(0.3))
                    .frame(height: 18)
                    .frame(maxWidth: 120)
                
                Rectangle()
                    .fill(Color.gray.opacity(0.3))
                    .frame(height: 14)
                    .frame(maxWidth: 200)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .redacted(reason: .placeholder)
    }
} 
