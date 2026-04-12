package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

public interface CredentialRepository {
    public fun credentialJsonFlow(accountKey: MicroBlogKey): Flow<String>
}
