import SwiftUI
@preconcurrency import KotlinSharedUI

struct NotificationScreen: View {
    @State private var presenter: KotlinPresenter<NotificationPresenterState>
    @State private var selectedType: NotificationFilter? = nil
    @Environment(\.openURL) private var openURL
    @State private var showTopBar = true

    init(accountType: AccountType) {
        self._presenter = .init(initialValue: .init(presenter: NotificationPresenter(accountType: accountType)))
        self._selectedType = .init(initialValue: presenter.state.notificationType)
    }

    var body: some View {
        List {
            TimelinePagingView(data: presenter.state.listState)
                .listRowSeparator(.hidden)
                .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                .padding(.horizontal)
        }
        .listRowSpacing(2)
        .listStyle(.plain)
        .background(Color(.systemGroupedBackground))
        .toolbar {
            ToolbarItem(placement: .principal) {
                StateView(state: presenter.state.allTypes) { allTypes in
                    Picker("notification_type_title", selection: $selectedType) {
                        ForEach(0..<allTypes.count) { index in
                            if let type = allTypes[index] as? NotificationFilter {
                                Text("\(type.name)").tag(type)
                            }
                        }
                    }
                    .pickerStyle(.segmented)
                    .onChange(of: selectedType) { oldValue, newValue in
                        if let value = newValue {
                            if oldValue != newValue {
                                presenter.state.onNotificationTypeChanged(value: value)
                            }
                        }
                    }
                }
            }
        }
        .refreshable {
            try? await presenter.state.refresh()
        }
        .onChange(of: presenter.state.notificationType) { oldValue, newValue in
            if selectedType == nil {
                selectedType = newValue
            }
        }
    }
}
