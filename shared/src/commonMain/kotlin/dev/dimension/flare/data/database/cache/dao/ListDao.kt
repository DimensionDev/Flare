package dev.dimension.flare.data.database.cache.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.dimension.flare.data.database.cache.model.DbList
import dev.dimension.flare.data.database.cache.model.DbListMember
import dev.dimension.flare.data.database.cache.model.DbListMemberWithContent
import dev.dimension.flare.data.database.cache.model.DbListPaging
import dev.dimension.flare.data.database.cache.model.DbListWithContent
import dev.dimension.flare.data.database.cache.model.DbUserWithListMembership
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ListDao {
    @Transaction
    @Query(
        "SELECT * FROM DbListPaging WHERE pagingKey = :pagingKey",
    )
    fun getPagingSource(pagingKey: String): PagingSource<Int, DbListWithContent>

    @Query("SELECT * FROM DbList WHERE listKey = :listKey AND accountType = :accountType")
    fun getList(
        listKey: MicroBlogKey,
        accountType: DbAccountType,
    ): Flow<DbList?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(timelines: List<DbListPaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lists: List<DbList>)

    @Query("DELETE FROM DbListPaging WHERE pagingKey = :pagingKey")
    suspend fun deleteByPagingKey(pagingKey: String)

    @Query("DELETE FROM DbList WHERE accountType = :accountType")
    suspend fun deleteByAccountType(accountType: String)

    @Query("DELETE FROM DbList WHERE listKey = :listKey AND accountType = :accountType")
    suspend fun deleteByListKey(
        listKey: MicroBlogKey,
        accountType: DbAccountType,
    )

    @Query("DELETE FROM DbListPaging WHERE listKey = :listKey AND accountType = :accountType")
    suspend fun deletePagingByListKey(
        listKey: MicroBlogKey,
        accountType: DbAccountType,
    )

    @Query("DELETE FROM DbList")
    suspend fun clearAllLists()

    @Query("DELETE FROM DbListPaging")
    suspend fun clearAllListPaging()

    @Query("UPDATE DbList SET content = :content WHERE listKey = :listKey AND accountType = :accountType")
    suspend fun updateListContent(
        listKey: MicroBlogKey,
        accountType: DbAccountType,
        content: DbList.ListContent,
    )

    @Transaction
    @Query("SELECT * FROM DbListMember WHERE listKey = :listKey")
    fun getListMembers(listKey: MicroBlogKey): PagingSource<Int, DbListMemberWithContent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<DbListMember>)

    @Query("DELETE FROM DbListMember WHERE listKey = :listKey")
    suspend fun deleteMembersByListKey(listKey: MicroBlogKey)

    @Query("DELETE FROM DbListMember WHERE memberKey = :memberKey AND listKey = :listKey")
    suspend fun deleteMemberFromList(
        memberKey: MicroBlogKey,
        listKey: MicroBlogKey,
    )

    @Transaction
    @Query("SELECT * FROM DbUser WHERE userKey = :userKey")
    fun getUserByKey(userKey: MicroBlogKey): PagingSource<Int, DbUserWithListMembership>
}
