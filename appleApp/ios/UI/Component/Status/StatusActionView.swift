import SwiftUI
import KotlinSharedUI
import SwiftUIBackports
import FlareAppleCore

#if canImport(UIKit)
import UIKit
#endif

// MARK: - Top-level container
// Hoists @ScaledMetric, @Environment reads to a single place
// instead of duplicating them in every child view instance.

public struct StatusActionsView: View {
    @Environment(\.timelineAppearance.postActionStyle) private var postActionStyle
    @Environment(\.timelineAppearance.showNumbers) private var showNumbers
    @Environment(\.timelineAppearance.postActionLayout) private var postActionLayout
    @Environment(\.openURL) private var openURL
    @ScaledMetric(relativeTo: .footnote) private var fontSize = 13
    private let data: [ActionMenu]
    private let useText: Bool
    private let allowSpacer: Bool
    private let applyPostActionLayout: Bool

    public init(data: [ActionMenu], useText: Bool, allowSpacer: Bool = true, applyPostActionLayout: Bool = true) {
        self.data = data
        self.useText = useText
        self.allowSpacer = allowSpacer
        self.applyPostActionLayout = applyPostActionLayout
    }

    public var body: some View {
        let actions = resolvedData
        if useText {
            ForEach(0..<actions.count, id: \.self) { index in
                StatusActionView(
                    data: actions[index],
                    useText: true,
                    isFixedWidth: false,
                    fontSize: fontSize,
                    showNumbers: showNumbers,
                    openURL: openURL
                )
            }
        } else {
            HStack {
                ForEach(0..<actions.count, id: \.self) { index in
                    let item = actions[index]
                    if (index == actions.count - 1 && postActionStyle == .leftAligned) ||
                        (postActionStyle == .rightAligned && index == 0) ||
                        (postActionStyle == .stretch && index != 0) {
                        if allowSpacer {
                            Spacer()
                        }
                    }
                    StatusActionView(
                        data: item,
                        useText: useText,
                        isFixedWidth: index != actions.count - 1,
                        fontSize: fontSize,
                        showNumbers: showNumbers,
                        openURL: openURL
                    )
                }
            }
            .backport
            .labelIconToTitleSpacing(4)
        }
    }

    private var resolvedData: [ActionMenu] {
        guard applyPostActionLayout else { return data }
        return castActionMenus(
            PostActionLayoutHelpers.shared.apply(
                actions: data,
                config: postActionLayout
            )
        )
    }
}

// MARK: - Single action (sealed class switch)
// No @Environment or @ScaledMetric — values passed from parent.

public struct StatusActionView: View {
    private let data: ActionMenu
    private let useText: Bool
    private let isFixedWidth: Bool
    private let fontSize: CGFloat
    private let showNumbers: Bool
    private let openURL: OpenURLAction

    public init(
        data: ActionMenu,
        useText: Bool,
        isFixedWidth: Bool,
        fontSize: CGFloat,
        showNumbers: Bool,
        openURL: OpenURLAction
    ) {
        self.data = data
        self.useText = useText
        self.isFixedWidth = isFixedWidth
        self.fontSize = fontSize
        self.showNumbers = showNumbers
        self.openURL = openURL
    }

