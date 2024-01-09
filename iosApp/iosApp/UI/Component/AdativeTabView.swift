import SwiftUI

struct AdativeTabView: View {
    let items: [TabModel]
    let secondaryItems: [TabModel]
    let onSettingsclicked: () -> Void
    let leading: AnyView
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var selectedTabItem: TabModel
    init<V>(
        items: [TabModel],
        secondaryItems: [TabModel],
        leading: V,
        onSettingsclicked: @escaping () -> Void
    ) where V: View {
        self.items = items
        self.secondaryItems = secondaryItems
        self.onSettingsclicked = onSettingsclicked
        self.leading = AnyView(leading)
        _selectedTabItem = State(initialValue: items.first!)
    }
    var body: some View {
        // NavigationSplitView does not work well with TabView + NavigationStack, so I just create my own
        HStack(spacing: 0) {
            if horizontalSizeClass != .compact {
                VStack {
                    leading
                    List(selection: Binding<TabModel?>(get: {
                        self.selectedTabItem
                    }, set: { newValue in
                        if let value = newValue {
                            self.selectedTabItem = value
                        }
                    })) {
                        ForEach(items) { item in
                            HStack {
                                Image(systemName: item.image)
                                Text(item.title)
                            }
                            .listRowSeparator(.hidden)
                            .tag(item)
                            #if !os(macOS)
                            .listRowBackground(selectedTabItem == item ? Color(uiColor: .systemFill) : Color.clear)
                            .foregroundStyle(selectedTabItem == item ? Color.accentColor : Color.primary)
                            #else
                            .padding(8)
                            #endif
                        }
                        if !secondaryItems.isEmpty {
                            Divider()
                                .listRowBackground(Color.clear)
                            ForEach(secondaryItems) { item in
                                HStack {
                                    Image(systemName: item.image)
                                    Text(item.title)
                                }
                                .listRowSeparator(.hidden)
                                .tag(item)
                                #if !os(macOS)
                                .listRowBackground(selectedTabItem == item ? Color(uiColor: .systemFill) : Color.clear)
                                .foregroundStyle(selectedTabItem == item ? Color.accentColor : Color.primary)
                                #else
                                .padding(8)
                                #endif
                            }
                        }
                    }
                    .scrollContentBackground(.hidden)
                    .listStyle(.plain)
                    Spacer()
                    HStack {
                        Button(action: onSettingsclicked) {
                            Image(systemName: "gear")
                            Text("Settings")
                        }
                        .buttonStyle(.plain)
                        Spacer()
                    }
                    .padding()
                }
                #if !os(macOS)
                .background(Color(UIColor.secondarySystemBackground))
                #else
                .background(Color(NSColor.windowBackgroundColor))
                #endif
                .frame(width: 256)
            }
            #if !os(macOS)
            TabView(selection: $selectedTabItem) {
                ForEach(items, id: \.title) { item in
                    item.destination
                        .tabItem {
                            Image(systemName: item.image)
                            Text(item.title)
                        }
                        .tag(item)
                        .if(horizontalSizeClass != .compact) { view in
                            view
#if !os(macOS)
                                .toolbar(.hidden, for: .tabBar)
#endif
                        }
                }
                ForEach(secondaryItems, id: \.title) { item in
                    item.destination
                        .tabItem {
                            Image(systemName: item.image)
                            Text(item.title)
                        }
                        .tag(item)
                    #if !os(macOS)
                        .toolbar(.hidden, for: .tabBar)
                    #endif
                }
            }
            #else
            selectedTabItem.destination
            #endif
        }
    }
}

struct TabModel: Identifiable, Hashable {
    public var id: String {
        self.title
    }
    static func == (lhs: TabModel, rhs: TabModel) -> Bool {
        return lhs.title == rhs.title
    }
    func hash(into hasher: inout Hasher) {
        hasher.combine(title)
    }
    let title: String
    let image: String
    let destination: AnyView
    init<V>(title: String, image: String, destination: V) where V: View {
        self.title = title
        self.image = image
        self.destination = AnyView(destination)
    }
}
