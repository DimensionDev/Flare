
import shared
import SwiftUI

struct ProfileTabBarViewV2: View {
    @Binding var selectedTabKey: String?

    let availableTabs: [FLTabItem]

    @Environment(FlareTheme.self) private var theme

    var body: some View {
        VStack(spacing: 0) {
            tabPickerView
                .padding(.horizontal, 16)
                .padding(.vertical, 8)

            Divider()
                .background(theme.labelColor.opacity(0.2))
        }
        .background(theme.primaryBackgroundColor)
    }

    private var tabPickerView: some View {
      //  FlareLog.debug("🎨 [ProfileTabBarV2] 渲染Picker - availableTabs数量: \(availableTabs.count)")
       // for (index, tab) in availableTabs.enumerated() {
            //let title = getTabTitle(tab.metaData.title)
        //    FlareLog.debug("🎨 [ProfileTabBarV2] Tab[\(index)]: key=\(tab.key), title=\(title)")
        // }

        return Picker("Profile Tabs", selection: Binding(
            get: {
                let current = selectedTabKey ?? availableTabs.first?.key ?? ""
                //FlareLog.debug("🎯 [ProfileTabBarV2] 当前选中Tab: \(current)")
                return current
            },
            set: { newValue in
                FlareLog.debug("🔄 [ProfileTabBarV2] Tab切换: \(selectedTabKey ?? "nil") → \(newValue)")
                selectedTabKey = newValue
            }
        )) {
            ForEach(availableTabs, id: \.key) { tab in
                tabItemView(for: tab)
                    .tag(tab.key)
            }
        }
        .pickerStyle(.segmented)
    }

    private func tabItemView(for tab: FLTabItem) -> some View {
        let title = getTabTitle(tab.metaData.title)
        FlareLog.debug("🎨 [ProfileTabBarV2] 渲染tabItemView - key: \(tab.key), title: \(title), shouldShowTabTitle: \(shouldShowTabTitle)")

        return HStack(spacing: 4) {
            if shouldShowTabTitle {
                Text(title)
                    .font(.caption2)
                    .lineLimit(1)
            }
        }
    }

    private var shouldShowTabTitle: Bool {
        availableTabs.count <= 4
    }

    private func getTabTitle(_ title: FLTitleType) -> String {
        switch title {
        case let .text(text):
            text
        case let .localized(key):
            NSLocalizedString(key, comment: "")
        }
    }
}
