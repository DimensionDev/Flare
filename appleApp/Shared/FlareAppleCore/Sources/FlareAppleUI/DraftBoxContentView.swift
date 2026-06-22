import AppleFontAwesome
import FlareAppleCore
@preconcurrency import KotlinSharedUI
import SwiftUI

#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

public enum DraftBoxContentRowMode {
    case compact
    case regular
}

public struct DraftBoxContentView: View {
    @StateObject private var presenter = KotlinPresenter(presenter: DraftBoxPresenter())

    private let rowMode: DraftBoxContentRowMode
    private let showsEditAction: Bool
    private let onEditDraft: ((String) -> Void)?

    public init(
        rowMode: DraftBoxContentRowMode = .regular,
        showsEditAction: Bool = true,
        onEditDraft: ((String) -> Void)? = nil
    ) {
        self.rowMode = rowMode
        self.showsEditAction = showsEditAction
        self.onEditDraft = onEditDraft
    }

    public var body: some View {
        Group {
            if presenter.state.items.isEmpty {
                ContentUnavailableView {
                    Label {
                        Text("draft_box_empty_title", bundle: FlareAppleUILocalization.bundle)
                    } icon: {
                        Image(fontAwesome: .inbox)
                    }
                } description: {
                    Text("draft_box_empty_description", bundle: FlareAppleUILocalization.bundle)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(presenter.state.items, id: \.groupId) { draft in
                        DraftBoxContentRow(
                            draft: draft,
                            rowMode: rowMode,
                            showsEditAction: showsEditAction,
                            onRetry: {
                                presenter.state.retry(groupId: draft.groupId)
                            },
                            onSend: {
                                presenter.state.send(groupId: draft.groupId)
                            },
                            onDelete: {
                                presenter.state.delete(groupId: draft.groupId)
                            },
                            onEdit: onEditDraft.map { action in
                                { action(draft.groupId) }
                            }
                        )
                    }
                }
                .modifier(DraftBoxContentListStyle())
                .animation(.snappy, value: presenter.state.items.count)
            }
        }
        .navigationTitle(Text("draft_box_title", bundle: FlareAppleUILocalization.bundle))
    }
}

private struct DraftBoxContentRow: View {
    let draft: UiDraft
    let rowMode: DraftBoxContentRowMode
    let showsEditAction: Bool
    let onRetry: () -> Void
    let onSend: () -> Void
    let onDelete: () -> Void
    let onEdit: (() -> Void)?

    var body: some View {
        Group {
            if let onEdit {
                Button(action: onEdit) {
                    content
                }
                .buttonStyle(.plain)
                .disabled(draft.status == .sending)
            } else {
                content
            }
        }
        .contextMenu {
            DraftBoxActionItems(
                draft: draft,
                showsEditAction: showsEditAction,
                onSend: onSend,
                onRetry: onRetry,
                onDelete: onDelete,
                onEdit: onEdit
            )
        }
    }

    private var content: some View {
        HStack(alignment: .top, spacing: rowMode == .compact ? 10 : 12) {
            if rowMode == .compact || draft.status != .draft {
                DraftStatusIcon(status: draft.status)
                    .padding(.top, 2)
            }

            VStack(alignment: .leading, spacing: rowMode == .compact ? 8 : 10) {
                if !draft.accounts.isEmpty {
                    DraftAccountsStrip(accounts: Array(draft.accounts))
                }

                VStack(alignment: .leading, spacing: rowMode == .compact ? 4 : 6) {
                    if let spoilerText = draft.data.spoilerText?.nonEmpty {
                        Text(spoilerText)
                            .font(rowMode == .compact ? .caption : .subheadline)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }

                    Text(draft.previewText)
                        .font(.body)
                        .foregroundStyle(.primary)
                        .multilineTextAlignment(.leading)
                        .lineLimit(2)
                }

                if rowMode == .regular, !draft.medias.isEmpty {
                    ScrollView(.horizontal) {
                        HStack(spacing: 8) {
                            ForEach(Array(draft.medias.prefix(4).enumerated()), id: \.offset) { _, media in
                                DraftMediaThumbnail(media: media)
                            }
                        }
                    }
                    .scrollIndicators(.hidden)
                }

                HStack(spacing: 8) {
                    if rowMode == .compact {
                        DraftStatusText(status: draft.status)
                    }

                    DateTimeText(data: draft.updatedAt, fullTime: true)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)

                    if !draft.medias.isEmpty {
                        Text(attachmentCountText(draft.medias.count))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            if draft.status != .sending {
                Menu {
                    DraftBoxActionItems(
                        draft: draft,
                        showsEditAction: showsEditAction,
                        onSend: onSend,
                        onRetry: onRetry,
                        onDelete: onDelete,
                        onEdit: onEdit
                    )
                } label: {
                    Image(fontAwesome: .ellipsisVertical)
                        .foregroundStyle(.secondary)
                        .frame(width: rowMode == .compact ? 24 : 28, height: rowMode == .compact ? 24 : 28)
                }
                .modifier(DraftBoxMenuButtonStyle())
            }
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle())
        .opacity(draft.status == .sending ? 0.62 : 1)
    }
}

private struct DraftBoxActionItems: View {
    let draft: UiDraft
    let showsEditAction: Bool
    let onSend: () -> Void
    let onRetry: () -> Void
    let onDelete: () -> Void
    let onEdit: (() -> Void)?

    var body: some View {
        if draft.status == .draft {
            Button(action: onSend) {
                Label {
                    Text("draft_box_send", bundle: FlareAppleUILocalization.bundle)
                } icon: {
                    Image(systemName: "paperplane.fill")
                }
            }
        }

        if draft.status == .failed {
            Button(action: onRetry) {
                Label {
                    Text("draft_box_retry", bundle: FlareAppleUILocalization.bundle)
                } icon: {
                    Image(systemName: "arrow.clockwise")
                }
            }
        }

        if showsEditAction, let onEdit {
            Button(action: onEdit) {
                Label {
                    Text("draft_box_edit", bundle: FlareAppleUILocalization.bundle)
                } icon: {
                    Image(systemName: "square.and.pencil")
                }
            }
        }

        Button(role: .destructive, action: onDelete) {
            Label {
                Text("delete_button", bundle: FlareAppleUILocalization.bundle)
            } icon: {
                Image(systemName: "trash")
            }
        }
        .disabled(draft.status == .sending)
    }
}

private struct DraftAccountsStrip: View {
    let accounts: [UiDraftAccount]

    var body: some View {
        HStack(spacing: 6) {
            ForEach(Array(accounts.prefix(6).enumerated()), id: \.offset) { _, account in
                if let avatar = account.avatar {
                    NetworkImage(data: avatar.url, customHeader: avatar.customHeaders)
                        .frame(width: 22, height: 22)
                        .clipShape(Circle())
                } else {
                    Image(systemName: "person.crop.circle.fill")
                        .resizable()
                        .scaledToFit()
                        .foregroundStyle(.secondary)
                        .frame(width: 22, height: 22)
                }
            }

            if accounts.count > 6 {
                Text("+\(accounts.count - 6)")
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 6)
                    .frame(height: 22)
                    .background(.quaternary, in: Capsule())
            }
        }
    }
}

private struct DraftStatusIcon: View {
    let status: UiDraftStatus

