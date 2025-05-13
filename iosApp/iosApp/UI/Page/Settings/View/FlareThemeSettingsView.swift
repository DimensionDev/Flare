import shared
import SwiftUI

struct FlareThemeSettingsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.appSettings) private var appSettings

    // 主题设置
    @State private var selectedTheme: ColorSetName = Theme.shared!.selectedSet
    @State private var displayMode: AppDisplayMode = Theme.shared!.appDisplayMode

    // 字体设置
    @State private var fontSizeScale: Double = Theme.shared!.fontSizeScale
    @State private var lineSpacing: Double = Theme.shared!.lineSpacing
    @State private var showFontPicker = false

    // 自定义颜色
    @State private var showColorPicker = false
    @State private var customTintColor: Color = Theme.shared!.tintColor

    // 文本预览
    private let previewText = "这是一段文本预览，用于展示字体大小和行距的效果。This is a sample text preview to demonstrate font size and line spacing effects."

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    // 显示模式选择
                    displayModeSection

                    // 主题预览和选择
                    themeSection

                    // 字体设置
                    fontSection

                    // 自定义颜色设置
                    colorSection
                }
                .padding()
            }
            .navigationTitle("主题与外观")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("完成") {
                        dismiss()
                    }
                }
            }
            .onChange(of: selectedTheme) { _, newValue in
                // 更新主题
                Theme.shared!.selectedSet = newValue
                Theme.shared!.applyGlobalUIElements()
            }
            .onChange(of: displayMode) { _, newValue in
                // 更新显示模式
                Theme.shared!.appDisplayMode = newValue
                Theme.shared!.applyGlobalUIElements()
            }
            .applyTheme()
        }
    }

    // 显示模式部分
    private var displayModeSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("显示模式")
                .font(.headline)
                .padding(.leading)

            Picker("显示模式", selection: $displayMode) {
                ForEach(AppDisplayMode.allCases) { mode in
                    Text(mode.localizedName).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)
        }
    }

    // 主题选择部分
    private var themeSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("主题")
                .font(.headline)
                .padding(.leading)

            ThemeSelectionGrid(selectedTheme: $selectedTheme)
        }
    }

    // 字体设置部分
    private var fontSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("文本设置")
                .font(.headline)
                .padding(.leading)

            // 字体选择
            VStack(alignment: .leading, spacing: 4) {
                Picker(
                    "字体",
                    selection: .init(
                        get: { () -> FontState in
                            if Theme.shared!.chosenFont?.fontName == "OpenDyslexic-Regular" {
                                return FontState.openDyslexic
                            } else if Theme.shared!.chosenFont?.fontName == "AtkinsonHyperlegible-Regular" {
                                return FontState.hyperLegible
                            } else if Theme.shared!.chosenFont?.fontName == ".AppleSystemUIFontRounded-Regular" {
                                return FontState.SFRounded
                            }
                            return Theme.shared!.chosenFont != nil ? FontState.custom : FontState.system
                        },
                        set: { newValue in
                            switch newValue {
                            case .system:
                                Theme.shared!.chosenFont = nil
                            case .openDyslexic:
                                Theme.shared!.chosenFont = UIFont(name: "OpenDyslexic", size: 1)
                            case .hyperLegible:
                                Theme.shared!.chosenFont = UIFont(name: "Atkinson Hyperlegible", size: 1)
                            case .SFRounded:
                                Theme.shared!.chosenFont = UIFont.systemFont(ofSize: 1).rounded()
                            case .custom:
                                showFontPicker = true
                            }
                        })
                ) {
                    ForEach(FontState.allCases, id: \.rawValue) { fontState in
                        Text(fontState.title).tag(fontState)
                    }
                }
                .pickerStyle(.navigationLink)
            }
            .padding()
            .background(Theme.shared!.secondaryBackgroundColor)
            .cornerRadius(10)

            // 字体大小
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("字体大小")
                    Spacer()
                    Text(String(format: "%.1fx", fontSizeScale))
                        .foregroundColor(.secondary)
                }

                Slider(value: $fontSizeScale, in: 0.8 ... 1.5, step: 0.1) { changed in
                    if changed {
                        Theme.shared!.fontSizeScale = fontSizeScale
                    }
                }
            }
            .padding()
            .background(Theme.shared!.secondaryBackgroundColor)
            .cornerRadius(10)

            // 行距
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text("行距")
                    Spacer()
                    Text(String(format: "%.1fx", lineSpacing))
                        .foregroundColor(.secondary)
                }

                Slider(value: $lineSpacing, in: 1.0 ... 2.0, step: 0.1) { changed in
                    if changed {
                        Theme.shared!.lineSpacing = lineSpacing
                    }
                }
            }
            .padding()
            .background(Theme.shared!.secondaryBackgroundColor)
            .cornerRadius(10)

            // 文本预览
            Text(previewText)
                .font(.system(size: 16 * fontSizeScale))
                .lineSpacing(5 * lineSpacing)
                .padding()
                .frame(maxWidth: .infinity)
                .background(Theme.shared!.secondaryBackgroundColor)
                .cornerRadius(10)

            // 重置字体按钮
            Button {
                fontSizeScale = 1.0
                lineSpacing = 1.2
                Theme.shared!.resetFont()
            } label: {
                Text("重置为默认设置")
                    .foregroundColor(Theme.shared!.tintColor)
                    .padding()
                    .frame(maxWidth: .infinity)
                    .background(Theme.shared!.secondaryBackgroundColor)
                    .cornerRadius(10)
            }
        }
        .sheet(isPresented: $showFontPicker) {
            FontPicker()
        }
    }

    // 颜色自定义部分
    private var colorSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("自定义颜色")
                .font(.headline)
                .padding(.leading)

            // 强调色选择
            Button {
                showColorPicker = true
            } label: {
                HStack {
                    Text("强调色")
                    Spacer()
                    Rectangle()
                        .fill(customTintColor)
                        .frame(width: 20, height: 20)
                        .cornerRadius(4)
                    Image(systemName: "chevron.right")
                        .font(.footnote)
                        .foregroundColor(.secondary)
                }
                .padding()
                .background(Theme.shared!.secondaryBackgroundColor)
                .cornerRadius(10)
            }
            .buttonStyle(.plain)

            // 重置颜色按钮
            Button {
                Theme.shared!.applySet(selectedTheme)
                customTintColor = Theme.shared!.tintColor
            } label: {
                Text("重置为默认颜色")
                    .foregroundColor(Theme.shared!.tintColor)
                    .padding()
                    .frame(maxWidth: .infinity)
                    .background(Theme.shared!.secondaryBackgroundColor)
                    .cornerRadius(10)
            }
        }
        .sheet(isPresented: $showColorPicker) {
            ColorPickerView(selectedColor: $customTintColor) { color in
                Theme.shared!.tintColor = color
                Theme.shared!.applyGlobalUIElements()
            }
        }
    }
}

// 字体选择器
struct FontPickerViewController: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIFontPickerViewController {
        let picker = UIFontPickerViewController()
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_: UIFontPickerViewController, context _: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    class Coordinator: NSObject, UIFontPickerViewControllerDelegate {
        func fontPickerViewControllerDidPickFont(_ viewController: UIFontPickerViewController) {
            guard let descriptor = viewController.selectedFontDescriptor else { return }
            let font = UIFont(descriptor: descriptor, size: Theme.shared!.currentBaseFontSize)
            Theme.shared!.chosenFont = font
            viewController.dismiss(animated: true, completion: nil)
        }
    }
}

// 颜色选择器
struct ColorPickerView: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var selectedColor: Color
    var onColorSelected: (Color) -> Void

    var body: some View {
        NavigationStack {
            VStack {
                ColorPicker("选择颜色", selection: $selectedColor)
                    .padding()
            }
            .navigationTitle("自定义颜色")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("确定") {
                        onColorSelected(selectedColor)
                        dismiss()
                    }
                }
            }
        }
    }
}
