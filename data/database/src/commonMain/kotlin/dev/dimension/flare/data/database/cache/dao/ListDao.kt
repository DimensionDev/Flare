package dev.dimension.flare.data.database.cache.dao

import androidx.paging.PagingSource
import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.paging.PagingSourceDaoReturnTypeConverter
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
@DaoReturnTypeConverters(PagingSourceDaoReturnTypeConverter::class)
public interface ListDao {
    @Transaction
    @Query(
        "SELECT * FROM DbListPaging WHERE pagingKey = :pagingKey",
    )
    public fun getPagingSource(pagingKey: String): PagingSource<Int, DbListWithContent>

    @Transaction
    @Query("SELECT * FROM DbListPaging WHERE pagingKey = :pagingKey")
    public fun getListKeysFlow(pagingKey: String): Flow<List<DbListWithContent>>

    @Query("SELECT * FROM DbList WHERE listKey = :listKey AND accountType = :accountType")
    public fun getList(
        listKey: MicroBlogKey,
        accountType: DbAccountType,
    ): Flow<DbList?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertAllPaging(timelines: List<DbListPaging>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertAllList(lists: List<DbList>)

    @Query("DELETE FROM DbListPaging WHERE pagingKey = :pagingKey")
    public suspend fun deleteByPagingKey(pagingKey: String)

    @Query("DELETE FROM DbList WHERE accountType = :accountType")
    public suspend fun deleteByAccountType(accountType: String)

    @Query("DELETE FROM DbList WHERE listKey = :listKey AND accountType = :accountType")
    public suspend fun deleteByListKey(
        listKey: MicroBlogKey,
        accountType: DbAccountType,
    )

    @Query("DELETE FROM DbListPaging WHERE listKey = :listKey AND accountType = :accountType")
    public suspend fun deletePagingByListKey(
        listKey: MicroBlogKey,
        accountType: DbAccountType,
    )

    @Query("DELETE FROM DbList")
    public suspend fun clearAllLists()

    @Query("DELETE FROM DbListPaging")
    public suspend fun clearAllListPaging()

    @Query("UPDATE DbList SET content = :content WHERE listKey = :listKey AND accountType = :accountType")
    public suspend fun updateListContent(
        listKey: MicroBlogKey,
        accountType: DbAccountType,
        content: DbList.ListContent,
    )

    @Transaction
    @Query("SELECT * FROM DbListMember WHERE listKey = :listKey")
    public fun getListMembers(listKey: MicroBlogKey): PagingSource<Int, DbListMemberWithContent>

    @Transaction
    @Query("SELECT * FROM DbListMember WHERE listKey = :listKey")
    public fun getListMembersFlow(listKey: MicroBlogKey): Flow<List<DbListMemberWithContent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertAllMember(members: List<DbListMember>)

    @Query("DELETE FROM DbListMember WHERE listKey = :listKey")
    public suspend fun deleteMembersByListKey(listKey: MicroBlogKey)

    @Query("DELETE FROM DbListMember WHERE memberKey = :memberKey AND listKey = :listKey")
    public suspend fun deleteMemberFromList(
        memberKey: MicroBlogKey,
        listKey: MicroBlogKey,
    )

    @Transaction
    @Query("SELECT * FROM DbUser WHERE userKey = :userKey")
    public fun getUserByKey(userKey: MicroBlogKey): PagingSource<Int, DbUserWithListMembership>

    @Transaction
    @Query("SELECT * FROM DbUser WHERE userKey = :userKey")
    public fun getUserByKeyFlow(userKey: MicroBlogKey): Flow<DbUserWithListMembership>
}
