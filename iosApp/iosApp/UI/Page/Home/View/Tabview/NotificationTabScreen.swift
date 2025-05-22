import shared
import SwiftUI

struct NotificationTabScreen: View {
    @State private var presenter: NotificationPresenter
    @State private var notificationType: NotificationFilter = .all
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @Environment(FlareTheme.self) private var theme

    init(accountType: AccountType) {
        presenter = .init(accountType: accountType)
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                if horizontalSizeClass == .compact,
                   case let .success(data) = onEnum(of: state.allTypes),
                   data.data.count > 1
                {
                    Picker("notification_type", selection: $notificationType) {
                        ForEach(1 ... data.data.count, id: \.self) { index in
                            if let item = data.data[index - 1] as? NotificationFilter {
                                Text(item.name)
                                    .tag(item)
                            }
                        }
                    }
                    .pickerStyle(.segmented)
                    .listRowSeparator(.hidden)
                    .listRowBackground(theme.primaryBackgroundColor)
                }
                StatusTimelineComponent(
                    data: state.listState,
                    detailKey: nil
                )
                .listRowBackground(theme.primaryBackgroundColor)
            }
            .onChange(of: notificationType) {
                state.onNotificationTypeChanged(value: notificationType)
            }
            .refreshable {
                try? await state.refresh()
            }
            .toolbar {
                if horizontalSizeClass != .compact,
                   case let .success(data) = onEnum(of: state.allTypes),
                   data.data.count > 1
                {
                    ToolbarItem(placement: .primaryAction) {
                        Picker("notification_type", selection: $notificationType) {
                            ForEach(1 ... data.data.count, id: \.self) { index in
                                if let item = data.data[index - 1] as? NotificationFilter {
                                    Text(item.name)
                                        .tag(item)
                                }
                            }
                        }.background(theme.secondaryBackgroundColor)
                            .pickerStyle(.segmented)
                    }
                }
            }
            //  .listStyle(.plain)
            .navigationTitle("home_tab_notifications_title")
            .listRowBackground(theme.primaryBackgroundColor)
        }.navigationBarTitleDisplayMode(.inline)
            .scrollContentBackground(.hidden)
            .background(theme.primaryBackgroundColor)
    }
}
