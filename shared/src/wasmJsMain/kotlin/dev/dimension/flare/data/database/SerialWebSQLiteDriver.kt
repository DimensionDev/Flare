package dev.dimension.flare.data.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SerialWebSQLiteDriver(
    private val delegate: SQLiteDriver,
) : SQLiteDriver {
    private val openMutex = Mutex()
    private val connections = mutableMapOf<String, SerialWebSQLiteConnection>()

    override val hasConnectionPool: Boolean
        get() = false

    override suspend fun open(fileName: String): SQLiteConnection =
        openMutex.withLock {
            connections.getOrPut(fileName) {
                SerialWebSQLiteConnection(
                    delegate = delegate.open(fileName),
                    onClosed = {
                        connections.remove(fileName)
                    },
                )
            }
        }
}

private class SerialWebSQLiteConnection(
    private val delegate: SQLiteConnection,
    private val onClosed: () -> Unit,
) : SQLiteConnection {
    private val operationMutex = Mutex()
    private val savepoints = ArrayDeque<String>()
    private var isClosed = false
    private var transactionDepth = 0
    private var nextSavepointId = 0

    override fun inTransaction(): Boolean = transactionDepth > 0 || delegate.inTransaction()

    override suspend fun prepare(sql: String): SQLiteStatement =
        operationMutex.withLock {
            SerialWebSQLiteStatement(
                delegate = delegate.prepare(sql),
                connection = this,
                transactionOperation = TransactionOperation.from(sql),
            )
        }

    override fun close() {
        if (isClosed) {
            return
        }
        isClosed = true
        onClosed()
        delegate.close()
    }

    suspend fun step(
        statement: SQLiteStatement,
        operation: TransactionOperation,
    ): Boolean =
        operationMutex.withLock {
            when (operation) {
                TransactionOperation.Begin -> beginTransaction(statement)
                TransactionOperation.Commit -> commitTransaction(statement)
                TransactionOperation.Rollback -> rollbackTransaction(statement)
                TransactionOperation.None -> statement.step()
            }
        }

    private suspend fun beginTransaction(statement: SQLiteStatement): Boolean {
        if (transactionDepth > 0) {
            val savepoint = nextSavepoint()
            executeDirect("SAVEPOINT $savepoint")
            savepoints.addLast(savepoint)
            transactionDepth += 1
            return false
        }
        val result = statement.step()
        transactionDepth = 1
        return result
    }

    private suspend fun commitTransaction(statement: SQLiteStatement): Boolean {
        if (transactionDepth > 1) {
            val savepoint = savepoints.removeLast()
            executeDirect("RELEASE SAVEPOINT $savepoint")
            transactionDepth -= 1
            return false
        }
        val result = statement.step()
        transactionDepth = 0
        savepoints.clear()
        return result
    }

    private suspend fun rollbackTransaction(statement: SQLiteStatement): Boolean {
        if (transactionDepth > 1) {
            val savepoint = savepoints.removeLast()
            executeDirect("ROLLBACK TO SAVEPOINT $savepoint")
            executeDirect("RELEASE SAVEPOINT $savepoint")
            transactionDepth -= 1
            return false
        }
        try {
            return statement.step()
        } finally {
            transactionDepth = 0
            savepoints.clear()
        }
    }

    private suspend fun executeDirect(sql: String) {
        val statement = delegate.prepare(sql)
        try {
            statement.step()
        } finally {
            statement.close()
        }
    }

    private fun nextSavepoint(): String = "room_nested_${nextSavepointId++}"
}

private class SerialWebSQLiteStatement(
    private val delegate: SQLiteStatement,
    private val connection: SerialWebSQLiteConnection,
    private val transactionOperation: TransactionOperation,
) : SQLiteStatement {
    override fun bindBlob(
        index: Int,
        value: ByteArray,
    ) = delegate.bindBlob(index, value)

    override fun bindDouble(
        index: Int,
        value: Double,
    ) = delegate.bindDouble(index, value)

    override fun bindFloat(
        index: Int,
        value: Float,
    ) = delegate.bindFloat(index, value)

    override fun bindLong(
        index: Int,
        value: Long,
    ) = delegate.bindLong(index, value)

    override fun bindInt(
        index: Int,
        value: Int,
    ) = delegate.bindInt(index, value)

    override fun bindBoolean(
        index: Int,
        value: Boolean,
    ) = delegate.bindBoolean(index, value)

    override fun bindText(
        index: Int,
        value: String,
    ) = delegate.bindText(index, value)

    override fun bindNull(index: Int) = delegate.bindNull(index)

    override fun getBlob(index: Int): ByteArray = delegate.getBlob(index)

    override fun getDouble(index: Int): Double = delegate.getDouble(index)

    override fun getFloat(index: Int): Float = delegate.getFloat(index)

    override fun getLong(index: Int): Long = delegate.getLong(index)

    override fun getInt(index: Int): Int = delegate.getInt(index)

    override fun getBoolean(index: Int): Boolean = delegate.getBoolean(index)

    override fun getText(index: Int): String = delegate.getText(index)

    override fun isNull(index: Int): Boolean = delegate.isNull(index)

    override fun getColumnCount(): Int = delegate.getColumnCount()

    override fun getColumnName(index: Int): String = delegate.getColumnName(index)

    override fun getColumnNames(): List<String> = delegate.getColumnNames()

    override fun getColumnType(index: Int): Int = delegate.getColumnType(index)

    override suspend fun step(): Boolean = connection.step(delegate, transactionOperation)

    override fun reset() = delegate.reset()

    override fun clearBindings() = delegate.clearBindings()

    override fun close() = delegate.close()
}

private enum class TransactionOperation {
    Begin,
    Commit,
    Rollback,
    None,
    ;

    companion object {
        fun from(sql: String): TransactionOperation {
            val normalized = sql.trimStart().uppercase()
            return when {
                normalized.startsWith("BEGIN") -> Begin
                normalized.startsWith("COMMIT") || normalized.startsWith("END") -> Commit
                normalized.startsWith("ROLLBACK") && !normalized.startsWith("ROLLBACK TO") -> Rollback
                else -> None
            }
        }
    }
}