    var body: some View {
        Image(systemName: status.symbolName)
            .foregroundStyle(status.tint)
            .frame(width: 18, height: 18)
    }
}

private struct DraftStatusText: View {
    let status: UiDraftStatus

    var body: some View {
        Text(FlareAppleUILocalization.string(status.titleKey))
            .font(.caption.weight(.medium))
            .foregroundStyle(status.tint)
    }
}

private struct DraftMediaThumbnail: View {
    let media: UiDraftMedia

    var body: some View {
        Group {
            switch media.type {
            case .image:
                platformImage
            case .video:
                placeholder(icon: "video")
            case .other:
                placeholder(icon: "doc")
            }
        }
        .frame(width: 60, height: 60)
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    @ViewBuilder
    private var platformImage: some View {
        #if os(iOS)
        if let image = UIImage(contentsOfFile: media.cachePath) {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
        } else {
            placeholder(icon: "photo")
        }
        #elseif os(macOS)
        if let image = NSImage(contentsOfFile: media.cachePath) {
            Image(nsImage: image)
                .resizable()
                .scaledToFill()
        } else {
            placeholder(icon: "photo")
        }
        #else
        placeholder(icon: "photo")
        #endif
    }

    @ViewBuilder
    private func placeholder(icon: String) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(Color.flareSecondarySystemGroupedBackground)
            Image(systemName: icon)
                .foregroundStyle(.secondary)
        }
    }
}

private struct DraftBoxContentListStyle: ViewModifier {
    @ViewBuilder
    func body(content: Content) -> some View {
        #if os(macOS)
        content.listStyle(.inset)
        #else
        content
        #endif
    }
}

private struct DraftBoxMenuButtonStyle: ViewModifier {
    @ViewBuilder
    func body(content: Content) -> some View {
        #if os(macOS)
        content
            .menuStyle(.borderlessButton)
            .menuIndicator(.hidden)
            .fixedSize()
        #else
        content
            .buttonStyle(.plain)
        #endif
    }
}

private func attachmentCountText(_ count: Int) -> String {
    FlareAppleUILocalization.string(
        "draft_box_attachment_count",
        fallback: "%d attachments",
        arguments: [count]
    )
}

private extension UiDraft {
    var previewText: String {
        if let content = data.content.nonEmpty {
            return content
        }
        if let spoilerText = data.spoilerText?.nonEmpty {
            return spoilerText
        }
        if !medias.isEmpty {
            return attachmentCountText(medias.count)
        }
        return FlareAppleUILocalization.string("draft_box_empty_draft", fallback: "Empty draft")
    }
}

private extension UiDraftStatus {
    var titleKey: String {
        switch self {
        case .sending:
            "draft_box_status_sending"
        case .failed:
            "draft_box_status_failed"
        default:
            "draft_box_status_draft"
        }
    }

    var symbolName: String {
        switch self {
        case .sending:
            "arrow.up.circle.fill"
        case .failed:
            "exclamationmark.triangle.fill"
        default:
            "doc.text.fill"
        }
    }

    var tint: Color {
        switch self {
        case .sending:
            .green
        case .failed:
            .red
        default:
            .secondary
        }
    }
}

private extension String {
    var nonEmpty: String? {
        let value = trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }
}
