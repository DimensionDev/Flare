import SwiftUI
import shared

struct NotificationScreen: View {
    @State var viewModel = NotificationViewModel()
    var body: some View {
        List {
            if case .success(let data) = onEnum(of: viewModel.model.allTypes) {
                if (data.data.count > 0) {
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
            StatusTimelineStateBuilder(data: viewModel.model.listState)
        }.listStyle(.plain).refreshable {
            viewModel.model.refresh()
        }.activateViewModel(viewModel: viewModel)
    }
}

@Observable
class NotificationViewModel: MoleculeViewModelBase<NotificationState, NotificationPresenter> {
    var notificationType: NotificationFilter = NotificationFilter.all {
        didSet {
            model.onNotificationTypeChanged(value: notificationType)
        }
    }
}

#Preview {
    NotificationScreen()
}
