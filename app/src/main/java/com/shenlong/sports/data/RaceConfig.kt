package com.shenlong.sports.data

/**
 * 比赛配置
 */
data class RaceConfig(
    val name: String = "",            // 比赛名称
    val group: String = "",           // 比赛组别
    val distanceMeters: Int = 0,      // 比赛距离(米)
    val trackLengthMeters: Int = 400, // 跑道长度(米)
    val qrCooldownSeconds: Int = 5,   // 扫码冷却时间(秒)，同一号码重复识别间隔
    val awardTopN: Int = 3            // 取前N名（成绩单分割线位置，0=不显示）
) {
    /** 根据比赛距离与跑道长度计算总圈数 */
    val totalLaps: Int
        get() = if (trackLengthMeters <= 0) 0 else
            (distanceMeters + trackLengthMeters - 1) / trackLengthMeters
}
