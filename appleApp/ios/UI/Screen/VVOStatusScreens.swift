import SwiftUI
import UIKit
import FlareAppleCore
import FlareAppleUI
@preconcurrency import KotlinSharedUI

struct VVOStatusScreen: View {
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @StateObject private var presenter: KotlinPresenter<VVOStatusDetailState>
    @State private var selectedType: VVOStatusDetailType = .comment
    @State private var tabsView = VVOStatusTabsView()
    private let statusKey: MicroBlogKey

    init(accountType: AccountType, statusKey: MicroBlogKey) {
        self.statusKey = statusKey
        self._presenter = .init(
            wrappedValue: .init(
                presenter: VVOStatusDetailPresenter(accountType: accountType, statusKey: statusKey)
            )
        )
    }

    var body: some View {
        HStack(alignment: .top, spacing: 0) {
            if !isCompactLayout {
                UITimelineCollectionView(
                    detailStatusKey: statusKey,
                    headerState: presenter.state.status,
                    suppressInitialRefreshIndicator: true
                )
                .ignoresSafeArea(edges: .vertical)
                .frame(width: 400)
                .padding(.leading, 16)
            }

            UITimelineCollectionView(
                data: selectedType == .comment ? presenter.state.comment : presenter.state.repost,
                detailStatusKey: statusKey,
                headerState: isCompactLayout ? presenter.state.status : nil,
                accessoryItems: accessoryItems,
                suppressInitialRefreshIndicator: true
            )
            .id(selectedType)
            .ignoresSafeArea(edges: .vertical)
            .refreshable {
                switch selectedType {
                case .comment:
                    try? await presenter.state.refreshComment()
                case .repost:
                    try? await presenter.state.refreshRepost()
                }
            }
        }
        .background(Color(timelineDisplayMode == .plain && isCompactLayout ? .clear : .systemGroupedBackground))
        .navigationTitle("vvo_status_title")
    }

    private var isCompactLayout: Bool {
        horizontalSizeClass == .compact
    }

    private var accessoryItems: [UITimelineCollectionViewAccessoryItem] {
        tabsView.configure(selection: selectedType) { [selection = $selectedType] in
            selection.wrappedValue = $0
        }
        return [UITimelineCollectionViewAccessoryItem(id: "vvo_status_tabs", view: tabsView)]
    }
}

struct VVOCommentScreen: View {
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @StateObject private var presenter: KotlinPresenter<VVOCommentState>
    private let statusKey: MicroBlogKey

    init(accountType: AccountType, statusKey: MicroBlogKey) {
        self.statusKey = statusKey
        self._presenter = .init(
            wrappedValue: .init(
                presenter: VVOCommentPresenter(accountType: accountType, commentKey: statusKey)
            )
        )
    }

    var body: some View {
        UITimelineCollectionView(
            data: presenter.state.list,
            detailStatusKey: statusKey,
            headerState: presenter.state.root,
            suppressInitialRefreshIndicator: true
        )
        .ignoresSafeArea(edges: .vertical)
        .frame(maxWidth: horizontalSizeClass == .compact ? .infinity : 600)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .refreshable {
            try? await presenter.state.refresh()
        }
        .background(Color(timelineDisplayMode == .plain ? .clear : .systemGroupedBackground))
    }
}

private enum VVOStatusDetailType: Int {
    case repost
    case comment
}

private final class VVOStatusTabsView: UIView {
    private let control = UISegmentedControl(items: [
        String(localized: "vvo_status_reposts"),
        String(localized: "Comments"),
    ])
    private var onSelectionChanged: ((VVOStatusDetailType) -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        addSubview(control)
        control.addAction(UIAction { [weak self] _ in
            guard let self,
                  let selection = VVOStatusDetailType(rawValue: self.control.selectedSegmentIndex) else { return }
            self.onSelectionChanged?(selection)
        }, for: .valueChanged)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) not supported")
    }

    func configure(selection: VVOStatusDetailType, onSelectionChanged: @escaping (VVOStatusDetailType) -> Void) {
        control.selectedSegmentIndex = selection.rawValue
        self.onSelectionChanged = onSelectionChanged
    }

    override var intrinsicContentSize: CGSize {
        CGSize(width: UIView.noIntrinsicMetric, height: control.intrinsicContentSize.height + 16)
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        control.frame = bounds.insetBy(dx: 16, dy: 8)
    }
}
