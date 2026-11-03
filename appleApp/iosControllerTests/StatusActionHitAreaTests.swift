import XCTest
import SwiftUI
import UIKit
import KotlinSharedUI
import FlareAppleUI

@MainActor
final class StatusActionHitAreaTests: XCTestCase {
    func testCompactActionsFillTheirAllocatedSlot() {
        let metrics = StatusActionLayoutMetrics(fontSize: 13, scale: 3)
        for count: Int64? in [nil, 0, 128] {
            let item = ActionMenu.Item(updateKey: "hit-area", icon: .reply, text: nil,
                                       count: count.map { UiNumber(value: $0) }, color: nil,
                                       clickEvent: ClickEventNoop.shared, actionFamily: nil, enabled: true)
            for action in [item as ActionMenu, ActionMenu.Group(displayItem: item, actions: [item])] {
                let host = UIHostingController(rootView:
                    StatusActionView(data: action, useText: false, isFixedWidth: false,
                                     metrics: metrics, showNumbers: true,
                                     openURL: OpenURLAction { _ in .handled })
                        .font(.system(size: metrics.fontSize))
                )
                host.safeAreaRegions = []
                let size = host.sizeThatFits(in: CGSize(width: 84, height: 50))
                XCTAssertEqual(size.width, 84, accuracy: 0.01,
                               "The label and its content shape must fill the slot: count=\(String(describing: count))")
                XCTAssertLessThan(size.height, 50)
            }
        }
    }

    func testCompactActionsKeepTheirNaturalSizeForAnUnlimitedProposal() {
        let item = ActionMenu.Item(updateKey: "natural", icon: .reply, text: nil, count: nil,
                                   color: nil, clickEvent: ClickEventNoop.shared, actionFamily: nil, enabled: true)
        let metrics = StatusActionLayoutMetrics(fontSize: 13, scale: 3)
        let host = UIHostingController(rootView:
            StatusActionView(data: item, useText: false, isFixedWidth: false,
                             metrics: metrics, showNumbers: true,
                             openURL: OpenURLAction { _ in .handled })
        )
        host.safeAreaRegions = []
        let size = host.sizeThatFits(in: CGSize(width: CGFloat.infinity, height: 50))
        XCTAssertEqual(size.width, metrics.size(titleWidth: nil, reservesNumberWidth: false).width)
        XCTAssertTrue(size.height.isFinite)
    }

    func testRowsWithoutSpacerKeepTheirNaturalWidth() {
        let item = ActionMenu.Item(updateKey: "compact-row", icon: .reply, text: nil, count: nil,
                                   color: nil, clickEvent: ClickEventNoop.shared, actionFamily: nil, enabled: true)
        let host = UIHostingController(rootView:
            StatusActionsView(data: [item], useText: false, allowSpacer: false, applyPostActionLayout: false)
        )
        host.safeAreaRegions = []
        XCTAssertEqual(host.sizeThatFits(in: CGSize(width: 362, height: 50)).width, 28, accuracy: 0.01)
    }
}
