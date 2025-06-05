import shared
import SwiftUI

struct StatusPlaceHolder: View {
    var body: some View {
        StatusItemView(
            data: createSampleStatus(
                user: createSampleUser()
            ),
            detailKey: nil
        )
        .redacted(reason: .placeholder)
    }
}
