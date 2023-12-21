import SwiftUI

struct AdativeTabView: View {
    let items: [TabModel]
    let onSettingsclicked: () -> Void
    let onComposeClicked: () -> Void
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var selectedTabItem: TabModel
    init(items: [TabModel], onSettingsclicked: @escaping () -> Void, onComposeClicked: @escaping () -> Void) {
        self.items = items
        self.onSettingsclicked = onSettingsclicked
        self.onComposeClicked = onComposeClicked
        _selectedTabItem = State(initialValue: items.first!)
    }
    var body: some View {
        // NavigationSplitView does not work well with TabView + NavigationStack, so I just create my own
        HStack {
            if (horizontalSizeClass != .compact) {
                VStack {
                    HStack {
                        Text("Flare")
                            .font(.title)
                        Spacer()
                        Button(action: onComposeClicked, label: {
                            Image(systemName: "square.and.pencil")
                        })
                    }
                    .padding([.horizontal, .top])
                    List(items, selection: Binding<TabModel?>(get: {
                        self.selectedTabItem
                    }, set: { Value in
                        if let value = Value {
                            self.selectedTabItem = value
                        }
                    })) { item in
                        HStack {
                            Image(systemName: item.image)
                            Text(item.title)
                        }
                        .listRowSeparator(.hidden)
                        .tag(item)
                    }
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
                    .padding(.horizontal)
                }
                .background(Color(UIColor.secondarySystemBackground))
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
                        .if(horizontalSizeClass != .compact) { View in
                            View
                                .toolbar(.hidden, for: .tabBar)
                        }
                }
            }
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
