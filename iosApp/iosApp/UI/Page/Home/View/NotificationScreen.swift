import SwiftUI
import shared

struct NotificationScreen: View {
    @State private var presenter: NotificationPresenter
    @State private var notificationType: NotificationFilter = .all
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    init(accountType: AccountType) {
        presenter = .init(accountType: accountType)
    }

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                if horizontalSizeClass == .compact,
                   case .success(let data) = onEnum(of: state.allTypes),
                   data.data.count > 1 {
                    Picker("notification_type", selection: $notificationType) {
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
                    data: state.listState,
                    detailKey: nil
                )
            }
            .onChange(of: notificationType) {
                state.onNotificationTypeChanged(value: notificationType)
            }
            .listStyle(.plain)
            .refreshable {
                try? await state.refresh()
            }
            .navigationTitle("notification_title")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #else
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: {
                        Task {
                            try? await state.refresh()
                        }
                    }, label: {
                        Image(systemName: "arrow.clockwise.circle")
                    })
                }
            }
            #endif
            .toolbar {
                if horizontalSizeClass != .compact,
                   case .success(let data) = onEnum(of: state.allTypes),
                   data.data.count > 1 {
                    ToolbarItem(placement: .primaryAction) {
                        Picker("notification_type", selection: $notificationType) {
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
        }
    }
}
