//
//  TweetListView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/10/12.
//

import SwiftUI

struct TweetListView: View {
    
    @StateObject var viewModel: TweetListViewModel
    
    init(user: User, tabType: ProfileTweetsTabType) {
        _viewModel = .init(wrappedValue: TweetListViewModel(user: user, tabType: tabType))
    }
    
    var body: some View {
        VStack {
            ScrollView {
                LazyVStack {
                    ForEach(viewModel.tweets) { tweet in
                        TweetRowView(tweet: tweet)
                    }
                }
            }
        }
    }
}

#Preview {
    TweetListView(user: PreviewProvider.user, tabType: .tweets)
}
