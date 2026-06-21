package com.audiora.domain.model

import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

data class Chapter(
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long
) {
    companion object {
        fun serializeList(chapters: List<Chapter>): String {
            val array = JSONArray()
            for (ch in chapters) {
                try {
                    val obj = JSONObject()
                    obj.put("title", ch.title)
                    obj.put("startMs", ch.startMs)
                    obj.put("endMs", ch.endMs)
                    obj.put("durationMs", ch.durationMs)
                    array.put(obj)
                } catch (e: Exception) {
                    Timber.e(e, "Error serializing Chapter")
                }
            }
            return array.toString()
        }

        fun deserializeList(jsonStr: String?): List<Chapter> {
            if (jsonStr.isNullOrEmpty()) return emptyList()
            val list = mutableListOf<Chapter>()
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        Chapter(
                            title = obj.getString("title"),
                            startMs = obj.getLong("startMs"),
                            endMs = obj.getLong("endMs"),
                            durationMs = obj.getLong("durationMs")
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error parsing chapters JSON")
            }
            return list
        }
    }
}
