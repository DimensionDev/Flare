const LOGIN_OAUTH_CONTEXT_KEY = "flare:login:oauth:context";

export type LoginMethodType =
    | "OAuth"
    | "Password"
    | "CredentialImport"
    | "QrConnect";

export type LoginOAuthContext = {
    platformId: string;
    host: string;
    methodType: LoginMethodType;
};

export function loginCallbackPath(platformId: string): string {
    return `/login/callback/${platformId.toLowerCase()}`;
}

export function loginRedirectUri(platformId: string): string {
    return `${globalThis.location.origin}${loginCallbackPath(platformId)}`;
}

export function loadLoginOAuthContext(): LoginOAuthContext | null {
    const raw = globalThis.sessionStorage?.getItem(LOGIN_OAUTH_CONTEXT_KEY);
    if (!raw) return null;
    try {
        return JSON.parse(raw) as LoginOAuthContext;
    } catch {
        return null;
    }
}

export function saveLoginOAuthContext(value: LoginOAuthContext): void {
    globalThis.sessionStorage?.setItem(
        LOGIN_OAUTH_CONTEXT_KEY,
        JSON.stringify(value),
    );
}

export function clearLoginOAuthContext(): void {
    globalThis.sessionStorage?.removeItem(LOGIN_OAUTH_CONTEXT_KEY);
}
