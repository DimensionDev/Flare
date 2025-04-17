import SwiftUI
import UIKit // Required for UIFont calculations

// MARK: - AttributedString Extensions (Mimicking ExyteChat Helpers)
// NOTE: These are simplified versions or might need adjustments based on exact ExyteChat implementation.

extension AttributedString {
     // Basic height calculation (Helper for numberOfLines approximation)
     // Note: width, lastLineWidth, numberOfLines, urls were removed due to potential conflicts/redeclarations
     // We might need to find alternative ways or ensure they are not defined elsewhere if errors persist.
     func height(withConstrainedWidth width: CGFloat, font: UIFont) -> CGFloat {
         let attributedString = NSAttributedString(self)
         let constraintRect = CGSize(width: width, height: .greatestFiniteMagnitude)
         let boundingBox = attributedString.boundingRect(with: constraintRect, options: .usesLineFragmentOrigin, context: nil)
         return ceil(boundingBox.height)
     }
}

// MARK: - String Extension (Mimicking ExyteChat Helpers)
// Mimics ExyteChat helpers for String
extension String {
     // Basic styler placeholder - just creates an AttributedString
     func styled(using styler: (String) -> AttributedString) -> AttributedString {
         return styler(self)
     }
 }


// MARK: - View Extensions (Mimicking ExyteChat Helpers)

// Gets the size of a view using GeometryReader
struct SizeGetter: ViewModifier {
    @Binding var size: CGSize

    func body(content: Content) -> some View {
        content
            .background(
                GeometryReader { proxy in
                    Color.clear
                        .preference(key: SizePreferenceKey.self, value: proxy.size)
                }
            )
            .onPreferenceChange(SizePreferenceKey.self) { newSize in
                // Avoid infinite loop by checking if size actually changed
                 if size != newSize { // Prevent feedback loop
                    self.size = newSize
                 }
            }
    }
}

struct SizePreferenceKey: PreferenceKey {
    static var defaultValue: CGSize = .zero
    static func reduce(value: inout CGSize, nextValue: () -> CGSize) {
        value = nextValue()
    }
}

extension View {
    func sizeGetter(_ size: Binding<CGSize>) -> some View {
        self.modifier(SizeGetter(size: size))
    }

    // ApplyIf modifier
    // Conditionally applies a modifier
    @ViewBuilder
    func applyIf<T: View>(_ condition: Bool, transform: (Self) -> T) -> some View {
        if condition {
            transform(self)
        } else {
            self
        }
    }

    // viewSize modifier (Simplified version)
    // Sets both width and height
     func viewSize(_ size: CGFloat) -> some View {
         self.frame(width: size, height: size)
     }
}