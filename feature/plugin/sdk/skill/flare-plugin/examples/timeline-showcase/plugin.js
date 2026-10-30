// Demo state is account-runtime-local and resets when the runtime is evicted.
let ownVotes = [];
let reacted = false;
let following = false;
let requestPending = true;
let revision = 0;
const createdAt = "2026-10-30T00:00:00Z";
const negotiated = {
  "flare.datasource.timeline/v1": ["page"],
  "flare.datasource.post/v1": ["detail", "mutate"],
  "flare.datasource.profile/v1": ["byId", "byHandle"],
  "flare.datasource.notification/v1": ["page"],
  "flare.datasource.relation/v1": ["state", "mutate"]
};
function key(id, context) { return { id, host: context.origin.slice(8) }; }
function profile(id, context) {
  return { key: key(id, context), handle: id, displayName: id === "alice" ? "Alice" : "Demo account" };
}
function token(action) { return action + ":" + revision; }
function button(action, label) { return { action, label, actionToken: token(action) }; }
function post(id, context) {
  return {
    key: key(id, context), author: profile("alice", context), createdAt,
    content: { value: id === "poll" ? "Which timeline item do you prefer?" : "A related post" },
    url: context.origin + "/posts/" + id, visibility: "public", sourceLanguages: ["en"],
    ...(id === "poll" ? {
      card: { title: "Flare", description: "Existing native UI, supplied by a plugin", url: "https://github.com/DimensionDev/Flare" },
      poll: { id: "demo-poll", options: [{ title: "Posts", votesCount: 2 + (ownVotes.includes(0) ? 1 : 0) },
        { title: "Mixed items", votesCount: 3 + (ownVotes.includes(1) ? 1 : 0) }],
        ownVotes, actionToken: token("Vote") },
      emojiReactions: [{ name: "👍", count: reacted ? 2 : 1, me: reacted, actionToken: token(reacted ? "Unreact" : "React") }]
    } : {})
  };
}
function announcement(id, context) {
  return { key: key(id, context), createdAt, actor: profile("alice", context), icon: "Info", text: "Welcome to mixed timelines" };
}
function page(context) {
  const items = [
    { type: "post", post: post("poll", context), presentation: {
      quotes: [post("quote", context)], inlineParents: [{ post: post("parent", context) }]
    } },
    { type: "feed", feed: { url: "https://github.com/DimensionDev/Flare", createdAt, title: "Flare development",
      source: { name: "Project feed" }, description: { value: "A feed row opens its URL." } } },
    { type: "user", user: { key: key("suggested-alice", context), createdAt, profile: profile("alice", context),
      buttons: [button(following ? "Unfollow" : "Follow", following ? "Unfollow" : "Follow")] } },
    { type: "userList", userList: { key: key("people", context), createdAt, users: [profile("alice", context)],
      message: announcement("people-message", context), post: post("related", context) } },
    { type: "message", message: announcement("welcome", context) }
  ];
  if (requestPending) items.push({ type: "user", user: {
    key: key("request-alice", context), createdAt, profile: profile("alice", context),
    message: { key: key("follow-request", context), createdAt, kind: "FollowRequest", actor: profile("alice", context) },
    buttons: [button("AcceptFollowRequest", "Accept"), button("RejectFollowRequest", "Reject")]
  } });
  return { items, endReached: true };
}
function checkToken(request) {
  if (request.actionToken !== token(request.action)) {
    throw flare.error({ code: "Remote", message: { value: "This demo action expired. Refresh the timeline." } });
  }
}
defineFlareSocialPlugin({
  login: { demo: { begin(request, context) {
    return { type: "success", value: { accountId: "demo", origin: context.origin, credential: {},
      profile: { ...profile("demo", context), displayName: request.values.name?.trim() || "Demo account" }, capabilities: negotiated } };
  } } },
  capabilities: {
    timeline: { page(request, context) { return page(context); } },
    profile: {
      byId(request, context) { return profile(request.key.id, context); },
      byHandle(request, context) { return profile(request.handle, context); }
    },
    notification: { page(request, context) {
      return { items: ["first", "second"].map(id => ({ type: "post", post: post("poll", context), presentation: {
        notificationKey: key(id, context), message: { key: key(id, context), createdAt, kind: "Favourite",
          icon: "Heart", actor: profile("alice", context) }
      } })), endReached: true };
    } },
    post: {
      detail(request, context) { return { post: post(request.key.id, context) }; },
      mutate(request, context) {
        checkToken(request);
        if (request.key.id !== "poll") throw flare.error({ code: "NotFound", message: { value: "Unknown post" } });
        if (request.action === "Vote") {
          const options = JSON.parse(request.parameters.options || "null");
          if (!Array.isArray(options) || options.length !== 1 || ![0, 1].includes(options[0]) || ownVotes.length) {
            throw flare.error({ code: "Validation", message: { value: "Choose one valid option" } });
          }
          ownVotes = options;
        } else if (["React", "Unreact"].includes(request.action) && request.parameters.reaction === "👍") {
          reacted = request.action === "React";
        } else throw flare.error({ code: "Unsupported", message: { value: "Unknown action" } });
        revision++;
        return { type: "updatedPost", post: post("poll", context) };
      }
    },
    relation: {
      state(request) { return { profileKey: request.key, following }; },
      mutate(request, context) {
        if (request.key.id !== "alice") throw flare.error({ code: "NotFound", message: { value: "Unknown profile" } });
        if (["AcceptFollowRequest", "RejectFollowRequest"].includes(request.action)) {
          checkToken(request);
          requestPending = false;
          return { type: "deleted" };
        }
        if (!["Follow", "Unfollow"].includes(request.action)) throw flare.error({ code: "Unsupported", message: { value: "Unknown action" } });
        // Profile relation controls use state(); timeline buttons supply actionToken.
        if (request.actionToken) checkToken(request);
        following = request.action === "Follow";
        if (!request.actionToken) return { type: "updatedRelation", relation: { profileKey: request.key, following } };
        return { type: "updatedProfile", relation: { profileKey: request.key, following }, profile: { ...profile("alice", context),
          actions: [button(following ? "Unfollow" : "Follow", following ? "Unfollow" : "Follow")] } };
      }
    }
  }
});
