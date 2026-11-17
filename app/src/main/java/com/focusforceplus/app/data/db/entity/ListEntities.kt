package com.focusforceplus.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A reusable list: groceries, a packing list, things to bring to the office.
 *
 * Unlike a todo, a list is never "done". Its items get ticked off, cleared, and
 * added again next week, which is why it has its own module instead of living
 * inside the checklist of a todo.
 */
@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val iconKey: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
    /** Set when the list is in the trash; null = not deleted. */
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "list_items",
    foreignKeys = [
        ForeignKey(
            entity = ListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("listId")],
)
data class ListItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val listId: Long,
    val text: String,
    /** Free detail next to the item: "2x", "the big one", "500 g". */
    val note: String? = null,
    val isChecked: Boolean = false,
    val checkedAt: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
)

/**
 * What a list has contained before. Feeds the suggestions under the input field,
 * so the weekly shop is a few taps instead of typing the same words again.
 * [key] is the lower-cased text and makes an entry unique per list.
 */
@Entity(
    tableName = "list_suggestions",
    indices = [Index(value = ["listId", "key"], unique = true)],
)
data class ListSuggestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val listId: Long,
    val key: String,
    val text: String,
    val useCount: Int = 1,
    val lastUsedAt: Long,
)
