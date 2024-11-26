import SwiftUI

//struct WaterfallGrid<Data: RandomAccessCollection, Content: View>: View where Data.Element: Identifiable {
//    let data: Data
//    let columns: Int
//    let spacing: CGFloat
//    let content: (Data.Element) -> Content
//    @State private var columnHeights: [CGFloat]
//    @State private var itemFrames: [Data.Element.ID: CGRect] = [:]
//    
//    init(data: Data,
//         columns: Int = 2,
//         spacing: CGFloat = 8,
//         @ViewBuilder content: @escaping (Data.Element) -> Content) {
//        self.data = data
//        self.columns = columns
//        self.spacing = spacing
//        self.content = content
//        _columnHeights = State(initialValue: Array(repeating: 0, count: columns))
//    }
//    
//    var body: some View {
//        GeometryReader { geometry in
//            ZStack(alignment: .topLeading) {
//                ForEach(data) { item in
//                    content(item)
//                        .background(GeometryReader { proxy in
//                            Color.clear.preference(
//                                key: ItemPreferenceKey.self,
//                                value: [ItemPreference(id: item.id, size: proxy.size)]
//                            )
//                        })
//                        .frame(width: itemWidth(containerWidth: geometry.size.width))
//                        .position(position(for: item.id, in: geometry.size))
//                }
//            }
//            .frame(height: maxHeight)
//        }
//        .onPreferenceChange(ItemPreferenceKey.self) { preferences in
//            for preference in preferences {
//                if itemFrames[preference.id as! Data.Element.ID] == nil {
//                    let columnIndex = shortestColumnIndex()
//                    let x = CGFloat(columnIndex) * (itemWidth(containerWidth: UIScreen.main.bounds.width) + spacing)
//                    let y = columnHeights[columnIndex]
//                    let frame = CGRect(x: x, y: y, width: preference.size.width, height: preference.size.height)
//                    itemFrames[preference.id as! Data.Element.ID] = frame
//                    columnHeights[columnIndex] += preference.size.height + spacing
//                }
//            }
//        }
//    }
//    
//    private func itemWidth(containerWidth: CGFloat) -> CGFloat {
//        let totalSpacing = spacing * CGFloat(columns - 1)
//        return (containerWidth - totalSpacing) / CGFloat(columns)
//    }
//    
//    private func shortestColumnIndex() -> Int {
//        guard let minHeight = columnHeights.min() else { return 0 }
//        return columnHeights.firstIndex(of: minHeight) ?? 0
//    }
//    
//    private func position(for id: Data.Element.ID, in size: CGSize) -> CGPoint {
//        guard let frame = itemFrames[id] else {
//            return .zero
//        }
//        return CGPoint(
//            x: frame.minX + itemWidth(containerWidth: size.width) / 2,
//            y: frame.minY + frame.height / 2
//        )
//    }
//    
//    private var maxHeight: CGFloat {
//        columnHeights.max() ?? 0
//    }
//}
//
//private struct ItemPreference: Equatable {
//    let id: AnyHashable
//    let size: CGSize
//}
//
//private struct ItemPreferenceKey: PreferenceKey {
//    static var defaultValue: [ItemPreference] = []
//    
//    static func reduce(value: inout [ItemPreference], nextValue: () -> [ItemPreference]) {
//        value.append(contentsOf: nextValue())
//    }
//}
