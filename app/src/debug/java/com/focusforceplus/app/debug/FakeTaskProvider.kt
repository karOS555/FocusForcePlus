package com.focusforceplus.app.debug

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri

/**
 * Debug builds only: a stand-in for the OpenTasks provider with the same table and
 * column names, so the task sync can be tried on an emulator without a tasks app.
 * One list, "Personal". Tasks can be added and changed from adb, which is what
 * "another app changed it" looks like to FocusForce+:
 *
 *     content insert --uri content://com.focusforceplus.app.debug.tasks/tasks --bind list_id:i:1 --bind title:s:Milk
 *     content update --uri content://com.focusforceplus.app.debug.tasks/tasks/3 --bind title:s:Bread
 *     content delete --uri content://com.focusforceplus.app.debug.tasks/tasks/3
 *
 * It takes no SQL from whoever calls it. A row is named by the id in the uri, the
 * only filter is the one the task sync sends, and tables and columns are picked
 * from the fixed lists below. So nothing a caller passes ever becomes part of a
 * statement, test helper or not. The manifest also keeps other apps out.
 */
class FakeTaskProvider : ContentProvider() {

    private lateinit var helper: SQLiteOpenHelper

    override fun onCreate(): Boolean {
        helper = object : SQLiteOpenHelper(context, "fake_tasks.db", null, 1) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE tasklists (_id INTEGER PRIMARY KEY, list_name TEXT, account_name TEXT, list_color INTEGER)")
                db.execSQL(
                    "CREATE TABLE tasks (_id INTEGER PRIMARY KEY AUTOINCREMENT, list_id INTEGER NOT NULL, " +
                        "title TEXT, description TEXT, due INTEGER, tz TEXT, is_allday INTEGER DEFAULT 0, " +
                        "status INTEGER DEFAULT 0, completed INTEGER, priority INTEGER DEFAULT 0)"
                )
                db.execSQL("INSERT INTO tasklists VALUES (1, 'Personal', 'Test account', -16738680)")
            }

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        return true
    }

    private enum class Table(val sqlName: String, val columns: List<String>) {
        LISTS("tasklists", listOf("_id", "list_name", "account_name", "list_color")),
        TASKS(
            "tasks",
            listOf("_id", "list_id", "title", "description", "due", "tz", "is_allday", "status", "completed", "priority"),
        ),
    }

    private fun table(uri: Uri): Table = when (uri.pathSegments.firstOrNull()) {
        "tasklists" -> Table.LISTS
        "tasks" -> Table.TASKS
        else -> throw IllegalArgumentException("Unknown $uri")
    }

    /** The table's own name for a column a caller asked for; refused if the table has none like it. */
    private fun Table.column(wanted: String): String =
        columns.firstOrNull { it == wanted } ?: throw IllegalArgumentException("$sqlName has no column like that")

    /**
     * Which rows are meant: the one whose id is in the uri, the tasks of one list (the
     * filter the task sync sends), or all of them. Any other selection is refused.
     */
    private fun rows(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Pair<String?, Array<String>?> {
        if (uri.pathSegments.size > 1) {
            require(selection == null) { "A uri with an id takes no selection" }
            return BY_ID to arrayOf(ContentUris.parseId(uri).toString())
        }
        return when (selection) {
            null -> null to null
            BY_LIST -> BY_LIST to arrayOf(requireNotNull(selectionArgs?.singleOrNull()) { "$BY_LIST takes one argument" })
            else -> throw IllegalArgumentException("The test provider only filters by \"$BY_LIST\"")
        }
    }

    /** The values under the table's own column names. A column it does not have is refused. */
    private fun Table.known(values: ContentValues?): ContentValues {
        val clean = ContentValues()
        values?.keySet()?.forEach { key ->
            val column = column(key)
            when (val value = values.get(key)) {
                null -> clean.putNull(column)
                is Long -> clean.put(column, value)
                is Int -> clean.put(column, value)
                is Boolean -> clean.put(column, if (value) 1 else 0)
                else -> clean.put(column, value.toString())
            }
        }
        return clean
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val table = table(uri)
        val (where, args) = rows(uri, selection, selectionArgs)
        val columns = projection?.map { table.column(it) }?.toTypedArray()
        // Always in the order the rows were made; a caller's sort order is not taken.
        return helper.readableDatabase.query(table.sqlName, columns, where, args, null, null, "_id")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val table = table(uri)
        val id = helper.writableDatabase.insertOrThrow(table.sqlName, null, table.known(values))
        notifyChange()
        return ContentUris.withAppendedId(Uri.parse("content://$AUTHORITY/${table.sqlName}"), id)
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        val table = table(uri)
        val (where, args) = rows(uri, selection, selectionArgs)
        return helper.writableDatabase.update(table.sqlName, table.known(values), where, args).also { if (it > 0) notifyChange() }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val table = table(uri)
        val (where, args) = rows(uri, selection, selectionArgs)
        return helper.writableDatabase.delete(table.sqlName, where, args).also { if (it > 0) notifyChange() }
    }

    override fun getType(uri: Uri): String? = null

    private fun notifyChange() {
        context?.contentResolver?.notifyChange(Uri.parse("content://$AUTHORITY/tasks"), null)
    }

    private companion object {
        const val AUTHORITY = "com.focusforceplus.app.debug.tasks"
        const val BY_ID = "_id = ?"

        /** The one filter the task sync uses (TaskStore.tasks). */
        const val BY_LIST = "list_id = ?"
    }
}
