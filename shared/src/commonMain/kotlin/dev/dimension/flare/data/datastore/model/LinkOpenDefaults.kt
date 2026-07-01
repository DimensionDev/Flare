package dev.dimension.flare.data.datastore.model

import dev.dimension.flare.model.MicroBlogKey

public sealed interface LinkOpenDefaultMethod {
    public data object Browser : LinkOpenDefaultMethod

    public data class Account(
        val accountKey: MicroBlogKey,
    ) : LinkOpenDefaultMethod
}

public fun AppSettings.LinkOpenDefaults.methodFor(host: String): LinkOpenDefaultMethod? =
    rules
        .lastOrNull { it.host == host }
        ?.method
        ?.toLinkOpenDefaultMethod()

public fun AppSettings.LinkOpenDefaults.upsert(
    host: String,
    method: LinkOpenDefaultMethod,
): AppSettings.LinkOpenDefaults =
    copy(
        rules =
            rules
                .filterNot { it.host == host }
                .plus(
                    AppSettings.LinkOpenDefaults.Rule(
                        host = host,
                        method = method.toAppSettingsMethod(),
                    ),
                ),
    )

public fun AppSettings.LinkOpenDefaults.remove(host: String): AppSettings.LinkOpenDefaults =
    copy(
        rules = rules.filterNot { it.host == host },
    )

private fun AppSettings.LinkOpenDefaults.Method.toLinkOpenDefaultMethod(): LinkOpenDefaultMethod =
    when (this) {
        AppSettings.LinkOpenDefaults.Method.Browser -> LinkOpenDefaultMethod.Browser
        is AppSettings.LinkOpenDefaults.Method.Account -> LinkOpenDefaultMethod.Account(accountKey)
    }

private fun LinkOpenDefaultMethod.toAppSettingsMethod(): AppSettings.LinkOpenDefaults.Method =
    when (this) {
        LinkOpenDefaultMethod.Browser -> AppSettings.LinkOpenDefaults.Method.Browser
        is LinkOpenDefaultMethod.Account -> AppSettings.LinkOpenDefaults.Method.Account(accountKey)
    }
