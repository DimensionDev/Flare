import SwiftUI

/// Single image display mode
enum SingleMode {
    /// Respect the image's own aspect ratio (recommend using .resizable().scaledToFit() inside `content`)
    case preserveImageAspect
    /// Force 16:9 (the container applies .aspectRatio(16/9, .fit))
    case force16x9
}

/// A generic mosaic grid (WeChat/Instagram-style)
/// - Rules:
///   1) items.count == 1 -> decided by `singleMode`
///   2) 2...4:
///      - odd (=3): left large image occupies the left half; right side has two images stacked vertically; overall 16:9
///      - even (2, 4): grid fill; overall 16:9
///   3) >4: 3-column grid; all full rows are 1:1 squares; the last incomplete row uses weight=1 to fill the width
struct AdaptiveMosaic<Item, Content: View>: View {
    let items: [Item]
    let spacing: CGFloat
    let singleMode: SingleMode
    let content: (Item) -> Content
    
    init(
        _ items: [Item],
        spacing: CGFloat = 4,
        singleMode: SingleMode = .preserveImageAspect,
        @ViewBuilder content: @escaping (Item) -> Content
    ) {
        self.items = items
        self.spacing = spacing
        self.singleMode = singleMode
        self.content = content
    }
    
    var body: some View {
        switch items.count {
        case 0:
            EmptyView()
        case 1:
            singleView(items[0])
        case 2...4:
            twoToFour(items)
        default:
            many(items)
        }
    }
}

// MARK: - Single image
private extension AdaptiveMosaic {
    func singleView(_ item: Item) -> some View {
        Group {
            switch singleMode {
            case .preserveImageAspect:
                // Let inner content keep its own aspect ratio (e.g., .scaledToFit())
                content(item)
                    .frame(maxWidth: .infinity)
                
            case .force16x9:
                content(item)
                    .clipped()
                    .aspectRatio(contentMode: .fill)
                    .frame(
                        minWidth: 0,
                        maxWidth: .infinity,
                        minHeight: 0,
                        maxHeight: .infinity
                    )
                    .aspectRatio(16 / 9, contentMode: .fit)
            }
        }
    }
}

// MARK: - 2 ~ 4 items (overall 16:9)
private extension AdaptiveMosaic {
    @ViewBuilder
    func twoToFour(_ arr: [Item]) -> some View {
        GeometryReader { geo in
            let W = geo.size.width
            let H = W * 9.0 / 16.0       // Fix container height to 16:9 of its width
            let halfW = (W - spacing) / 2
            let halfH = (H - spacing) / 2
            
            ZStack {
                switch arr.count {
                case 2:
                    // 1 row × 2 columns
                    HStack(spacing: spacing) {
                        contentBox(arr[0], size: CGSize(width: halfW, height: H))
                        contentBox(arr[1], size: CGSize(width: halfW, height: H))
                    }
                    
                case 3:
                    // Left: one large image (left half); Right: two stacked images
                    HStack(spacing: spacing) {
                        contentBox(arr[0], size: CGSize(width: halfW, height: H))
                        
                        VStack(spacing: spacing) {
                            contentBox(arr[1], size: CGSize(width: halfW, height: halfH))
                            contentBox(arr[2], size: CGSize(width: halfW, height: halfH))
                        }
                    }
                    
                case 4:
                    // 2 × 2 grid
                    VStack(spacing: spacing) {
                        HStack(spacing: spacing) {
                            contentBox(arr[0], size: CGSize(width: halfW, height: halfH))
                            contentBox(arr[1], size: CGSize(width: halfW, height: halfH))
                        }
                        HStack(spacing: spacing) {
                            contentBox(arr[2], size: CGSize(width: halfW, height: halfH))
                            contentBox(arr[3], size: CGSize(width: halfW, height: halfH))
                        }
                    }
                    
                default:
                    EmptyView()
                }
            }
            .frame(width: W, height: H)
        }
        .aspectRatio(16.0/9.0, contentMode: .fit) // Guard at the outer level as well
    }
    
    /// Unified cell wrapper: clip to a given fixed `size`
    @ViewBuilder
    func contentBox(_ item: Item, size: CGSize) -> some View {
        content(item)
            .frame(width: size.width, height: size.height)
            .clipped()
            .contentShape(Rectangle())
    }
}

// MARK: - > 4 items (max 3 columns; full rows 1:1; last row uses weights to fill)
private extension AdaptiveMosaic {
    @ViewBuilder
    func many(_ arr: [Item]) -> some View {
        GeometryReader { geo in
            let W = geo.size.width
            let columns = 3
            let fullRowCount = arr.count / columns
            let remainder = arr.count % columns
            
            // Side length of normal 1:1 squares based on a 3-column layout
            let side = (W - CGFloat(columns - 1) * spacing) / CGFloat(columns)
            
            VStack(spacing: spacing) {
                // Render full rows first (each cell is 1:1)
                ForEach(0..<fullRowCount, id: \.self) { row in
                    HStack(spacing: spacing) {
                        ForEach(0..<columns, id: \.self) { col in
                            let idx = row * columns + col
                            squareBox(arr[idx], side: side)
                        }
                    }
                }
                
                // Last row: if fewer than 3 items, fill the row using weight=1 (height keeps the same as above)
                if remainder > 0 {
                    HStack(spacing: spacing) {
                        ForEach(0..<remainder, id: \.self) { i in
                            let idx = fullRowCount * columns + i
                            content(arr[idx])
                                .frame(height: side)     // Keep height consistent with previous rows
                                .frame(maxWidth: .infinity)
                                .clipped()
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
    
    @ViewBuilder
    func squareBox(_ item: Item, side: CGFloat) -> some View {
        content(item)
            .frame(width: side, height: side)
            .clipped()
            .contentShape(Rectangle())
    }
}
