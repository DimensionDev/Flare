import MarkdownParser
import MarkdownView
import SwiftUI
import UIKit

struct TextViewMarkdown: UIViewRepresentable {
    let markdownText: String
    let style: FlareTextStyle.Style
    let fontScale: CGFloat

    func makeUIView(context _: Context) -> MarkdownTextViewWrapper {
        let wrapper = MarkdownTextViewWrapper()
        wrapper.configure(style: style, fontScale: fontScale)
        wrapper.setMarkdownText(markdownText)
        return wrapper
    }

    func updateUIView(_ uiView: MarkdownTextViewWrapper, context: Context) {
        if context.coordinator.lastMarkdownText != markdownText {
            context.coordinator.lastMarkdownText = markdownText
            uiView.setMarkdownText(markdownText)
        }

        // 更新样式（如果需要）
        uiView.configure(style: style, fontScale: fontScale)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    class Coordinator {
        var lastMarkdownText: String = ""
    }
}

// MARK: - MarkdownTextViewWrapper

class MarkdownTextViewWrapper: UIView {
    private let markdownTextView = MarkdownTextView()
    private var currentMarkdownText: String = ""
    private var currentStyle: FlareTextStyle.Style?
    private var currentFontScale: CGFloat = 1.0

    override init(frame: CGRect) {
        super.init(frame: frame)
        setupView()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setupView()
    }

    private func setupView() {
        addSubview(markdownTextView)
        markdownTextView.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            markdownTextView.topAnchor.constraint(equalTo: topAnchor),
            markdownTextView.leadingAnchor.constraint(equalTo: leadingAnchor),
            markdownTextView.trailingAnchor.constraint(equalTo: trailingAnchor),
            markdownTextView.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
    }

    func configure(style: FlareTextStyle.Style, fontScale: CGFloat) {
        let newTheme = TextViewMarkdownStyle.TextViewMarkdownStyleTheme(
            using: style,
            fontScale: fontScale
        )

        // 检查主题是否发生变化
        let themeChanged = currentStyle == nil ||
            !style.isEqual(to: currentStyle!) ||
            currentFontScale != fontScale

        if themeChanged {
            currentStyle = style
            currentFontScale = fontScale
            markdownTextView.theme = newTheme

            // 如果已有内容且主题发生变化，重新渲染
            if !currentMarkdownText.isEmpty {
                DispatchQueue.main.async { [weak self] in
                    self?.rerenderCurrentContent()
                }
            }
        }
    }

    func setMarkdownText(_ text: String) {
        guard text != currentMarkdownText else { return }
        currentMarkdownText = text

        // 在后台线程处理 Markdown
        DispatchQueue.global().async { [weak self] in
            guard let self else { return }

            // 处理常见的HTML标签，特别是<br>换行标签
            var processedContent: String = if text.lowercased().contains("<br") {
                // 将<br>、<br/>、<br />等变体都转换为换行符
                text.replacingOccurrences(
                    of: "<br\\s*/?>",
                    with: "\n",
                    options: [.regularExpression, .caseInsensitive]
                )
            } else {
                // 对于其他HTML标签，保持原样显示
                text
            }

            let parser = MarkdownParser()
            let result = parser.parse(processedContent)

            // 准备渲染内容
            let theme = markdownTextView.theme
            var renderedContexts: [String: RenderedItem] = [:]

            // 处理数学公式内容
            for (key, value) in result.mathContext {
                let image = MathRenderer.renderToImage(
                    latex: value,
                    fontSize: theme.fonts.body.pointSize,
                    textColor: theme.colors.body
                )?.withRenderingMode(.alwaysTemplate)

                let renderedContext = RenderedItem(
                    image: image,
                    text: value
                )
                renderedContexts["math://\(key)"] = renderedContext
            }

            // 切换到主线程更新 UI
            DispatchQueue.main.async {
                self.markdownTextView.setMarkdown(result.document, renderedContent: renderedContexts)
                self.invalidateIntrinsicContentSize()
                self.setNeedsLayout()
            }
        }
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        markdownTextView.frame = bounds
    }

    override var intrinsicContentSize: CGSize {
        let width = bounds.width > 0 ? bounds.width : UIView.noIntrinsicMetric
        if width == UIView.noIntrinsicMetric {
            return CGSize(width: UIView.noIntrinsicMetric, height: UIView.noIntrinsicMetric)
        }
        return markdownTextView.boundingSize(for: width)
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        markdownTextView.boundingSize(for: size.width)
    }

    private func rerenderCurrentContent() {
        guard !currentMarkdownText.isEmpty else { return }

        // 在后台线程重新处理当前内容
        DispatchQueue.global().async { [weak self] in
            guard let self else { return }

            // 处理常见的HTML标签，特别是<br>换行标签
            var processedContent: String = if currentMarkdownText.lowercased().contains("<br") {
                // 将<br>、<br/>、<br />等变体都转换为换行符
                currentMarkdownText.replacingOccurrences(
                    of: "<br\\s*/?>",
                    with: "\n",
                    options: [.regularExpression, .caseInsensitive]
                )
            } else {
                // 对于其他HTML标签，保持原样显示
                currentMarkdownText
            }

            let parser = MarkdownParser()
            let result = parser.parse(processedContent)

            // 准备渲染内容
            let theme = markdownTextView.theme
            var renderedContexts: [String: RenderedItem] = [:]

            // 处理数学公式内容
            for (key, value) in result.mathContext {
                let image = MathRenderer.renderToImage(
                    latex: value,
                    fontSize: theme.fonts.body.pointSize,
                    textColor: theme.colors.body
                )?.withRenderingMode(.alwaysTemplate)

                let renderedContext = RenderedItem(
                    image: image,
                    text: value
                )
                renderedContexts["math://\(key)"] = renderedContext
            }

            // 切换到主线程更新 UI
            DispatchQueue.main.async {
                self.markdownTextView.setMarkdown(result.document, renderedContent: renderedContexts)
                self.invalidateIntrinsicContentSize()
                self.setNeedsLayout()
            }
        }
    }
}
