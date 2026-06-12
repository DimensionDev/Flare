import AppleFontAwesome
import FlareAppleCore
import KotlinSharedUI
import SwiftUI
import SwiftUIBackports

struct HomeScreen: View {
    @StateObject private var presenter = KotlinPresenter<HomeTimelineWithTabsPresenterState>(
        presenter: HomeTimelineWithTabsPresenter()
    )
    @State private var selectedTabID: String?
    @Namespace private var actionsNamespace

    var body: some View {
        VStack {
            HStack {
                StateView(state: presenter.state.tabState) { state in
                    let tabs = state.cast(UiTimelineTabItem.self)
                    HomeTimelineTabPicker(
                        tabs: tabs,
                        selectedTabID: $selectedTabID
                    )
                } errorContent: { _ in
                    HomeTimelineTabPicker(
                        tabs: [],
                        selectedTabID: $selectedTabID
                    )
                } loadingContent: {
                    HomeTimelineTabLoadingPicker()
                }
                Spacer()
                if #available(macOS 26.0, *) {
                    GlassEffectContainer {
                        HStack {
                            Button {
                            } label: {
                                Label {
                                    Text(LocalizedStrings.string("settings_title", fallback: "Settings"))
                                } icon: {
                                    Image(fontAwesome: .sliders)
                                }
                                .labelStyle(.iconOnly)
                            }
                            .help(LocalizedStrings.string("settings_title", fallback: "Settings"))
                            .buttonStyle(.glass)
                            .glassEffectUnion(id: "top-actions", namespace: actionsNamespace)
                            Button {
                            } label: {
                                Label {
                                    Text(LocalizedStrings.string("refresh", fallback: "Refresh"))
                                } icon: {
                                    Image(fontAwesome: .arrowsRotate)
                                }
                                .labelStyle(.iconOnly)
                            }
                            .help(LocalizedStrings.string("refresh", fallback: "Refresh"))
                            .buttonStyle(.glass)
                            .glassEffectUnion(id: "top-actions", namespace: actionsNamespace)
                        }
                    }
                } else {
                    Button {
                    } label: {
                        Image(fontAwesome: .sliders)
                    }
                    .help(LocalizedStrings.string("settings_title", fallback: "Settings"))
                    Button {
                    } label: {
                        Image(fontAwesome: .arrowsRotate)
                    }
                    .help(LocalizedStrings.string("refresh", fallback: "Refresh"))
                }
            }
            .padding(.horizontal)
            
            Divider()
            Spacer()
        }
    }
}

private struct HomeTimelineTabPicker: View {
    let tabs: [UiTimelineTabItem]
    @Binding var selectedTabID: String?

    var body: some View {
        Picker(selection: selectedTabSelection) {
            if tabs.isEmpty {
                Text(selectedTabTitle)
                    .tag("")
            } else {
                ForEach(tabs, id: \.id) { tab in
                    Text(tab.title.text)
                        .tag(tab.id)
                }
            }
        } label: {
            Text(selectedTabTitle)
        }
        .pickerStyle(.menu)
        .labelsHidden()
        .font(.headline)
        .fixedSize()
        .disabled(tabs.isEmpty)
        .onChange(of: tabIDs) { _, ids in
            normalizeSelection(with: ids)
        }
        .onAppear {
            normalizeSelection(with: tabIDs)
        }
    }

    private var selectedTabSelection: Binding<String> {
        Binding {
            selectedTab?.id ?? tabs.first?.id ?? ""
        } set: { id in
            selectedTabID = id.isEmpty ? nil : id
        }
    }

    private var selectedTabTitle: String {
        selectedTab?.title.text
            ?? tabs.first?.title.text
            ?? LocalizedStrings.string("home_tab_home_title", fallback: "Home")
    }

    private var selectedTab: UiTimelineTabItem? {
        guard let selectedTabID else { return nil }
        return tabs.first { $0.id == selectedTabID }
    }

    private var tabIDs: [String] {
        tabs.map(\.id)
    }

    private func normalizeSelection(with ids: [String]) {
        if ids.isEmpty {
            selectedTabID = nil
        } else if selectedTabID == nil || !ids.contains(selectedTabID ?? "") {
            selectedTabID = ids.first
        }
    }
}

private struct HomeTimelineTabLoadingPicker: View {
    var body: some View {
        Text(LocalizedStrings.string("macos_loading", fallback: "Loading"))
            .font(.headline)
            .foregroundStyle(.secondary)
            .redacted(reason: .placeholder)
    }
}
