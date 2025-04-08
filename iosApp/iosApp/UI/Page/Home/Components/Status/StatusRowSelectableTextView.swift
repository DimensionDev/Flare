import SwiftUI

struct Tag: Hashable, Identifiable {
    let id: String
    let text: String
    let index: Int

    init(text: String, index: Int) {
        self.text = text
        self.index = index
        id = "\(text)_\(index)"
    }
}

struct StatusRowSelectableTextView: View {
    @Environment(\.dismiss) private var dismiss
    let content: AttributedString

    @State private var selectedTags: Set<Tag> = []

    private var tags: [Tag] {
        let markdownText = content.description
        var processedText = markdownText
        var extractedTags: [String] = []
        // replace md link to text link
        let linkPattern = "\\[([^\\]]+)\\]\\(([^\\)]+)\\)"
        if let regex = try? NSRegularExpression(pattern: linkPattern) {
            let range = NSRange(processedText.startIndex..., in: processedText)
            let matches = regex.matches(in: processedText, range: range)

            for match in matches.reversed() {
                if match.numberOfRanges == 3,
                   let urlRange = Range(match.range(at: 2), in: processedText)
                {
                    let url = String(processedText[urlRange])
                    extractedTags.append(url)

                    if let matchRange = Range(match.range, in: processedText) {
                        processedText.removeSubrange(matchRange)
                    }
                }
            }
        }

        let cleanText = processedText
            // HTML
            .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
            // markdown
            .replacingOccurrences(of: "[*_~`#]", with: "", options: .regularExpression)
            // 引号和其他特殊字符（保留字母、数字、空格和基本标点）
            .replacingOccurrences(of: "[^\\p{L}\\p{N}\\s.,!?-]", with: "", options: .regularExpression)
            // 空白字符
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)

        let textTags = cleanText.components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
            .filter { $0.count > 1 }

        let allTexts = textTags + extractedTags
        return allTexts.enumerated().map { index, text in
            Tag(text: text, index: index)
        }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                ScrollView {
                    FlowLayout(alignment: .leading, spacing: 8) {
                        ForEach(tags) { tag in
                            TagView(tag: tag.text, isSelected: selectedTags.contains(tag))
                                .onTapGesture {
                                    toggleSelection(tag)
                                }
                        }
                    }
                    .padding()
                    .padding(.bottom, 80)
                }

                if !selectedTags.isEmpty {
                    VStack {
                        Spacer()
                        Button {
                            copySelectedText()
                            dismiss()
                        } label: {
                            HStack {
                                Image(systemName: "doc.on.doc")
                                Text("Copy Selected")
                            }
                            .padding()
                            .frame(maxWidth: .infinity)
                            .background(Color.accentColor)
                            .foregroundColor(.white)
                            .cornerRadius(16)
                        }
                        .padding()
                        .background(
                            Rectangle()
                                .fill(.ultraThinMaterial)
                                .edgesIgnoringSafeArea(.bottom)
                        )
                    }
                }
            }
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Text("Done")
                            .bold()
                    }
                }
            }
            .navigationTitle("Select Text")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationBackground(.ultraThinMaterial)
        .presentationCornerRadius(16)
    }

    private func toggleSelection(_ tag: Tag) {
        if selectedTags.contains(tag) {
            selectedTags.remove(tag)
        } else {
            selectedTags.insert(tag)
        }
    }

    private func copySelectedText() {
        let selectedText = selectedTags.map(\.text).joined(separator: " ")
        UIPasteboard.general.string = selectedText
    }
}

struct TagView: View {
    let tag: String
    let isSelected: Bool

    var body: some View {
        Text(tag)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(isSelected ? Color.accentColor : Color.secondary.opacity(0.1))
            .foregroundColor(isSelected ? .white : .primary)
            .cornerRadius(16)
            .animation(.spring(), value: isSelected)
    }
}

struct FlowLayout: Layout {
    var alignment: HorizontalAlignment = .center
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache _: inout ()) -> CGSize {
        let rows = computeRows(proposal: proposal, subviews: subviews)
        let height = rows.map(\.height).reduce(0) { $0 + $1 + spacing }
        return CGSize(width: proposal.width ?? 0, height: max(0, height - spacing))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache _: inout ()) {
        let rows = computeRows(proposal: proposal, subviews: subviews)
        var y = bounds.minY

        for row in rows {
            var x = bounds.minX
            switch alignment {
            case .center:
                x = bounds.minX + (bounds.width - row.width) / 2
            case .trailing:
                x = bounds.minX + (bounds.width - row.width)
            default:
                break
            }

            for view in row.views {
                let size = view.sizeThatFits(proposal)
                view.place(at: CGPoint(x: x, y: y), proposal: proposal)
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private func computeRows(proposal: ProposedViewSize, subviews: Subviews) -> [Row] {
        var rows: [Row] = []
        var currentRow = Row()
        let maxWidth = proposal.width ?? 0

        for view in subviews {
            let size = view.sizeThatFits(proposal)

            if currentRow.width + size.width + (currentRow.views.isEmpty ? 0 : spacing) > maxWidth {
                rows.append(currentRow)
                currentRow = Row()
            }

            currentRow.add(view: view, size: size, spacing: spacing)
        }

        if !currentRow.views.isEmpty {
            rows.append(currentRow)
        }

        return rows
    }

    private struct Row {
        var views: [LayoutSubview] = []
        var width: CGFloat = 0
        var height: CGFloat = 0

        mutating func add(view: LayoutSubview, size: CGSize, spacing: CGFloat) {
            views.append(view)
            width += size.width + (views.count > 1 ? spacing : 0)
            height = max(height, size.height)
        }
    }
}
