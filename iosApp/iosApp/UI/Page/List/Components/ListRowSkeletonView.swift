import shared
import SwiftUI

struct ListRowSkeletonView: View {
    var body: some View {
        HStack(spacing: 16) {
            Circle()
                .fill(Color.gray.opacity(0.2))
                .frame(width: 50, height: 50)

            VStack(alignment: .leading, spacing: 8) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.gray.opacity(0.2))
                    .frame(height: 14)
                    .frame(width: 150)

                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.gray.opacity(0.2))
                    .frame(height: 12)
                    .frame(width: 200)
            }

            Spacer()
        }
        .padding(.vertical, 8)
        .shimmering()
    }
}
