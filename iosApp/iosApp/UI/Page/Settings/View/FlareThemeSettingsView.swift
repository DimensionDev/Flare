import SwiftUI
import shared

struct FlareThemeSettingsView: View {
    @Binding var settings: AppearanceSettings
    @ObservedObject private var themeProvider = FlareThemeProvider.shared
    @State private var selectedTheme: FlareTheme
    @Environment(\.presentationMode) var presentationMode
    
    init(settings: Binding<AppearanceSettings>) {
        self._settings = settings
        self._selectedTheme = State(initialValue: FlareThemeProvider.shared.flareTheme)
    }
    
    var body: some View {
        NavigationView {
            List {
                Section(header: Text("主题").font(.headline)) {
                    ForEach(FlareTheme.allCases) { theme in
                        Button {
                            selectedTheme = theme
                            withAnimation {
                                themeProvider.flareTheme = theme
                                settings.theme = theme.rawValue
                            }
                        } label: {
                            HStack {
                                Image(systemName: theme.icon)
                                    .foregroundColor(themeProvider.colorSet.accent)
                                Text(theme.displayName)
                                    .foregroundColor(themeProvider.colorSet.label)
                                Spacer()
                                if selectedTheme == theme {
                                    Image(systemName: "checkmark")
                                        .foregroundColor(themeProvider.colorSet.accent)
                                }
                            }
                        }
                    }
                }
            }
            .listStyle(InsetGroupedListStyle())
            .navigationTitle("主题设置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("完成") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
            .background(themeProvider.colorSet.background)
        }
        .background(themeProvider.colorSet.background)
        .onAppear {
            selectedTheme = themeProvider.flareTheme
        }
    }
} 