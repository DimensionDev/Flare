import NaturalLanguage
import SwiftUI

enum TagType: String, CaseIterable {
    case word = "Word"
    case sentence = "Sentence"
//    case paragraph = "Paragraph"
    case url = "URL"
    case email = "Email"

    var icon: String {
        switch self {
        case .word: "textformat"
        case .sentence: "text.quote"
//        case .paragraph: return "text.alignleft"
        case .url: "link"
        case .email: "envelope"
        }
    }

    var color: Color {
        switch self {
        case .word: .blue
        case .sentence: .green
//        case .paragraph: return .orange
        case .url: .purple
        case .email: .red
        }
    }
}

struct Tag: Hashable, Identifiable {
    let id: String
    let text: String
    let index: Int
    let type: TagType
    let language: NLLanguage?

    init(text: String, index: Int, type: TagType, language: NLLanguage? = nil) {
        self.text = text
        self.index = index
        self.type = type
        self.language = language
        id = "\(text)_\(index)_\(type.rawValue)"
    }
}

struct StatusRowSelectableTextView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(FlareTheme.self) private var theme
    let content: AttributedString

    @State private var selectedTags: Set<Tag> = []
    @State private var selectedGranularity: NLTokenUnit = .word
    @State private var selectedTagTypes: Set<TagType> = Set(TagType.allCases)
    @State private var detectedLanguage: NLLanguage?
    @State private var allTags: [Tag] = []

    private var filteredTags: [Tag] {
        allTags.filter { selectedTagTypes.contains($0.type) }
    }

    private var tagCounts: [TagType: Int] {
        Dictionary(grouping: allTags, by: { $0.type })
            .mapValues { $0.count }
    }

    private func processTextWithNaturalLanguage() {
        let markdownText = content.description

        // 1. 提取特殊元素
        let (specialTags, cleanedText) = extractSpecialElements(from: markdownText)

        // 2. 文本清理
        let cleanedTextSegments = cleanTextForTokenization(cleanedText)

        // 3. 语言检测
        let languageRecognizer = NLLanguageRecognizer()
        languageRecognizer.processString(cleanedText)
        detectedLanguage = languageRecognizer.dominantLanguage

        // 4. 分词处理
        var tempTokenizedTags: [Tag] = []
        for segment in cleanedTextSegments {
            tempTokenizedTags.append(contentsOf: tokenizeText(segment, unit: selectedGranularity, language: detectedLanguage))
        }

        // 5. 合并所有标签
        allTags = specialTags + tempTokenizedTags
    }

    private func extractSpecialElements(from text: String) -> ([Tag], String) {
        var processedText = text
        var specialTags: [Tag] = []
        var tagIndex = 0

        // 提取Markdown链接
        let linkPattern = "\\[([^\\]]+)\\]\\(([^\\)]+)\\)"
        if let regex = try? NSRegularExpression(pattern: linkPattern) {
            let range = NSRange(processedText.startIndex..., in: processedText)
            let matches = regex.matches(in: processedText, range: range)

            for match in matches.reversed() {
                if match.numberOfRanges == 3,
                   let textRange = Range(match.range(at: 1), in: processedText),
                   let urlRange = Range(match.range(at: 2), in: processedText)
                {
                    var linkText = String(processedText[textRange])
                    let url = String(processedText[urlRange])

                    // "长江电力开始发力了？<br />最近这几天TRX突然开始发力<br />一度逆势上涨 力压大盘<br />难道说？或许是？可能因为？<br /><br />[@justinsuntron](flare://ProfileWithNameAndHost/justinsuntron/twitter.com?accountKey=426425493@twitter.com)\n[\\#TRONEcoStar](flare://Search/%23TRONEcoStar?accountKey=426425493@twitter.com)\n\n\n {\n}"

                    if linkText.hasPrefix("\\"), linkText.dropFirst().hasPrefix("#") {
                        linkText = String(linkText.dropFirst())
                    }

                    specialTags.append(Tag(text: linkText, index: tagIndex, type: .word))
                    tagIndex += 1

                    specialTags.append(Tag(text: url, index: tagIndex, type: .url))
                    tagIndex += 1

                    if let matchRange = Range(match.range, in: processedText) {
                        processedText.removeSubrange(matchRange)
                    }
                }
            }
        }

        // 提取URL
        let urlPattern = "https?://[^\\s]+"
        if let urlRegex = try? NSRegularExpression(pattern: urlPattern) {
            let range = NSRange(processedText.startIndex..., in: processedText)
            let matches = urlRegex.matches(in: processedText, range: range)

            for match in matches.reversed() {
                if let matchRange = Range(match.range, in: processedText) {
                    let url = String(processedText[matchRange])
                    specialTags.append(Tag(text: url, index: tagIndex, type: .url))
                    tagIndex += 1
                    processedText.removeSubrange(matchRange)
                }
            }
        }

        // 提取邮箱
        let emailPattern = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
        if let emailRegex = try? NSRegularExpression(pattern: emailPattern) {
            let range = NSRange(processedText.startIndex..., in: processedText)
            let matches = emailRegex.matches(in: processedText, range: range)

            for match in matches.reversed() {
                if let matchRange = Range(match.range, in: processedText) {
                    let email = String(processedText[matchRange])
                    specialTags.append(Tag(text: email, index: tagIndex, type: .email))
                    tagIndex += 1
                    processedText.removeSubrange(matchRange)
                }
            }
        }

        return (specialTags, processedText)
    }

    private func cleanTextForTokenization(_ text: String) -> [String] {
        let segments = text.components(separatedBy: "<br />")

        return segments.compactMap { segment -> String? in
            let cleanedSegment = segment
                // HTML标签
                .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
                // Markdown格式字符
                .replacingOccurrences(of: "[*_~`#]", with: "", options: .regularExpression)
                // 多余的空白字符
                .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: "{ }", with: "")

            return cleanedSegment.isEmpty ? nil : cleanedSegment
        }
    }

    private func tokenizeText(_ text: String, unit: NLTokenUnit, language: NLLanguage?) -> [Tag] {
        let tokenizer = NLTokenizer(unit: unit)
        if let language {
            tokenizer.setLanguage(language)
        }
        tokenizer.string = text

        var tags: [Tag] = []
        var index = allTags.count

        tokenizer.enumerateTokens(in: text.startIndex ..< text.endIndex) { tokenRange, _ in
            let token = String(text[tokenRange]).trimmingCharacters(in: .whitespacesAndNewlines)

            if token.isEmpty || token.allSatisfy({ $0.isWhitespace || $0.isPunctuation }) {
                return true // 丢弃空的或纯标点/空格的token
            }

            if token.count == 1 {
                // 对于单字符token
                if unit == .word {
                    // 在单词模式下，保留字母、数字或isImportantSingleCharacter定义的特殊字符
                    let char = token.first!
                    if !(char.isLetter || char.isNumber || isImportantSingleCharacter(token)) {
                        return true // 丢弃不符合条件的单字符单词
                    }
                } else {
                    // 在句子/段落模式下（或其他非单词模式），仅保留isImportantSingleCharacter定义的特殊字符
                    if !isImportantSingleCharacter(token) {
                        return true // 丢弃非重要的单字符（对于句子/段落token）
                    }
                }
            }
            // 如果token长度大于1，或者长度为1但通过了上述检查，则保留该token

            let tagType: TagType = switch unit {
            case .word: .word
            case .sentence: .sentence
//                case .paragraph: return .paragraph
            default: .word
            }

            tags.append(Tag(text: token, index: index, type: tagType, language: language))
            index += 1
            return true
        }

        return tags
    }

    private func isImportantSingleCharacter(_ char: String) -> Bool {
        // 保留重要的单字符，如表情符号、特殊符号等
        guard char.count == 1 else { return false }
        let character = char.first!

        // 检查是否为表情符号
        let isEmoji = character.unicodeScalars.contains { scalar in
            scalar.properties.isEmoji
        }

        return isEmoji || character.isSymbol || character.isMathSymbol
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                VStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 8) {
//                        Text(content) // debug 用

//                        Text("Tokenization Granularity")
//                            .font(.headline)
//                            .foregroundColor(.primary)

                        Picker("Granularity", selection: $selectedGranularity) {
                            Text("Word").tag(NLTokenUnit.word)
                            Text("Sentence").tag(NLTokenUnit.sentence)
//                            Text("Paragraph").tag(NLTokenUnit.paragraph)
                        }
                        .pickerStyle(SegmentedPickerStyle())
                        .onChange(of: selectedGranularity) { _ in
                            processTextWithNaturalLanguage()
                        }
                    }

                    // 标签类型过滤
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Tag Types")
                            .font(.headline)
                            .foregroundColor(theme.labelColor)

                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(TagType.allCases, id: \.self) { tagType in
                                    Button(action: {
                                        if selectedTagTypes.contains(tagType) {
                                            selectedTagTypes.remove(tagType)
                                        } else {
                                            selectedTagTypes.insert(tagType)
                                        }
                                    }) {
                                        HStack(spacing: 4) {
                                            Image(systemName: tagType.icon)
                                            Text(tagType.rawValue)
                                            if let count = tagCounts[tagType] {
                                                Text("(\(count))")
                                                    .font(.caption)
                                            }
                                        }
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 6)
                                        .background(
                                            selectedTagTypes.contains(tagType) ?
                                                tagType.color.opacity(0.2) : Color.gray.opacity(0.1)
                                        )
                                        .foregroundColor(
                                            selectedTagTypes.contains(tagType) ?
                                                tagType.color : theme.labelColor.opacity(0.6)
                                        )
                                        .cornerRadius(16)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 16)
                                                .stroke(
                                                    selectedTagTypes.contains(tagType) ?
                                                        tagType.color : Color.clear,
                                                    lineWidth: 1
                                                )
                                        )
                                    }
                                }
                            }
                            .padding(.horizontal)
                        }
                    }

                    HStack {
                        if let language = detectedLanguage {
                            HStack(spacing: 4) {
                                Image(systemName: "globe")
                                Text("Language: \(language.rawValue.uppercased())")
                            }
                            .font(.caption)
                            .foregroundColor(theme.labelColor.opacity(0.6))
                        }

                        Spacer()

                        Text("Total: \(filteredTags.count) tags")
                            .font(.caption)
                            .foregroundColor(theme.labelColor.opacity(0.6))
                    }
                }
                .padding()
                .background(theme.secondaryBackgroundColor)

                // 标签显示区域
                ZStack {
                    ScrollView {
                        FlowLayout(alignment: .leading, spacing: 8) {
                            ForEach(filteredTags) { tag in
                                EnhancedTagView(tag: tag, isSelected: selectedTags.contains(tag))
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
                                ToastView(icon: UIImage(systemName: "checkmark.circle"), message: NSLocalizedString("Copy Success", comment: "")).show()

                                // dismiss()
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
            .navigationTitle("Copy Any")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                processTextWithNaturalLanguage()
            }
        }
        .background(theme.primaryBackgroundColor)
        .presentationBackground(theme.primaryBackgroundColor)
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

struct EnhancedTagView: View {
    let tag: Tag
    let isSelected: Bool

    var body: some View {
        HStack(spacing: 4) {
            if tag.type != TagType.word {
                Image(systemName: tag.type.icon).font(.caption2)
            }

            Text(tag.text)
            // .lineLimit(1)
            // if let language = tag.language, language != .undetermined {
            //     Text(language.rawValue.uppercased())
            //         .font(.caption2)
            //         .padding(.horizontal, 4)
            //         .padding(.vertical, 1)
            //         .background(Color.secondary.opacity(0.2))
            //         .cornerRadius(4)
            // }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(
            isSelected ?
                tag.type.color :
                tag.type.color.opacity(0.1)
        )
        .foregroundColor(
            isSelected ? .white : tag.type.color
        )
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(
                    tag.type.color.opacity(isSelected ? 0 : 0.3),
                    lineWidth: 1
                )
        )
        .scaleEffect(isSelected ? 1.05 : 1.0)
        .animation(.spring(response: 0.3, dampingFraction: 0.6), value: isSelected)
    }
}

struct TagView: View {
    let tag: String
    let isSelected: Bool
    @Environment(FlareTheme.self) private var theme

    var body: some View {
        Text(tag)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(isSelected ? Color.accentColor : theme.labelColor.opacity(0.1))
            .foregroundColor(isSelected ? .white : theme.labelColor)
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
