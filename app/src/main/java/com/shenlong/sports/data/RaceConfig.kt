package com.shenlong.sports.data

/**
 * 比赛配置
 */
data class RaceConfig(
    val name: String = "",            // 比赛名称
    val group: String = "",           // 比赛组别
    val distanceMeters: Int = 0,      // 比赛距离(米)
    val trackLengthMeters: Int = 400  // 跑道长度(米)
) {
    /** 根据比赛距离与跑道长度计算总圈数 */
    val totalLaps: Int
        get() = if (trackLengthMeters <= 0) 0 else
            (distanceMeters + trackLengthMeters - 1) / trackLengthMeters
}
