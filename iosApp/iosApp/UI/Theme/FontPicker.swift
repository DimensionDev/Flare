import SwiftUI

struct FontPicker: View {
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        NavigationStack {
            UIFontPickerRepresentable { fontDescriptor in
                if let descriptor = fontDescriptor {
                    let font = UIFont(descriptor: descriptor, size: Theme.shared!.currentBaseFontSize)
                    Theme.shared!.chosenFont = font
                }
                dismiss()
            }
            .navigationTitle("选择字体")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") {
                        dismiss()
                    }
                }
            }
        }
    }
}

struct UIFontPickerRepresentable: UIViewControllerRepresentable {
    var onFontPicked: (UIFontDescriptor?) -> Void
    
    func makeUIViewController(context: Context) -> UIFontPickerViewController {
        let fontPicker = UIFontPickerViewController()
        fontPicker.delegate = context.coordinator
        return fontPicker
    }
    
    func updateUIViewController(_ uiViewController: UIFontPickerViewController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(onFontPicked: onFontPicked)
    }
    
    class Coordinator: NSObject, UIFontPickerViewControllerDelegate {
        var onFontPicked: (UIFontDescriptor?) -> Void
        
        init(onFontPicked: @escaping (UIFontDescriptor?) -> Void) {
            self.onFontPicked = onFontPicked
        }
        
        func fontPickerViewControllerDidPickFont(_ viewController: UIFontPickerViewController) {
            onFontPicked(viewController.selectedFontDescriptor)
        }
        
        func fontPickerViewControllerDidCancel(_ viewController: UIFontPickerViewController) {
            onFontPicked(nil)
        }
    }
} 