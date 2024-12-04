import Foundation
import SwiftUI

struct CustomGrid<Item, ItemView>: View where ItemView: View {
    let items: [Item]
    let columns: Int
    let itemBuilder: (Item) -> ItemView

    var body: some View {
        if items.count == 3 {
            // Special layout for 3 items
            HStack(spacing: 0.3) {
                // Left item takes full height
                if let firstItem = items.first {
                    itemBuilder(firstItem)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                
                // Right column with two items
                VStack(spacing: 0.3) {
                    ForEach(1..<3) { index in
                        itemBuilder(items[index])
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                }
                .frame(maxWidth: .infinity)
            }
        } else {
            // Default grid layout for other cases
            VStack(spacing: 0.3) {
                ForEach(0..<rowsCount, id: \.self) { rowIndex in
                    HStack(spacing: 0.3) {
                        ForEach(0..<columns, id: \.self) { columnIndex in
                            if let item = itemFor(row: rowIndex, column: columnIndex) {
                                itemBuilder(item)
                                    .frame(maxWidth: .infinity)
                            } else {
//                                Spacer()
//                                    .frame(maxWidth: .infinity)
                            }
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
