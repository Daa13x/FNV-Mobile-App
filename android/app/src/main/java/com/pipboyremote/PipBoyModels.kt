package com.pipboyremote

import org.json.JSONArray
import org.json.JSONObject

/** Strictly presentation-oriented values. Missing game fields stay unknown. */
data class GameItem(
    val id: String,
    val name: String,
    val category: String,
    val count: Int,
    val equipped: Boolean,
    val weight: Double?,
    val value: Double?,
    val condition: Double?
)

data class GameQuest(
    val id: String,
    val title: String,
    val status: String,
    val objectives: List<String>
)

data class MapMarker(val name: String, val visited: Boolean?, val canFastTravel: Boolean?)

data class GameSnapshot(
    val source: String,
    val isDemo: Boolean,
    val characterName: String?,
    val level: Int?,
    val hp: Double?,
    val maxHp: Double?,
    val ap: Double?,
    val maxAp: Double?,
    val carryWeight: Double?,
    val maxCarryWeight: Double?,
    val caps: Double?,
    val radiation: Double?,
    val location: String?,
    val special: Map<String, Double>,
    val inventory: List<GameItem>,
    val quests: List<GameQuest>,
    val world: String?,
    val markers: List<MapMarker>,
    val radioStation: String?,
    val hunger: Double?,
    val thirst: Double?,
    val sleep: Double?,
    val capabilities: Map<String, Boolean>,
    val gameTick: Long?
) {
    fun supports(name: String): Boolean = capabilities[name] == true
}

private fun JSONObject.numberOrNull(key: String): Double? =
    (opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }

private fun JSONObject.intOrNull(key: String): Int? =
    numberOrNull(key)?.takeIf { it >= 0 && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }?.toInt()

private fun JSONObject.stringOrNull(key: String): String? =
    (opt(key) as? String)?.takeIf { it.isNotBlank() }

private inline fun <T> JSONArray?.objects(map: (JSONObject) -> T): List<T> {
    val array = this ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            add(map(obj))
        }
    }
}

/** Expects our own bridge protocol v1, not raw AYN JSON. Never guesses absent values. */
fun parseGameSnapshot(root: JSONObject): GameSnapshot {
    require(root.optInt("protocol", -1) == 1) { "Unsupported snapshot protocol" }
    val source = root.stringOrNull("source") ?: throw IllegalArgumentException("Snapshot lacks a source")
    val demo = source == "mock"
    val character = root.optJSONObject("character") ?: JSONObject()
    val specialData = root.optJSONObject("special") ?: JSONObject()
    val special = buildMap {
        val keys = specialData.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            specialData.numberOrNull(key)?.let { put(key, it) }
        }
    }
    val inventory = root.optJSONArray("inventory").objects { item ->
        GameItem(
            id = item.optString("id", ""),
            name = item.stringOrNull("name") ?: "Unknown item",
            category = item.stringOrNull("category") ?: "Misc",
            count = item.intOrNull("count") ?: 0,
            equipped = item.optBoolean("equipped", false),
            weight = item.numberOrNull("weight"),
            value = item.numberOrNull("value"),
            condition = item.numberOrNull("condition")?.takeIf { it in 0.0..1.0 }
        )
    }
    val quests = root.optJSONArray("quests").objects { quest ->
        GameQuest(
            id = quest.optString("id", ""),
            title = quest.stringOrNull("title") ?: "Unnamed quest",
            status = quest.optString("status", "unknown"),
            objectives = buildList {
                val objectives = quest.optJSONArray("objectives") ?: return@buildList
                for (i in 0 until objectives.length()) {
                    val text = objectives.optString(i, "")
                    if (text.isNotBlank()) add(text)
                }
            }
        )
    }
    val map = root.optJSONObject("map") ?: JSONObject()
    val markers = map.optJSONArray("markers").objects { marker ->
        MapMarker(
            name = marker.stringOrNull("name") ?: "Unknown marker",
            visited = (marker.opt("visited") as? Boolean),
            canFastTravel = (marker.opt("canFastTravel") as? Boolean)
        )
    }
    val survival = root.optJSONObject("survival") ?: JSONObject()
    val caps = root.optJSONObject("capabilities") ?: JSONObject()
    val capabilities = buildMap {
        val keys = caps.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = caps.opt(key)
            if (value is Boolean) put(key, value)
        }
    }
    return GameSnapshot(
        source = source,
        isDemo = demo,
        characterName = character.stringOrNull("name"),
        level = character.intOrNull("level"),
        hp = character.numberOrNull("hp"),
        maxHp = character.numberOrNull("maxHp"),
        ap = character.numberOrNull("ap"),
        maxAp = character.numberOrNull("maxAp"),
        carryWeight = character.numberOrNull("carryWeight"),
        maxCarryWeight = character.numberOrNull("maxCarryWeight"),
        caps = character.numberOrNull("caps"),
        radiation = character.numberOrNull("radiation"),
        location = character.stringOrNull("location"),
        special = special,
        inventory = inventory,
        quests = quests,
        world = map.stringOrNull("world"),
        markers = markers,
        radioStation = root.optJSONObject("radio")?.stringOrNull("station"),
        hunger = survival.numberOrNull("hunger"),
        thirst = survival.numberOrNull("thirst"),
        sleep = survival.numberOrNull("sleep"),
        capabilities = capabilities,
        gameTick = (root.opt("sequence") as? Number)?.toLong()
    )
}
