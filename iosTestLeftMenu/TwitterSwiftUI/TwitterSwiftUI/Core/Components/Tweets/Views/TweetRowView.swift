//
//  TweetRowView.swift
//  TwitterSwiftUI
//
//  Created by パクギョンソク on 2023/09/21.
//

import SwiftUI
import Kingfisher

struct TweetRowView: View {
    
    @ObservedObject var viewModel: TweetRowViewModel
    
    init(tweet: Tweet) {
        self.viewModel = TweetRowViewModel(tweet: tweet)
    }
    
    var body: some View {
        
        VStack(alignment: .leading) {
            HStack(alignment: .top, spacing: 12) {
                
                if let user = viewModel.tweet.user {
                    KFImage(URL(string: user.profileImageUrl))
                        .resizable()
                        .scaledToFill()
                        .frame(width: 40, height: 40)
                        .clipShape(Circle())
                    
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            
                            Text("\(user.username)")
                                .font(.subheadline).bold()

                            Text("@\(user.email.emailUsername ?? "")")
                                .foregroundColor(.gray)
                                .font(.caption)

//                            Text(viewModel.tweet.timestamp)
//                                .foregroundColor(.gray)
//                                .font(.caption)
                        }
                        
                        // tweet caption
                        Text(viewModel.tweet.caption)
                            .font(.subheadline)
                            // テキストの左寄せ
                            .multilineTextAlignment(.leading)
                    }
                    .foregroundColor(.black)
                }
                
                
            }
            
            HStack {
                Button {
                    
                } label: {
                     Image(systemName: "bubble.left")
                        .font(.subheadline)
                }
                
                Spacer()
                
                Button {
                    
                } label: {
                     Image(systemName: "arrow.2.squarepath")
                        .font(.subheadline)
                }
                
                Spacer()
                
                if viewModel.tweet.didLike ?? false {
                    Button {
                        Task { try await viewModel.unlikeTweet() }
                    } label: {
                         Image(systemName: "heart.fill")
                            .font(.subheadline)
                            .foregroundColor(.red)
                    }
                } else {
                    Button {
                        Task { try await viewModel.likeTweet() }
                    } label: {
                         Image(systemName: "heart")
                            .font(.subheadline)
                    }
                }
                
                Spacer()
                
                Button {
                    
                } label: {
                     Image(systemName: "bookmark")
                        .font(.subheadline)
                }
            }
            .padding(.horizontal, 25)
            .padding(.vertical, 8)
            .foregroundColor(.primary)
            
            Divider()
        }
        .padding()
    }
}

 

