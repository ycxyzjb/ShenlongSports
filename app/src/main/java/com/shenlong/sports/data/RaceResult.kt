package com.shenlong.sports.data

/**
 * 单个运动员的最终成绩行
 */
data class RaceResult(
    val rank: Int,               // 排名 (退赛/弃权排末尾)
    val number: String,          // 号码
    val name: String,            // 姓名
    val team: String,            // 单位/队伍
    val completedLaps: Int,      // 完成圈数
    val totalLaps: Int,          // 总圈数
    val statusLabel: String,     // 状态文字
    val finishTimeMs: Long,      // 完赛/退赛时间戳
    val isFinished: Boolean,     // 是否正常完赛
    val isDnf: Boolean,          // 是否退赛
    val isDns: Boolean           // 是否弃权
)
