# 时间线媒体高度：客户端实现对比

核对日期：2026-11-03。范围为时间线中的单媒体，并补充与多图、视频、帖子详情有关的差异。
依据官方文档与官方开源仓库；没有运行客户端，默认分支源码不代表所有已发布或部署版本。

## 固定源码版本

| 客户端 | 本次核对提交 |
| --- | --- |
| Mastodon Web | [ddd646cd1fec741f92f3c1016218c49ee760a3c4](https://github.com/mastodon/mastodon/commit/ddd646cd1fec741f92f3c1016218c49ee760a3c4) |
| Bluesky 官方客户端 | [bc5942183413c28b3b0881165f9f5361e72e1446](https://github.com/bluesky-social/social-app/commit/bc5942183413c28b3b0881165f9f5361e72e1446) |
| Ice Cubes | [98098b9c4b5928f89f338203193c2bf40ab7f38c](https://github.com/Dimillian/IceCubesApp/commit/98098b9c4b5928f89f338203193c2bf40ab7f38c) |
| Phanpy | [a03425ea314c5be26d79e572a68a6d8f4b88e5de](https://github.com/cheeaun/phanpy/commit/a03425ea314c5be26d79e572a68a6d8f4b88e5de) |

下文 W 表示媒体可用宽度，r 表示媒体宽/高。Web 数值单位为 CSS px；Ice Cubes 使用逻辑点；Bluesky 按 React Native / Web 布局单位计算。

## Mastodon 官方 Web：固定限高，普通竖图随之缩窄

- 高度常量是 460px 和 566px；单图和视频通常使用 460，所在查询容器宽度超过 500px 时使用 566。单图使用媒体元数据比例与自动宽度，普通竖图受到限高时同步变窄。[常量](https://github.com/mastodon/mastodon/blob/ddd646cd1fec741f92f3c1016218c49ee760a3c4/app/javascript/styles/mastodon/basics.scss#L16-L17)、[单图样式](https://github.com/mastodon/mastodon/blob/ddd646cd1fec741f92f3c1016218c49ee760a3c4/app/javascript/styles/mastodon/components.scss#L6372-L6401)、[比例来源](https://github.com/mastodon/mastodon/blob/ddd646cd1fec741f92f3c1016218c49ee760a3c4/app/javascript/mastodon/components/media_gallery.jsx#L295-L312)、[视频样式](https://github.com/mastodon/mastodon/blob/ddd646cd1fec741f92f3c1016218c49ee760a3c4/app/javascript/styles/mastodon/components.scss#L6749-L6769)
- 单图还有 120px 最小宽度、64px 最小高度；图像内部使用 cover。因此极端比例不能一概承诺完整显示。帖子详情单图上限放宽至 2 × 566 = 1132px。多图使用独立网格，未沿用单图比例规则。[尺寸边界](https://github.com/mastodon/mastodon/blob/ddd646cd1fec741f92f3c1016218c49ee760a3c4/app/javascript/styles/mastodon/components.scss#L6372-L6401)、[图片缩放](https://github.com/mastodon/mastodon/blob/ddd646cd1fec741f92f3c1016218c49ee760a3c4/app/javascript/styles/mastodon/components.scss#L6520-L6537)、[多图比例](https://github.com/mastodon/mastodon/blob/ddd646cd1fec741f92f3c1016218c49ee760a3c4/app/javascript/mastodon/components/media_gallery.jsx#L295-L325)
- 设计曾使用 max(视口高度 × 0.60, 400px)，并居中展示缩窄视频；后续改为固定 460px、左对齐，再为宽容器提高到 566px。566 的说明是让常规桌面布局中的方图能够铺满宽度。这段历史不支持把最初的视口公式当作现行规则。[早期方案](https://github.com/mastodon/mastodon/pull/36966)、[固定高度与左对齐](https://github.com/mastodon/mastodon/pull/37032)、[宽容器调整](https://github.com/mastodon/mastodon/pull/37136)

## Bluesky 官方客户端：按列宽设上限，极长图另行裁切

- 普通单图先将容器比例限制到至少 1:2，再限制外层高度。原生端的单图高度最多 W × 16/9，Web 最多 W；内部卡片依据该比例缩窄，按布局起始边排列。因此一般竖图可保持比例，源比例小于 1:2 的极长图会裁切，并显示展开提示。[高度与宽度](https://github.com/bluesky-social/social-app/blob/bc5942183413c28b3b0881165f9f5361e72e1446/src/components/images/AutoSizedImage.tsx#L22-L68)、[裁切规则](https://github.com/bluesky-social/social-app/blob/bc5942183413c28b3b0881165f9f5361e72e1446/src/components/images/AutoSizedImage.tsx#L104-L158)、[裁切提示](https://github.com/bluesky-social/social-app/blob/bc5942183413c28b3b0881165f9f5361e72e1446/src/components/images/AutoSizedImage.tsx#L160-L201)
- 视频原生端的高度预算更小，为 W × 14/9；Web 仍使用 W 高度预算。Web 竖视频卡片只有在控件能放下时才缩窄；未知比例、比 1:2 更长，或计算宽度小于 280px 时，保留满宽容器，内部视频使用 contain。[原生视频](https://github.com/bluesky-social/social-app/blob/bc5942183413c28b3b0881165f9f5361e72e1446/src/components/Post/Embed/VideoEmbed/index.tsx#L41-L70)、[Web 视频卡片](https://github.com/bluesky-social/social-app/blob/bc5942183413c28b3b0881165f9f5361e72e1446/src/components/Post/Embed/VideoEmbed/index.web.tsx#L150-L178)、[Web 视频缩放](https://github.com/bluesky-social/social-app/blob/bc5942183413c28b3b0881165f9f5361e72e1446/src/components/Post/Embed/VideoEmbed/VideoEmbedInner/VideoEmbedInnerWeb.tsx#L83-L88)
- 详情中的主帖单图走另一分支，允许比例放宽至 1:4，仍不是任意长图完整展示。多图有独立网格/画廊分支，选择受附件类型、数量与功能开关影响，不能直接套单图规则。[详情分支](https://github.com/bluesky-social/social-app/blob/bc5942183413c28b3b0881165f9f5361e72e1446/src/components/Post/Embed/ImageEmbed.tsx#L112-L137)、[详情比例与布局](https://github.com/bluesky-social/social-app/blob/bc5942183413c28b3b0881165f9f5361e72e1446/src/components/images/AutoSizedImage.tsx#L227-L253)、[多图分支选择](https://github.com/bluesky-social/social-app/blob/bc5942183413c28b3b0881165f9f5361e72e1446/src/components/Post/Embed/ImageEmbed.tsx#L44-L50)

## Ice Cubes：固定高度预算，图片可裁切

- 普通单媒体先根据可用宽度与源比例推导高度，最后单独限制高度不超过 450pt，宽度至少 200pt。图片使用 scaledToFill 和 clipped；高度达到上限后没有同步缩窄宽度，超长图会裁切。[单媒体调用](https://github.com/Dimillian/IceCubesApp/blob/98098b9c4b5928f89f338203193c2bf40ab7f38c/Packages/StatusKit/Sources/StatusKit/Row/Subviews/StatusRowMediaPreviewView.swift#L34-L60)、[图片显示](https://github.com/Dimillian/IceCubesApp/blob/98098b9c4b5928f89f338203193c2bf40ab7f38c/Packages/StatusKit/Sources/StatusKit/Row/Subviews/StatusRowMediaPreviewView.swift#L437-L478)、[尺寸计算](https://github.com/Dimillian/IceCubesApp/blob/98098b9c4b5928f89f338203193c2bf40ab7f38c/Packages/StatusKit/Sources/StatusKit/Row/Subviews/StatusRowMediaPreviewView.swift#L510-L552)
- 普通多图横向滚动，高度 300pt；紧凑显示多图高度 100pt、单媒体 200×200。多图图片同样允许裁切。[分档](https://github.com/Dimillian/IceCubesApp/blob/98098b9c4b5928f89f338203193c2bf40ab7f38c/Packages/StatusKit/Sources/StatusKit/Row/Subviews/StatusRowMediaPreviewView.swift#L34-L70)、[多图项目](https://github.com/Dimillian/IceCubesApp/blob/98098b9c4b5928f89f338203193c2bf40ab7f38c/Packages/StatusKit/Sources/StatusKit/Row/Subviews/StatusRowMediaPreviewView.swift#L129-L170)
- 视频共用限高外框，内部是系统 VideoPlayer。此调用没有显式指定视频缩放模式，不能仅据图片 fill 判断视频画面也裁切。[媒体分流](https://github.com/Dimillian/IceCubesApp/blob/98098b9c4b5928f89f338203193c2bf40ab7f38c/Packages/StatusKit/Sources/StatusKit/Row/Subviews/StatusRowMediaPreviewView.swift#L439-L457)、[播放器](https://github.com/Dimillian/IceCubesApp/blob/98098b9c4b5928f89f338203193c2bf40ab7f38c/Packages/MediaUI/Sources/MediaUI/MediaUIAttachmentVideoView.swift#L203-L230)

## Phanpy：视口比例限高，同时推导更窄的宽度

- 普通单媒体横图最高 33vh，竖图和方图最高 45vh，同时受源媒体尺寸约束。宽度由源比例、窗口高度、源宽度与容器宽度共同约束；横图推导宽度时还使用 160px 高度下限，即 r × max(160px, 0.33 × window.innerHeight)，竖图使用 r × 0.45 × window.innerHeight。它使用窗口视口，没有扣除时间线工具栏，也没有 600px 固定上限。[尺寸和方向](https://github.com/cheeaun/phanpy/blob/a03425ea314c5be26d79e572a68a6d8f4b88e5de/src/components/media.jsx#L113-L132)、[推导宽度](https://github.com/cheeaun/phanpy/blob/a03425ea314c5be26d79e572a68a6d8f4b88e5de/src/components/media.jsx#L196-L213)、[单媒体样式](https://github.com/cheeaun/phanpy/blob/a03425ea314c5be26d79e572a68a6d8f4b88e5de/src/components/status.css#L1343-L1365)
- 仍保留 88px 最小宽高；图片默认 cover，极端比例可能裁切。视频使用 scale-down。因此它接近“视口限高 + 缩窄”，但不满足任何比例都完整显示的严格要求。[最小尺寸常量](https://github.com/cheeaun/phanpy/blob/a03425ea314c5be26d79e572a68a6d8f4b88e5de/src/index.css#L238)、[最小尺寸](https://github.com/cheeaun/phanpy/blob/a03425ea314c5be26d79e572a68a6d8f4b88e5de/src/components/status.css#L1343-L1352)、[图片和视频](https://github.com/cheeaun/phanpy/blob/a03425ea314c5be26d79e572a68a6d8f4b88e5de/src/components/status.css#L1472-L1513)
- 引用卡片单媒体最高 160px，多图采用独立网格。详情主帖使用 large 样式，单媒体规则放宽至 min(源高度, 80vh)；父容器另写的 60vh 被 max-height: none !important 覆盖。此处是 CSS 级联结论，未进行浏览器测量。[详情主帖](https://github.com/cheeaun/phanpy/blob/a03425ea314c5be26d79e572a68a6d8f4b88e5de/src/pages/status.jsx#L1085-L1091)、[场景样式](https://github.com/cheeaun/phanpy/blob/a03425ea314c5be26d79e572a68a6d8f4b88e5de/src/components/status.css#L1366-L1391)、[父容器覆盖](https://github.com/cheeaun/phanpy/blob/a03425ea314c5be26d79e572a68a6d8f4b88e5de/src/components/status.css#L1209-L1213)、[网格规则](https://github.com/cheeaun/phanpy/blob/a03425ea314c5be26d79e572a68a6d8f4b88e5de/src/components/status.css#L1198-L1311)

## X：官方说明只保证特定单图比例完整展示

官方帮助说明，单张照片在宽高比 3:4 至 2:1 范围内完整显示。该页没有给出超长图或竖视频的统一视口系数、固定高度上限；本次没有登录实测，因此不推断其精确裁切或限高公式。[X 官方说明](https://help.x.com/en/using-x/posting-gifs-and-pictures)

## 对 Flare 拟议规则的含义

当前确定公式为 maxHeight = min(时间线有效可见高度 × 0.5, 420)，并要求保持真实比例、完整显示、超限时宽高一起缩小；极端长图预览允许居中裁剪。

该规则由原生端“限制单附件高度”开关控制，默认关闭，支持全局默认与时间线覆盖；只有“展开媒体尺寸”同时开启时生效。父开关关闭时隐藏子开关并保留其值，出现/隐藏沿用设置页动画。关闭限高恢复原有宽高布局，切换保留阅读位置与敏感媒体展开状态。旧配置缺少此字段时默认关闭；Web 不显示或应用该选项，编辑设置时保留已有的原生端选择。

以下是基于上述实现的产品推论：

1. 在本次核对的客户端中，没有发现完全一致的公式。Phanpy 可参考视口计算，Mastodon 可参考普通竖图限高后的缩窄；0.5 和 420 是 Flare 经截图确认后调整的参数。
2. 严格完整显示比这些客户端更强：Mastodon/Phanpy 有最小尺寸与 cover 的边界，Bluesky 给极长图设置裁切阈值，Ice Cubes 明确允许图片裁切。
3. 视频需要单独验证控件空间。Bluesky Web 的 280px 卡片宽度兜底表明，媒体能按比例缩窄并不意味着播放控件还能正常排布。
4. 其他客户端的场景预算各不相同。Flare 最终选择统一帖子媒体基础组件中的单附件规则，覆盖时间线、详情和引用帖；多附件、个人媒体页、全屏查看器与 Web 不变。
5. Flare 允许极端长图预览按最低 9:21 比例居中裁剪，视频保留原比例；控件需适应缩小后的媒体边界，不能靠加宽外框突破高度上限。

以上客户端对比仅基于源码和官方文档，未进行第三方客户端设备、浏览器或线上版本验证。
