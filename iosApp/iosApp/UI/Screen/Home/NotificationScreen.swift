import SwiftUI
import shared

struct NotificationScreen: View {
    @State var viewModel = NotificationViewModel()
    @Environment(StatusEvent.self) var statusEvent: StatusEvent
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    var body: some View {
        List {
            if horizontalSizeClass == .compact, case .success(let data) = onEnum(of: viewModel.model.allTypes) {
                if (data.data.count > 1) {
                    Picker("NotificationType", selection: $viewModel.notificationType) {
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
            StatusTimelineComponent(data: viewModel.model.listState, mastodonEvent: statusEvent, misskeyEvent: statusEvent, blueskyEvent: statusEvent)
        }
        .listStyle(.plain)
        .refreshable {
            do {
                try await viewModel.model.refresh()
            } catch {
                
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text("Notification")
            }
            if horizontalSizeClass != .compact, case .success(let data) = onEnum(of: viewModel.model.allTypes) {
                ToolbarItem(placement: .primaryAction) {
                    Picker("NotificationType", selection: $viewModel.notificationType) {
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
