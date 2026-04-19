import SwiftUI
import KotlinSharedUI

struct TwitterArticleScreen: View {
    @StateObject private var presenter: KotlinPresenter<TwitterArticlePresenterState>
    
    let placeholderText = """
Lorem ipsum dolor sit amet, consectetur adipiscing elit. Etiam consectetur sapien a tortor sollicitudin fermentum. Vestibulum at tincidunt ipsum. In maximus justo dui, ut auctor ante faucibus vitae. Donec fermentum nec sem id viverra. Donec efficitur feugiat consectetur. Cras tempor suscipit tempus. Sed velit elit, vestibulum ut rhoncus in, pretium eget justo. Integer nec vulputate nisi.

Nullam eu augue ut est gravida pharetra. Cras ullamcorper sodales enim, eget feugiat metus aliquam in. Donec elementum venenatis nisi, non hendrerit sem commodo bibendum. Sed pharetra luctus ipsum, vitae lacinia sem. Proin sodales, enim sed vestibulum semper, nisi ipsum sagittis ante, at semper elit purus in ipsum. Fusce accumsan varius scelerisque. Pellentesque blandit risus purus, id convallis sem fermentum nec. Donec accumsan ullamcorper porta.

Vestibulum tempus turpis nibh. Praesent tempus varius mattis. Nullam et cursus diam, sit amet tincidunt quam. Nunc id nunc nibh. Etiam vel gravida metus. Quisque eget lacus hendrerit, aliquam diam non, faucibus dui. Praesent dignissim tortor in rutrum euismod. Pellentesque elit lorem, imperdiet sed lectus sed, venenatis molestie urna.

Mauris porttitor sapien ex, sed pharetra nibh mattis id. Interdum et malesuada fames ac ante ipsum primis in faucibus. Proin euismod congue risus, ut fermentum mi. Pellentesque sagittis commodo malesuada. Cras molestie vulputate nisl quis placerat. Integer egestas imperdiet sem, ac bibendum tortor imperdiet sed. Pellentesque maximus velit quis felis interdum tincidunt.

Maecenas fringilla vitae leo sit amet lacinia. Donec in dui a ex hendrerit volutpat. Etiam sit amet aliquet arcu. Phasellus placerat at eros eu ornare. Morbi venenatis mi sed tortor aliquet, nec volutpat ex commodo. In interdum elit ac leo efficitur, ultricies sodales enim feugiat. Fusce rutrum erat felis, malesuada varius risus imperdiet vitae. Ut bibendum sagittis metus, a placerat ex varius vel.

"""
    let accountType: AccountType
    let tweetId: String
    let articleId: String?
    
    init(accountType: AccountType, tweetId: String, articleId: String?) {
        self.accountType = accountType
        self.tweetId = tweetId
        self.articleId = articleId
        self._presenter = .init(wrappedValue: .init(presenter: TwitterArticlePresenter(accountType: accountType, tweetId: tweetId, articleId: articleId)))
    }
    
    var body: some View {
        StateView(state: presenter.state.data) { article in
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let image = article.image {
                        ListCardView {
                            NetworkImage(data: image)
                                .frame(maxHeight: 280)
                                .clipShape(.rect(cornerRadius: 12))
                        }
                    }
                    Text(article.title)
                        .font(.title)
                        .bold()
                        .frame(maxWidth: .infinity, alignment: .leading)
                    UserCompatView(data: article.profile)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    RichText(text: article.content)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding()
            }
        } errorContent: { error in
            Text(error.message ?? "Failed to load article")
        } loadingContent: {
            ScrollView {
                VStack {
                    Text("Loading...")
                        .font(.title)
                        .bold()
                        .redacted(reason: .placeholder)
                    ListCardView {
                        Text(placeholderText)
                            .redacted(reason: .placeholder)
                            .padding()
                    }
                }
                .padding()
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}
