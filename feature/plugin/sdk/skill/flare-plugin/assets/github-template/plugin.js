// Adapt the illustrative website contract in README.md before publication.
definePlugin({
  login: {
    token: {
      async begin(request, context) {
        const token = request.values.token && request.values.token.trim();
        if (!token) fail("Validation", "Enter an access token");
        const profile = mapProfile(await requestJson(context.origin + "/api/me", token), context);
        return {
          type: "success",
          value: {
            accountId: profile.key.id,
            origin: context.origin,
            credential: { accessToken: token },
            profile,
            capabilities: { "flare.datasource.timeline/v1": ["page"] },
          },
        };
      },
    },
  },
  capabilities: {
    timeline: {
      async page(request, context) {
        if (request.timelineId !== "home") fail("Unsupported", "Unknown timeline");
        const credential = await flare.credential.read();
        if (!credential || typeof credential !== "object" || Array.isArray(credential) ||
            typeof credential.accessToken !== "string" || !credential.accessToken) {
          fail("AuthenticationRequired", "Import your access token again");
        }
        let url = context.origin + "/api/posts?limit=" + request.page.limit;
        if (request.page.direction !== "refresh" && request.page.cursor) {
          url += "&cursor=" + encodeURIComponent(request.page.cursor);
        }
        const data = await requestJson(url, credential.accessToken);
        if (!Array.isArray(data.items)) fail("InvalidResponse", "Expected a post array");
        if (data.next_cursor != null && (typeof data.next_cursor !== "string" || !data.next_cursor)) {
          fail("InvalidResponse", "Expected a non-empty cursor or null");
        }
        return {
          items: data.items.map(post => ({
            key: { id: requiredString(post && post.id), host: accountHost(context) },
            author: mapProfile(post.author, context),
            createdAt: requiredString(post.created_at),
            content: { format: "plain", value: requiredString(post.text) },
            detailType: "link",
            url: requiredString(post.url),
          })),
          olderCursor: data.next_cursor,
          endReached: data.next_cursor == null,
        };
      },
    },
  },
});

function accountHost(context) {
  return context.origin.slice("https://".length);
}

function mapProfile(value, context) {
  return {
    key: { id: requiredString(value && value.id), host: accountHost(context) },
    handle: requiredString(value.username),
    displayName: requiredString(value.name),
  };
}

function requiredString(value) {
  if (typeof value !== "string" || !value.trim()) fail("InvalidResponse", "Missing required text field");
  return value;
}

async function requestJson(url, token) {
  let response;
  try {
    response = await flare.http.request({ url, authorization: { type: "bearer", token } });
  } catch (error) {
    if (error && error.code === "host.unavailable") fail("Network", "The request could not be completed");
    throw error;
  }
  if (response.status === 401) fail("AuthenticationRequired", "The access token was rejected");
  if (response.status === 404) fail("NotFound", "The requested resource was not found");
  if (response.status === 429) {
    const entry = Object.entries(response.headers).find(([name]) => name.toLowerCase() === "retry-after");
    const value = entry && entry[1][0];
    const seconds = value && /^\d+$/.test(value) ? Number(value) : undefined;
    throw flare.error({
      code: "RateLimited",
      message: { value: "Try again later" },
      retryAfterSeconds: typeof seconds === "number" && Number.isInteger(seconds) && seconds <= 604800 ? seconds : undefined,
    });
  }
  if (response.status < 200 || response.status >= 300) fail("Remote", "The website returned an error");
  let data;
  try {
    data = JSON.parse(response.body);
  } catch (_) {
    fail("InvalidResponse", "The website did not return JSON");
  }
  if (!data || typeof data !== "object" || Array.isArray(data)) fail("InvalidResponse", "Expected a JSON object");
  return data;
}

/** @param {PluginErrorCodeV1} code @param {string} message @returns {never} */
function fail(code, message) {
  throw flare.error({ code, message: { value: message } });
}
