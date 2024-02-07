import SwiftUI
import shared

struct NotificationScreen: View {
    @State var viewModel = NotificationViewModel()
    @Environment(StatusEvent.self) var statusEvent: StatusEvent
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    var body: some View {
        List {
            if horizontalSizeClass == .compact,
               case .success(let data) = onEnum(of: viewModel.model.allTypes),
               data.data.count > 1 {
                Picker("notification_type", selection: $viewModel.notificationType) {
                    ForEach(1...data.data.count, id: \.self) { index in
                        if let item = data.data[index - 1] as? NotificationFilter {
                            Text(item.name)
                                .tag(item)
                        }
                    }
                }
                .pickerStyle(.segmented)
                .listRowSeparator(.hidden)
            }
            StatusTimelineComponent(
                data: viewModel.model.listState,
                mastodonEvent: statusEvent,
                misskeyEvent: statusEvent,
                blueskyEvent: statusEvent,
                xqtEvent: statusEvent
            )
        }
        .listStyle(.plain)
        .refreshable {
            try? await viewModel.model.refresh()
        }
        .navigationTitle("notification_title")
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        #else
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(action: {
                    Task {
                        try? await viewModel.model.refresh()
                    }
                }, label: {
                    Image(systemName: "arrow.clockwise.circle")
                })
            }
        }
        #endif
        .toolbar {
            if horizontalSizeClass != .compact,
               case .success(let data) = onEnum(of: viewModel.model.allTypes),
               data.data.count > 1 {
                ToolbarItem(placement: .primaryAction) {
                    Picker("notification_type", selection: $viewModel.notificationType) {
                        ForEach(1...data.data.count, id: \.self) { index in
                            if let item = data.data[index - 1] as? NotificationFilter {
                                Text(item.name)
                                    .tag(item)
                            }
                        }
                    }
                    .pickerStyle(.segmented)
                }
            }
        }
        .activateViewModel(viewModel: viewModel)
    }
}

@Observable
class NotificationViewModel: MoleculeViewModelBase<NotificationState, NotificationPresenter> {
    var notificationType: NotificationFilter = NotificationFilter.all {
        willSet {
            model.onNotificationTypeChanged(value: newValue)
        }
    }
}
