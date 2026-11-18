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
    /** The order of the list's sections, one name per line, e.g. the aisles of a shop. */
    val sectionOrder: String? = null,
) {
    val sections: List<String> get() = sectionOrder?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
}

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
    /** Where it is in the shop or the bag: "Produce", "Toiletries". Null = no section. */
    val section: String? = null,
    /** How much: "2", "500 g", "x3". Parsed from what was typed. */
    val quantity: String? = null,
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
    /** The section this item went into last time, so it lands there again. */
    val section: String? = null,
)
