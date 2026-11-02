import XCTest

@MainActor
final class RouterNavigationStateTests: XCTestCase {
    func testReloginSuccessAtRootDismissesSheetWithoutPoppingEmptyStack() {
        let navigation = RouterNavigationState<String>()
        navigation.sheet = "relogin"

        navigation.dismissSheet()

        XCTAssertNil(navigation.sheet)
        XCTAssertTrue(navigation.backStack.isEmpty)
    }

    func testReloginSuccessPreservesParentNavigationAndCanBeRepeated() {
        let navigation = RouterNavigationState<String>()
        navigation.backStack = ["settings", "accounts"]
        navigation.sheet = "relogin"

        navigation.dismissSheet()
        navigation.dismissSheet()

        XCTAssertNil(navigation.sheet)
        XCTAssertEqual(navigation.backStack, ["settings", "accounts"])
    }

    func testDismissingSheetPreservesCover() {
        let navigation = RouterNavigationState<String>()
        navigation.sheet = "relogin"
        navigation.cover = "media"

        navigation.dismissSheet()

        XCTAssertNil(navigation.sheet)
        XCTAssertEqual(navigation.cover, "media")
    }

    func testDismissingCoverAtRootDoesNotPopEmptyStack() {
        let navigation = RouterNavigationState<String>()
        navigation.cover = "media"

        navigation.dismissCover()

        XCTAssertNil(navigation.cover)
        XCTAssertTrue(navigation.backStack.isEmpty)
    }

    func testDismissingCoverPreservesParentAndSheetAndCanBeRepeated() {
        let navigation = RouterNavigationState<String>()
        navigation.backStack = ["post"]
        navigation.sheet = "share"
        navigation.cover = "media"

        navigation.dismissCover()
        navigation.dismissCover()

        XCTAssertNil(navigation.cover)
        XCTAssertEqual(navigation.sheet, "share")
        XCTAssertEqual(navigation.backStack, ["post"])
    }

    func testGoingBackPopsOnlyOneNavigationDestination() {
        let navigation = RouterNavigationState<String>()
        navigation.backStack = ["settings", "accounts"]

        navigation.goBack()

        XCTAssertEqual(navigation.backStack, ["settings"])
    }

    func testGoingBackAtRootIsSafe() {
        let navigation = RouterNavigationState<String>()

        navigation.goBack()

        XCTAssertTrue(navigation.backStack.isEmpty)
    }
}
