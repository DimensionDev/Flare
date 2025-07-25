import shared
import SwiftUI

struct AppearanceUIScreen: View {
    @Environment(\.appSettings) private var appSettings
    @Environment(FlareTheme.self) private var theme
    @State private var presenter = AppearancePresenter()
    @State private var localValues = DisplaySettingsLocalValues()
    @State private var isFontSelectorPresented = false

    @State private var isThemeAutoSectionExpanded = true
    @State private var isFontSectionExpanded = true

    var body: some View {
        ObservePresenter(presenter: presenter) { state in
            List {
                if case let .success(success) = onEnum(of: state.sampleStatus) {
                    VStack {
                        StatusItemView(
                            data: success.data,
                            detailKey: nil
                        ).listRowInsets(EdgeInsets())
                    }.allowsHitTesting(false)
                }

                // Theme部分
                Section {
                    themeAutoSection
                }.listRowBackground(theme.primaryBackgroundColor)
                // Font部分
                Section { fontSection }.listRowBackground(theme.primaryBackgroundColor)

                // 渲染引擎选择部分
                Section("Text Render Engine") {
                    Picker(selection: Binding(get: {
                        appSettings.appearanceSettings.renderEngine
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.renderEngine, to: value))
                    }), content: {
                        ForEach(RenderEngine.allCases, id: \.self) { engine in
                            Text(engine.title).tag(engine)
                        }
                    }, label: {
                        Text("Text Render Engine")
                    })
                }.listRowBackground(theme.primaryBackgroundColor)

                Section("settings_appearance_generic") {
                    // 注释原主题选择
                    /*
                     Picker(selection: Binding(get: {
                         appSettings.appearanceSettings.theme
                     }, set: { value in
                         appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.theme, to: value))
                     }), content: {
                         Text("settings_appearance_theme_auto")
                             .tag(Theme.auto)
                         Text("settings_appearance_theme_dark")
                             .tag(Theme.dark)
                         Text("settings_appearance_theme_light")
                             .tag(Theme.light)
                     }, label: {
                         Text("settings_appearance_theme_color")
                         Text("settings_appearance_theme_color_description")
                     })
                     */

                    // 保留其他设置
                    Picker(selection: Binding(get: {
                        appSettings.appearanceSettings.avatarShape
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.avatarShape, to: value))
                    }), content: {
                        Text("settings_appearance_avatar_shape_round")
                            .tag(AvatarShape.circle)
                        Text("settings_appearance_avatar_shape_square")
                            .tag(AvatarShape.square)
                    }, label: {
                        Text("settings_appearance_avatar_shape")
                        Text("settings_appearance_avatar_shape_description")
                    })

