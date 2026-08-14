type JsonPrimitive = string | number | boolean | null;
type JsonValue = JsonPrimitive | JsonValue[] | { [key: string]: JsonValue };

interface PluginInvocationContextV1 {
  readonly pluginId: string;
  readonly platformId: string;
  readonly packageHash: string;
  readonly scope: "detector" | "login" | "account" | "guest";
  readonly origin: string;
  readonly accountId?: string;
  readonly locale: string;
  readonly approvedOrigins: readonly string[];
  readonly credentialAvailable: boolean;
  readonly assetHandles: readonly string[];
}

interface PluginDefinitionV1 {
  detector?: { detect(request: DetectorRequestV1, context: PluginInvocationContextV1): MaybePromise<DetectorResultV1> };
  login?: Record<string, LoginServiceV1>;
  capabilities?: {
    timeline?: { page(request: TimelinePageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<PostV1>> };
    search?: Partial<SearchServiceV1>;
    profile?: Partial<ProfileServiceV1>;
    post?: Partial<PostServiceV1>;
    relation?: Partial<RelationServiceV1>;
    compose?: { publish(request: ComposeRequestV1, context: PluginInvocationContextV1): MaybePromise<ComposeResultV1> };
    notification?: Partial<NotificationServiceV1>;
    list?: Partial<ListServiceV1>;
    directMessage?: Partial<DirectMessageServiceV1>;
    article?: Partial<ArticleServiceV1>;
    gallery?: Partial<GalleryServiceV1>;
    tabCatalog?: { page(request: PageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<TimelineSectionV1>> };
  };
}

interface LoginServiceV1 {
  begin(request: LoginBeginRequestV1, context: PluginInvocationContextV1): MaybePromise<LoginTransitionV1>;
  resume?(request: LoginResumeRequestV1, context: PluginInvocationContextV1): MaybePromise<LoginTransitionV1>;
  check?(request: CookieCheckRequestV1, context: PluginInvocationContextV1): MaybePromise<LoginTransitionV1>;
}

interface SearchServiceV1 {
  posts(request: SearchRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<PostV1>>;
  profiles(request: SearchRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<ProfileV1>>;
  discoverPosts(request: SearchRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<PostV1>>;
  discoverProfiles(request: SearchRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<ProfileV1>>;
  discoverHashtags(request: SearchRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<HashtagV1>>;
}

interface ProfileServiceV1 {
  byId(request: EntityRequestV1, context: PluginInvocationContextV1): MaybePromise<ProfileV1>;
  byHandle(request: HandleRequestV1, context: PluginInvocationContextV1): MaybePromise<ProfileV1>;
  timeline(request: ProfileTimelineRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<PostV1>>;
  following(request: EntityPageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<ProfileV1>>;
  followers(request: EntityPageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<ProfileV1>>;
}

interface PostServiceV1 {
  detail(request: EntityRequestV1, context: PluginInvocationContextV1): MaybePromise<PostV1>;
  context(request: EntityPageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<PostV1>>;
  delete(request: EntityRequestV1, context: PluginInvocationContextV1): MaybePromise<MutationResultV1>;
  mutate(request: MutationRequestV1, context: PluginInvocationContextV1): MaybePromise<MutationResultV1>;
}

interface RelationServiceV1 {
  state(request: EntityRequestV1, context: PluginInvocationContextV1): MaybePromise<RelationV1>;
  mutate(request: MutationRequestV1, context: PluginInvocationContextV1): MaybePromise<MutationResultV1>;
}
interface NotificationServiceV1 {
  page(request: NotificationPageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<NotificationV1>>;
  badge(request: EmptyRequestV1, context: PluginInvocationContextV1): MaybePromise<CountResultV1>;
}
interface ListServiceV1 {
  page(request: PageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<SocialListV1>>;
  detail(request: EntityRequestV1, context: PluginInvocationContextV1): MaybePromise<SocialListV1>;
  create(request: ListMutationRequestV1, context: PluginInvocationContextV1): MaybePromise<SocialListV1>;
  update(request: ListMutationRequestV1, context: PluginInvocationContextV1): MaybePromise<SocialListV1>;
  delete(request: ListMutationRequestV1, context: PluginInvocationContextV1): MaybePromise<MutationResultV1>;
  timeline(request: EntityPageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<PostV1>>;
  members(request: EntityPageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<ProfileV1>>;
  memberships(request: EntityPageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<SocialListV1>>;
  addMember(request: ListMemberRequestV1, context: PluginInvocationContextV1): MaybePromise<ProfileV1>;
  removeMember(request: ListMemberRequestV1, context: PluginInvocationContextV1): MaybePromise<MutationResultV1>;
}
interface DirectMessageServiceV1 {
  rooms(request: PageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<DirectMessageRoomV1>>;
  room(request: EntityRequestV1, context: PluginInvocationContextV1): MaybePromise<DirectMessageRoomV1>;
  messages(request: DirectMessagePageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<DirectMessageV1>>;
  send(request: DirectMessageSendRequestV1, context: PluginInvocationContextV1): MaybePromise<DirectMessageV1>;
  delete(request: DirectMessageDeleteRequestV1, context: PluginInvocationContextV1): MaybePromise<MutationResultV1>;
  leave(request: EntityRequestV1, context: PluginInvocationContextV1): MaybePromise<MutationResultV1>;
  create(request: EntityRequestV1, context: PluginInvocationContextV1): MaybePromise<DirectMessageRoomV1>;
  badge(request: EmptyRequestV1, context: PluginInvocationContextV1): MaybePromise<CountResultV1>;
  canSend(request: EntityRequestV1, context: PluginInvocationContextV1): MaybePromise<BooleanResultV1>;
}
interface ArticleServiceV1 {
  detail(request: EntityRequestV1, context: PluginInvocationContextV1): MaybePromise<ArticleV1>;
  comments(request: EntityPageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<PostV1>>;
}
interface GalleryServiceV1 {
  detail(request: EntityRequestV1, context: PluginInvocationContextV1): MaybePromise<GalleryV1>;
  comments(request: EntityPageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<PostV1>>;
  recommendations(request: EntityPageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<PostV1>>;
}

interface DetectorRequestV1 { origin: string }
type DetectorMatchV1 = "none" | "compatible" | "exact";
interface DetectorResultV1 {
  match: DetectorMatchV1;
  canonicalOrigin: string;
  software?: string;
  instance?: InstanceMetadataV1;
}
interface InstanceMetadataV1 {
  domain: string;
  title?: string;
  description?: string;
  iconUrl?: string;
  bannerUrl?: string;
  usersCount?: number;
  registrationEnabled?: boolean;
}

interface LoginBeginRequestV1 {
  methodId: string;
  origin: string;
  flowId: string;
  state?: string;
  redirectUri?: string;
  values: Record<string, string>;
}
interface LoginResumeRequestV1 {
  methodId: string;
  origin: string;
  flowId: string;
  redirectUri?: string;
  callbackParameters: Record<string, string>;
  pendingPayload?: JsonValue;
}
interface CookieCheckRequestV1 {
  methodId: string;
  origin: string;
  flowId: string;
  cookies: { cookies: CookieValueV1[] };
}
interface CookieValueV1 { sourceUrl: string; name: string; value: string }
type LoginTransitionV1 =
  | { type: "pending" }
  | { type: "externalBrowser"; url: string; pendingPayload: JsonValue }
  | { type: "webCookie"; startUrl: string }
  | { type: "success"; value: LoginSuccessV1 };
interface LoginSuccessV1 {
  accountId: string;
  origin: string;
  credential: JsonValue;
  profile: ProfileV1;
  capabilities: Record<string, string[]>;
  composeConfig?: ComposeConfigV1;
}

interface EntityKeyV1 { id: string; host: string }
interface RichTextV1 { format?: "plain" | "html"; value: string }
interface ProfileFieldV1 { name: string; value: RichTextV1 }
interface ProfileV1 {
  key: EntityKeyV1;
  handle: string;
  displayName: string;
  avatarUrl?: string;
  bannerUrl?: string;
  description?: RichTextV1;
  url?: string;
  followersCount?: number;
  followingCount?: number;
  postsCount?: number;
  locked?: boolean;
  bot?: boolean;
  fields?: ProfileFieldV1[];
  entityToken?: string;
  actions?: ActionDescriptorV1[];
}
type MediaTypeV1 = "image" | "video" | "gif" | "audio";
interface MediaV1 {
  id: string;
  type: MediaTypeV1;
  url: string;
  previewUrl?: string;
  description?: string;
  width?: number;
  height?: number;
  durationMillis?: number;
}
type VisibilityV1 = "public" | "unlisted" | "followers" | "direct";
type SemanticActionV1 =
  | "Favourite" | "Unfavourite" | "Repost" | "Unrepost" | "Bookmark" | "Unbookmark"
  | "Delete" | "Reply" | "Follow" | "Unfollow" | "Block" | "Unblock" | "Mute" | "Unmute";
interface ActionDescriptorV1 {
  action: SemanticActionV1;
  enabled?: boolean;
  active?: boolean;
  count?: number;
  actionToken?: string;
}
interface PostV1 {
  key: EntityKeyV1;
  author: ProfileV1;
  createdAt: string;
  content: RichTextV1;
  url?: string;
  media?: MediaV1[];
  repost?: PostV1;
  replyTo?: EntityKeyV1;
  spoilerText?: string;
  sensitive?: boolean;
  visibility?: VisibilityV1;
  favouritesCount?: number;
  repostsCount?: number;
  repliesCount?: number;
  entityToken?: string;
  actions?: ActionDescriptorV1[];
}
interface HashtagV1 { name: string; url?: string }

type PageDirectionV1 = "refresh" | "older" | "newer";
interface PageRequestV1 {
  direction: PageDirectionV1;
  limit: number;
  cursor?: string;
  parameters: Record<string, string>;
}
interface PageV1<T> {
  items?: T[];
  olderCursor?: string;
  newerCursor?: string;
  endReached?: boolean;
}
interface TimelinePageRequestV1 { timelineId: string; page: PageRequestV1; parameters: Record<string, string> }
interface SearchRequestV1 { query: string; page: PageRequestV1 }
interface EntityRequestV1 { key: EntityKeyV1; entityToken?: string }
interface EntityPageRequestV1 { key: EntityKeyV1; page: PageRequestV1; entityToken?: string }
interface HandleRequestV1 { handle: string; host: string }
interface ProfileTimelineRequestV1 {
  profile: EntityKeyV1;
  tabId?: string;
  page: PageRequestV1;
  parameters: Record<string, string>;
}
interface MutationRequestV1 {
  key: EntityKeyV1;
  action: SemanticActionV1;
  actionToken?: string;
  parameters: Record<string, string>;
}
type MutationResultV1 =
  | { type: "updatedPost"; post: PostV1 }
  | { type: "updatedProfile"; profile: ProfileV1 }
  | { type: "updatedRelation"; relation: RelationV1 }
  | { type: "deleted" }
  | { type: "invalidate"; keys: EntityKeyV1[] }
  | { type: "noChange" };

interface RelationV1 {
  profileKey: EntityKeyV1;
  following?: boolean;
  followedBy?: boolean;
  blocking?: boolean;
  muting?: boolean;
  actionTokens?: Partial<Record<SemanticActionV1, string>>;
}
type NotificationKindV1 = "Mention" | "Reply" | "Favourite" | "Repost" | "Follow" | "Other";
interface NotificationV1 {
  id: string;
  createdAt: string;
  kind: NotificationKindV1;
  actor?: ProfileV1;
  post?: PostV1;
  message?: RichTextV1;
}
interface SocialListV1 { id: string; title: string; memberCount?: number; entityToken?: string }
interface DirectMessageRoomV1 {
  key: EntityKeyV1;
  title: string;
  participants: ProfileV1[];
  lastMessage?: DirectMessageV1;
  unreadCount?: number;
  entityToken?: string;
}
interface DirectMessageV1 {
  key: EntityKeyV1;
  roomKey: EntityKeyV1;
  sender: ProfileV1;
  createdAt: string;
  content: RichTextV1;
  fromCurrentAccount?: boolean;
  entityToken?: string;
}
interface ArticleV1 {
  key: EntityKeyV1;
  title: string;
  author?: ProfileV1;
  createdAt?: string;
  content: RichTextV1;
  url?: string;
  coverUrl?: string;
}
interface GalleryV1 {
  key: EntityKeyV1;
  title: string;
  author?: ProfileV1;
  createdAt: string;
  content?: RichTextV1;
  url: string;
  images: MediaV1[];
  orientation?: "horizontal" | "vertical";
  entityToken?: string;
  actions?: ActionDescriptorV1[];
}
type HostIconV1 =
  | "Home" | "Notification" | "Search" | "Profile" | "Local" | "World" | "Featured"
  | "Bookmark" | "Heart" | "List" | "Messages" | "Channel" | "Like" | "Repost"
  | "Reply" | "Delete" | "Follow" | "Block" | "Mute" | "Info";
interface TimelineDescriptorV1 {
  id: string;
  title: WireTextV1;
  icon: HostIconV1;
  display?: "List" | "Grid";
  parameters?: Record<string, string>;
}
interface TimelineSectionV1 { id: string; title: WireTextV1; timelines: PageV1<TimelineDescriptorV1> }
type EmptyRequestV1 = Record<string, never>;
interface NotificationPageRequestV1 { filterId?: string; page: PageRequestV1 }
interface ListMutationRequestV1 { id?: string; title?: string; entityToken?: string }
interface ListMemberRequestV1 { listId: string; profileKey: EntityKeyV1; entityToken?: string }
interface DirectMessageSendRequestV1 { roomKey: EntityKeyV1; text: string; roomEntityToken?: string }
interface DirectMessagePageRequestV1 { roomKey: EntityKeyV1; page: PageRequestV1; roomEntityToken?: string }
interface DirectMessageDeleteRequestV1 {
  roomKey: EntityKeyV1;
  messageKey: EntityKeyV1;
  roomEntityToken?: string;
  messageEntityToken?: string;
}
interface CountResultV1 { value: number }
interface BooleanResultV1 { value: boolean }

interface ComposeConfigV1 {
  text?: { maxLength: number };
  media?: {
    minCountForNew?: number;
    maxCount: number;
    maxBytes: number;
    supportedMimeTypes?: string[];
    altTextMaxLength?: number;
    canSensitive?: boolean;
  };
  visibility?: { allowed: VisibilityV1[]; default?: VisibilityV1 };
  contentWarning?: boolean;
  poll?: { maxOptions: number };
  language?: { maxCount: number };
}
interface ComposeAssetV1 { handle: string; fileName?: string; mimeType?: string; description?: string }
interface ComposeRequestV1 {
  text: string;
  visibility: VisibilityV1;
  languages: string[];
  assets: ComposeAssetV1[];
  sensitive: boolean;
  spoilerText?: string;
  replyTo?: EntityKeyV1;
}
interface ComposeResultV1 { post: PostV1 }

type HttpAuthorizationV1 =
  | { type: "bearer"; token: string }
  | { type: "basic"; username: string; password: string };
type HttpBodyV1 =
  | { type: "json"; value: JsonValue }
  | { type: "text"; value: string; contentType?: string }
  | { type: "form"; values: Record<string, string> }
  | { type: "multipart"; parts: HttpMultipartPartV1[] };
type HttpMultipartPartV1 =
  | { type: "text"; name: string; value: string; contentType?: string }
  | { type: "asset"; name: string; handle: string; fileName?: string; contentType?: string };
interface HttpRequestV1 {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE" | "HEAD";
  url: string;
  headers?: Record<string, string>;
  authorization?: HttpAuthorizationV1;
  cookies?: { name: string; value: string }[];
  body?: HttpBodyV1;
  timeoutMillis?: number;
}
interface HttpResponseV1 { status: number; headers: Record<string, string[]>; body: string }

type PluginErrorCodeV1 =
  | "AuthenticationRequired" | "NotFound" | "Validation" | "RateLimited" | "Network"
  | "Remote" | "Unsupported" | "InvalidResponse" | "Cancelled";
interface WireTextV1 { value?: string; key?: string; fallback?: string; args?: Record<string, JsonPrimitive> }
interface PluginErrorV1 {
  code: PluginErrorCodeV1;
  message: WireTextV1;
  retryAfterSeconds?: number;
  remoteCode?: string;
}

interface FlareHostV1 {
  readonly http: { request(request: HttpRequestV1): Promise<HttpResponseV1> };
  readonly credential: {
    read(): Promise<JsonValue>;
    replace(value: JsonValue): Promise<boolean>;
  };
  readonly crypto: {
    randomHex(size: number): Promise<string>;
    uuid(): Promise<string>;
    sha256(value: string): Promise<string>;
  };
  readonly locale: { current(): Promise<string> };
  error(value: PluginErrorV1): Error;
}

type MaybePromise<T> = T | Promise<T>;

declare function definePlugin(plugin: PluginDefinitionV1): void;
declare const flare: FlareHostV1;
