// Adapt the illustrative website contract in README.md before publication.
defineFlareSocialPlugin({
  login: {
    token: {
      async begin(request, context) {
        const token = request.values.token?.trim();
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
        const cursor = data.next_cursor;
        if (cursor != null && (typeof cursor !== "string" || !cursor)) {
          fail("InvalidResponse", "Expected a non-empty cursor or null");
        }
        return {
          items: data.items.map((value: unknown): TimelineItemV1 => {
            const post = requiredObject(value);
            return { type: "post", post: {
              key: { id: requiredString(post.id), host: accountHost(context) },
              author: mapProfile(post.author, context),
              createdAt: requiredString(post.created_at),
              content: { format: "plain", value: requiredString(post.text) },
              detailType: "link",
              url: requiredString(post.url),
            } };
          }),
          olderCursor: cursor ?? undefined,
          endReached: cursor == null,
        };
      },
    },
  },
});

function accountHost(context: PluginInvocationContextV1): string {
  return context.origin.slice("https://".length);
}

function mapProfile(value: unknown, context: PluginInvocationContextV1): ProfileV1 {
  const profile = requiredObject(value);
  return {
    key: { id: requiredString(profile.id), host: accountHost(context) },
    handle: requiredString(profile.username),
    displayName: requiredString(profile.name),
  };
}

function requiredString(value: unknown): string {
  if (typeof value !== "string" || !value.trim()) fail("InvalidResponse", "Missing required text field");
  return value;
}

function requiredObject(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) fail("InvalidResponse", "Expected a JSON object");
  return value as Record<string, unknown>;
}

async function requestJson(url: string, token: string): Promise<Record<string, unknown>> {
  let response: HttpResponseV1;
  try {
    response = await flare.http.request({ url, authorization: { type: "bearer", token } });
  } catch (error) {
    if (error && typeof error === "object" && "code" in error && error.code === "host.unavailable") {
      fail("Network", "The request could not be completed");
    }
    throw error;
  }
  if (response.status === 401) fail("AuthenticationRequired", "The access token was rejected");
  if (response.status === 404) fail("NotFound", "The requested resource was not found");
  if (response.status === 429) {
    const entry = Object.entries(response.headers).find(([name]) => name.toLowerCase() === "retry-after");
    const value = entry?.[1][0];
    const seconds = value && /^\d+$/.test(value) ? Number(value) : undefined;
    throw flare.error({
      code: "RateLimited",
      message: { value: "Try again later" },
      retryAfterSeconds: typeof seconds === "number" && Number.isInteger(seconds) && seconds <= 604800 ? seconds : undefined,
    });
  }
  if (response.status < 200 || response.status >= 300) fail("Remote", "The website returned an error");
  let data: unknown;
  try {
    data = JSON.parse(response.body);
  } catch {
    fail("InvalidResponse", "The website did not return JSON");
  }
  return requiredObject(data);
}

function fail(code: PluginErrorCodeV1, message: string): never {
  throw flare.error({ code, message: { value: message } });
}