                    // 保留其他设置项
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.showActions
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showActions, to: value))
                    })) {
                        Text("settings_appearance_show_actions")
                        Text("settings_appearance_show_actions_description")
                    }
                    if appSettings.appearanceSettings.showActions {
                        Toggle(isOn: Binding(get: {
                            appSettings.appearanceSettings.showNumbers
                        }, set: { value in
                            appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showNumbers, to: value))
                        })) {
                            Text("settings_appearance_show_numbers")
                            Text("settings_appearance_show_numbers_description")
                        }
                    }
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.showLinkPreview
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showLinkPreview, to: value))
                    })) {
                        Text("settings_appearance_show_link_previews")
                        Text("settings_appearance_show_link_previews_description")
                    }
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.showMedia
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showMedia, to: value))
                    })) {
                        Text("settings_appearance_show_media")
                        Text("settings_appearance_show_media_description")
                    }
                    if appSettings.appearanceSettings.showMedia {
                        Toggle(isOn: Binding(get: {
                            appSettings.appearanceSettings.showSensitiveContent
                        }, set: { value in
                            appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.showSensitiveContent, to: value))
                        })) {
                            Text("settings_appearance_show_cw_img")
                            Text("settings_appearance_show_cw_img_description")
                        }
                    }
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.enableFullSwipePop
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.enableFullSwipePop, to: value))
                    })) {
                        Text("Full Swipe Back")
                        Text("Allow swiping back from anywhere on the screen")
                    }
                    Toggle(isOn: Binding(get: {
                        appSettings.appearanceSettings.hideScrollToTopButton
                    }, set: { value in
                        appSettings.update(newValue: appSettings.appearanceSettings.changing(path: \.hideScrollToTopButton, to: value))
                    })) {
                        Text("Hide Scroll to Top Button")
                        Text("Hide the floating scroll to top button")
                    }
                }.listRowBackground(theme.primaryBackgroundColor)
                    .buttonStyle(.plain)
            }
            .scrollContentBackground(.hidden)
            .background(theme.secondaryBackgroundColor)
            .navigationTitle("settings_appearance_title")
            .navigationBarTitleDisplayMode(.inline)
            .task(id: localValues.tintColor) {
                do { try await Task.sleep(for: .microseconds(500)) } catch {}
                theme.tintColor = localValues.tintColor
            }
            .task(id: localValues.primaryBackgroundColor) {
                do { try await Task.sleep(for: .microseconds(500)) } catch {}
                theme.primaryBackgroundColor = localValues.primaryBackgroundColor
            }
            .task(id: localValues.secondaryBackgroundColor) {
                do { try await Task.sleep(for: .microseconds(500)) } catch {}
                theme.secondaryBackgroundColor = localValues.secondaryBackgroundColor
            }
            .task(id: localValues.labelColor) {
                do { try await Task.sleep(for: .microseconds(500)) } catch {}
                theme.labelColor = localValues.labelColor
            }
            .task(id: localValues.lineSpacing) {
                do { try await Task.sleep(for: .microseconds(500)) } catch {}
                theme.lineSpacing = localValues.lineSpacing
            }
            .task(id: localValues.fontSizeScale) {
                do { try await Task.sleep(for: .microseconds(500)) } catch {}
                theme.fontSizeScale = localValues.fontSizeScale
            }
            #if os(macOS)
            .toggleStyle(.switch)
            .pickerStyle(.segmented)
            #endif
        }
    }

    private var themeSelectorButton: some View {
        Picker(selection: .init(get: {
            theme.selectedSet.rawValue
        }, set: { value in
            let themeSet = ColorSetName(rawValue: value) ?? ColorSetName.themeLight
            theme.selectedSet = themeSet

            theme.followSystemColorScheme = false

            theme.applySet(set: themeSet)
        }), content: {
            ForEach(availableColorsSets, id: \.id) { colorSet in
                Text(colorSet.light.name.rawValue)
                    .tag(colorSet.light.name.rawValue)
                Text(colorSet.dark.name.rawValue)
                    .tag(colorSet.dark.name.rawValue)
            }
        }, label: {
            Text("settings_appearance_theme_color")
        })
    }

    private var themeAutoSection: some View {
        DisclosureGroup("Auto Theme Settings", isExpanded: $isThemeAutoSectionExpanded) {
            Toggle("Auto Theme ", isOn: Binding(
                get: { theme.followSystemColorScheme },
                set: { theme.followSystemColorScheme = $0 }
            ))
            themeSelectorButton
            // Group {
            //     ColorPicker("Tint Color", selection: $localValues.tintColor)
            //     ColorPicker("Background", selection: $localValues.primaryBackgroundColor)
            //     ColorPicker("Secondary Background", selection: $localValues.secondaryBackgroundColor)
            //     ColorPicker("Text Color", selection: $localValues.labelColor)
            // }
            // .disabled(theme.followSystemColorScheme)
            // .opacity(theme.followSystemColorScheme ? 0.5 : 1.0)
            // .onChange(of: theme.selectedSet) { _, _ in
            //     localValues.tintColor = theme.tintColor
            //     localValues.primaryBackgroundColor = theme.primaryBackgroundColor
            //     localValues.secondaryBackgroundColor = theme.secondaryBackgroundColor
            //     localValues.labelColor = theme.labelColor
            // }
        }
    }

    private var fontSection: some View {
        DisclosureGroup("Font Settings", isExpanded: $isFontSectionExpanded) {
            Picker(
                "Font",
                selection: .init(
                    get: { () -> FlareTheme.FontState in
                        if theme.chosenFont?.fontName == "OpenDyslexic-Regular" {
                            return FlareTheme.FontState.openDyslexic
                        } else if theme.chosenFont?.fontName == "AtkinsonHyperlegible-Regular" {
                            return FlareTheme.FontState.hyperLegible
                        } else if theme.chosenFont?.fontName == ".AppleSystemUIFontRounded-Regular" {
                            return FlareTheme.FontState.SFRounded
                        }
                        return theme.chosenFontData != nil ? FlareTheme.FontState.custom : FlareTheme.FontState.system
                    },
                    set: { newValue in
                        switch newValue {
                        case .system:
                            theme.chosenFont = nil
                        case .openDyslexic:
                            theme.chosenFont = UIFont(name: "OpenDyslexic", size: 1)
                        case .hyperLegible:
                            theme.chosenFont = UIFont(name: "Atkinson Hyperlegible", size: 1)
                        case .SFRounded:
                            theme.chosenFont = UIFont.systemFont(ofSize: 1).rounded()
                        case .custom:
                            isFontSelectorPresented = true
                        }
                    }
                )
            ) {
                ForEach(FlareTheme.FontState.allCases, id: \.rawValue) { fontState in
                    if fontState == .custom {
                        if let chosenFontName = theme.chosenFont?.familyName {
                            Text("Custom: \(chosenFontName)")
                                .font(.caption)
                                .foregroundColor(.gray).tag(fontState)
                        } else {
                            Text("Custom")
                                .font(.caption)
                                .foregroundColor(.gray).tag(fontState)
                        }
                    } else {
                        Text(fontState.title).tag(fontState)
                    }
                }
            }
            .sheet(isPresented: $isFontSelectorPresented, content: { FontPicker() })

            VStack {
                Slider(value: $localValues.fontSizeScale, in: 0.5 ... 1.5, step: 0.1)
                Text("Font Size Scale: \(String(format: "%.1f", localValues.fontSizeScale))")
                    .font(.scaledBody)
            }.alignmentGuide(.listRowSeparatorLeading) { d in
                d[.leading]
            }

            VStack {
                Slider(value: $localValues.lineSpacing, in: 0.4 ... 10.0, step: 0.2)
                Text(
                    "Line Spacing: \(String(format: "%.1f", localValues.lineSpacing))"
                )
                .font(.scaledBody)
            }.alignmentGuide(.listRowSeparatorLeading) { d in
                d[.leading]
            }
        }
    }

    // private var themeSelectorButton: some View {
    //     // router.navigate(to: .messages(accountType: accountType))
    //     NavigationLink(destination: ThemePreviewView()) {
    //         HStack {
    //             Text("Theme")
    //             Spacer()
    //             Text(theme.selectedSet.rawValue)
    //         }
    //     }
    // }
}

// 辅助类，用于存储设置值
@MainActor
@Observable class DisplaySettingsLocalValues {
    var tintColor = FlareTheme.shared.tintColor
    var primaryBackgroundColor = FlareTheme.shared.primaryBackgroundColor
    var secondaryBackgroundColor = FlareTheme.shared.secondaryBackgroundColor
    var labelColor = FlareTheme.shared.labelColor
    var lineSpacing = FlareTheme.shared.lineSpacing
    var fontSizeScale = FlareTheme.shared.fontSizeScale
}
