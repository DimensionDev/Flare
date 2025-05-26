import shared
import SwiftUI

struct TabItemsViewSwiftUI: View {
    @Binding var selection: String
    let items: [FLTabItem]
    @Namespace private var tabNamespace
    @State private var scrollPosition: String?

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            ScrollViewReader { proxy in
                HStack(spacing: 20) {
                    ForEach(items, id: \.key) { item in
                        VStack(spacing: 4) {
                            tabItemView(for: item)
                                .id(item.key)
                                .onTapGesture {
                                    withAnimation(.spring()) {
                                        selection = item.key
                                        scrollPosition = item.key
                                    }
                                }

                            if selection == item.key {
                                Rectangle()
                                    .fill(Color.blue)
                                    .frame(height: 2)
                                    .matchedGeometryEffect(id: "activeTab", in: tabNamespace)
                            } else {
                                Rectangle()
                                    .fill(Color.clear)
                                    .frame(height: 2)
                            }
                        }
                    }
                }
                .scrollTargetLayout()
                .onChange(of: selection) { _, newValue in
                    withAnimation(.spring()) {
                        proxy.scrollTo(newValue, anchor: .center)
                    }
                }
            }
        }
        .scrollTargetBehavior(.viewAligned)
        .scrollPosition(id: $scrollPosition)
    }

    @ViewBuilder
    private func tabItemView(for item: FLTabItem) -> some View {
        switch item.metaData.title {
        case let .text(title):
            if containsEmoji(title) {
                let components = splitTextAndEmoji(title)
                HStack(spacing: 2) {
                    ForEach(components.indices, id: \.self) { index in
                        let component = components[index]
                        if component.isEmoji {
//                            EmojiText(
//                                text: component.text,
//                                color: selection == item.key ? FColors.Text.swiftUIPrimary  : FColors.Text.swiftUISecondary
//                            )
                        } else {
                            Text(component.text)
                                .font(.system(size: 14))
//                                .foregroundColor(selection == item.key ? FColors.Text.swiftUIPrimary  : FColors.Text.swiftUISecondary)
                                .fontWeight(selection == item.key ? .medium : .regular)
                        }
                    }
                }
            } else {
                Text(title)
                    .font(.system(size: 14))
//                    .foregroundColor(selection == item.key ? FColors.Text.swiftUIPrimary  : FColors.Text.swiftUISecondary)
                    .fontWeight(selection == item.key ? .medium : .regular)
            }
        case let .localized(key):
            let title = NSLocalizedString(key, comment: "")
            if containsEmoji(title) {
                let components = splitTextAndEmoji(title)
                HStack(spacing: 2) {
                    ForEach(components.indices, id: \.self) { index in
                        let component = components[index]
                        if component.isEmoji {
//                            EmojiText(
//                                text: component.text,
//                                color: selection == item.key ? FColors.Text.swiftUIPrimary  : FColors.Text.swiftUISecondary
//                            )
                        } else {
                            Text(component.text)
                                .font(.system(size: 14))
//                                .foregroundColor(selection == item.key ? FColors.Text.swiftUIPrimary  : FColors.Text.swiftUISecondary)
                                .fontWeight(selection == item.key ? .medium : .regular)
                        }
                    }
                }
            } else {
                Text(title)
                    .font(.system(size: 14))
//                    .foregroundColor(selection == item.key ? FColors.Text.swiftUIPrimary  : FColors.Text.swiftUISecondary)
                    .fontWeight(selection == item.key ? .medium : .regular)
            }
        }
    }

    private func containsEmoji(_ text: String) -> Bool {
        for scalar in text.unicodeScalars {
            if scalar.properties.isEmoji {
                return true
            }
        }
        return false
    }

    private func splitTextAndEmoji(_ text: String) -> [TextComponent] {
        var components: [TextComponent] = []
        var currentComponent = ""
        var currentIsEmoji = false

        for character in text {
            let isEmoji = character.unicodeScalars.first?.properties.isEmoji ?? false

            if currentComponent.isEmpty {
                currentComponent.append(character)
                currentIsEmoji = isEmoji
            } else if isEmoji == currentIsEmoji {
                currentComponent.append(character)
            } else {
                components.append(TextComponent(text: currentComponent, isEmoji: currentIsEmoji))
                currentComponent = String(character)
                currentIsEmoji = isEmoji
            }
        }

        if !currentComponent.isEmpty {
            components.append(TextComponent(text: currentComponent, isEmoji: currentIsEmoji))
        }

        return components
    }
}

struct TextComponent {
    let text: String
    let isEmoji: Bool
}

extension UnicodeScalar {
    var isEmoji: Bool {
        properties.isEmoji
    }
}

extension Character {
    var isEmoji: Bool {
        for scalar in unicodeScalars {
            if scalar.isEmoji {
                return true
            }
        }
        return false
    }
}
