const PIXELFED_CAPABILITIES = {
  "flare.datasource.timeline/v1": ["page"],
  "flare.datasource.search/v1": [
    "posts",
    "profiles",
    "discoverPosts",
    "discoverProfiles",
    "discoverHashtags",
  ],
  "flare.datasource.profile/v1": ["byId", "byHandle", "timeline", "following", "followers"],
  "flare.datasource.post/v1": ["detail", "context", "delete", "mutate"],
  "flare.datasource.compose/v1": ["publish"],
};

definePlugin({
  detector: {
    async detect(request, context) {
      const origin = requireOrigin(request.origin || context.origin);
      const instance = await apiRequest(origin, "/api/v1/instance");
      if (!isPixelfed(instance.data)) {
        return { match: "none", canonicalOrigin: origin };
      }
      return {
        match: "exact",
        canonicalOrigin: origin,
        software: "Pixelfed",
        instance: mapInstance(instance.data, origin),
      };
    },
  },

  login: {
    oauth: {
      async begin(request, context) {
        const origin = requireOrigin(request.origin || context.origin);
        const instance = await apiRequest(origin, "/api/v1/instance");
        requirePixelfed(instance.data);
        const application = await apiRequest(origin, "/api/v1/apps", {
          method: "POST",
          body: {
            type: "form",
            values: {
              client_name: "Flare",
              redirect_uris: requiredString(request.redirectUri, "OAuth redirect URI is missing"),
              scopes: "read write",
              website: "https://github.com/DimensionDev/Flare",
            },
          },
        });
        const clientId = application.data && application.data.client_id;
        const clientSecret = application.data && application.data.client_secret;
        if (!clientId || !clientSecret) invalidResponse("Pixelfed did not return OAuth client credentials");
        const authorizationUrl = addQuery(origin + "/oauth/authorize", {
          client_id: String(clientId),
          redirect_uri: request.redirectUri,
          response_type: "code",
          scope: "read write",
          state: requiredString(request.state, "OAuth state is missing"),
        });
        return {
          type: "externalBrowser",
          url: authorizationUrl,
          pendingPayload: {
            clientId: String(clientId),
            clientSecret: String(clientSecret),
            composeConfig: composeConfig(instance.data),
          },
        };
      },

      async resume(request, context) {
        const origin = requireOrigin(request.origin || context.origin);
        const pending = request.pendingPayload || {};
        const callback = request.callbackParameters || {};
        if (callback.error) validationError("Pixelfed denied authorization", String(callback.error));
        const code = requiredString(callback.code, "OAuth callback code is missing");
        const clientId = requiredString(pending.clientId, "OAuth client ID is missing");
        const clientSecret = requiredString(pending.clientSecret, "OAuth client secret is missing");
        const tokenResponse = await apiRequest(origin, "/oauth/token", {
          method: "POST",
          timeoutMillis: 120000,
          body: {
            type: "form",
            values: {
              grant_type: "authorization_code",
              code,
              client_id: clientId,
              client_secret: clientSecret,
              redirect_uri: requiredString(request.redirectUri, "OAuth redirect URI is missing"),
              scope: "read write",
            },
          },
        });
        const accessToken = tokenResponse.data && tokenResponse.data.access_token;
        if (!accessToken) invalidResponse("Pixelfed did not return an access token");
        const accountResponse = await apiRequest(origin, "/api/v1/accounts/verify_credentials", {
          token: String(accessToken),
        });
        const account = accountResponse.data;
        if (!account || account.id == null) invalidResponse("Pixelfed did not return the authenticated account");
        return {
          type: "success",
          value: {
            accountId: String(account.id),
            origin,
            credential: { accessToken: String(accessToken) },
            profile: mapProfile(account, origin),
            capabilities: PIXELFED_CAPABILITIES,
            composeConfig: pending.composeConfig || null,
          },
        };
      },
    },
  },

  capabilities: {
    timeline: {
      async page(request, context) {
        const timeline = request.timelineId;
        if (timeline === "home") {
          return statusPage(context, "/api/v1/timelines/home", request.page);
        }
        if (timeline === "discover") {
          return statusPage(context, "/api/v1/discover/posts", request.page, {}, unwrapStatuses);
        }
        if (timeline === "local" || timeline === "federated") {
          return statusPage(context, "/api/v1/timelines/public", request.page, {
            local: timeline === "local" ? "true" : "false",
          });
        }
        if (timeline === "bookmarks") {
          return statusPage(context, "/api/v1/bookmarks", request.page);
        }
        if (timeline === "favourites") {
          return statusPage(context, "/api/v1/favourites", request.page);
        }
        unsupportedError("Unknown Pixelfed timeline", timeline);
      },
    },

    search: {
      async posts(request, context) {
        return searchPage(context, request, "statuses", mapPost);
      },
      async profiles(request, context) {
        return searchPage(context, request, "accounts", mapProfile);
      },
      async discoverPosts(request, context) {
        return statusPage(context, "/api/v1/discover/posts", request.page, {}, unwrapStatuses);
      },
      async discoverProfiles(request, context) {
        return profilePage(context, "/api/v1.1/discover/accounts/popular", request.page, {}, unwrapProfiles);
      },
      async discoverHashtags(request, context) {
        const response = await pagedGet(context, "/api/v1.1/discover/posts/hashtags", request.page);
        const values = unwrapArray(response.data, ["hashtags", "tags", "data"]);
        const items = values.map((value) => mapHashtag(value, context.origin)).filter((value) => value.name);
        return pageResult(items, cursorFor(response, "/api/v1.1/discover/posts/hashtags", request.page, {}, values));
      },
    },

    profile: {
      async byId(request, context) {
        const response = await authenticatedRequest(context, "/api/v1/accounts/" + encodeURIComponent(request.key.id));
        return mapProfile(response.data, context.origin);
      },
      async byHandle(request, context) {
        const handle = requiredString(request.handle, "Profile handle is missing").replace(/^@/, "");
        const response = await authenticatedRequest(context, addQuery("/api/v1/accounts/lookup", { acct: handle }));
        return mapProfile(response.data, context.origin);
      },
      async timeline(request, context) {
        const parameters = {};
        if (request.tabId === "gallery" || (request.parameters && request.parameters.onlyMedia === "true")) {
          parameters.only_media = "true";
        }
        return statusPage(
          context,
          "/api/v1/accounts/" + encodeURIComponent(request.profile.id) + "/statuses",
          request.page,
          parameters,
        );
      },
      async following(request, context) {
        return profilePage(
          context,
          "/api/v1/accounts/" + encodeURIComponent(request.key.id) + "/following",
          request.page,
        );
      },
      async followers(request, context) {
        return profilePage(
          context,
          "/api/v1/accounts/" + encodeURIComponent(request.key.id) + "/followers",
          request.page,
        );
      },
    },

    post: {
      async detail(request, context) {
        const response = await authenticatedRequest(context, "/api/v1/statuses/" + encodeURIComponent(request.key.id));
        return mapPost(response.data, context.origin, context.accountId);
      },
      async context(request, context) {
        const id = encodeURIComponent(request.key.id);
        const detail = await authenticatedRequest(context, "/api/v1/statuses/" + id);
        const thread = await authenticatedRequest(context, "/api/v1/statuses/" + id + "/context");
        const values = [];
        for (const status of (thread.data && thread.data.ancestors) || []) values.push(status);
        values.push(detail.data);
        for (const status of (thread.data && thread.data.descendants) || []) values.push(status);
        return pageResult(
          values.filter(Boolean).map((status) => mapPost(status, context.origin, context.accountId)),
          null,
          null,
          true,
        );
      },
      async delete(request, context) {
        await authenticatedRequest(context, "/api/v1/statuses/" + encodeURIComponent(request.key.id), {
          method: "DELETE",
        });
        return { type: "deleted" };
      },
      async mutate(request, context) {
        const suffix = {
          Favourite: "favourite",
          Unfavourite: "unfavourite",
          Repost: "reblog",
          Unrepost: "unreblog",
          Bookmark: "bookmark",
          Unbookmark: "unbookmark",
        }[request.action];
        if (!suffix) unsupportedError("Unsupported Pixelfed post action", request.action);
        const response = await authenticatedRequest(
          context,
          "/api/v1/statuses/" + encodeURIComponent(request.key.id) + "/" + suffix,
          { method: "POST" },
        );
        return { type: "updatedPost", post: mapPost(response.data, context.origin, context.accountId) };
      },
    },

    compose: {
      async publish(request, context) {
        const assets = request.assets || [];
        if (!request.replyTo && assets.length === 0) {
          validationError("Pixelfed posts require at least one media attachment", "media_required");
        }
        const mediaIds = [];
        for (const asset of assets) {
          const parts = [
            {
              type: "asset",
              name: "file",
              handle: asset.handle,
              fileName: asset.fileName || "upload",
              contentType: asset.mimeType || "application/octet-stream",
            },
          ];
          if (asset.description) {
            parts.push({ type: "text", name: "description", value: asset.description });
          }
          const uploaded = await authenticatedRequest(context, "/api/v1/media", {
            method: "POST",
            timeoutMillis: 120000,
            body: { type: "multipart", parts },
          });
          if (!uploaded.data || uploaded.data.id == null) invalidResponse("Pixelfed did not return a media ID");
          mediaIds.push(String(uploaded.data.id));
        }

        const payload = {
          status: request.text || "",
          media_ids: mediaIds,
          visibility: toPixelfedVisibility(request.visibility),
          sensitive: request.sensitive === true,
          spoiler_text: request.spoilerText || "",
        };
        if (request.replyTo) payload.in_reply_to_id = request.replyTo.id;
        const response = await authenticatedRequest(context, "/api/v1/statuses", {
          method: "POST",
          timeoutMillis: 120000,
          headers: { "Idempotency-Key": await flare.crypto.uuid() },
          body: { type: "json", value: payload },
        });
        return { post: mapPost(response.data, context.origin, context.accountId) };
      },
    },
  },
});

