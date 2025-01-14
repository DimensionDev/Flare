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
                    //Picker 背景色
                    .listRowBackground(Colors.Background.swiftUIPrimary)
                }
                StatusTimelineComponent(
                    data: state.listState,
                    detailKey: nil
                )
                // 列表项背景色
                .listRowBackground(Colors.Background.swiftUIPrimary)
            }
            .onChange(of: notificationType) {
                state.onNotificationTypeChanged(value: notificationType)
            }
            .listStyle(.plain)
            //列表背景色
            .scrollContentBackground(.hidden)
            .background(Colors.Background.swiftUIPrimary)
            .refreshable {
                try? await state.refresh()
            }
            .navigationTitle("home_tab_notifications_title")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            //导航栏背景色
            // .toolbarBackground(Colors.Background.swiftUIPrimary, for: .navigationBar)
            // .toolbarBackground(.visible, for: .navigationBar)
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
