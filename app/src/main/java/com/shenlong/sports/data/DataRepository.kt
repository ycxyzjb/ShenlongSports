package com.shenlong.sports.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 数据持久化仓库 - 使用 SharedPreferences + JSON 保存/恢复数据
 *
 * 保存内容：
 * - 比赛配置（名称、组别、距离、跑道长度）
 * - 运动员列表（号码、姓名、单位、状态、圈数等）
 * - 比赛状态（是否进行中、开始时间戳、已用时间）
 */
object DataRepository {

    private const val PREFS_NAME = "shenlong_sports_data"
    private const val KEY_CONFIG = "race_config"
    private const val KEY_ATHLETES = "athletes"
    private const val KEY_IS_RUNNING = "is_running"
    private const val KEY_START_TIMESTAMP = "start_timestamp"
    private const val KEY_ELAPSED_MS = "elapsed_ms"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存比赛配置
     */
    fun saveConfig(context: Context, config: RaceConfig) {
        val json = JSONObject().apply {
            put("name", config.name)
            put("group", config.group)
            put("distanceMeters", config.distanceMeters)
            put("trackLengthMeters", config.trackLengthMeters)
        }
        getPrefs(context).edit().putString(KEY_CONFIG, json.toString()).apply()
    }

    /**
     * 加载比赛配置
     */
    fun loadConfig(context: Context): RaceConfig {
        val str = getPrefs(context).getString(KEY_CONFIG, null) ?: return RaceConfig()
        return try {
            val json = JSONObject(str)
            RaceConfig(
                name = json.optString("name", ""),
                group = json.optString("group", ""),
                distanceMeters = json.optInt("distanceMeters", 0),
                trackLengthMeters = json.optInt("trackLengthMeters", 400)
            )
        } catch (_: Exception) {
            RaceConfig()
        }
    }

    /**
     * 保存运动员列表
     */
    fun saveAthletes(context: Context, athletes: List<Athlete>) {
        val array = JSONArray()
        for (a in athletes) {
            val json = JSONObject().apply {
                put("number", a.number)
                put("name", a.name)
                put("team", a.team)
                put("status", a.status.name)
                put("completedLaps", a.completedLaps)
                put("lastLapTimeMs", a.lastLapTimeMs)
                put("dnfAtLap", a.dnfAtLap)
            }
            array.put(json)
        }
        getPrefs(context).edit().putString(KEY_ATHLETES, array.toString()).apply()
    }

    /**
     * 加载运动员列表
     */
    fun loadAthletes(context: Context): List<Athlete> {
        val str = getPrefs(context).getString(KEY_ATHLETES, null) ?: return emptyList()
        return try {
            val array = JSONArray(str)
            (0 until array.length()).map { i ->
                val json = array.getJSONObject(i)
                Athlete(
                    number = json.getString("number"),
                    name = json.getString("name"),
                    team = json.optString("team", ""),
                    status = try {
                        AthleteStatus.valueOf(json.getString("status"))
                    } catch (_: Exception) {
                        AthleteStatus.ACTIVE
                    },
                    completedLaps = json.optInt("completedLaps", 0),
                    lastLapTimeMs = json.optLong("lastLapTimeMs", 0L),
                    dnfAtLap = json.optInt("dnfAtLap", 0)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 保存比赛运行状态
     */
    fun saveRaceState(context: Context, isRunning: Boolean, startTimestamp: Long, elapsedMs: Long) {
        getPrefs(context).edit()
            .putBoolean(KEY_IS_RUNNING, isRunning)
            .putLong(KEY_START_TIMESTAMP, startTimestamp)
            .putLong(KEY_ELAPSED_MS, elapsedMs)
            .apply()
    }

    /**
     * 加载比赛运行状态
     * @return Triple(isRunning, startTimestamp, elapsedMs)
     */
    fun loadRaceState(context: Context): Triple<Boolean, Long, Long> {
        val prefs = getPrefs(context)
        return Triple(
            prefs.getBoolean(KEY_IS_RUNNING, false),
            prefs.getLong(KEY_START_TIMESTAMP, 0L),
            prefs.getLong(KEY_ELAPSED_MS, 0L)
        )
    }

    /**
     * 清除所有数据（重置比赛时使用）
     */
    fun clearAll(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
