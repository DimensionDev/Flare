import XCTest

final class FlareUIDemoNavigationTests: XCTestCase {
    @MainActor
    func testCatalogImageUsesExplicit64PointFrame() {
        continueAfterFailure = false

        let app = XCUIApplication()
        app.launch()

        let catalogImage = app.descendants(matching: .any)["demo-catalog-image"]
        XCTAssertTrue(catalogImage.waitForExistence(timeout: 5))
        XCTAssertEqual(catalogImage.frame.width, 64, accuracy: 1)
        XCTAssertEqual(catalogImage.frame.height, 64, accuracy: 1)
    }

    @MainActor
    func testLeadingEdgeSwipeReturnsToCatalog() throws {
        continueAfterFailure = false

        let app = XCUIApplication()
        app.launch()

        let resourcesEntry = app.buttons["demo-open-resources"]
        XCTAssertTrue(resourcesEntry.waitForExistence(timeout: 5))
        resourcesEntry.tap()

        let resourcesPage = app.otherElements["demo-resources"]
        XCTAssertTrue(resourcesPage.waitForExistence(timeout: 2))

        let window = app.windows.firstMatch
        let navigation = app.descendants(matching: .any)["demo-navigation"]
        XCTAssertTrue(navigation.waitForExistence(timeout: 1))
        XCTAssertEqual(navigation.frame.minX, window.frame.minX, accuracy: 1)
        XCTAssertEqual(navigation.frame.maxX, window.frame.maxX, accuracy: 1)

        let leadingEdge = navigation.coordinate(withNormalizedOffset: CGVector(dx: 0.001, dy: 0.5))
        let destination = navigation.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5))
        leadingEdge.press(forDuration: 0.05, thenDragTo: destination)

        XCTAssertTrue(
            resourcesEntry.waitForExistence(timeout: 2),
            "Expected a leading-edge swipe to return to the feature catalog"
        )
        XCTAssertFalse(resourcesPage.exists, "Resources page should be popped after edge swipe")

        resourcesEntry.tap()
        XCTAssertTrue(
            resourcesPage.waitForExistence(timeout: 2),
            "The declarative back stack should accept the same destination again after native pop"
        )
    }
}
