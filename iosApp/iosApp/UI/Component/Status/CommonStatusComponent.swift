import SwiftUI
import MarkdownUI
import shared
import NetworkImage

struct CommonStatusComponent<HeaderTrailing>: View where HeaderTrailing: View {
    @Environment(\.openURL) private var openURL
    let content: String
    let avatar: String
    let name: String
    let handle: String
    let userKey: MicroBlogKey
    let medias: [UiMedia]
    let timestamp: Int64
    @ViewBuilder let headerTrailing: () -> HeaderTrailing
    var body: some View {
        VStack(alignment:.leading) {
            HStack {
                Button(
                    action: {
                        openURL(URL(string: AppDeepLink.Profile.shared.invoke(userKey: userKey))!)
                    }
                ) {
                    NetworkImage(url: URL(string: avatar)){ image in
                        image.resizable().scaledToFit()
                    }
                    .frame(width: 48, height: 48)
                    .clipShape(Circle())
                }
                VStack(alignment: .leading) {
                    Markdown(name)
                        .lineLimit(1)
                        .font(.headline)
                        .markdownInlineImageProvider(.emoji)
                    Text(handle)
                        .lineLimit(1)
                        .font(.subheadline)
                        .foregroundColor(.gray)
                }
                Spacer()
                HStack {
                    headerTrailing()
                    dateFormatter(Date(timeIntervalSince1970: .init(integerLiteral: timestamp)))
                }
                .foregroundColor(.gray)
                .font(.caption)
            }
            Markdown(content)
                .font(.body)
                .markdownInlineImageProvider(.emoji)
            if !medias.isEmpty {
                MediaComponent(medias: medias)
            }
        }.frame(maxWidth: .infinity, alignment: .leading)
    }
    
    
    private func dateFormatter(_ date: Date) -> some View {
        let now = Date()
        let oneDayAgo = Calendar.current.date(byAdding: .day, value: -1, to: now)!
        
        if date > oneDayAgo {
            // If the date is within the last day, use the .timer style
            return Text(date, style: .relative)
        } else {
            // Otherwise, use the .dateTime style
            return Text(date, style: .date)
        }
    }
}

#Preview {
    CommonStatusComponent(content: "haha", avatar: "https://pbs.twimg.com/profile_images/1657513391131590656/mnAV7E7G_400x400.jpg", name: "hahaname", handle: "haha.haha", userKey: MicroBlogKey(id: "", host: ""), medias: [UiMediaImage(url: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500", previewUrl: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500", description: nil, height: 500, width: 1500),UiMediaImage(url: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500", previewUrl: "https://pbs.twimg.com/profile_banners/1547244200671846406/1684016886/1500x500", description: nil, height: 500, width: 1500)], timestamp: 1696838289, headerTrailing: {EmptyView()})
}
