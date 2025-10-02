import SwiftUI
import KotlinSharedUI

struct VVOStatusScreen: View {
    let statusKey: MicroBlogKey
    let accountType: AccountType
    @StateObject private var presenter: KotlinPresenter<VVOStatusDetailState>
    @State private var selectedType: VVOStatusDetailType = .comment
    var body: some View {
        List {
            ListCardView {
                StateView(state: presenter.state.status) { item in
                    TimelineView(data: item, detailStatusKey: statusKey)
                } errorContent: { error in
                    ListErrorView(error: error, onRetry: {})
                } loadingContent: {
                    TimelinePlaceholderView()
                }
                .padding()
            }
            .listRowSeparator(.hidden)
            .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
            .padding(.horizontal)
            .listRowBackground(Color.clear)
            
            Picker(selection: $selectedType) {
                Text("vvo_status_reposts").tag(VVOStatusDetailType.repost)
                Text("vvo_status_comments").tag(VVOStatusDetailType.comment)
            } label: {
                
            }
            .pickerStyle(.segmented)
            .listRowSeparator(.hidden)
            .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
            .padding(.horizontal)
            .listRowBackground(Color.clear)
            
            switch selectedType {
            case .comment: TimelinePagingView(data: presenter.state.comment)
                    .listRowSeparator(.hidden)
                    .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                    .padding(.horizontal)
                    .listRowBackground(Color.clear)
            case .repost: TimelinePagingView(data: presenter.state.repost)
                    .listRowSeparator(.hidden)
                    .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                    .padding(.horizontal)
                    .listRowBackground(Color.clear)
            }

            
        }
        .scrollContentBackground(.hidden)
        .listRowSpacing(2)
        .listStyle(.plain)
        .background(Color(.systemGroupedBackground))
        .navigationTitle("vvo_status_title")
    }
}

enum VVOStatusDetailType {
    case comment
    case repost
}

extension VVOStatusScreen {
    init(accountType: AccountType, statusKey: MicroBlogKey) {
        self.accountType = accountType
        self.statusKey = statusKey
        self._presenter = .init(wrappedValue: .init(presenter: VVOStatusDetailPresenter(accountType: accountType, statusKey: statusKey)))
    }
}

struct VVOCommentScreen: View {
    let statusKey: MicroBlogKey
    let accountType: AccountType
    @StateObject private var presenter: KotlinPresenter<VVOCommentState>
    var body: some View {
        List {
            StateView(state: presenter.state.root) { item in
                TimelineView(data: item, detailStatusKey: statusKey)
            } errorContent: { error in
                ListErrorView(error: error, onRetry: {})
            } loadingContent: {
                TimelinePlaceholderView()
            }
            .listRowSeparator(.hidden)
            .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
            .padding(.horizontal)
            .padding()
            .listRowBackground(Color.clear)
            TimelinePagingView(data: presenter.state.list)
                .listRowSeparator(.hidden)
                .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                .padding(.horizontal)
                .listRowBackground(Color.clear)
        }
        .scrollContentBackground(.hidden)
        .listRowSpacing(2)
        .listStyle(.plain)
        .background(Color(.systemGroupedBackground))
    }
}

extension VVOCommentScreen {
    init(accountType: AccountType, statusKey: MicroBlogKey) {
        self.accountType = accountType
        self.statusKey = statusKey
        self._presenter = .init(wrappedValue: .init(presenter: VVOCommentPresenter(accountType: accountType, commentKey: statusKey)))
    }
}