async function statusPage(context, path, page, parameters, unwrap) {
  const query = parameters || {};
  const response = await pagedGet(context, path, page, query);
  const values = (unwrap || unwrapStatuses)(response.data);
  const cursor = cursorFor(response, path, page, query, values);
  return pageResult(
    values.map((status) => mapPost(status, context.origin, context.accountId)),
    cursor,
    null,
    cursor == null,
  );
}

async function profilePage(context, path, page, parameters, unwrap) {
  const query = parameters || {};
  const response = await pagedGet(context, path, page, query);
  const values = (unwrap || unwrapProfiles)(response.data);
  const cursor = cursorFor(response, path, page, query, values);
  return pageResult(
    values.map((profile) => mapProfile(profile, context.origin)),
    cursor,
    null,
    cursor == null,
  );
}

async function searchPage(context, request, type, mapper) {
  const page = request.page;
  let offset = 0;
  if (page.cursor && page.cursor.indexOf("offset:") === 0) {
    offset = Math.max(0, Number(page.cursor.slice(7)) || 0);
  }
  const query = {
    q: request.query || "",
    type,
    resolve: "true",
    limit: String(page.limit),
    offset: String(offset),
  };
  const path = page.cursor && /^https:\/\//i.test(page.cursor) ? page.cursor : addQuery("/api/v2/search", query);
  const response = await authenticatedRequest(context, path);
  const values = unwrapArray(response.data, [type]);
  const linked = nextLink(response.headers);
  const olderCursor = linked || (values.length >= page.limit ? "offset:" + String(offset + values.length) : null);
  return pageResult(
    values.map((value) => mapper(value, context.origin, context.accountId)),
    olderCursor,
    null,
    olderCursor == null,
  );
}

