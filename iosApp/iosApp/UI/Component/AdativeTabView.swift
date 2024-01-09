import SwiftUI

struct AdativeTabView: View {
    let items: [TabModel]
    let secondaryItems: [TabModel]
    let leading: AnyView
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var selectedTabItem: TabModel
    init<V>(
        items: [TabModel],
        secondaryItems: [TabModel],
        leading: V
    ) where V: View {
        self.items = items
        self.secondaryItems = secondaryItems
        self.leading = AnyView(leading)
        _selectedTabItem = State(initialValue: items.first!)
    }
    var list: some View {
        List(selection: Binding<TabModel?>(get: {
            self.selectedTabItem
        }, set: { newValue in
            if let value = newValue {
                self.selectedTabItem = value
            }
        })) {
            leading
            Section {
                ForEach(items) { item in
                    HStack {
                        Image(systemName: item.image)
                            .frame(width: 24)
                        Text(item.title)
                    }
                    .listRowSeparator(.hidden)
                    .tag(item)
                }
            }
            if !secondaryItems.isEmpty {
                Divider()
                    .listRowBackground(Color.clear)
                Section {
                    ForEach(secondaryItems) { item in
                        HStack {
                            Image(systemName: item.image)
                            Text(item.title)
                        }
                        .listRowSeparator(.hidden)
                        .tag(item)
                    }
                }
            }
        }
    }
    var body: some View {
#if os(macOS)
        NavigationSplitView {
            list
        } detail: {
            selectedTabItem.destination
        }
#else
        // NavigationSplitView does not work well with TabView + NavigationStack, so I just create my own
        HStack(spacing: 0) {
            if horizontalSizeClass != .compact {
                list
                    .listStyle(.sidebar)
//                    .scrollContentBackground(.hidden)
//                    .listStyle(.plain)
//                    .background(Color(UIColor.secondarySystemBackground))
                    .frame(width: 256)
            }
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
                                .toolbar(.hidden, for: .tabBar)
                        }
                }
                ForEach(secondaryItems, id: \.title) { item in
                    item.destination
                        .tabItem {
                            Image(systemName: item.image)
                            Text(item.title)
                        }
                        .tag(item)
                        .toolbar(.hidden, for: .tabBar)
                }
            }
        }
#endif
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
