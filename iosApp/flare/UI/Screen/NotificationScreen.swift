import SwiftUI
import KotlinSharedUI

struct NotificationScreen: View {
    @State private var presenter: KotlinPresenter<NotificationPresenterState>
    @State private var selectedType: NotificationFilter? = nil
    @Environment(\.openURL) private var openURL
    
    init(accountType: AccountType) {
        self._presenter = .init(initialValue: .init(presenter: NotificationPresenter(accountType: accountType)))
        self._selectedType = .init(initialValue: presenter.state.notificationType)
    }
    
    var body: some View {
        VStack {
            StateView(state: presenter.state.allTypes) { allTypes in
                Picker("notification_type_title", selection: $selectedType) {
                    ForEach(0..<allTypes.count) { index in
                        if let type = allTypes[index] as? NotificationFilter {
                            Text("\(type.name)").tag(type)
                        }
                    }
                }
                .onChange(of: selectedType) { oldValue, newValue in
                    if let value = newValue {
                        presenter.state.onNotificationTypeChanged(value: value)
                    }
                }
            }
            
            TimelineView(key: presenter.key, data: presenter.state.listState, topPadding: 0, onOpenLink: { link in openURL(.init(string: link)!) })
                    .background(Color(.systemGroupedBackground))
                    .ignoresSafeArea()
            
        }
    }
}
