import AVKit
import CoreMedia
import ExyteChat
import Kingfisher
import ObjectiveC
import shared
import SwiftUI

enum DMMessageMenuActionAction: MessageMenuAction {
    case copy, reply, edit, delete, print

    func title() -> String {
        switch self {
        case .copy:
            "Copy"
        case .reply:
            "Reply"
        case .edit:
            "Edit"
        case .delete:
            "Delete"
        case .print:
            "Print"
        }
    }

    func icon() -> Image {
        switch self {
        case .copy:
            Image(systemName: "doc.on.doc")
        case .reply:
            Image(systemName: "arrowshape.turn.up.left")
        case .edit:
            Image(systemName: "square.and.pencil")
        case .delete:
            Image(systemName: "xmark.bin")
        case .print:
            Image(systemName: "printer")
        }
    }

    static func menuItems(for message: ExyteChat.Message) -> [DMMessageMenuActionAction] {
        if message.user.isCurrentUser {
            [.copy, .delete]
        } else {
            [.reply, .copy, .delete]
        }
    }
}
