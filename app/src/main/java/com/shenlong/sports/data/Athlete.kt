package com.shenlong.sports.data

/**
 * 运动员状态
 */
enum class AthleteStatus {
    /** 正常参赛 */
    ACTIVE,
    /** 弃权 (Did Not Start) */
    DNS,
    /** 退赛 (Did Not Finish) */
    DNF,
    /** 完赛 */
    FINISHED
}

/**
 * 运动员
 */
data class Athlete(
    val number: String,          // 号码
    val name: String,            // 姓名
    val team: String = "",       // 单位/队伍
    val status: AthleteStatus = AthleteStatus.ACTIVE,
    /** 已完成圈数 */
    val completedLaps: Int = 0,
    /** 最后一圈完成时间戳(毫秒)，用于排序 */
    val lastLapTimeMs: Long = 0L,
    /** 退赛时已完成的圈数(用于成绩表展示) */
    val dnfAtLap: Int = 0
) {
    /** 是否参与记圈(正常状态且未完赛) */
    val isCounting: Boolean
        get() = status == AthleteStatus.ACTIVE

    /** 成绩表显示的状态文字 */
    val statusLabel: String
        get() = when (status) {
            AthleteStatus.DNS -> "弃权"
            AthleteStatus.DNF -> "退赛"
            AthleteStatus.FINISHED -> "完赛"
            AthleteStatus.ACTIVE -> "进行中"
        }
}