async function pagedGet(context, path, page, parameters) {
  let target = path;
  if (page.cursor) {
    if (/^https:\/\//i.test(page.cursor)) {
      target = page.cursor;
    } else if (page.cursor.indexOf("max_id:") === 0) {
      target = addQuery(path, Object.assign({}, parameters || {}, { max_id: page.cursor.slice(7), limit: page.limit }));
    }
  } else {
    target = addQuery(path, Object.assign({}, parameters || {}, { limit: page.limit }));
  }
  return authenticatedRequest(context, target);
}

function cursorFor(response, path, page, parameters, values) {
  const linked = nextLink(response.headers);
  if (linked) return linked;
  if (values.length === 0 || values.length < page.limit) return null;
  const last = values[values.length - 1];
  return last && last.id != null ? "max_id:" + String(last.id) : null;
}

function pageResult(items, olderCursor, newerCursor, endReached) {
  return {
    items,
    olderCursor: olderCursor || null,
    newerCursor: newerCursor || null,
    endReached: endReached === true,
  };
}

async function authenticatedRequest(context, path, options) {
  const credential = await flare.credential.read();
  if (!credential || !credential.accessToken) authenticationError();
  const merged = Object.assign({}, options || {}, { token: String(credential.accessToken) });
  return apiRequest(context.origin, path, merged);
}

