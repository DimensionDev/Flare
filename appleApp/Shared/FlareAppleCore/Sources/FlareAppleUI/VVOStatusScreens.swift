import SwiftUI
import FlareAppleCore
@preconcurrency import KotlinSharedUI

public struct VVOStatusScreen: View {
    @Environment(\.timelineAppearance.timelineDisplayMode) private var timelineDisplayMode
    #if os(iOS)
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    #endif

    private let statusKey: MicroBlogKey
    @StateObject private var presenter: KotlinPresenter<VVOStatusDetailState>
    @State private var selectedType: VVOStatusDetailType = .comment

    public init(accountType: AccountType, statusKey: MicroBlogKey) {
        self.statusKey = statusKey
        self._presenter = .init(
            wrappedValue: .init(
                presenter: VVOStatusDetailPresenter(
                    accountType: accountType,
                    statusKey: statusKey
                )
            )
        )
    }

    public var body: some View {
        HStack(
            alignment: .top,
            spacing: 0
        ) {
            if !isCompactLayout {
                ScrollView {
                    ListCardView {
                        StateView(state: presenter.state.status) { item in
                            TimelineView(data: item, detailStatusKey: statusKey)
                        } errorContent: { error in
                            ListErrorView(error: error, onRetry: {})
                        } loadingContent: {
                            TimelinePlaceholderView()
                        }
                        .padding()
                    }
                }
                .padding(.leading)
                .frame(width: 400)
            }
            List {
                if isCompactLayout {
                    AdaptiveTimelineCard(index: 0, totalCount: 1) {
                        StateView(state: presenter.state.status) { item in
                            TimelineView(data: item, detailStatusKey: statusKey)
                        } errorContent: { error in
                            ListErrorView(error: error, onRetry: {})
                        } loadingContent: {
                            TimelinePlaceholderView()
                        }
                        .padding(.horizontal)
                        .padding(.vertical, 12)
                    }
                    .listRowSeparator(.hidden)
                    .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                    .listRowBackground(Color.clear)
                }
                Picker(selection: $selectedType) {
                    Text("vvo_status_reposts", bundle: FlareAppleUILocalization.bundle)
                        .tag(VVOStatusDetailType.repost)
                    Text("Comments", bundle: FlareAppleUILocalization.bundle)
                        .tag(VVOStatusDetailType.comment)
                } label: {
                    
                }
                .pickerStyle(.segmented)
                .listRowSeparator(.hidden)
                .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
                .padding(.horizontal)
                .listRowBackground(Color.clear)
                
                switch selectedType {
                case .comment: TimelinePagingListContent(data: presenter.state.comment)
                case .repost: TimelinePagingListContent(data: presenter.state.repost)
                }
            }
            .detectScrolling()
            .scrollContentBackground(.hidden)
            .vvoListRowSpacing()
            .listStyle(.plain)
        }
        .background(backgroundColor)
        .navigationTitle(Text("vvo_status_title", bundle: FlareAppleUILocalization.bundle))
    }

    private var isCompactLayout: Bool {
        #if os(iOS)
        horizontalSizeClass == .compact
        #else
        true
        #endif
    }

    private var backgroundColor: Color {
        if timelineDisplayMode == .plain && isCompactLayout {
            return .clear
        }
        return .flareSystemGroupedBackground
    }
}

public struct VVOCommentScreen: View {
    private let statusKey: MicroBlogKey
    @StateObject private var presenter: KotlinPresenter<VVOCommentState>

    public init(accountType: AccountType, statusKey: MicroBlogKey) {
        self.statusKey = statusKey
        self._presenter = .init(
            wrappedValue: .init(
                presenter: VVOCommentPresenter(
                    accountType: accountType,
                    commentKey: statusKey
                )
            )
        )
    }

    public var body: some View {
        List {
            StateView(state: presenter.state.root) { item in
                TimelineView(data: item, detailStatusKey: statusKey)
            } errorContent: { error in
                ListErrorView(error: error, onRetry: {})
            } loadingContent: {
                TimelinePlaceholderView()
            }
            .listRowSeparator(.hidden)
            .listRowInsets(.init(top: 0, leading: 0, bottom: 0, trailing: 0))
            .padding(.horizontal)
            .padding()
            .listRowBackground(Color.clear)
            TimelinePagingListContent(data: presenter.state.list, usesDefaultHorizontalPadding: true)
        }
        .detectScrolling()
        .scrollContentBackground(.hidden)
        .vvoListRowSpacing()
        .listStyle(.plain)
        .background(Color.flareSystemGroupedBackground)
    }
}

private enum VVOStatusDetailType {
    case comment
    case repost
}

private extension View {
    @ViewBuilder
    func vvoListRowSpacing() -> some View {
        #if os(iOS)
        listRowSpacing(2)
        #else
        self
        #endif
    }
}
