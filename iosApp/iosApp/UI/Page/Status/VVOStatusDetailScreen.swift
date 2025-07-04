import shared
import SwiftUI

struct VVOStatusDetailScreen: View {
    @State private var presenter: VVOStatusDetailPresenter
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var type: DetailStatusType = .comment
    private let statusKey: MicroBlogKey

    // 获取全局的AppState
    @Environment(FlareAppState.self) private var menuState

    init(accountType: AccountType, statusKey: MicroBlogKey) {
        presenter = .init(accountType: accountType, statusKey: statusKey)
        self.statusKey = statusKey
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            HStack {
                if horizontalSizeClass != .compact {
                    switch onEnum(of: state.status) {
                    case let .success(data): StatusItemView(data: data.data, detailKey: statusKey)
                    case .loading: StatusPlaceHolder()
                    case .error: EmptyView()
                    }
                }
                List {
                    if horizontalSizeClass == .compact {
                        switch onEnum(of: state.status) {
                        case let .success(data): StatusItemView(data: data.data, detailKey: statusKey)
                        case .loading: StatusPlaceHolder()
                        case .error: EmptyView()
                        }
                        Picker("notification_type", selection: $type) {
                            Text("status_detail_repost")
                                .tag(DetailStatusType.repost)
                            Text("status_detail_comment")
                                .tag(DetailStatusType.comment)
                        }
                        .pickerStyle(.segmented)
                        .listRowSeparator(.hidden)
                    }
                    switch type {
                    case .comment:
                        StatusTimelineComponent(data: state.comment, detailKey: nil)
                    case .repost:
                        StatusTimelineComponent(data: state.repost, detailKey: nil)
                    }
                }
                .listStyle(.plain)
            }
            .navigationTitle("status_detail")
            #if os(iOS)
                .navigationBarTitleDisplayMode(.inline)
//           #else
//           .toolbar {
//               ToolbarItem(placement: .confirmationAction) {
//                   Button(action: {
//                       Task {
//                           try? await state.refresh()
//                       }
//                   }, label: {
//                       Image(systemName: "arrow.clockwise.circle")
//                   })
//               }
//           }
            #endif
                .toolbar {
                    if horizontalSizeClass != .compact {
                        ToolbarItem(placement: .primaryAction) {
                            Picker("notification_type", selection: $type) {
                                Text("status_detail_repost")
                                    .tag(DetailStatusType.repost)
                                Text("status_detail_comment")
                                    .tag(DetailStatusType.comment)
                            }
                            .pickerStyle(.segmented)
                        }
                    }
                }
        }
        // 使用新的导航手势修饰符
        .environment(menuState)
    }
}

enum DetailStatusType {
    case comment
    case repost
}
