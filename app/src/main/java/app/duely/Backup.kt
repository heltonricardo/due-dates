package app.duely

import org.json.JSONArray
import org.json.JSONObject

object Backup {
    fun toJson(reminders: List<Reminder>, states: List<OccurrenceState>): String {
        val root = JSONObject()
        root.put("version", 1)
        val rs = JSONArray()
        reminders.forEach {
            rs.put(
                JSONObject()
                    .put("id", it.id)
                    .put("groupId", it.groupId)
                    .put("title", it.title)
                    .put("note", it.note ?: JSONObject.NULL)
                    .put("type", it.type.name)
                    .put("startDate", it.startDate)
                    .put("anchorDay", it.anchorDay)
                    .put("occurrenceCount", it.occurrenceCount ?: JSONObject.NULL)
                    .put("icon", it.icon)
                    .put("color", it.color)
                    .put("businessDay", it.businessDay)
                    .put("notifyWeekends", it.notifyWeekends)
            )
        }
        val ss = JSONArray()
        states.forEach {
            ss.put(
                JSONObject()
                    .put("id", it.id)
                    .put("groupId", it.groupId)
                    .put("period", it.period)
                    .put("title", it.title)
                    .put("note", it.note ?: JSONObject.NULL)
                    .put("dueDate", it.dueDate)
                    .put("completedAt", it.completedAt ?: JSONObject.NULL)
                    .put("deleted", it.deleted)
                    .put("isException", it.isException)
                    .put("icon", it.icon)
                    .put("color", it.color)
                    .put("notifyWeekends", it.notifyWeekends)
            )
        }
        root.put("reminders", rs)
        root.put("states", ss)
        return root.toString(2)
    }

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else getString(key)

    fun parse(text: String): Pair<List<Reminder>, List<OccurrenceState>> {
        val root = JSONObject(text)
        require(root.getInt("version") == 1)
        val rs = root.getJSONArray("reminders")
        val ss = root.getJSONArray("states")
        val reminders = (0 until rs.length()).map {
            val o = rs.getJSONObject(it)
            Reminder(
                id = o.getLong("id"),
                groupId = o.getString("groupId"),
                title = o.getString("title"),
                note = o.str("note"),
                type = Freq.valueOf(o.getString("type")),
                startDate = o.getString("startDate"),
                anchorDay = o.getInt("anchorDay"),
                occurrenceCount = if (o.isNull("occurrenceCount")) null else o.getInt("occurrenceCount"),
                icon = o.getString("icon"),
                color = o.getInt("color"),
                businessDay = o.getInt("businessDay"),
                notifyWeekends = o.getBoolean("notifyWeekends")
            )
        }
        val states = (0 until ss.length()).map {
            val o = ss.getJSONObject(it)
            OccurrenceState(
                id = o.getLong("id"),
                groupId = o.getString("groupId"),
                period = o.getString("period"),
                title = o.getString("title"),
                note = o.str("note"),
                dueDate = o.getString("dueDate"),
                completedAt = o.str("completedAt"),
                deleted = o.getBoolean("deleted"),
                isException = o.getBoolean("isException"),
                icon = o.getString("icon"),
                color = o.getInt("color"),
                notifyWeekends = o.getBoolean("notifyWeekends")
            )
        }
        return reminders to states
    }
}
