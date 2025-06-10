import MarkdownView
import MarkdownParser
import SwiftUI
import UIKit

struct TextViewMarkdown: UIViewRepresentable {
    let markdownText: String
    let style: FlareTextStyle.Style
    let fontScale: CGFloat
    
    func makeUIView(context: Context) -> MarkdownTextViewWrapper {
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
            markdownTextView.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])
    }
    
    func configure(style: FlareTextStyle.Style, fontScale: CGFloat) {
        let newTheme = TextViewMarkdownStyle.TextViewMarkdownStyleTheme(
            using: style,
            fontScale: fontScale
        )
        

        let themeChanged = currentStyle == nil || 
                          !style.isEqual(to: currentStyle!) || 
                          currentFontScale != fontScale
        
        if themeChanged {
            currentStyle = style
            currentFontScale = fontScale
            markdownTextView.theme = newTheme
            

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
        

        DispatchQueue.global().async { [weak self] in
            guard let self = self else { return }
            

            var processedContent: String
            
            if text.lowercased().contains("<br") {
                // 将<br>、<br/>、<br />等变体都转换为换行符
                processedContent = text.replacingOccurrences(
                    of: "<br\\s*/?>", 
                    with: "\n", 
                    options: [.regularExpression, .caseInsensitive]
                )
            } else {

                processedContent = text
            }

            let parser = MarkdownParser()
            let result = parser.parse(processedContent)
            

            let theme = self.markdownTextView.theme
            var renderedContexts: [String: RenderedItem] = [:]
            

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
        return markdownTextView.boundingSize(for: size.width)
    }
    
    private func rerenderCurrentContent() {
        guard !currentMarkdownText.isEmpty else { return }
        

        DispatchQueue.global().async { [weak self] in
            guard let self = self else { return }
            

            var processedContent: String
            
            if self.currentMarkdownText.lowercased().contains("<br") {
                // 将<br>、<br/>、<br />等变体都转换为换行符
                processedContent = self.currentMarkdownText.replacingOccurrences(
                    of: "<br\\s*/?>", 
                    with: "\n", 
                    options: [.regularExpression, .caseInsensitive]
                )
            } else {

                processedContent = self.currentMarkdownText
            }

            let parser = MarkdownParser()
            let result = parser.parse(processedContent)
            

            let theme = self.markdownTextView.theme
            var renderedContexts: [String: RenderedItem] = [:]
            

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
            

            DispatchQueue.main.async {
                self.markdownTextView.setMarkdown(result.document, renderedContent: renderedContexts)
                self.invalidateIntrinsicContentSize()
                self.setNeedsLayout()
            }
        }
    }
}