    public var body: some View {
        switch onEnum(of: data) {
        case .item(let item):
            StatusActionItemView(
                data: item,
                useText: useText,
                isFixedWidth: isFixedWidth,
                fontSize: fontSize,
                showNumbers: showNumbers,
                openURL: openURL
            )
        case .group(let group):
            if useText {
                Divider()
                ForEach(0..<group.actions.count, id: \.self) { index in
                    StatusActionView(
                        data: group.actions[index],
                        useText: true,
                        isFixedWidth: false,
                        fontSize: fontSize,
                        showNumbers: showNumbers,
                        openURL: openURL
                    )
                }
                Divider()
            } else {
                Menu {
                    ForEach(0..<group.actions.count, id: \.self) { index in
                        StatusActionView(
                            data: group.actions[index],
                            useText: true,
                            isFixedWidth: false,
                            fontSize: fontSize,
                            showNumbers: showNumbers,
                            openURL: openURL
                        )
                    }
                } label: {
                    if let text = group.displayItem.count?.humanized, showNumbers {
                        Label {
                            Text(text)
                                .lineLimit(1)
                                .frame(minWidth: isFixedWidth ? fontSize * 2.5 : nil, alignment: .leading)
                        } icon: {
                            StatusActionIcon(icon: group.displayItem.icon)
                        }
                    } else {
                        StatusActionIcon(icon: group.displayItem.icon)
                            .frame(minWidth: fontSize * 1.5, minHeight: fontSize * 1.5)
                            .contentShape(Rectangle())
                    }
                }
                .optionalForegroundStyle(group.displayItem.color?.swiftColor)
                .buttonStyle(.plain)
            }
        case .divider:
            Divider()
        }
    }
}

// MARK: - Leaf action item (Button)
// Computes display text once to avoid nested _ConditionalContent branches.

public struct StatusActionItemView: View {
    private let data: ActionMenu.Item
    private let useText: Bool
    private let isFixedWidth: Bool
    private let fontSize: CGFloat
    private let showNumbers: Bool
    private let openURL: OpenURLAction

    public init(
        data: ActionMenu.Item,
        useText: Bool,
        isFixedWidth: Bool,
        fontSize: CGFloat,
        showNumbers: Bool,
        openURL: OpenURLAction
    ) {
        self.data = data
        self.useText = useText
        self.isFixedWidth = isFixedWidth
        self.fontSize = fontSize
        self.showNumbers = showNumbers
        self.openURL = openURL
    }

    private var resolvedText: Text? {
        if useText, let text = data.text?.resolvedString {
            return Text(text)
        } else if showNumbers, let count = data.count?.humanized {
            return Text(count)
        }
        return nil
    }

    private func triggerHapticFeedback() {
        #if os(iOS)
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.prepare()
        generator.impactOccurred()
        #endif
    }

    public var body: some View {
        Button(role: data.color?.role) {
            triggerHapticFeedback()
            data.onClicked(ClickContext(launcher: AppleUriLauncher(openUrl: openURL)))
        } label: {
            if let text = resolvedText {
                Label {
                    text.frame(minWidth: isFixedWidth ? fontSize * 2.5 : nil, alignment: .leading)
                } icon: {
                    StatusActionIcon(icon: data.icon)
                }
            } else {
                StatusActionIcon(icon: data.icon)
            }
        }
        .optionalForegroundStyle(data.color?.swiftColor)
        .buttonStyle(.plain)
    }
}

// MARK: - Helpers

private extension View {
    @ViewBuilder
    func optionalForegroundStyle(_ color: Color?) -> some View {
        if let color {
            self.foregroundStyle(color)
        } else {
            self
        }
    }
}

public extension ActionMenu.ItemColor {
    var swiftColor: Color? {
        switch self {
        case .red: return .red
        case .contentColor: return .primary
        case .primaryColor: return .accentColor
        default: return nil
        }
    }

    var role: ButtonRole? {
        switch self {
        case .red:
                .destructive
        case .primaryColor:
            #if os(iOS)
            if #available(iOS 26.0, *) {
                    .confirm
            } else {
                nil
            }
            #else
            nil
            #endif
        default:
            nil
        }
    }
}

public struct StatusActionIcon: View {
    private let icon: UiIcon?

    public init(icon: UiIcon?) {
        self.icon = icon
    }

    public var body: some View {
        if let icon = icon {
            icon.image
        }
    }
}

private func castActionMenus(_ value: Any) -> [ActionMenu] {
    if let actions = value as? [ActionMenu] {
        return actions
    }
    if let actions = value as? NSArray {
        return actions.cast(ActionMenu.self)
    }
    return []
}