async function apiRequest(originValue, path, options) {
  const origin = requireOrigin(originValue);
  const value = options || {};
  const request = {
    method: value.method || "GET",
    url: absoluteUrl(origin, path),
    headers: Object.assign({ Accept: "application/json" }, value.headers || {}),
  };
  if (value.token) request.authorization = { type: "bearer", token: value.token };
  if (value.body) request.body = value.body;
  if (value.timeoutMillis) request.timeoutMillis = value.timeoutMillis;

  let response;
  try {
    response = await flare.http.request(request);
  } catch (error) {
    throw pluginFailure("Network", "error.network", "Unable to reach the Pixelfed instance", null, hostErrorCode(error));
  }
  const data = parseResponseBody(response);
  if (response.status >= 200 && response.status < 300) return { data, headers: response.headers || {} };

  const remoteCode = remoteErrorCode(data);
  if (response.status === 401) authenticationError(remoteCode);
  if (response.status === 404) {
    throw pluginFailure("NotFound", "error.notFound", "The requested Pixelfed content was not found", null, remoteCode);
  }
  if (response.status === 422) {
    throw pluginFailure("Validation", "error.validation", remoteMessage(data, "Pixelfed rejected the request"), null, remoteCode);
  }
  if (response.status === 429) {
    throw pluginFailure(
      "RateLimited",
      "error.rateLimited",
      "Pixelfed is rate limiting requests",
      retryAfterSeconds(response.headers),
      remoteCode,
    );
  }
  throw pluginFailure("Remote", "error.remote", remoteMessage(data, "Pixelfed returned HTTP " + response.status), null, remoteCode);
}

function parseResponseBody(response) {
  const body = response && typeof response.body === "string" ? response.body.trim() : "";
  if (!body) return null;
  try {
    return JSON.parse(body);
  } catch (_) {
    invalidResponse("Pixelfed returned invalid JSON");
  }
}

function mapInstance(instance, origin) {
  const stats = instance && instance.stats;
  const registrations = instance && instance.registrations;
  return {
    domain: originHost(origin),
    title: stringOr(instance && instance.title, "Pixelfed"),
    description: optionalString((instance && (instance.description || instance.short_description)) || null),
    usersCount: numberOrNull(stats && (stats.user_count || stats.users)) || 0,
    registrationEnabled:
      typeof registrations === "object" && registrations !== null
        ? registrations.enabled === true
        : registrations === true,
  };
}

function composeConfig(instance) {
  const configuration = (instance && instance.configuration) || {};
  const statuses = configuration.statuses || {};
  const media = configuration.media_attachments || {};
  const maximumBytes = positiveMinimum(
    numberOrNull(media.image_size_limit),
    numberOrNull(media.video_size_limit),
    10 * 1024 * 1024,
  );
  const mimeTypes = Array.isArray(media.supported_mime_types)
    ? media.supported_mime_types.filter(validMimeType).slice(0, 128)
    : ["image/jpeg", "image/png", "image/gif", "image/webp", "video/mp4"];
  return {
    text: { maxLength: positiveInteger(statuses.max_characters, 500, 1000000) },
    media: {
      minCountForNew: 1,
      maxCount: positiveInteger(statuses.max_media_attachments, 4, 100),
      maxBytes: maximumBytes,
      supportedMimeTypes: mimeTypes,
      altTextMaxLength: nonNegativeInteger(media.description_limit, 1500, 100000),
      canSensitive: true,
    },
    visibility: { allowed: ["public", "unlisted", "followers"], default: "public" },
    contentWarning: true,
  };
}

