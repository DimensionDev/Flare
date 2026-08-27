# FlareUI LazyColumn / LazyRow 一手资料调研

> 调研日期：2026-08-27
>
> 范围：Cash App Redwood、React / React Native、.NET MAUI、Jetpack Compose LazyLayout。
> 来源规则：仅引用官方文档、官方仓库源码和本仓库源码；源码引用固定到具体提交。

## 结论摘要

FlareUI 的 lazy list 不应实现为“可滚动的 `Column` / `Row`”。这仍会让 Compose Runtime 先创建全部 item subtree，既没有 composition window，也无法让原生 collection adapter 按需绑定 item。

四套方案共同指向一个更合适的分层：

1. 公共层保存轻量的 item 描述（interval provider），而不是立即 emit 全部 children。
2. `LazyColumn` / `LazyRow` 共享一个按方向参数化的底层协议，公共 API 只做易用包装。
3. 每个平台用原生虚拟列表负责 viewport、测量、滚动和 cell pool；公共层负责 item factory、稳定 identity、状态锚点和一致的事件/命令语义。
4. item 的 `key`、`contentType`、原生 cell recycling、Flare subtree composition/state retention 是四个不同概念，不能合并成一个“复用”开关。
5. Redwood 的 `itemsBefore + loaded items + itemsAfter + placeholder` 稀疏窗口协议很适合 guest/host 或跨进程边界，但它移除了稳定 key/content type，且 UIKit `LazyRow` 实际未实现；FlareUI 应借架构，不应复制其 API 缺口。
6. 最接近 FlareUI Kotlin DSL 的行为基线是 Compose `LazyListScope`；最值得借鉴的跨平台 adapter 边界是 MAUI 的 common API + native handler；React Native 则提供了最清楚的可调 window/batch/估算模型。

## 当前 FlareUI 约束

当前 runtime 中，一个 `FlareWidget` 最多暴露一个 `FlareChildren`；普通 composition 的结构变更直接映射为 backend `insert/move/remove`。[FlareWidget.kt](../runtime/src/commonMain/kotlin/dev/dimension/flare/ui/FlareWidget.kt) [FlareRuntime.kt](../runtime/src/commonMain/kotlin/dev/dimension/flare/ui/FlareRuntime.kt)

当节点从普通 applier tree 移除时，runtime 会递归 dispose 整个 subtree。[RuntimeNode.kt](../runtime/src/commonMain/kotlin/dev/dimension/flare/ui/RuntimeNode.kt) 架构文档也明确把 scrolling 与 native lazy collections 列为尚未实现的能力。[ARCHITECTURE.md](../ARCHITECTURE.md)

由此可推得：lazy item 不能只是普通 `FlareChildren` 中的全部直接子节点。它需要一个新的 item-provider / item-composition 边界，让 backend 可以只请求当前窗口内的 item；否则“虚拟化”最多只发生在 native view 层，Compose/Flare subtree 仍是全量的。

## 横向比较

| 维度 | Redwood | React Native `VirtualizedList` | MAUI `CollectionView` | Jetpack Compose | 对 FlareUI 的含义 |
| --- | --- | --- | --- | --- | --- |
| 数据 / DSL | `item`、`items(count)`，内部 interval；无 key/type | `data + getItem + getItemCount + renderItem` | `ItemsSource + DataTemplate` | `item/items/itemsIndexed` interval DSL | 采用 interval provider，避免物化全部 item |
| 可见区与窗口 | native 上报首末可见 index；guest 计算 loaded range | offset/viewport + 已测尺寸或平均尺寸估算；overscan + batch | 公共层不规定算法，委托原生 control | measure pass 从 anchor 向前后填满 viewport | 公共协议传 viewport/anchor；测量尽量留给 native adapter |
| identity / key | global index；稳定 key 被移除 | `keyExtractor`，默认 `key/id/index` | 公共 API 无显式 key；Android item id 是 position | stable unique key，缺省 position | Flare 必须正式支持 stable key；index 只能是降级路径 |
| 回收 / 复用 | native cell pool；可选 subtree reuse；无 content type | 窗口外 React cell unmount，无 type-aware pool contract | native cell 根据模板复用 | composition slot 按 content type 兼容复用 | `contentType` 映射 native view type/reuse id；与 key 分开 |
| item 状态 | 离开 loaded window 后重新 compose；state 只存 index | 窗口外内部状态不保留 | cell 被重新绑定，公共层无 item-state 保留保证 | keyed `SaveableStateHolder` 可保留可保存状态 | 明确 v1 状态契约；业务状态默认 hoist，keyed saveable registry 可后续增强 |
| 滚动控制 | index + animated；恢复只存 index | index/item/offset/end；未测量目标可能失败 | index/item + Start/Center/End/MakeVisible | index + offset；立即/动画；layout info | state 至少包含 index、offset、visible info；命令需有 alignment/失败语义 |
| 异构 item | DSL 可异构，但单一通用 cell/type | `renderItem` 可异构，无显式 reuse type | `DataTemplateSelector`，reuse id 含模板 | `contentType` 控制兼容复用 | `contentType` 是一等 API，不从 composable shape 猜测 |
| cache / prefetch | 按 item 数、方向调整 loaded window | `windowSize`、batch size、batch period、优先级 | 依赖原生 control，无统一 cache knob | beyond-bounds、type-aware slot cache、frame-budget prefetch | 公共层定义 hint/事件，不承诺各平台完全相同的内部常量 |
| 增量数据加载 | 与 UI windowing 分离 | `onEndReached` | `RemainingItemsThreshold` | Paging 集成 | 与 UI 虚拟化分成独立能力，要求调用方去重/背压 |
| 跨平台 adapter | 同一 widget protocol，各平台完成度不一致 | RN 自身 Android/iOS host | common API 映射 native handlers | 同一 Compose layout engine | 为每个平台做 capability tests，不能仅凭统一接口宣称行为一致 |

