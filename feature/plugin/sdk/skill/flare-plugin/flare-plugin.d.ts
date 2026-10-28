/** Flare Social Plugin API v1. Ambient editor types; do not import at runtime.
 * See ABI.md and references/ for validation, defaults, and operation dependencies.
 * Optional service members do not relax a declared capability's required method set.
 */
type JsonPrimitive = string | number | boolean | null;
type JsonValue = JsonPrimitive | JsonValue[] | { [key: string]: JsonValue };

/** Deeply frozen per-call Host metadata. Globals are disposable; origin is normalized HTTPS. */
interface PluginInvocationContextV1 {
  readonly pluginId: string;
  readonly platformId: string;
  readonly packageHash: string;
  readonly scope: "detector" | "login" | "account" | "guest";
  readonly origin: string;
  /** Present for account calls; login and guest calls do not expose an account credential. */
  readonly accountId?: string;
  readonly locale: string;
  /** HTTP allowlist for this scope; authOrigins expands login only, not account/guest access. */
  readonly approvedOrigins: readonly string[];
  readonly credentialAvailable: boolean;
  readonly assetHandles: readonly string[];
}

/** Register once, synchronously. Run Host API calls only inside these methods.
 * Manifest operation names, registration paths, and login negotiation must agree.
 * @see references/capabilities.md
 */
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

/** Key this service by the manifest login method ID.
 * OAuth: begin -> externalBrowser/success, resume -> success.
 * Password/CredentialImport/Form: begin -> success.
 * WebCookie: begin -> webCookie, check -> pending/success.
 * @see references/authentication.md
 */
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
  delete(request: EntityRequestV1, context: PluginInvocationContextV1): MaybePromise<DeleteResultV1>;
  mutate(request: MutationRequestV1, context: PluginInvocationContextV1): MaybePromise<PostMutationResultV1>;
}

