package com.focusforceplus.app.data.model

import org.json.JSONArray
import org.json.JSONObject

data class ChecklistItem(val text: String, val done: Boolean = false)

fun List<ChecklistItem>.toChecklistJson(): String {
    val arr = JSONArray()
    forEach { item ->
        arr.put(JSONObject().apply {
            put("text", item.text)
            put("done", item.done)
        })
    }
    return arr.toString()
}

fun checklistFromJson(json: String?): List<ChecklistItem> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        List(arr.length()) { i ->
            val obj = arr.getJSONObject(i)
            ChecklistItem(obj.getString("text"), obj.getBoolean("done"))
        }
    } catch (_: Exception) {
        emptyList()
    }
}
