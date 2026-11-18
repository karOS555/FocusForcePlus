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
 * One list, "Personal". Tasks can be added and changed from adb with
 * `content insert/update --uri content://com.focusforceplus.app.debug.tasks/tasks`,
 * which is what "another app changed it" looks like to FocusForce+.
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

    private fun table(uri: Uri): String = when (uri.pathSegments.firstOrNull()) {
        "tasklists" -> "tasklists"
        "tasks" -> "tasks"
        else -> throw IllegalArgumentException("Unknown $uri")
    }

    private fun byId(uri: Uri, selection: String?): String? =
        if (uri.pathSegments.size > 1) "_id = ${ContentUris.parseId(uri)}" + (selection?.let { " AND ($it)" } ?: "") else selection

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        helper.readableDatabase.query(table(uri), projection, byId(uri, selection), selectionArgs, null, null, sortOrder)

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val id = helper.writableDatabase.insertOrThrow(table(uri), null, values)
        notifyChange()
        return ContentUris.withAppendedId(Uri.parse("content://$AUTHORITY/${table(uri)}"), id)
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        helper.writableDatabase.update(table(uri), values, byId(uri, selection), selectionArgs).also { if (it > 0) notifyChange() }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        helper.writableDatabase.delete(table(uri), byId(uri, selection), selectionArgs).also { if (it > 0) notifyChange() }

    override fun getType(uri: Uri): String? = null

    private fun notifyChange() {
        context?.contentResolver?.notifyChange(Uri.parse("content://$AUTHORITY/tasks"), null)
    }

    private companion object {
        const val AUTHORITY = "com.focusforceplus.app.debug.tasks"
    }
}