function mapProfile(account, origin) {
  if (!account || account.id == null) invalidResponse("Pixelfed returned an invalid profile");
  const host = originHost(origin);
  const acct = String(account.acct || account.username || account.id).replace(/^@/, "");
  const handle = "@" + (acct.indexOf("@") >= 0 ? acct : acct + "@" + host);
  const profile = {
    key: { id: String(account.id), host },
    handle,
    displayName: stringOr(account.display_name || account.username, acct),
    description: { format: "html", value: String(account.note || "") },
    followersCount: numberOrNull(account.followers_count),
    followingCount: numberOrNull(account.following_count),
    postsCount: numberOrNull(account.statuses_count),
    locked: account.locked === true,
    bot: account.bot === true,
    fields: [],
    actions: [],
  };
  const avatar = safeHttpsUrl(account.avatar_static || account.avatar, origin);
  const banner = safeHttpsUrl(account.header_static || account.header, origin);
  const url = safeHttpsUrl(account.url, origin);
  if (avatar) profile.avatarUrl = avatar;
  if (banner) profile.bannerUrl = banner;
  if (url) profile.url = url;
  if (Array.isArray(account.fields)) {
    profile.fields = account.fields.slice(0, 128).map((field) => ({
      name: String((field && field.name) || ""),
      value: { format: "html", value: String((field && field.value) || "") },
    }));
  }
  return profile;
}

function mapPost(status, origin, accountId, depth) {
  if (!status || status.id == null || !status.account) invalidResponse("Pixelfed returned an invalid status");
  const level = depth || 0;
  const host = originHost(origin);
  const post = {
    key: { id: String(status.id), host },
    author: mapProfile(status.account, origin),
    createdAt: String(status.created_at || "1970-01-01T00:00:00Z"),
    content: { format: "html", value: String(status.content || status.caption || "") },
    media: mapMediaList(status.media_attachments, origin),
    sensitive: status.sensitive === true,
    visibility: fromPixelfedVisibility(status.visibility),
    favouritesCount: numberOrNull(status.favourites_count),
    repostsCount: numberOrNull(status.reblogs_count),
    repliesCount: numberOrNull(status.replies_count),
    actions: postActions(status, accountId),
  };
  const url = safeHttpsUrl(status.url || status.uri, origin);
  if (url) post.url = url;
  if (status.in_reply_to_id != null) post.replyTo = { id: String(status.in_reply_to_id), host };
  if (status.spoiler_text) post.spoilerText = String(status.spoiler_text);
  if (status.reblog && level < 4) post.repost = mapPost(status.reblog, origin, accountId, level + 1);
  return post;
}

function postActions(status, accountId) {
  const actions = [
    { action: "Reply" },
    {
      action: status.favourited === true ? "Unfavourite" : "Favourite",
      active: status.favourited === true,
      count: numberOrNull(status.favourites_count),
    },
    {
      action: status.reblogged === true ? "Unrepost" : "Repost",
      active: status.reblogged === true,
      count: numberOrNull(status.reblogs_count),
    },
    {
      action: status.bookmarked === true ? "Unbookmark" : "Bookmark",
      active: status.bookmarked === true,
    },
  ];
  if (accountId != null && status.account && String(status.account.id) === String(accountId)) {
    actions.push({ action: "Delete" });
  }
  return actions;
}

function mapMediaList(values, origin) {
  if (!Array.isArray(values)) return [];
  return values.map((value) => mapMedia(value, origin)).filter(Boolean);
}

