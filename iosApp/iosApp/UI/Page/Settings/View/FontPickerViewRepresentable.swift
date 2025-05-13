import SwiftUI
import UIKit

struct CustomFontPickerView: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var selectedFontName: String
    
    var body: some View {
        FontPickerView(selectedFontName: $selectedFontName)
    }
}

struct FontPickerView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var fontProvider = AppFontProvider.shared
    @Binding var selectedFontName: String
    
    init(selectedFontName: Binding<String>) {
        self._selectedFontName = selectedFontName
    }
    
    var body: some View {
        NavigationView {
            List {
                Section {
                    ForEach(FontType.allCases) { fontType in
                        Button {
                            fontProvider.selectedFontType = fontType
                            updateSelectedFont()
                        } label: {
                            HStack {
                                Text(fontType.rawValue)
                                    .font(getFontForType(fontType))
                                    .foregroundColor(.primary)
                                
                                Spacer()
                                
                                if fontProvider.selectedFontType == fontType {
                                    Image(systemName: "checkmark")
                                        .foregroundColor(.accentColor)
                                }
                            }
                        }
                    }
                } header: {
                    Text("字体类型")
                }
                
                Section {
                    Button {
                        fontProvider.fontWeightString = "thin"
                        updateSelectedFont()
                    } label: {
                        getFontWeightRow("极细", weight: "thin")
                    }
                    
                    Button {
                        fontProvider.fontWeightString = "ultraLight"
                        updateSelectedFont()
                    } label: {
                        getFontWeightRow("超细", weight: "ultraLight")
                    }
                    
                    Button {
                        fontProvider.fontWeightString = "light"
                        updateSelectedFont()
                    } label: {
                        getFontWeightRow("细体", weight: "light")
                    }
                    
                    Button {
                        fontProvider.fontWeightString = "regular"
                        updateSelectedFont()
                    } label: {
                        getFontWeightRow("常规", weight: "regular")
                    }
                    
                    Button {
                        fontProvider.fontWeightString = "medium"
                        updateSelectedFont()
                    } label: {
                        getFontWeightRow("中等", weight: "medium")
                    }
                    
                    Button {
                        fontProvider.fontWeightString = "semibold"
                        updateSelectedFont()
                    } label: {
                        getFontWeightRow("半粗", weight: "semibold")
                    }
                    
                    Button {
                        fontProvider.fontWeightString = "bold"
                        updateSelectedFont()
                    } label: {
                        getFontWeightRow("粗体", weight: "bold")
                    }
                    
                    Button {
                        fontProvider.fontWeightString = "heavy"
                        updateSelectedFont()
                    } label: {
                        getFontWeightRow("特粗", weight: "heavy")
                    }
                    
                    Button {
                        fontProvider.fontWeightString = "black"
                        updateSelectedFont()
                    } label: {
                        getFontWeightRow("黑体", weight: "black")
                    }
                } header: {
                    Text("字体粗细")
                }
                
                Section {
                    VStack {
                        HStack {
                            Text("Aa")
                                .font(.caption)
                            Slider(value: $fontProvider.fontSizeMultiplier, in: 0.7...1.4, step: 0.05)
                                .onChange(of: fontProvider.fontSizeMultiplier) { _ in
                                    updateSelectedFont()
                                }
                            Text("Aa")
                                .font(.title)
                        }
                        
                        Text("字体大小：\(Int(fontProvider.fontSizeMultiplier * 100))%")
                    }
                } header: {
                    Text("字体大小")
                }
                
                Section {
                    // 预览当前字体效果
                    VStack(spacing: 12) {
                        Text("标题文本")
                            .font(fontProvider.font(for: .title2))
                        
                        Text("这是一段使用当前字体设置的示例文本。")
                            .font(fontProvider.font(for: .body))
                        
                        Text("小标题文本")
                            .font(fontProvider.font(for: .subheadline))
                        
                        Text("脚注文本")
                            .font(fontProvider.font(for: .caption))
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color(.secondarySystemBackground))
                    .cornerRadius(12)
                } header: {
                    Text("预览")
                }
            }
            .navigationTitle("字体设置")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("完成") {
                        dismiss()
                    }
                }
            }
        }
    }
    
    private func updateSelectedFont() {
        // 获取字体名称
        let fontName: String
        switch fontProvider.selectedFontType {
        case .system:
            fontName = ".SFUI-Regular"
        case .rounded:
            fontName = "SFRounded-Regular"
        case .serif:
            fontName = "NewYork-Regular"
        case .mono:
            fontName = "SFMono-Regular"
        }
        selectedFontName = fontName
    }
    
    private func getFontForType(_ type: FontType) -> Font {
        let fontWeight = getFontWeight(fontProvider.fontWeightString)
        
        switch type {
        case .system:
            return .system(.body, design: .default, weight: fontWeight)
        case .rounded:
            return .system(.body, design: .rounded, weight: fontWeight)
        case .serif:
            return .system(.body, design: .serif, weight: fontWeight)
        case .mono:
            return .system(.body, design: .monospaced, weight: fontWeight)
        }
    }
    
    private func getFontWeight(_ weight: String) -> Font.Weight {
        switch weight {
        case "thin":
            return .thin
        case "ultraLight":
            return .ultraLight
        case "light":
            return .light
        case "regular":
            return .regular
        case "medium":
            return .medium
        case "semibold":
            return .semibold
        case "bold":
            return .bold
        case "heavy":
            return .heavy
        case "black":
            return .black
        default:
            return .regular
        }
    }
    
    private func getFontWeightRow(_ title: String, weight: String) -> some View {
        HStack {
            Text(title)
                .font(.system(.body, design: .default, weight: getFontWeight(weight)))
                .foregroundColor(.primary)
            
            Spacer()
            
            if fontProvider.fontWeightString == weight {
                Image(systemName: "checkmark")
                    .foregroundColor(.accentColor)
            }
        }
    }
}

struct SystemFontPickerViewRepresentable: UIViewControllerRepresentable {
    @Binding var selectedFontName: String
    @Environment(\.presentationMode) var presentationMode
    
    func makeUIViewController(context: Context) -> UIFontPickerViewController {
        let fontPicker = UIFontPickerViewController()
        fontPicker.delegate = context.coordinator
        return fontPicker
    }
    
    func updateUIViewController(_ uiViewController: UIFontPickerViewController, context: Context) {
        // 无需更新
    }
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, UIFontPickerViewControllerDelegate {
        let parent: SystemFontPickerViewRepresentable
        
        init(_ parent: SystemFontPickerViewRepresentable) {
            self.parent = parent
        }
        
        func fontPickerViewControllerDidPickFont(_ viewController: UIFontPickerViewController) {
            guard let descriptor = viewController.selectedFontDescriptor else { return }
            
            // 获取字体名称
            if let fontName = descriptor.object(forKey: .name) as? String {
                parent.selectedFontName = fontName
            }
            
            // 关闭字体选择器
            parent.presentationMode.wrappedValue.dismiss()
        }
        
        func fontPickerViewControllerDidCancel(_ viewController: UIFontPickerViewController) {
            parent.presentationMode.wrappedValue.dismiss()
        }
    }
}

// FontPickerViewRepresentable 现在是一个辅助类型，基于用户偏好选择不同的字体选择器
struct FontPickerViewRepresentable: View {
    @Binding var selectedFontName: String
    
    var body: some View {
        // 默认使用自定义字体选择器
        CustomFontPickerView(selectedFontName: $selectedFontName)
        
        // 如果需要切换到系统字体选择器，可以取消注释下面的代码
        // SystemFontPickerViewRepresentable(selectedFontName: $selectedFontName)
    }
} 
 