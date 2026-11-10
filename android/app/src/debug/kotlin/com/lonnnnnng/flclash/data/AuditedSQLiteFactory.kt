package com.lonnnnnng.flclash.data

import android.content.ContentValues
import android.database.sqlite.SQLiteTransactionListener
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.lonnnnnng.flclash.common.StateWriteAudit
import java.util.IdentityHashMap
import java.util.UUID

/** 作者 long：Room的suspend DAO最终经过此驱动；只在最外层end正常返回后标记提交，不能用DAO返回替代事务结果。 */
internal class AuditedSQLiteFactory : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val delegate = FrameworkSQLiteOpenHelperFactory().create(configuration)
        return object : SupportSQLiteOpenHelper by delegate {
            private val wrappers = IdentityHashMap<SupportSQLiteDatabase, SupportSQLiteDatabase>()
            @Synchronized private fun wrap(db: SupportSQLiteDatabase) = wrappers.getOrPut(db) { AuditedSQLiteDatabase(db) }
            override val writableDatabase get() = wrap(delegate.writableDatabase)
            override val readableDatabase get() = wrap(delegate.readableDatabase)
        }
    }
}

private class AuditedSQLiteDatabase(private val delegate: SupportSQLiteDatabase) : SupportSQLiteDatabase by delegate {
    private data class Transaction(val id: String = UUID.randomUUID().toString(), var marked: Boolean = false,
        var childFailed: Boolean = false, var uncertain: Boolean = false, var writes: Int = 0, var failedWrites: Int = 0)
    private val frames = object : ThreadLocal<MutableList<Transaction>>() {
        override fun initialValue() = mutableListOf<Transaction>()
    }
    private fun stack(): MutableList<Transaction> = checkNotNull(frames.get())
    private fun begin(block: () -> Unit) {
        val unobservedParent = StateWriteAudit.enabled && stack().isEmpty() && delegate.inTransaction()
        block()
        if (StateWriteAudit.enabled || stack().isNotEmpty()) stack().add(Transaction(uncertain = unobservedParent))
    }
    override fun beginTransaction() = begin { delegate.beginTransaction() }
    override fun beginTransactionNonExclusive() = begin { delegate.beginTransactionNonExclusive() }
    override fun beginTransactionReadOnly() = begin { delegate.beginTransactionReadOnly() }
    override fun beginTransactionWithListener(transactionListener: SQLiteTransactionListener) = begin { delegate.beginTransactionWithListener(transactionListener) }
    override fun beginTransactionWithListenerNonExclusive(transactionListener: SQLiteTransactionListener) = begin { delegate.beginTransactionWithListenerNonExclusive(transactionListener) }
    override fun beginTransactionWithListenerReadOnly(transactionListener: SQLiteTransactionListener) = begin { delegate.beginTransactionWithListenerReadOnly(transactionListener) }
    override fun setTransactionSuccessful() { delegate.setTransactionSuccessful(); stack().lastOrNull()?.marked = true }
    override fun endTransaction() {
        val stack = stack(); val frame = stack.lastOrNull()
        try {
            delegate.endTransaction()
            if (frame != null) {
                stack.removeAt(stack.lastIndex)
                val successful = frame.marked && !frame.childFailed
                stack.lastOrNull()?.let { parent -> parent.childFailed = parent.childFailed || !successful; parent.uncertain = parent.uncertain || frame.uncertain; parent.writes += frame.writes; parent.failedWrites += frame.failedWrites }
                // 作者 long：只有失败语句的事务也需要最终结果；语句失败不代表SQLite一定回滚，仍按原事务结束判定。
                if (frame.writes > 0 || frame.failedWrites > 0) StateWriteAudit.event(if (stack.isNotEmpty()) "db_nested_end" else if (frame.uncertain || delegate.inTransaction()) "db_outcome_unknown" else if (successful) "db_committed" else "db_rolled_back",
                    mapOf("transaction" to frame.id, "parent" to stack.lastOrNull()?.id, "successful" to successful, "writes" to frame.writes, "failedWrites" to frame.failedWrites))
            }
        } catch (failure: Throwable) {
            if (frame != null && stack.lastOrNull() === frame) stack.removeAt(stack.lastIndex)
            stack.lastOrNull()?.uncertain = true
            StateWriteAudit.event("db_outcome_unknown", mapOf("transaction" to frame?.id, "failureType" to failure.javaClass.simpleName))
            throw failure
        }
    }
    private fun yieldTransaction(block: () -> Boolean): Boolean {
        // 作者 long：yield可能提交并重开事务，普通嵌套栈无法证明分段结果，显式标不确定而不伪报整个事务提交。
        stack().forEach { it.uncertain = true }
        StateWriteAudit.event("db_yield_boundary_unknown", mapOf("transaction" to stack().lastOrNull()?.id))
        return block()
    }
    override fun yieldIfContendedSafely() = yieldTransaction { delegate.yieldIfContendedSafely() }
    override fun yieldIfContendedSafely(sleepAfterYieldDelay: Long) = yieldTransaction { delegate.yieldIfContendedSafely(sleepAfterYieldDelay) }
    private fun table(sql: String): String? = Regex("(?i)\\b(?:INTO|UPDATE(?:\\s+OR\\s+(?:ABORT|FAIL|IGNORE|REPLACE|ROLLBACK))?|FROM)\\s+[`\"]?(profiles|app_preferences|proxy_delay_results|scripts)[`\"]?\\b")
        .find(sql)?.groupValues?.get(1)?.lowercase()
    private fun <T> write(sql: String, values: () -> Map<String, Any?>, resultKind: String? = null, block: () -> T): T {
        if (!StateWriteAudit.enabled) return block()
        val table = table(sql)
        if (table == null) return block()
        val frame = stack().lastOrNull(); val id = UUID.randomUUID().toString()
        val detail = mapOf("write" to id, "table" to table, "sqlDigest" to StateWriteAudit.digest(sql.toByteArray()),
            "verb" to sql.trimStart().takeWhile { it.isLetter() }.uppercase(), "bindings" to values(),
            "transaction" to frame?.id, "rootTransaction" to stack().firstOrNull()?.id)
        StateWriteAudit.event("db_write_accepted", detail)
        try {
            return block().also {
                frame?.let { it.writes++ }
                StateWriteAudit.event(if (frame != null) "db_write_executed" else if (!delegate.inTransaction()) "db_autocommit_returned" else "db_write_commit_unknown",
                    mapOf("write" to id, "table" to table, "verb" to detail["verb"], "transaction" to frame?.id,
                        "rootTransaction" to stack().firstOrNull()?.id) + if (resultKind != null && it is Number) mapOf(resultKind to it) else emptyMap())
            }
        } catch (failure: Throwable) {
            frame?.let { it.failedWrites++ }
            StateWriteAudit.event("db_write_failed", detail + mapOf("failureType" to failure.javaClass.simpleName)); throw failure
        }
    }
    override fun compileStatement(sql: String): SupportSQLiteStatement {
        val statement = delegate.compileStatement(sql)
        return object : SupportSQLiteStatement by statement {
            private val values = sortedMapOf<String, Any?>()
            private fun record(index: Int, value: Any?) { if (StateWriteAudit.enabled) values[index.toString()] = StateWriteAudit.value(value) }
            override fun bindNull(index: Int) { statement.bindNull(index); record(index, null) }
            override fun bindLong(index: Int, value: Long) { statement.bindLong(index, value); record(index, value) }
            override fun bindDouble(index: Int, value: Double) { statement.bindDouble(index, value); record(index, value) }
            override fun bindString(index: Int, value: String) { statement.bindString(index, value); record(index, value) }
            override fun bindBlob(index: Int, value: ByteArray) { statement.bindBlob(index, value); record(index, value) }
            override fun clearBindings() { statement.clearBindings(); values.clear() }
            override fun execute() = write(sql, { values.toMap() }) { statement.execute() }
            override fun executeInsert() = write(sql, { values.toMap() }, "rowId") { statement.executeInsert() }
            override fun executeUpdateDelete() = write(sql, { values.toMap() }, "affectedRows") { statement.executeUpdateDelete() }
        }
    }
    private fun values(bindArgs: Array<out Any?>?) = bindArgs.orEmpty().mapIndexed { i, value -> (i + 1).toString() to StateWriteAudit.value(value) }.toMap()
    override fun execSQL(sql: String) = write(sql, { emptyMap() }) { delegate.execSQL(sql) }
    override fun execSQL(sql: String, bindArgs: Array<out Any?>) = write(sql, { values(bindArgs) }) { delegate.execSQL(sql, bindArgs) }
    override fun insert(table: String, conflictAlgorithm: Int, values: ContentValues): Long = write("INSERT INTO $table",
        { values.valueSet().associate { it.key to StateWriteAudit.value(it.value) } }, "rowId") { delegate.insert(table, conflictAlgorithm, values) }
    override fun update(table: String, conflictAlgorithm: Int, values: ContentValues, whereClause: String?, whereArgs: Array<out Any?>?): Int =
        write("UPDATE $table", { mapOf("values" to values.valueSet().associate { it.key to StateWriteAudit.value(it.value) }, "where" to values(whereArgs)) }, "affectedRows") {
            delegate.update(table, conflictAlgorithm, values, whereClause, whereArgs)
        }
    override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int = write("DELETE FROM $table", { values(whereArgs) }, "affectedRows") { delegate.delete(table, whereClause, whereArgs) }
}
