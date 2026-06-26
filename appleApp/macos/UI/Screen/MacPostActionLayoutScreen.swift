import FlareAppleCore
import FlareAppleUI
import KotlinSharedUI
import SwiftUI

struct MacPostActionLayoutScreen: View {
    @StateObject private var presenter = KotlinPresenter(presenter: SettingsPresenter())
    @Environment(\.dismiss) private var dismiss
    @Environment(\.timelineAppearance) private var timelineAppearance
    @State private var config = PostActionLayoutConfig.companion.Default

    private let placements = PostActionLayoutSupport.placements

    var body: some View {
        HStack(spacing: 0) {
            previewPane
                .frame(width: 390)

            Divider()

            editorPane
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .frame(minWidth: 860, minHeight: 560)
        .navigationTitle("Post actions")
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("done") {
                    dismiss()
                }
            }
        }
        .onAppear {
            config = normalizedTimelineConfig
        }
        .onChange(of: persistedSignature) { _, _ in
            config = normalizedTimelineConfig
        }
    }

    private var previewPane: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                Text("post_action_layout_preview_title")
                    .font(.headline)
                Text("post_action_layout_preview_description")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            ScrollView {
                PostActionLayoutPreview(config: config)
                    .padding(.vertical, 8)
            }
        }
        .padding(24)
        .frame(maxHeight: .infinity, alignment: .top)
    }

    private var editorPane: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Toggle(isOn: Binding(get: {
                    config.enabled
                }, set: { enabled in
                    updateConfig(
                        PostActionLayoutSupport.withEnabled(
                            config: config,
                            enabled: enabled
                        )
                    )
                })) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Customize actions")
                        Text("post_action_layout_customize_description")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .toggleStyle(.switch)

                Divider()

                VStack(alignment: .leading, spacing: 20) {
                    ForEach(Array(placements.enumerated()), id: \.element.name) { index, placement in
                        actionSection(placement)

                        if index < placements.count - 1 {
                            Divider()
                        }
                    }
                }
                .disabled(!config.enabled)
                .opacity(config.enabled ? 1 : 0.45)
            }
            .padding(24)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func actionSection(_ placement: PostActionPlacement) -> some View {
        let families = families(for: placement)

        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(placement.postActionLayoutTitleKey)
                    .font(.headline)

                Text(verbatim: "\(families.count)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(.quaternary, in: Capsule())

                Spacer()
            }

            if families.isEmpty {
                Text("No actions")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, minHeight: 36, alignment: .leading)
            } else {
                VStack(spacing: 0) {
                    ForEach(Array(families.enumerated()), id: \.element.name) { index, family in
                        actionRow(
                            family: family,
                            placement: placement,
                            index: index,
                            totalCount: families.count
                        )

                        if index < families.count - 1 {
                            Divider()
                                .padding(.leading, 34)
                        }
                    }
                }
            }
        }
    }

    private func actionRow(
        family: PostActionFamily,
        placement: PostActionPlacement,
        index: Int,
        totalCount: Int
    ) -> some View {
        HStack(spacing: 12) {
            Image(fontAwesome: family.postActionLayoutIcon)
                .frame(width: 22, height: 22)
                .foregroundStyle(.primary)

            Text(family.postActionLayoutTitleKey)
                .frame(minWidth: 120, alignment: .leading)

            Spacer(minLength: 16)

            Picker(selection: Binding(get: {
                placement
            }, set: { target in
                guard target.name != placement.name else {
                    return
                }
                updateConfig(
                    PostActionLayoutSupport.moveTo(
                        config: config,
                        family: family,
                        placement: target
                    )
                )
            })) {
                ForEach(placements, id: \.name) { target in
                    Text(target.postActionLayoutTitleKey)
                        .tag(target)
                }
            } label: {
                Text("post_action_layout_placement")
            }
            .labelsHidden()
            .frame(width: 150)

            HStack(spacing: 4) {
                Button {
                    move(family: family, offset: -1)
                } label: {
                    Image(systemName: "chevron.up")
                        .frame(width: 20, height: 20)
                }
                .buttonStyle(.borderless)
                .disabled(index == 0)
                .help(Text("macos_action_move_up"))

                Button {
                    move(family: family, offset: 1)
                } label: {
                    Image(systemName: "chevron.down")
                        .frame(width: 20, height: 20)
                }
                .buttonStyle(.borderless)
                .disabled(index >= totalCount - 1)
                .help(Text("macos_action_move_down"))
            }
        }
        .padding(.vertical, 7)
        .frame(minHeight: 36)
    }

    private var persistedSignature: String {
        PostActionLayoutSupport.signature(config: timelineAppearance.postActionLayout)
    }

    private var normalizedTimelineConfig: PostActionLayoutConfig {
        PostActionLayoutSupport.normalizedForEdit(config: timelineAppearance.postActionLayout)
    }

    private func families(for placement: PostActionPlacement) -> [PostActionFamily] {
        PostActionLayoutSupport.families(
            config: config,
            placement: placement
        )
    }

    private func updateConfig(_ value: PostActionLayoutConfig) {
        let normalized = PostActionLayoutSupport.normalizedForEdit(config: value)
        config = normalized
        presenter.state.updatePostActionLayout(value: normalized)
    }

    private func move(family: PostActionFamily, offset: Int32) {
        updateConfig(
            PostActionLayoutSupport.moveBy(
                config: config,
                family: family,
                offset: offset
            )
        )
    }
}
