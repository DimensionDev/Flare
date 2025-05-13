import SwiftUI

// 主题预览视图，用于在设置中展示主题样式
struct ThemePreviewView: View {
    var colorSet: ColorSetName
    var isSelected: Bool

    var body: some View {
        VStack(spacing: 4) {
            // 主题预览内容
            ZStack(alignment: .topLeading) {
                // 背景
                RoundedRectangle(cornerRadius: 12)
                    .fill(colorSet.set.background)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(isSelected ? colorSet.set.accent : .clear, lineWidth: 2)
                    )

                // 内容预览
                VStack(alignment: .leading, spacing: 8) {
                    // 标题栏
                    HStack {
                        Circle()
                            .fill(colorSet.set.accent)
                            .frame(width: 18, height: 18)
                        Text(colorSet.localizedName)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(colorSet.set.label)
                        Spacer()
                    }
                    .padding(.top, 10)
                    .padding(.horizontal, 10)

                    // 内容预览
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 6) {
                            Circle()
                                .fill(colorSet.set.secondaryBackground)
                                .frame(width: 30, height: 30)

                            VStack(alignment: .leading, spacing: 4) {
                                Text("文本标题")
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(colorSet.set.label)
                                Text("文本描述")
                                    .font(.system(size: 10))
                                    .foregroundColor(colorSet.set.secondaryLabel)
                            }
                        }

                        Rectangle()
                            .fill(colorSet.set.secondaryBackground)
                            .frame(height: 40)
                            .cornerRadius(6)
                    }
                    .padding(.horizontal, 10)

                    // 底部栏
                    HStack(spacing: 12) {
                        Image(systemName: "bubble.left")
                            .font(.system(size: 12))
                            .foregroundColor(colorSet.set.secondaryLabel)

                        Image(systemName: "arrow.2.squarepath")
                            .font(.system(size: 12))
                            .foregroundColor(colorSet.set.secondaryLabel)

                        Image(systemName: "heart")
                            .font(.system(size: 12))
                            .foregroundColor(colorSet.set.secondaryLabel)

                        Image(systemName: "square.and.arrow.up")
                            .font(.system(size: 12))
                            .foregroundColor(colorSet.set.secondaryLabel)

                        Spacer()
                    }
                    .padding(.horizontal, 10)
                    .padding(.bottom, 10)
                }
            }
            .frame(height: 140)

            // 主题名称
            Text(colorSet.localizedName)
                .font(.system(size: 12))
                .foregroundColor(.primary)
        }
    }
}

// 用于设置页面的主题选择网格
struct ThemeSelectionGrid: View {
    @Binding var selectedTheme: ColorSetName
    let columns = Array(repeating: GridItem(.flexible(), spacing: 12), count: 2)

    var body: some View {
        LazyVGrid(columns: columns, spacing: 16) {
            ForEach(ColorSetName.allCases) { theme in
                Button {
                    withAnimation {
                        selectedTheme = theme
                        guard let sharedTheme = Theme.shared else { return }
                        sharedTheme.applySet(theme)
                        sharedTheme.applyGlobalUIElements()
                    }
                } label: {
                    ThemePreviewView(colorSet: theme, isSelected: selectedTheme == theme)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal)
    }
}

#Preview {
    VStack {
        ThemePreviewView(colorSet: .flareLight, isSelected: true)
            .frame(width: 160)
        ThemePreviewView(colorSet: .flareDark, isSelected: false)
            .frame(width: 160)
    }
    .padding()
}
