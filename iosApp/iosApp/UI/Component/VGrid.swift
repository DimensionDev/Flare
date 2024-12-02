import Foundation
import SwiftUI

struct CustomGrid<Item, ItemView>: View where ItemView: View {
    let items: [Item]
    let columns: Int
    let itemBuilder: (Item) -> ItemView

    var body: some View {
        VStack(spacing: 0.3) {  
            ForEach(0..<rowsCount, id: \.self) { rowIndex in
                HStack(spacing: 0.3) {  
                    ForEach(0..<columns, id: \.self) { columnIndex in
                        if let item = itemFor(row: rowIndex, column: columnIndex) {
                            itemBuilder(item)
                                .frame(maxWidth: .infinity)
                        } else {
//                            Spacer()
//                                .frame(maxWidth: .infinity)
                        }
                    }
                }
            }
        }
    }

    private var rowsCount: Int {
        (items.count + columns - 1) / columns
    }

    private func itemFor(row: Int, column: Int) -> Item? {
        let index = row * columns + column
        return index < items.count ? items[index] : nil
    }
}