interface RelationServiceV1 {
  state(request: EntityRequestV1, context: PluginInvocationContextV1): MaybePromise<RelationV1>;
  mutate(request: MutationRequestV1, context: PluginInvocationContextV1): MaybePromise<RelationMutationResultV1>;
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
  delete(request: ListMutationRequestV1, context: PluginInvocationContextV1): MaybePromise<DeleteResultV1>;
  timeline(request: EntityPageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<PostV1>>;
  members(request: EntityPageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<ProfileV1>>;
  memberships(request: EntityPageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<SocialListV1>>;
  addMember(request: ListMemberRequestV1, context: PluginInvocationContextV1): MaybePromise<ProfileV1>;
  removeMember(request: ListMemberRequestV1, context: PluginInvocationContextV1): MaybePromise<DeleteResultV1>;
}
interface DirectMessageServiceV1 {
  rooms(request: PageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<DirectMessageRoomV1>>;
  room(request: EntityRequestV1, context: PluginInvocationContextV1): MaybePromise<DirectMessageRoomV1>;
  messages(request: DirectMessagePageRequestV1, context: PluginInvocationContextV1): MaybePromise<PageV1<DirectMessageV1>>;
  send(request: DirectMessageSendRequestV1, context: PluginInvocationContextV1): MaybePromise<DirectMessageV1>;
  delete(request: DirectMessageDeleteRequestV1, context: PluginInvocationContextV1): MaybePromise<DeleteResultV1>;
  leave(request: EntityRequestV1, context: PluginInvocationContextV1): MaybePromise<DeleteResultV1>;
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
/** Return none unless the website matches verified software/API evidence. */
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
  /** Host-persisted OAuth data from begin; globals do not survive the flow. At most 64 KiB JSON. */
  pendingPayload?: JsonValue;
}
interface CookieCheckRequestV1 {
  methodId: string;
  origin: string;
  flowId: string;
  cookies: { cookies: CookieValueV1[] };
}
/** sourceUrl is the declared probe URL; do not merge Cookies across probes/origins blindly. */
interface CookieValueV1 { sourceUrl: string; name: string; value: string }
type LoginTransitionV1 =
  | { type: "pending" }
  | { type: "externalBrowser"; url: string; pendingPayload: JsonValue }
  | { type: "webCookie"; startUrl: string }
  | { type: "success"; value: LoginSuccessV1 };
/** accountId must equal profile.key.id; origin/profile host must match the selected instance.
 * Negotiate declared complete capability sets with at least one displayable capability.
 * The Host persists credential (at most 64 KiB JSON) and the authenticated profile.
 */
interface LoginSuccessV1 {
  accountId: string;
  origin: string;
  credential: JsonValue;
  profile: ProfileV1;
  capabilities: Record<string, string[]>;
  composeConfig?: ComposeConfigV1;
}

/** Stable ID in the selected API namespace; host is an HTTPS authority without scheme/path. */
interface EntityKeyV1 { id: string; host: string }
/** format defaults to plain. HTML is sanitized; remote HTML images also need mediaNetwork. */
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
/** HTTPS URLs only; automatic loading requires permissions.mediaNetwork. Preserve alt text. */
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
/** Semantic Host control. enabled defaults true; active/count remain meaningful when disabled.
 * actionToken is bounded opaque mutation data, not a page cursor or credential store.
 */
interface ActionDescriptorV1 {
  action: SemanticActionV1;
  enabled?: boolean;
  active?: boolean;
  count?: number;
  actionToken?: string;
}
type PostDetailTypeV1 = "post" | "article" | "gallery" | "link";
interface PostV1 {
  key: EntityKeyV1;
  author: ProfileV1;
  createdAt: string;
  content: RichTextV1;
  /** Required for detailType "link"; absolute HTTPS URL without URL credentials. */
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
  /** Defaults to "post"; internal details use key, while "link" opens url in the browser. */
  detailType?: PostDetailTypeV1;
}
interface HashtagV1 { name: string; url?: string }

type PageDirectionV1 = "refresh" | "older" | "newer";
/** limit is 1..100. refresh starts without a continuation; older/newer use their own cursors. */
interface PageRequestV1 {
  direction: PageDirectionV1;
  limit: number;
  cursor?: string;
  parameters: Record<string, string>;
}
/** items defaults []; endReached defaults false and suppresses olderCursor only.
 * Empty pages may still advance cursors. At most 200 items; cursors at most 4096 characters.
 * @see references/wire-data.md
 */
interface PageV1<T> {
  items?: T[];
  olderCursor?: string;
  newerCursor?: string;
  endReached?: boolean;
}
interface TimelinePageRequestV1 { timelineId: string; page: PageRequestV1; parameters: Record<string, string> }
interface SearchRequestV1 { query: string; page: PageRequestV1 }
/** entityToken is opaque entity context (at most 8192 characters), not a paging cursor. */
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
/** Each operation accepts only its narrowed result union below. Remote failure should throw.
 * Updated entities must match the requested key; invalidation keys must be unique.
 */
type MutationResultV1 =
  | { type: "updatedPost"; post: PostV1 }
  | { type: "updatedProfile"; profile: ProfileV1 }
  | { type: "updatedRelation"; relation: RelationV1 }
  | { type: "deleted" }
  | { type: "invalidate"; keys: EntityKeyV1[] }
  | { type: "noChange" };
type DeleteResultV1 = Extract<MutationResultV1, { type: "deleted" | "noChange" }>;
type PostMutationResultV1 = Extract<MutationResultV1, { type: "updatedPost" | "deleted" | "invalidate" | "noChange" }>;
type RelationMutationResultV1 = Extract<MutationResultV1, { type: "updatedRelation" | "noChange" }>;

/** Return full authoritative relation state after mutation, including replacement actionTokens.
 * Omitted booleans default false; partial updatedRelation can clear existing state.
 */
interface RelationV1 {
  profileKey: EntityKeyV1;
  following?: boolean;
  followedBy?: boolean;
  blocking?: boolean;
  muting?: boolean;
  actionTokens?: Partial<Record<SemanticActionV1, string>>;
}
type NotificationKindV1 = "Mention" | "Reply" | "Favourite" | "Repost" | "Follow" | "Other";
/** id identifies the event, not its post; multiple events on one post remain distinct. */
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
/** Poll with page.newerCursor from the previous result, not roomEntityToken. */
interface DirectMessagePageRequestV1 { roomKey: EntityKeyV1; page: PageRequestV1; roomEntityToken?: string }
interface DirectMessageDeleteRequestV1 {
  roomKey: EntityKeyV1;
  messageKey: EntityKeyV1;
  roomEntityToken?: string;
  messageEntityToken?: string;
}
interface CountResultV1 { value: number }
interface BooleanResultV1 { value: boolean }

/** Manifest defaults are required for compose.publish. Login config is merged by section:
 * shared limits are narrowed; a section present on only one side is retained.
 * @see references/manifest.md#compose-defaults
 */
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
/** Host-owned per-invocation upload handle. Forward via multipart; bytes never enter JS. */
interface ComposeAssetV1 { handle: string; fileName?: string; mimeType?: string; description?: string }
interface ComposePollV1 { options: string[]; expiresInSeconds: number; multiple?: boolean }
interface ComposeRequestV1 {
  text: string;
  visibility: VisibilityV1;
  languages: string[];
  assets: ComposeAssetV1[];
  sensitive: boolean;
  spoilerText?: string;
  replyTo?: EntityKeyV1;
  poll?: ComposePollV1;
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
/** Absolute approved HTTPS URL. method defaults GET; GET/HEAD cannot have a body.
 * Use structured authorization/cookies; raw transport/auth headers are restricted.
 * timeoutMillis may not exceed the current invocation deadline.
 */
interface HttpRequestV1 {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE" | "HEAD";
  url: string;
  headers?: Record<string, string>;
  authorization?: HttpAuthorizationV1;
  cookies?: { name: string; value: string }[];
  body?: HttpBodyV1;
  timeoutMillis?: number;
}
/** body is text; parse JSON explicitly after checking status. Headers contain string arrays.
 * HTTP failure statuses are returned normally; this is not a browser Response.
 */
interface HttpResponseV1 { status: number; headers: Record<string, string[]>; body: string }

type PluginErrorCodeV1 =
  | "AuthenticationRequired" | "NotFound" | "Validation" | "RateLimited" | "Network"
  | "Remote" | "Unsupported" | "InvalidResponse" | "Cancelled";
/** Exactly one of value/key; localized key requires fallback.
 * args accepts finite numbers, booleans, or strings; omit null arguments.
 * Manifest text instead uses a string or { key, fallback }.
 */
interface WireTextV1 { value?: string; key?: string; fallback?: string; args?: Record<string, JsonPrimitive> }
/** Throw flare.error(value) for recoverable failures; retryAfterSeconds is 0..604800. */
interface PluginErrorV1 {
  code: PluginErrorCodeV1;
  message: WireTextV1;
  retryAfterSeconds?: number;
  remoteCode?: string;
}

interface FlareHostV1 {
  readonly http: { request(request: HttpRequestV1): Promise<HttpResponseV1> };
  /** Available only in account scope. replace atomically replaces the whole JSON payload. */
  readonly credential: {
    read(): Promise<JsonValue>;
    replace(value: JsonValue): Promise<boolean>;
  };
  readonly crypto: {
    /** size counts random bytes (1..1024), producing twice as many hex characters. */
    randomHex(size: number): Promise<string>;
    uuid(): Promise<string>;
    /** SHA-256 of UTF-8 input (at most 1 MiB); returns hex, not base64url/bytes. */
    sha256(value: string): Promise<string>;
  };
  readonly locale: { current(): Promise<string> };
  error(value: PluginErrorV1): Error;
}

type MaybePromise<T> = T | Promise<T>;

declare function definePlugin(plugin: PluginDefinitionV1): void;
declare const flare: FlareHostV1;
