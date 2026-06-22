import AppleFontAwesome
import FlareAppleCore
import FlareAppleUI
@preconcurrency import KotlinSharedUI
import SwiftUI

struct NotificationScreen: View {
    let accountKey: MicroBlogKey
    @StateObject private var presenter: KotlinPresenter<AccountNotificationPresenterState>
    @State private var selectedFilter: NotificationFilter?

    init(accountKey: MicroBlogKey) {
        self.accountKey = accountKey
        _presenter = .init(
            wrappedValue: .init(
                presenter: AccountNotificationPresenter(accountKey: accountKey, initialFilter: .all)
            )
        )
    }

    private var supportedFilters: [NotificationFilter] {
        switch onEnum(of: presenter.state.supportedNotificationFilters) {
        case .success(let data):
            data.data.cast(NotificationFilter.self)
        case .loading, .error:
            []
        }
    }

    private var presenterSelectedFilterStableKey: String {
        presenter.state.selectedFilter.stableKey
    }

    private var selectedFilterStableKey: String? {
        selectedFilter?.stableKey
    }

    private var supportedFiltersSignature: String {
        supportedFilters.map(\.stableKey).joined(separator: "|")
    }

    private var timelineKey: String {
        [
            presenter.key,
            accountKey.host,
            accountKey.id,
            presenterSelectedFilterStableKey,
        ].joined(separator: "::")
    }

    var body: some View {
        TimelinePagingContent(
            data: presenter.state.timeline,
            detailStatusKey: nil,
            key: timelineKey
        )
        .id(timelineKey)
        .navigationTitle(Text("home_tab_notifications_title"))
        .refreshable {
            try? await presenter.state.refreshSuspend()
        }
        .toolbar {
            if supportedFilters.count > 1 {
                ToolbarItem(placement: .primaryAction) {
                    NotificationFilterPicker(
                        allTypes: supportedFilters,
                        selected: $selectedFilter
                    )
                    .frame(width: filterPickerWidth)
                }
            }

            ToolbarItem(placement: .primaryAction) {
                Button {
                    Task {
                        try? await presenter.state.refreshSuspend()
                    }
                } label: {
                    Label {
                        Text("Refresh")
                    } icon: {
                        Image(fontAwesome: .arrowsRotate)
                    }
                }
            }
        }
        .onAppear {
            syncSelectedFilterFromPresenter()
        }
        .onChange(of: presenterSelectedFilterStableKey) { _, _ in
            syncSelectedFilterFromPresenter()
        }
        .onChange(of: supportedFiltersSignature) { _, _ in
            syncSelectedFilterFromPresenter()
        }
        .onChange(of: selectedFilterStableKey) { _, _ in
            syncSelectedFilterToPresenter()
        }
    }

    private var filterPickerWidth: CGFloat {
        CGFloat(min(max(supportedFilters.count, 2), 4)) * 92
    }

    private func syncSelectedFilterFromPresenter() {
        let presenterFilter = presenter.state.selectedFilter
        let resolvedFilter =
            supportedFilters.first { $0.stableKey == presenterFilter.stableKey } ??
            supportedFilters.first ??
            presenterFilter

        if selectedFilter?.stableKey != resolvedFilter.stableKey {
            selectedFilter = resolvedFilter
        }
    }

    private func syncSelectedFilterToPresenter() {
        guard
            let selectedFilter,
            selectedFilter.stableKey != presenterSelectedFilterStableKey
        else {
            return
        }
        presenter.state.setFilter(filter: selectedFilter)
    }
}

private struct NotificationFilterPicker: View {
    let allTypes: [NotificationFilter]
    @Binding var selected: NotificationFilter?

    private var resolvedSelection: NotificationFilter {
        if let selected,
           let matchingFilter = allTypes.first(where: { $0.stableKey == selected.stableKey }) {
            return matchingFilter
        }
        return allTypes.first ?? .all
    }

    var body: some View {
        Picker("notification_type_title", selection: Binding<NotificationFilter>(
            get: { resolvedSelection },
            set: { value in selected = value }
        )) {
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
    }
}

private extension NotificationFilter {
    var stableKey: String {
        switch self {
        case .all:
            "all"
        case .comment:
            "comment"
        case .like:
            "like"
        case .mention:
            "mention"
        }
    }
}