## 1. Cash App Redwood

### 1.1 状态与适用范围

Redwood 官方已声明 0.19 是可预见未来的最终版本，项目停止活跃开发，因此它适合作为架构案例，不适合作为会继续收敛行为的依赖基线。[Redwood CHANGELOG](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/CHANGELOG.md#L11-L15)

本节源码固定到 [`5c49a0b`](https://github.com/cashapp/redwood/commit/5c49a0bcc224b7fef10316bfdeb227639bcc42ec)。

### 1.2 数据 DSL 与 composition window

Redwood 的 `LazyListScope` 只有 `item {}` 和 `items(count) { index -> }`，List/Array 只是便利扩展；多个 interval 可以描述异构内容，而不会先创建全部 item。[LazyDsl.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-compose/src/commonMain/kotlin/app/cash/redwood/lazylayout/compose/LazyDsl.kt#L25-L107) DSL 会被压缩为 interval，并在给定 global index 时定位对应 factory。[LazyListIntervalContent.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-compose/src/commonMain/kotlin/app/cash/redwood/lazylayout/compose/LazyListIntervalContent.kt#L25-L58)

端到端流程是：

```text
LazyListScope intervals
        │
        ▼
LoadingStrategy.loadRange(totalCount)
        │
        ├─ itemsBefore
        ├─ loaded item subtrees
        ├─ itemsAfter
        └─ placeholder subtree pool
        │
        ▼
LazyList widget protocol
        │
        ▼
native virtual list + sparse bindings
```

guest 只 compose `loadRange` 中的 item，并向 host 发送 `itemsBefore`、实际 `items`、`itemsAfter` 和 20 个预建 placeholder。[LazyList.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-compose/src/commonMain/kotlin/app/cash/redwood/lazylayout/compose/LazyList.kt#L40-L65) 这些字段连同方向、viewport callback 和 scroll command 是正式 widget schema；`LazyColumn` 与 `LazyRow` 共用同一个 widget，通过 `isVertical` 区分。[widgets.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-schema/src/main/kotlin/app/cash/redwood/lazylayout/widgets.kt#L34-L47)

`LazyListUpdateProcessor` 明确维护两个窗口：已加载窗口与用户可见窗口。用户滚出已加载区域时先绑定 placeholder，真实 subtree 到达后在同一 binding/cell 中替换。[LazyListUpdateProcessor.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-widget/src/commonMain/kotlin/app/cash/redwood/lazylayout/widget/LazyListUpdateProcessor.kt#L21-L57) `itemsBefore/itemsAfter` 使用稀疏结构，逻辑上一百万个未加载位置不等于创建一百万个 placeholder 对象。[SparseList.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-widget/src/commonMain/kotlin/app/cash/redwood/lazylayout/widget/SparseList.kt#L18-L60)

这个协议尤其适合 Treehouse 这类 guest/host 或序列化边界。对当前同进程 FlareUI，它更像可选的高级协议：native adapter 可以直接向公共 item provider 请求 index；但如果未来要跨进程、跨语言或异步产生 subtree，`itemsBefore/itemsAfter + sparse placeholder` 是经过验证的模型。

### 1.3 viewport 与 preload

`LoadingStrategy` 只接收首末可见 index，并返回需要进入 view tree 的 range；接口要求 range 覆盖最近 viewport，并允许两侧预加载。[LoadingStrategy.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-compose/src/commonMain/kotlin/app/cash/redwood/lazylayout/compose/LoadingStrategy.kt#L18-L50)

默认 `ScrollOptimizedLoadingStrategy` 按 item 数而非像素工作：初始向后 15 项；滚动时主方向 20、反方向 5；停止后主方向 20、另一侧 10；窗口连续时尽量保留旧 range，减少 churn。[ScrollOptimizedLoadingStrategy.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-compose/src/commonMain/kotlin/app/cash/redwood/lazylayout/compose/ScrollOptimizedLoadingStrategy.kt#L24-L45) [窗口算法](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-compose/src/commonMain/kotlin/app/cash/redwood/lazylayout/compose/ScrollOptimizedLoadingStrategy.kt#L81-L151)

优点是简单且跨平台；缺点是 20 个 24dp 行和 20 个全屏卡片的成本完全不同。FlareUI 可保留“方向偏置 + 滚动结束扩窗”的思想，但不宜把固定 item count 当成唯一策略。

### 1.4 key、状态与滚动

Redwood 从 Compose lazy 实现移植 interval 代码时，明确移除了 keys、content types、sticky headers。[LazyListIntervalContent.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-compose/src/commonMain/kotlin/app/cash/redwood/lazylayout/compose/LazyListIntervalContent.kt#L22-L24) 实际 composition 使用 `key(index)`，因此 identity 是位置，不是业务对象。[LazyList.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-compose/src/commonMain/kotlin/app/cash/redwood/lazylayout/compose/LazyList.kt#L59-L62)

item 留在 loaded window 内不会反复 compose；离开窗口后回来会重新 compose。[LazyListTest.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-compose/src/commonTest/kotlin/app/cash/redwood/lazylayout/compose/LazyListTest.kt#L112-L157) `LazyListState` 的 saver 只保存首个可见 index，没有 offset、anchor key 或 layout info。[LazyListState.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-compose/src/commonMain/kotlin/app/cash/redwood/lazylayout/compose/LazyListState.kt#L29-L46)

程序滚动只有 `index + animated`；递增 command id 使同一目标也能再次触发。host 会等逻辑 item count 足够后才执行，并去重相同 viewport 回调。[LazyListState.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-compose/src/commonMain/kotlin/app/cash/redwood/lazylayout/compose/LazyListState.kt#L53-L98) [LazyListScrollProcessor.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-widget/src/commonMain/kotlin/app/cash/redwood/lazylayout/widget/LazyListScrollProcessor.kt#L20-L65)

这些选择会让 prepend、reorder、item 状态跟随业务对象等场景变弱；而 update processor 对 children move 直接报错。[LazyListUpdateProcessor.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-widget/src/commonMain/kotlin/app/cash/redwood/lazylayout/widget/LazyListUpdateProcessor.kt#L241-L269) FlareUI 不应复制 index-only identity。

### 1.5 placeholder、cell recycling 与 subtree reuse

Redwood 要求每次 placeholder composition 内容和尺寸相同。[LazyDsl.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-compose/src/commonMain/kotlin/app/cash/redwood/lazylayout/compose/LazyDsl.kt#L118-L133) 20 个真实 placeholder 耗尽后，host 以第一个 placeholder 的尺寸制造 size-only placeholder。[LazyListUpdateProcessor.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-widget/src/commonMain/kotlin/app/cash/redwood/lazylayout/widget/LazyListUpdateProcessor.kt#L409-L434) 这对高度差异很大的异构列表只是粗略 scroll extent。

Redwood 实际有相互独立的复用层：

- loaded composition window：离开窗口即卸载。
- placeholder pool：真实 placeholder 加 size-only clone。
- native cell pool：Android `RecyclerView` 与 UIKit table cell 各自回收 container。
- Treehouse subtree pool：只有 item 根显式 `Modifier.reuse()` 时才启用，按 widget shape 匹配，并不是 LazyList 自动行为。[HostProtocolAdapter.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-protocol-host/src/commonMain/kotlin/app/cash/redwood/protocol/host/HostProtocolAdapter.kt#L190-L284) [NodeReuse.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-protocol-host/src/commonMain/kotlin/app/cash/redwood/protocol/host/NodeReuse.kt#L23-L55)

因此“回收一个原生 container”不等于“保留这个业务 item 的 composition/state”，也不等于“把旧 subtree 安全绑定给新 key”。FlareUI 的协议需要分别表达这三件事。

### 1.6 各平台完成度与明确限制

| Redwood backend | 实现 | 已知事实 |
| --- | --- | --- |
| Android View | `RecyclerView + LinearLayoutManager` | Row/Column 均支持；所有内容使用同一 view type，pool 上限硬编码为 30。[ViewLazyList.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-view/src/main/kotlin/app/cash/redwood/lazylayout/view/ViewLazyList.kt#L90-L141) |
| UIKit | `UITableView` | **`isVertical` 是空实现，并有 `TODO: support horizontal LazyLists`；即 API 暴露 `LazyRow`，UIKit 实际不支持。** width/height/cross-axis 也未实现。[UIViewLazyList.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-uiview/src/commonMain/kotlin/app/cash/redwood/lazylayout/uiview/UIViewLazyList.kt#L274-L317) |
| Compose UI | Compose `LazyColumn` / `LazyRow` | 两方向可用，但保存的 `itemsBefore/itemsAfter` 没有加入 native list，源码标有 `TODO Fix item count truncation`；滚动也始终走非动画 `scrollToItem`。[ComposeUiLazyList.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-composeui/src/commonMain/kotlin/app/cash/redwood/lazylayout/composeui/ComposeUiLazyList.kt#L124-L210) |
| DOM | flex/overflow + observer | 只增长最高可见 index，不卸载已滚出项；程序滚动等能力未完成。[HTMLLazyList.kt](https://github.com/cashapp/redwood/blob/5c49a0bcc224b7fef10316bfdeb227639bcc42ec/redwood-lazylayout-dom/src/commonMain/kotlin/app/cash/redwood/lazylayout/dom/HTMLLazyList.kt#L130-L192) |

Redwood 最重要的反例是：统一 schema 并不自动带来平台语义对齐。FlareUI 的 `LazyRow` 必须从第一阶段就在 UIKit/AppKit/Android/Compose backend 的 capability test 中出现。

## 2. React 与 React Native

React 源码固定到 [`29d9d31`](https://github.com/facebook/react/commit/29d9d3184484b03cb0369e0494617207df777b7af)，React Native 源码固定到 [`d6ba88e`](https://github.com/facebook/react-native/commit/d6ba88e16d1cc42c0e90a31eb6586586df2e9d5e)。

### 2.1 React key 是 reconciliation identity，不是缓存策略

React 官方文档要求列表 key 在同级中稳定且唯一；用 index 处理会发生插入、删除、重排时的错误匹配，随机 key 则导致每次重建并丢失输入状态。[Rendering Lists](https://react.dev/learn/rendering-lists#keeping-list-items-in-order-with-key)

reconciler 源码会把旧 children 放进 key map；无 key 的 child 才退化为 index。新 child 按 `key ?? index` 匹配，未消费的旧 child 被删除。[ReactChildFiber.js：构建 map](https://github.com/facebook/react/blob/29d9d3184484b03cb0369e0494617207df777b7af/packages/react-reconciler/src/ReactChildFiber.js#L467-L499) [按 key 匹配](https://github.com/facebook/react/blob/29d9d3184484b03cb0369e0494617207df777b7af/packages/react-reconciler/src/ReactChildFiber.js#L985-L1019) [move/delete 阶段](https://github.com/facebook/react/blob/29d9d3184484b03cb0369e0494617207df777b7af/packages/react-reconciler/src/ReactChildFiber.js#L1308-L1361)

React state 绑定到 render tree 中的 identity/position；key 可以改变该 identity，但节点真的 unmount 后 state 仍会被销毁。[Preserving and Resetting State](https://react.dev/learn/preserving-and-resetting-state#state-is-tied-to-a-position-in-the-render-tree)

对 FlareUI 的直接含义是：`key` 能解决窗口内重排与 keyed state registry 的寻址，但它本身不会虚拟化，也不会让已 dispose 的 subtree 自动复活。

### 2.2 VirtualizedList 的数据接口与窗口算法

React Native `FlatList` 是 `VirtualizedList` 的便利封装。后者用 `data + getItem(data,index) + getItemCount(data) + renderItem` 支持普通数组之外的数据结构；`horizontal` 让同一实现覆盖 row/column。[VirtualizedList 官方文档](https://reactnative.dev/docs/virtualizedlist) [FlatList 官方文档](https://reactnative.dev/docs/flatlist)

窗口计算以 scroll offset、visible length 和 cell metrics 为输入：

1. 可见像素区间是 `[offset, offset + visibleLength]`。
2. overscan 总长度为 `(windowSize - 1) * visibleLength`；默认 `windowSize = 21`，即当前屏加前后最多各约 10 屏。
3. 算法把像素边界映射为 item index，再围绕可见区扩张；扩张方向受速度方向影响，且单批新增 cell 受 `maxToRenderPerBatch` 限制（默认 10）。[VirtualizeUtils.js](https://github.com/facebook/react-native/blob/d6ba88e16d1cc42c0e90a31eb6586586df2e9d5e/packages/virtualized-lists/Lists/VirtualizeUtils.js#L89-L243) [默认参数](https://github.com/facebook/react-native/blob/d6ba88e16d1cc42c0e90a31eb6586586df2e9d5e/packages/virtualized-lists/Lists/VirtualizedListProps.js#L305-L334)
4. 已测 item 使用精确 frame；未测 item 使用已测平均长度，并尽量从最高已测 frame 外推；`getItemLayout` 可以为固定/可计算尺寸提供精确 offset。[ListMetricsAggregator.js](https://github.com/facebook/react-native/blob/d6ba88e16d1cc42c0e90a31eb6586586df2e9d5e/packages/virtualized-lists/Lists/ListMetricsAggregator.js#L168-L242)
5. 窗口外区域在滚动内容中表现为合适尺寸的空白 spacer；远离 viewport 的 item 低优先级批量渲染，靠近 viewport 的 item 高优先级渲染。官方明确承认快速滚动可能暂时看到空白。[VirtualizedList 官方文档](https://reactnative.dev/docs/virtualizedlist)

这套算法揭示了三个独立旋钮：空间窗口大小、每批工作量、批次时间间隔。FlareUI 即便让 native adapter 主导算法，也应在内部把它们作为独立概念，而不是只有一个 `cacheItemCount`。

### 2.3 key、状态、回收与滚动

默认 key extractor 依次使用对象的 `key`、`id`、最后才是 index。[VirtualizeUtils.js](https://github.com/facebook/react-native/blob/d6ba88e16d1cc42c0e90a31eb6586586df2e9d5e/packages/virtualized-lists/Lists/VirtualizeUtils.js#L246-L254) 解析后的 key 同时用作 React element key、cell key 和 ref map key。[VirtualizedList.js](https://github.com/facebook/react-native/blob/d6ba88e16d1cc42c0e90a31eb6586586df2e9d5e/packages/virtualized-lists/Lists/VirtualizedList.js#L785-L846)

不过 `VirtualizedList` 官方文档明确说明，item 滚出 render window 后其内部状态不保留，应把状态放进 item data 或外部 store。它也是 `PureComponent`，依赖 render 的外部值需要通过不可变 data 或 `extraData` 触发更新；异步填充窗口可能出现 blank area。[VirtualizedList 官方文档](https://reactnative.dev/docs/virtualizedlist)

这是一种“卸载并重建”的 composition 策略，不是 MAUI/RecyclerView 那种面向模板类型的公共 cell pool contract。`CellRendererComponent` 允许替换 container，但 API 没有 Compose `contentType` 等价物；`removeClippedSubviews` 只是把不可见原生 view 从 native hierarchy detach，官方也警告它可能造成缺失内容，不能把它当 item state cache。[FlatList 官方文档](https://reactnative.dev/docs/flatlist)

滚动 API 支持 index、item、offset、end。目标 index 尚未测量且无 `getItemLayout` 时，`scrollToIndex` 可能失败，回调只提供最高已测 index 和平均长度，调用方需先滚到可达位置再重试。[VirtualizedListProps.js](https://github.com/facebook/react-native/blob/d6ba88e16d1cc42c0e90a31eb6586586df2e9d5e/packages/virtualized-lists/Lists/VirtualizedListProps.js#L206-L215) `maintainVisibleContentPosition` 会记录首个可见 key，在头部插入后寻找它的新 index 并平移窗口。[VirtualizedList.js](https://github.com/facebook/react-native/blob/d6ba88e16d1cc42c0e90a31eb6586586df2e9d5e/packages/virtualized-lists/Lists/VirtualizedList.js#L729-L782)

### 2.4 已知限制

- `key` 只在节点仍参与 reconciliation 时保留 React state；virtualizer unmount 后不能依靠 key 保存内部 state。
- 未知尺寸只能估算，远距离 `scrollToIndex` 需要固定布局信息或失败/纠正流程。
- 大 window 降低 blank 风险但增加内存；大 batch 提高 fill rate 但阻塞交互。
- `FlatList.numColumns` 要求同一行 item 高度一致，不是 masonry。[FlatList 官方文档](https://reactnative.dev/docs/flatlist)
- `removeClippedSubviews` 有 missing-content 风险；不能作为跨平台必选优化。[VirtualizedListProps.js](https://github.com/facebook/react-native/blob/d6ba88e16d1cc42c0e90a31eb6586586df2e9d5e/packages/virtualized-lists/Lists/VirtualizedListProps.js#L253-L267)

## 3. .NET MAUI CollectionView / ItemsView

MAUI 源码固定到 [`073c90c`](https://github.com/dotnet/maui/commit/073c90c8911e2e7adc0082a13ef5a3a22d4b4d29)。

### 3.1 common API 与 native-handler 边界

MAUI `CollectionView` 的公共模型是 `ItemsSource: IEnumerable` 加 `ItemTemplate: DataTemplate`。它不公开 cell 概念，并明确说明自动使用底层原生 control 的 virtualization；vertical/horizontal list 与 grid 都由 layout 配置表达。[CollectionView 官方文档](https://learn.microsoft.com/en-us/dotnet/maui/user-interface/controls/collectionview/?view=net-maui-10.0) [layout 官方文档](https://learn.microsoft.com/en-us/dotnet/maui/user-interface/controls/collectionview/layout?view=net-maui-10.0)

这是对 FlareUI 很重要的先例：common API 不需要发明一套跨平台像素布局/回收引擎。它可以定义 item provider、identity、type、滚动和可见区语义，再由 Android `RecyclerView`、UIKit `UICollectionView`、AppKit `NSCollectionView`、Compose LazyList 执行平台算法。

MAUI 当前文档也说明 iOS/Mac Catalyst 的优化 handler 已成为 .NET 10 默认，.NET 11 Windows handler 基于 WinUI `ItemsRepeater` 以改善 virtualization/scrolling。这进一步表明其性能路径是持续向原生 virtual control 收敛，而不是在 common 层统一实现窗口算法。[CollectionView 官方文档](https://learn.microsoft.com/en-us/dotnet/maui/user-interface/controls/collectionview/?view=net-maui-10.0)

### 3.2 template type 与回收

Android `ItemsViewAdapter` 直接继承 `RecyclerView.Adapter`。使用 `DataTemplateSelector` 时，所选模板 id 成为 view type 并被缓存；bind 时取 position 对应数据，recycle 时通知 holder。[ItemsViewAdapter.cs](https://github.com/dotnet/maui/blob/073c90c8911e2e7adc0082a13ef5a3a22d4b4d29/src/Controls/src/Core/Handlers/Items/Android/Adapters/ItemsViewAdapter.cs#L7-L143)

`TemplatedItemViewHolder` 在模板不变时保留现有 view，仅替换 `BindingContext`；模板改变时回收旧 content 并重新 `CreateContent()`。被 RecyclerView 回收时，它从 MAUI logical children 中移除，再绑定时重新加入。[TemplatedItemViewHolder.cs](https://github.com/dotnet/maui/blob/073c90c8911e2e7adc0082a13ef5a3a22d4b4d29/src/Controls/src/Core/Handlers/Items/Android/TemplatedItemViewHolder.cs#L35-L88)

iOS 优化 handler 使用 `UICollectionView.DequeueReusableCell`；reuse id 包含 cell 类型、方向和选中的 `DataTemplate.Id`，因此不同模板和方向不会误入同一 pool。[ItemsViewController2.cs](https://github.com/dotnet/maui/blob/073c90c8911e2e7adc0082a13ef5a3a22d4b4d29/src/Controls/src/Core/Handlers/Items2/iOS/ItemsViewController2.cs#L111-L130) [reuse id](https://github.com/dotnet/maui/blob/073c90c8911e2e7adc0082a13ef5a3a22d4b4d29/src/Controls/src/Core/Handlers/Items2/iOS/ItemsViewController2.cs#L407-L426)

这正是 FlareUI `contentType` 的 native 映射：Android view type / iOS reuse id / AppKit item identifier / Compose slot compatibility。它不是业务 identity。

### 3.3 identity 与状态

MAUI 的公共 CollectionView API 没有与 React/Compose stable key 对等的一等参数；Android adapter 的 `GetItemId(position)` 直接返回 position。[ItemsViewAdapter.cs](https://github.com/dotnet/maui/blob/073c90c8911e2e7adc0082a13ef5a3a22d4b4d29/src/Controls/src/Core/Handlers/Items/Android/Adapters/ItemsViewAdapter.cs#L140-L147) 数据变更依靠可观察 collection 通知，且 ItemsSource 更新必须发生在 UI thread。[populate data 官方文档](https://learn.microsoft.com/en-us/dotnet/maui/user-interface/controls/collectionview/populate-data?view=net-maui-10.0)

由源码可作出的保守推论是：复用 cell 中的 view-local transient state 会随 holder 被重新绑定，框架并没有承诺它跟随某个业务 item；业务状态应在 model/view-model 中。FlareUI 若已有 Compose Runtime state，应比 MAUI 多提供一层明确的 keyed state 语义，而不是依赖 native cell 恰好未被回收。

### 3.4 测量、滚动与增量加载

MAUI 默认 `MeasureAllItems`；`MeasureFirstItem` 只测第一项并把尺寸用于后续项，在 item 尺寸统一时性能更好。[layout 官方文档](https://learn.microsoft.com/en-us/dotnet/maui/user-interface/controls/collectionview/layout?view=net-maui-10.0#item-sizing) 这与 FlareUI 可提供的 `estimatedItemSize` / fixed-extent hint 类似，但动态尺寸列表不能误用首项尺寸作为精确值。

`Scrolled` 事件提供 horizontal/vertical offset 与 first/center/last visible index。`ScrollTo` 可按 index 或 item 定位，支持 animation 与 `MakeVisible/Start/Center/End` alignment。[scrolling 官方文档](https://learn.microsoft.com/en-us/dotnet/maui/user-interface/controls/collectionview/scrolling?view=net-maui-10.0) 数据插入时，`ItemsUpdatingScrollMode` 提供 `KeepItemsInView`、`KeepScrollOffset`、`KeepLastItemInView` 三种策略。[scroll position 官方文档](https://learn.microsoft.com/en-us/dotnet/maui/user-interface/controls/collectionview/scrolling?view=net-maui-10.0#control-scroll-position-when-new-items-are-added)

增量数据加载通过 `RemainingItemsThreshold`、command 和 event 触发；`-1` 禁用、`0` 到末尾触发、正数表示剩余 item 阈值。[populate data 官方文档](https://learn.microsoft.com/en-us/dotnet/maui/user-interface/controls/collectionview/populate-data?view=net-maui-10.0#load-data-incrementally) 这只是“何时请求更多业务数据”，不等于 UI subtree window。

### 3.5 已知限制与启示

- MAUI 不公开统一 cache/prefetch 大小，具体行为属于 native handler；FlareUI 的公共 cache 参数宜定义为 hint，而不是逐平台像素级保证。
- `MeasureFirstItem` 只适合同尺寸 item；异构高度必须允许各 item 实测。
- 把 CollectionView 放进不能提供有界 viewport 的 StackLayout，可能阻止滚动；把 `ItemsLayout` 设为 StackLayout-based layout 会关闭 virtualization、全量测量渲染，并让增量加载阈值连续触发。[populate data 官方警告](https://learn.microsoft.com/en-us/dotnet/maui/user-interface/controls/collectionview/populate-data?view=net-maui-10.0#load-data-incrementally)
- template selection 可以解决异构复用，但没有 stable business key；FlareUI 需要把 MAUI handler 架构与 React/Compose identity 语义组合起来。

## 4. Jetpack Compose LazyLayout 基线

AndroidX 源码固定到 [`6ae639f`](https://github.com/androidx/androidx/commit/6ae639fbaf432d072d7743936127a93c6e82aa2e)。

### 4.1 DSL、key 与 content type

`LazyListScope.item/items` 原生支持 stable unique `key` 与 `contentType`。key 缺省时 position 充当 identity；提供 key 后，在当前可见项之前插入/删除数据时，会尽量保持该 key 仍为首个可见项。相同 `contentType` 的 item composition 才被认为可兼容复用。[LazyDsl.kt](https://github.com/androidx/androidx/blob/6ae639fbaf432d072d7743936127a93c6e82aa2e/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/LazyDsl.kt#L33-L88)

这套接口是 FlareUI 最合适的 public DSL 基线：它符合 Kotlin/Compose 用户预期，也同时提供 native adapter 所需的 business key 与 reuse type。

### 4.2 composition 与 measure window

底层 `LazyLayout` 的定义就是“只 compose/layout 当前需要的 item”；measure pass 主动请求某个 item，即表示它当前需要被 composition。[LazyLayout.kt](https://github.com/androidx/androidx/blob/6ae639fbaf432d072d7743936127a93c6e82aa2e/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/layout/LazyLayout.kt#L32-L39)

LazyList measure 从已知 `firstVisibleItemIndex + scrollOffset` 开始：offset 为负时向前测量，再向后测量直到填满 viewport；完全离屏的已测 item 会从 visible set 移除。随后额外加入 beyond-bounds 与 pinned items。[LazyListMeasure.kt](https://github.com/androidx/androidx/blob/6ae639fbaf432d072d7743936127a93c6e82aa2e/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/LazyListMeasure.kt#L140-L266) [extra items](https://github.com/androidx/androidx/blob/6ae639fbaf432d072d7743936127a93c6e82aa2e/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/LazyListMeasure.kt#L321-L344)

FlareUI 不必把这套 measure 算法复制到 `RecyclerView` / `UICollectionView`，但应保留同样的状态输入输出：anchor index/key、anchor offset、visible item info、viewport range，以及可选 beyond-bounds/prefetch 请求。

### 4.3 slot reuse、状态与 prefetch

Compose 的 lazy slot 只有 `contentType` 相同才兼容；当前实现每种 type 最多保留 7 个 slot，常量解释为 RecyclerView 默认 5 个 pool + 2 个 cache。[LazyLayout.kt](https://github.com/androidx/androidx/blob/6ae639fbaf432d072d7743936127a93c6e82aa2e/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/layout/LazyLayout.kt#L148-L173) 这是实现细节，不应成为 FlareUI public contract 的固定数字。

item content factory 以 key 缓存 content lambda；当 item 移动时，它通过 key 重新查 index，并在 `SaveableStateProvider(key)` 下运行 item content。[LazyLayoutItemContentFactory.kt](https://github.com/androidx/androidx/blob/6ae639fbaf432d072d7743936127a93c6e82aa2e/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/layout/LazyLayoutItemContentFactory.kt#L48-L128) 这解释了为什么 stable key 能让可保存的 item state 跟随业务项，但普通未保存状态仍不应被当作无限期 cache。

`LazyListState` 暴露首个可见 index/offset 和 layout info，提供 `scrollToItem`、`requestScrollToItem`、`animateScrollToItem`；默认 saver 保存 index 与 offset。使用 custom key 时，它还会在头部插入/删除后寻找原首项的新位置。[LazyListState.kt](https://github.com/androidx/androidx/blob/6ae639fbaf432d072d7743936127a93c6e82aa2e/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/LazyListState.kt#L251-L264) [滚动 API](https://github.com/androidx/androidx/blob/6ae639fbaf432d072d7743936127a93c6e82aa2e/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/LazyListState.kt#L439-L473) [动画与 saver](https://github.com/androidx/androidx/blob/6ae639fbaf432d072d7743936127a93c6e82aa2e/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/LazyListState.kt#L631-L742)

prefetch API 区分只 precompose 和 precompose + premeasure；调度器按 `contentType` 维护耗时移动平均，只在 frame budget 足够时执行，urgent 请求可提升优先级，并支持 nested prefetch。[LazyLayoutPrefetchState.kt](https://github.com/androidx/androidx/blob/6ae639fbaf432d072d7743936127a93c6e82aa2e/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/layout/LazyLayoutPrefetchState.kt#L140-L226) [type-aware metrics](https://github.com/androidx/androidx/blob/6ae639fbaf432d072d7743936127a93c6e82aa2e/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/layout/LazyLayoutPrefetchState.kt#L339-L408) [frame-budget execution](https://github.com/androidx/androidx/blob/6ae639fbaf432d072d7743936127a93c6e82aa2e/compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/lazy/layout/LazyLayoutPrefetchState.kt#L565-L707)

### 4.4 已知限制

Compose 官方文档提醒：item 初始为 0 像素时，lazy layout 可能先 compose 全部 item；异步内容出现后尺寸变化又会使滚动位置失真。Paging placeholder 也应有接近真实内容的尺寸。[Lists 官方文档](https://developer.android.com/develop/ui/compose/lists)

因此 FlareUI 必须要求 lazy list 获得有界 main-axis viewport，并为未加载/未测量 item 提供非零、合理的 extent estimate；否则任何 window 算法都可能退化为全量工作。

## 5. 对 FlareUI 落地的研究结论

以下是由上述实现共同支持的设计约束，不是最终 API 定稿。

### 5.1 数据与 DSL

- `LazyColumn` / `LazyRow` 应共享同一个内部 `LazyList(orientation)` primitive 和 controller，避免两个方向形成两套协议。
- 公共 DSL 至少需要 `item`、`items(count)`、List/Array `items`、`itemsIndexed`；内部保存 interval，不 emit 全量 subtree。
- 每项应有 `key` 和 `contentType`。`key` 是业务 identity；`contentType` 是复用兼容性。允许缺省 key 时，只能明确文档化为 position fallback，并在 debug 模式检查重复 key。
- heterogeneous item 不要求不同 API；多个 interval / `contentType` 即可描述。不要从实际 widget tree shape 动态猜 type。

### 5.2 runtime seam

最关键的新抽象不是 `ScrollView`，而是“按 index/key 创建一个独立 item subtree”的能力。可行边界应满足：

- backend adapter 能请求、绑定、解绑某个 item，而无需让主 `FlareApplier` 持有全部 item child。
- 每个 realized item 有独立 composition 生命周期，或有等价的可重用 composition slot；离开窗口后是 dispose、进入有界 hot cache，还是保存可恢复 state，应由明确策略决定。
- 原生 cell pool 按 `contentType` 工作；业务 key 不用作 reuse id。
- list dataset 更新应批处理为 insert/remove/move/change 或 snapshot diff，不能把原生 adapter 暴露在 Compose apply transaction 的中间状态。Redwood 的 `onEndChanges` 批处理是直接先例。

### 5.3 viewport、测量与窗口

- native adapter 是实际 viewport 与 measurement 的 source of truth：Android `RecyclerView`、UIKit `UICollectionView`、AppKit `NSCollectionView`、Compose `LazyColumn/Row`。
- 公共反馈至少包括 first/last visible index、每个可见项 key/index/offset/size、viewport start/end、scroll direction/velocity（若平台可得）。
- 对未知尺寸同时支持 fixed extent / per-type estimate / measured cache。只用全局平均值会在异构列表中产生明显误差；只用 Redwood 单一 placeholder 尺寸更弱。
- cache window、每批 composition 数、prefetch 调度应分开。平台可以解释为 hint；不能要求所有 backend 使用相同常量。
- prepend/update 时用 `firstVisibleKey + intra-item offset` 作 anchor；仅保存 index 不足以抵抗重排。

### 5.4 回收与状态

建议在设计文档中明确区分：

| 层 | 寻址依据 | 目的 | 是否保留业务状态 |
| --- | --- | --- | --- |
| 尺寸缓存 | key，必要时 type | 估算 offset/extent | 否 |
| 原生 cell pool | contentType | 少创建 native container | 否 |
| composition slot pool | contentType/shape | 少做 subtree 初始化 | 不应隐式保证 |
| keyed state registry | key | item 离窗后恢复可保存状态 | 是，且必须有容量/可保存性边界 |

v1 可以采用保守契约：离开 composition window 后 item-local transient state 不保证保留，业务状态必须 hoist；同时预留 keyed saveable-state registry。不要让 RecyclerView/UICollectionView 恰好保留 view 的行为成为跨平台语义。

### 5.5 滚动与增量加载

- `LazyListState` 至少应观察 index、offset、visible items/layout info，并提供立即/动画滚到 index 的命令。
- alignment 应从一开始统一为 `Start/Center/End/MakeVisible` 或等价集合；远距离目标尺寸未知时，需要失败/估算后纠正的定义。
- 数据更新时至少定义 keep-anchor、keep-offset、keep-end 三类策略，覆盖 feed prepend、普通列表和 chat。
- `onEndReached` / prefetch callback 属于业务数据加载，不是 UI virtualization。需要文档化去重、并发与“加载中”控制，避免 MAUI 所示的无界 viewport 连续触发。

### 5.6 跨平台 adapter 建议

| Flare backend | 推荐底座 | 必须验证 |
| --- | --- | --- |
| Android View | `RecyclerView + LinearLayoutManager` | vertical/horizontal、stable anchor、view type、variable size、fast fling |
| UIKit | `UICollectionView` + list/compositional layout | **LazyRow 真正工作**、self-sizing、batch update、reuse id |
| AppKit | `NSCollectionView` | 两方向、self-sizing、selection/focus 与 reuse |
| Compose UI | Compose `LazyColumn/LazyRow` | global index 不被 loaded window 截断、key/type 透传、避免双重 windowing |

Compose backend 尤其要避免 Redwood 的问题：若公共层先截取 loaded window，再把局部数组交给 Compose LazyList，必须显式维护 global index/total extent；更简单的方向是让 Compose backend 直接消费同一个 logical item provider，由 Compose LazyList 自己决定 realized window。

### 5.7 最低验收矩阵

在宣布 `LazyColumn` / `LazyRow` 可用前，四个 backend 都应覆盖：

- 空列表、单项、百万逻辑项但只 realized 小窗口。
- 首屏、快速 fling、远距离 `scrollToItem`、重复滚到同一 index。
- prepend/append/insert/remove/move/change、数据缩短到当前 anchor 之前。
- stable key 跟随 reorder；重复 key 明确失败；无 key 的 position fallback 行为明确。
- 多 `contentType`、同 type 重绑、type 改变、动态高度/宽度。
- item 离窗再回来时，hoisted state 与可保存 state 的约定一致。
- list 获得无界 main-axis constraint 时 fail-fast 或明确退化，不能静默 compose 全量。
- nested lazy list、同方向嵌套、LazyRow in LazyColumn 的最小支持边界。
- Android、UIKit、AppKit、Compose 上 LazyColumn 与 LazyRow 对称通过；不能重复 Redwood “接口存在但 UIKit 横向为空实现”的缺陷。

## 固定源码版本

| 项目 | 提交 |
| --- | --- |
| Cash App Redwood | [`5c49a0bcc224b7fef10316bfdeb227639bcc42ec`](https://github.com/cashapp/redwood/commit/5c49a0bcc224b7fef10316bfdeb227639bcc42ec) |
| React | [`29d9d3184484b03cb0369e0494617207df777b7af`](https://github.com/facebook/react/commit/29d9d3184484b03cb0369e0494617207df777b7af) |
| React Native | [`d6ba88e16d1cc42c0e90a31eb6586586df2e9d5e`](https://github.com/facebook/react-native/commit/d6ba88e16d1cc42c0e90a31eb6586586df2e9d5e) |
| .NET MAUI | [`073c90c8911e2e7adc0082a13ef5a3a22d4b4d29`](https://github.com/dotnet/maui/commit/073c90c8911e2e7adc0082a13ef5a3a22d4b4d29) |
| AndroidX | [`6ae639fbaf432d072d7743936127a93c6e82aa2e`](https://github.com/androidx/androidx/commit/6ae639fbaf432d072d7743936127a93c6e82aa2e) |
