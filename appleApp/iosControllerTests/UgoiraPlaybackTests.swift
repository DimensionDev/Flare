import XCTest

@testable import FlareAppleUI

final class UgoiraPlaybackTests: XCTestCase {
    func testFrameDelayPreservesTimingAndCatchesUpAcrossLoopBoundaries() {
        // The variable-duration sequence is 40, 80, 100 ms (220 ms per loop).
        XCTAssertEqual(ugoiraFrameDelay(frameEnd: 0.12, duration: 0.22, selectedAt: 0.06, now: 0.07), 0.05, accuracy: 0.000_001)
        XCTAssertEqual(ugoiraFrameDelay(frameEnd: 0.22, duration: 0.22, selectedAt: 0.12, now: 0.13), 0.09, accuracy: 0.000_001)
        XCTAssertEqual(ugoiraFrameDelay(frameEnd: 0.22, duration: 0.22, selectedAt: 0.34, now: 0.35), 0.09, accuracy: 0.000_001)
        XCTAssertEqual(ugoiraFrameDelay(frameEnd: 0.22, duration: 0.22, selectedAt: 0.12, now: 0.23), 0.001, accuracy: 0.000_001)
        XCTAssertEqual(ugoiraFrameDelay(frameEnd: 0.22, duration: 0.22, selectedAt: 0.12, now: 0.68), 0.001, accuracy: 0.000_001)
    }
}
