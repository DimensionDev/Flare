import SwiftUI
@preconcurrency import KotlinSharedUI

struct NotificationScreen: View {
    @StateObject private var presenter: KotlinPresenter<NotificationPresenterState>
    @State private var selectedType: NotificationFilter?
    @Environment(\.openURL) private var openURL
    @State private var showTopBar = true

    init(accountType: AccountType) {
        self._presenter = .init(wrappedValue: .init(presenter: NotificationPresenter(accountType: accountType)))
//        self._selectedType = .init(initialValue: presenter.state.notificationType)
        print("create NotificationScreen")
    }

    var body: some View {
        TimelinePagingContent(data: presenter.state.listState, detailStatusKey: nil)
        .onAppear {
            selectedType = presenter.state.notificationType
        }
        .detectScrolling()
        .toolbar {
            let placement = if #available(iOS 26.0, *) {
                ToolbarItemPlacement.automatic
            } else {
                ToolbarItemPlacement.title
            }
            ToolbarItem(placement: placement) {
                StateView(state: presenter.state.allTypes) { allTypes in
                    let allTypes = allTypes.cast(NotificationFilter.self)
                    if allTypes.count > 1 {
                        Picker("notification_type_title", selection: $selectedType) {
                            ForEach(allTypes, id: \.self) { type in
                                switch type {
                                case .all:
                                    Text("notification_type_all").tag(type)
                                case .comment:
                                    Text("notification_type_comments").tag(type)
                                case .like:
                                    Text("notification_type_likes").tag(type)
                                case .mention:
                                    Text("notification_type_mentions").tag(type)
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
        }
        .refreshable {
            try? await presenter.state.refresh()
        }
        .onChange(of: presenter.state.notificationType) { _, newValue in
            if selectedType == nil {
                selectedType = newValue
            }
        }
    }
}
