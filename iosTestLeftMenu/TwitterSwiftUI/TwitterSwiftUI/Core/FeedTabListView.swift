//
//  FeedTabListView.swift
//  TwitterSwiftUI
//
//  Created by paku on 2023/10/13.
//

import SwiftUI

struct FeedTabListView: View {
    
    @StateObject var viewModel: FeedTabListViewModel
        
    init(filterType: FeedTabFilter) {
        _viewModel = .init(wrappedValue: FeedTabListViewModel(selectedFilter: filterType))
    }
    
    var body: some View {
        VStack {
            ScrollView(showsIndicators: false) {
                LazyVStack {
                    ForEach(viewModel.selectedFilter == .recommend ? viewModel.tweets : viewModel.tweetsOfFollowing) { tweet in
                        NavigationLink {
                            if let user = tweet.user {
                                ProfileView(user: user)
                            }
                        } label: {
                            TweetRowView(tweet: tweet)
                        }
                    }
                }
            }
            .refreshable {
                viewModel.refreshed()
            }
        }
    }
}

#Preview {
    FeedTabListView(filterType: .recommend)
        .environmentObject(FeedViewModel())
}
