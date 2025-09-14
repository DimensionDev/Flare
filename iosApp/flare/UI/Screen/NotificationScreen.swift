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
            Section {
                PagingView(data: presenter.state.listState) { item in
                    TimelineView(data: item)
                }
            } header: {
                StateView(state: presenter.state.allTypes) { allTypes in
                    Picker("notification_type_title", selection: $selectedType) {
                        ForEach(0..<allTypes.count) { index in
                            if let type = allTypes[index] as? NotificationFilter {
                                Text("\(type.name)").tag(type)
                            }
                        }
                    }
                    .padding(.horizontal)
                    .padding(.bottom)
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