function mapMedia(media, origin) {
  if (!media || media.id == null) return null;
  const url = safeHttpsUrl(media.url || media.remote_url, origin);
  if (!url) return null;
  const preview = safeHttpsUrl(media.preview_url || media.url, origin);
  const dimensions = (media.meta && (media.meta.original || media.meta.small)) || {};
  const result = {
    id: String(media.id),
    type: mediaType(media.type),
    url,
  };
  if (preview) result.previewUrl = preview;
  if (media.description || media.alt_text) result.description = String(media.description || media.alt_text);
  const width = positiveNumberOrNull(dimensions.width);
  const height = positiveNumberOrNull(dimensions.height);
  const duration = positiveNumberOrNull(dimensions.duration || (media.meta && media.meta.duration));
  if (width) result.width = Math.round(width);
  if (height) result.height = Math.round(height);
  if (duration) result.durationMillis = Math.round(duration * 1000);
  return result;
}

function mapHashtag(value, origin) {
  if (typeof value === "string") return { name: value.replace(/^#/, "") };
  const result = { name: String((value && (value.name || value.tag)) || "").replace(/^#/, "") };
  const url = safeHttpsUrl(value && value.url, origin);
  if (url) result.url = url;
  return result;
}

function unwrapStatuses(value) {
  return unwrapArray(value, ["statuses", "posts", "data"])
    .map((item) => (item && (item.status || item.post)) || item)
    .filter(Boolean);
}

function unwrapProfiles(value) {
  return unwrapArray(value, ["accounts", "profiles", "data"])
    .map((item) => (item && (item.account || item.profile)) || item)
    .filter(Boolean);
}

function unwrapArray(value, keys) {
  if (Array.isArray(value)) return value;
  for (const key of keys) {
    if (value && Array.isArray(value[key])) return value[key];
  }
  return [];
}

function nextLink(headers) {
  const values = headerValues(headers, "link");
  for (const value of values) {
    const parts = String(value).split(/,\s*(?=<)/);
    for (const part of parts) {
      const match = part.match(/<([^>]+)>\s*;[^,]*\brel\s*=\s*"?next"?/i);
      if (match && /^https:\/\//i.test(match[1])) return match[1];
    }
  }
  return null;
}

function headerValues(headers, name) {
  if (!headers) return [];
  for (const key of Object.keys(headers)) {
    if (key.toLowerCase() === name.toLowerCase()) {
      return Array.isArray(headers[key]) ? headers[key] : [String(headers[key])];
    }
  }
  return [];
}

function retryAfterSeconds(headers) {
  const value = headerValues(headers, "retry-after")[0];
  if (value == null) return null;
  const seconds = Number(value);
  return Number.isFinite(seconds) && seconds >= 0 ? Math.min(604800, Math.round(seconds)) : null;
}

function remoteMessage(data, fallback) {
  const value = data && (data.error_description || data.error || data.message);
  return typeof value === "string" && value.trim() ? value.slice(0, 4096) : fallback;
}

function remoteErrorCode(data) {
  const value = data && (data.error_code || data.code || data.error);
  return typeof value === "string" && value.length <= 512 ? value : null;
}

function hostErrorCode(error) {
  return error && typeof error.code === "string" ? error.code.slice(0, 512) : null;
}

function pluginFailure(code, key, fallback, retryAfterSecondsValue, remoteCode) {
  const value = { code, message: { key, fallback } };
  if (retryAfterSecondsValue != null) value.retryAfterSeconds = retryAfterSecondsValue;
  if (remoteCode) value.remoteCode = remoteCode;
  return flare.error(value);
}

function authenticationError(remoteCode) {
  throw pluginFailure("AuthenticationRequired", "error.authentication", "Sign in to Pixelfed again", null, remoteCode);
}

function validationError(message, remoteCode) {
  throw pluginFailure("Validation", "error.validation", message, null, remoteCode || null);
}

function invalidResponse(message) {
  throw pluginFailure("InvalidResponse", "error.invalidResponse", message, null, null);
}

function unsupportedError(message, remoteCode) {
  throw pluginFailure("Unsupported", "error.unsupported", message, null, remoteCode == null ? null : String(remoteCode));
}

function requiredString(value, message) {
  if (value == null || String(value).trim() === "") validationError(message, "missing_value");
  return String(value);
}

function requirePixelfed(instance) {
  if (!isPixelfed(instance)) validationError("The selected instance does not identify itself as Pixelfed", "not_pixelfed");
}

function isPixelfed(instance) {
  const values = [
    instance && instance.version,
    instance && instance.software && instance.software.name,
    instance && instance.software && instance.software.version,
  ];
  return values.some((value) => String(value || "").toLowerCase().indexOf("pixelfed") >= 0);
}

function requireOrigin(value) {
  const origin = String(value || "").trim().replace(/\/+$/, "");
  if (!/^https:\/\/[^/?#@]+$/i.test(origin)) validationError("Enter a valid HTTPS Pixelfed instance", "invalid_origin");
  return origin;
}

function originHost(origin) {
  return requireOrigin(origin).slice(8).toLowerCase();
}

function absoluteUrl(origin, value) {
  const text = String(value || "");
  if (/^https:\/\//i.test(text)) return text;
  if (/^[a-z][a-z0-9+.-]*:/i.test(text)) validationError("Pixelfed returned a non-HTTPS URL", "invalid_url");
  return requireOrigin(origin) + (text.indexOf("/") === 0 ? "" : "/") + text;
}

function safeHttpsUrl(value, origin) {
  if (value == null || String(value).trim() === "") return null;
  try {
    const result = absoluteUrl(origin, value);
    return /^https:\/\/[^/?#@]+(?:[/?#].*)?$/i.test(result) ? result : null;
  } catch (_) {
    return null;
  }
}

function addQuery(path, values) {
  const pairs = [];
  for (const key of Object.keys(values || {})) {
    const raw = values[key];
    if (raw == null || raw === "") continue;
    const list = Array.isArray(raw) ? raw : [raw];
    for (const item of list) pairs.push(encodeURIComponent(key) + "=" + encodeURIComponent(String(item)));
  }
  if (pairs.length === 0) return path;
  return path + (String(path).indexOf("?") >= 0 ? "&" : "?") + pairs.join("&");
}

function fromPixelfedVisibility(value) {
  if (value === "unlisted") return "unlisted";
  if (value === "private") return "followers";
  if (value === "direct") return "direct";
  return "public";
}

function toPixelfedVisibility(value) {
  if (value === "followers") return "private";
  if (value === "unlisted") return "unlisted";
  if (value === "direct") validationError("Pixelfed direct visibility is not supported", "visibility");
  return "public";
}

function mediaType(value) {
  const type = String(value || "image").toLowerCase();
  if (type === "gifv") return "gif";
  return ["image", "video", "gif", "audio"].indexOf(type) >= 0 ? type : "image";
}

function stringOr(value, fallback) {
  return value == null || String(value) === "" ? String(fallback) : String(value);
}

function optionalString(value) {
  return value == null || String(value) === "" ? null : String(value);
}

function numberOrNull(value) {
  if (value == null || value === "") return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function positiveNumberOrNull(value) {
  const number = numberOrNull(value);
  return number != null && number > 0 ? number : null;
}

function positiveInteger(value, fallback, maximum) {
  const number = positiveNumberOrNull(value);
  return Math.min(maximum, Math.max(1, Math.round(number == null ? fallback : number)));
}

function nonNegativeInteger(value, fallback, maximum) {
  const number = numberOrNull(value);
  return Math.min(maximum, Math.max(0, Math.round(number == null ? fallback : number)));
}

function positiveMinimum(first, second, fallback) {
  const values = [first, second].filter((value) => value != null && value > 0);
  return Math.round(values.length === 0 ? fallback : Math.min.apply(Math, values));
}

function validMimeType(value) {
  return typeof value === "string" && /^[A-Za-z0-9!#$&^_.+-]+\/[A-Za-z0-9!#$&^_.+*-]+$/.test(value);
}
