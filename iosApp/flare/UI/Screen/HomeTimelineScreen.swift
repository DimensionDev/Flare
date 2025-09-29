import SwiftUI
@preconcurrency import KotlinSharedUI

struct HomeTimelineScreen: View {
    let toServiceSelect: () -> Void
    let toCompose: () -> Void
    let toTabSetting: () -> Void
    let accountType: AccountType
    @Environment(\.openURL) private var openURL
    @State private var selectedTabIndex = 0
    @StateObject private var presenter: KotlinPresenter<HomeTimelineWithTabsPresenterState>
    @State private var showTopBar = true
    @State private var headerOffset: CGFloat = 0
    @State private var lastNaturalOffset: CGFloat = 0
    @State private var isScrollingUp = false
    @State private var naturalScrollOffset: CGFloat = 0

    init(accountType: AccountType, toServiceSelect: @escaping () -> Void, toCompose: @escaping () -> Void, toTabSetting: @escaping () -> Void) {
        self.accountType = accountType
        self.toCompose = toCompose
        self.toServiceSelect = toServiceSelect
        self.toTabSetting = toTabSetting
        self._presenter = .init(wrappedValue: .init(presenter: HomeTimelineWithTabsPresenter(accountType: accountType)))
    }

    var body: some View {
        GeometryReader { proxy in
            let headerHeight = 60 + proxy.safeAreaInsets.top
            StateView(state: presenter.state.tabState) { state in
                let tabs: [TimelineTabItem] = state.cast(TimelineTabItem.self)
                let tab = tabs[selectedTabIndex]
                ZStack {
                    TimelineScreen(tabItem: tab)
                        .id(tab.key)
                }
                .onScrollGeometryChange(for: CGFloat.self, of: { proxy in
                    let maxHeight = proxy.contentSize.height - proxy.containerSize.height
                    return max(min(proxy.contentOffset.y + headerHeight, maxHeight), 0)
                }, action: { oldValue, newValue in
                    let isScrollingUp = oldValue < newValue
                    headerOffset = min(max(newValue - lastNaturalOffset, 0), headerHeight)
                    self.isScrollingUp = isScrollingUp
                    naturalScrollOffset = newValue
                })
                .onScrollPhaseChange({ oldPhase, newPhase, context in
                    if !newPhase.isScrolling && (headerOffset != 0 || headerOffset != headerHeight) {
                        withAnimation(.spring) {
                            if headerOffset > (headerHeight * 0.5) {
                                headerOffset = headerHeight
                            } else {
                                headerOffset = 0
                            }
                            
                            lastNaturalOffset = naturalScrollOffset - headerOffset
                        }
                    }
                })
                .onChange(of: isScrollingUp, { oldValue, newValue in
                    lastNaturalOffset = naturalScrollOffset - headerOffset
                })
                .safeAreaInset(edge: .top) {
                    HStack(
                        spacing: 2
                    ) {
                        ScrollView(.horizontal) {
                            HStack(
                                spacing: 8,
                            ) {
                                ForEach(0..<tabs.count, id: \.self) { index in
                                    let tab = tabs[index]
                                    Label {
                                        TabTitle(title: tab.metaData.title)
                                            .font(.subheadline)
                                    } icon: {
                                        TabIcon(icon: tab.metaData.icon, accountType: tab.account, size: 24)
                                    }
                                    .onTapGesture {
                                        withAnimation(.spring) {
                                            selectedTabIndex = index
                                        }
                                    }
                                    .padding(.horizontal)
                                    .padding(.vertical, 8)
                                    .foregroundStyle(selectedTabIndex == index ? Color.white : .primary)
                                    .glassEffect(selectedTabIndex == index ? .regular.tint(.accentColor) : .regular, in: .capsule)
                                }
                                if case .success = onEnum(of: presenter.state.user) {
                                    Button {
                                        toTabSetting()
                                    } label: {
                                        Image("fa-plus")
                                    }
                                    .buttonStyle(.glass)
                                }
                            }
                            .padding(.horizontal)
                            .padding(.vertical, 8)
                        }
                        .clipShape(.capsule)
                        .glassEffect(.regular.interactive(), in: .capsule)
                        .scrollIndicators(.hidden)
                        Spacer()
                        if case .error = onEnum(of: presenter.state.user) {
                            Button {
                                toServiceSelect()
                            } label: {
                                Text("Login")
                            }
                            .buttonStyle(.glass)
                            .padding()
                        } else {
                            Button {
                                toCompose()
                            } label: {
                                Image("fa-pen-to-square")
                                    .font(.title2)
                            }
                            .buttonStyle(.glass)
                        }
                    }
                    .padding(.horizontal)
                    .offset(y: -headerOffset)
                }
                .toolbarVisibility(.hidden, for: .navigationBar)
            }
        }
    }
}
