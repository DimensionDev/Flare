import SwiftUI


struct CommonProfileHeader<HeaderTrailing, HandleTrailing, Content>: View where HeaderTrailing: View, HandleTrailing: View, Content: View {
    let bannerUrl: String?
    let avatarUrl: String?
    let displayName: String
    let handle: String
    let description: String
    @ViewBuilder let headerTrailing: () -> HeaderTrailing
    @ViewBuilder let handleTrailing: () -> HandleTrailing
    @ViewBuilder let content: () -> Content
    
    init(bannerUrl: String?, avatarUrl: String?, displayName: String, handle: String, description: String, headerTrailing: @escaping () -> HeaderTrailing = { EmptyView() }, handleTrailing: @escaping () -> HandleTrailing = { EmptyView() }, content: @escaping () -> Content = { EmptyView() }) {
        self.bannerUrl = bannerUrl
        self.avatarUrl = avatarUrl
        self.displayName = displayName
        self.handle = handle
        self.description = description
        self.headerTrailing = headerTrailing
        self.handleTrailing = handleTrailing
        self.content = content
    }
    
    var body: some View {
        Text("")
    }
}



//#Preview {
//    CommonProfileHeader()
//}


typealias Start<V> = Group<V> where V:View
typealias Main<V> = Group<V> where V:View
typealias End<V> = Group<V> where V:View

struct ThreeItemView<V1, V2, V3>: View where V1: View, V2: View, V3: View {

    private let content: () -> TupleView<(Start<V1>, Main<V2>, End<V3>)>

    init(@ViewBuilder _ content: @escaping () -> TupleView<(Start<V1>, Main<V2>, End<V3>)>) {
        self.content = content
    }

    var body: some View {
        let (start, main, end) = self.content().value
        return HStack {
            start
            main
                .frame(minWidth: 0, maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            end
        }
        .frame(minWidth: 0, maxWidth: .infinity, maxHeight: 60, alignment: .leading)
    }
}

struct ThreeItemContainer_Previews: PreviewProvider {
    static var previews: some View {

        ThreeItemView {
            Start {
                Image(systemName: "envelope.fill")
            }
            Main {
                Text("Main")
            }
            End {
                Image(systemName: "chevron.right")
            }
        }
    }
}
