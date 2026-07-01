package com.shenlong.sports.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shenlong.sports.data.Athlete
import com.shenlong.sports.data.AthleteStatus
import com.shenlong.sports.data.DataRepository
import com.shenlong.sports.data.RaceConfig
import com.shenlong.sports.data.RaceResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 语音播报事件 */
sealed class VoiceEvent {
    data class AllLap(val totalLaps: Int) : VoiceEvent()
    data class OneLap(val number: String, val completed: Int, val totalLaps: Int) : VoiceEvent()
    data class LastLap(val number: String) : VoiceEvent()
    data class Finished(val number: String, val rank: Int) : VoiceEvent()
    data object AllFinished : VoiceEvent()
}

data class RaceUiState(
    val config: RaceConfig = RaceConfig(),
    val athletes: List<Athlete> = emptyList(),
    val isRunning: Boolean = false,
    val elapsedMs: Long = 0L,
    val startTimestamp: Long = 0L,
    val voiceEvent: VoiceEvent? = null,
    val toastMessage: String? = null
) {
    /** 参与记圈的运动员(非弃权) */
    val countingAthletes: List<Athlete>
        get() = athletes.filter { it.status != AthleteStatus.DNS }

    /** 排名列表：按圈数降序、最后记圈时间升序 */
    val rankedAthletes: List<Pair<Int, Athlete>>
        get() {
            val list = countingAthletes
            val sorted = list.sortedWith(
                compareByDescending<Athlete> { it.completedLaps }
                    .thenBy { it.lastLapTimeMs }
                    .thenBy { it.number }
            )
            return sorted.mapIndexed { index, athlete -> (index + 1) to athlete }
        }

    /** 是否所有参赛运动员都已完赛或退赛 */
    val allDone: Boolean
        get() = countingAthletes.isNotEmpty() &&
            countingAthletes.all { it.status == AthleteStatus.FINISHED || it.status == AthleteStatus.DNF }

    val totalLaps: Int
        get() = config.totalLaps
}

class RaceViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RaceUiState())
    val uiState: StateFlow<RaceUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        // 从本地存储恢复数据
        loadPersistedData()
    }

    /**
     * 从 SharedPreferences 恢复所有数据
     */
    private fun loadPersistedData() {
        val context = getApplication<Application>()
        val config = DataRepository.loadConfig(context)
        val athletes = DataRepository.loadAthletes(context)
        val (isRunning, startTimestamp, elapsedMs) = DataRepository.loadRaceState(context)

        _uiState.update { it.copy(
            config = config,
            athletes = athletes,
            isRunning = isRunning,
            startTimestamp = startTimestamp,
            elapsedMs = elapsedMs
        )}

        // 如果之前比赛正在进行，恢复计时器
        if (isRunning) {
            startTimer()
        }
    }

    /**
     * 持久化保存当前状态
     */
    private fun persistState() {
        val context = getApplication<Application>()
        val state = _uiState.value
        DataRepository.saveConfig(context, state.config)
        DataRepository.saveAthletes(context, state.athletes)
        DataRepository.saveRaceState(context, state.isRunning, state.startTimestamp, state.elapsedMs)
    }

    // region 比赛设置
    fun updateConfig(config: RaceConfig) {
        _uiState.update { it.copy(config = config) }
        persistState()
    }
    // endregion

    // region 运动员管理
    fun addAthlete(number: String, name: String, team: String) {
        if (number.isBlank() || name.isBlank()) return
        if (_uiState.value.athletes.any { it.number == number }) {
            _uiState.update { it.copy(toastMessage = "号码 $number 已存在") }
            return
        }
        _uiState.update { state ->
            state.copy(athletes = state.athletes + Athlete(number, name, team))
        }
        persistState()
    }

    fun batchImport(lines: List<String>) {
        val newAthletes = mutableListOf<Athlete>()
        val existing = _uiState.value.athletes.map { it.number }.toMutableSet()
        for (line in lines) {
            val parts = line.split(",", "，", "\t").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size < 2) continue
            val number = parts[0]
            val name = parts[1]
            val team = if (parts.size >= 3) parts[2] else ""
            if (number.isBlank() || name.isBlank() || number in existing) continue
            newAthletes += Athlete(number, name, team)
            existing += number
        }
        _uiState.update { state ->
            state.copy(
                athletes = state.athletes + newAthletes,
                toastMessage = "成功导入 ${newAthletes.size} 位运动员"
            )
        }
        persistState()
    }

    fun toggleDns(number: String) {
        _uiState.update { state ->
            state.copy(athletes = state.athletes.map { a ->
                if (a.number == number) {
                    val newStatus = if (a.status == AthleteStatus.DNS) AthleteStatus.ACTIVE else AthleteStatus.DNS
                    a.copy(status = newStatus)
                } else a
            })
        }
        persistState()
    }

    fun deleteAthlete(number: String) {
        _uiState.update { state ->
            state.copy(athletes = state.athletes.filterNot { it.number == number })
        }
        persistState()
    }

    fun clearAllAthletes() {
        _uiState.update { state ->
            state.copy(athletes = emptyList())
        }
        persistState()
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun clearVoice() {
        _uiState.update { it.copy(voiceEvent = null) }
    }
    // endregion

    // region 记圈
    fun startTiming() {
        if (_uiState.value.isRunning) return
        val now = System.currentTimeMillis()
        _uiState.update { it.copy(isRunning = true, startTimestamp = now) }
        startTimer()
        persistState()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                if (!state.isRunning) break
                val elapsed = System.currentTimeMillis() - state.startTimestamp
                _uiState.update { it.copy(elapsedMs = elapsed) }
                delay(100)
            }
        }
    }

    private fun stopTiming() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(isRunning = false) }
        persistState()
    }

    /** 检查是否所有参赛运动员都已完赛或退赛，如果是则停止计时并归零 */
    private fun checkAllDone() {
        val state = _uiState.value
        val counting = state.athletes.filter { it.status != AthleteStatus.DNS }
        if (counting.isNotEmpty() && counting.all { it.status == AthleteStatus.FINISHED || it.status == AthleteStatus.DNF }) {
            _uiState.update { it.copy(voiceEvent = VoiceEvent.AllFinished) }
            // 停止计时并将秒表归零
            timerJob?.cancel()
            timerJob = null
            _uiState.update { it.copy(isRunning = false, elapsedMs = 0L) }
            persistState()
        }
    }

    fun resetRace() {
        timerJob?.cancel()
        _uiState.update { state ->
            state.copy(
                isRunning = false,
                elapsedMs = 0L,
                startTimestamp = 0L,
                athletes = state.athletes.map { a ->
                    a.copy(
                        status = if (a.status == AthleteStatus.DNS) AthleteStatus.DNS else AthleteStatus.ACTIVE,
                        completedLaps = 0,
                        lastLapTimeMs = 0L,
                        dnfAtLap = 0
                    )
                },
                voiceEvent = null,
                toastMessage = "比赛已复位"
            )
        }
        persistState()
    }

    /** 所有运动员记一圈 */
    fun addLapToAll() {
        val state = _uiState.value
        if (!state.isRunning) {
            _uiState.update { it.copy(toastMessage = "请先开始记圈") }
            return
        }
        val now = System.currentTimeMillis()
        val totalLaps = state.totalLaps
        _uiState.update { s ->
            val updated = s.athletes.map { a ->
                if (a.status != AthleteStatus.ACTIVE) return@map a
                val newLaps = a.completedLaps + 1
                if (newLaps >= totalLaps) {
                    a.copy(
                        completedLaps = totalLaps,
                        status = AthleteStatus.FINISHED,
                        lastLapTimeMs = now
                    )
                } else {
                    a.copy(completedLaps = newLaps, lastLapTimeMs = now)
                }
            }
            s.copy(
                athletes = updated,
                voiceEvent = VoiceEvent.AllLap(totalLaps)
            )
        }
        persistState()
        checkAllDone()
    }

    /** 单个运动员记一圈 */
    fun addLap(number: String, fromQrScan: Boolean = false) {
        val state = _uiState.value
        if (!state.isRunning) {
            _uiState.update { it.copy(toastMessage = "请先开始记圈") }
            return
        }
        val now = System.currentTimeMillis()
        val totalLaps = state.totalLaps
        var voice: VoiceEvent? = null
        _uiState.update { s ->
            val updated = s.athletes.map { a ->
                if (a.number != number || a.status != AthleteStatus.ACTIVE) return@map a
                val newLaps = a.completedLaps + 1
                if (newLaps >= totalLaps) {
                    voice = VoiceEvent.Finished(a.number, 0)
                    a.copy(
                        completedLaps = totalLaps,
                        status = AthleteStatus.FINISHED,
                        lastLapTimeMs = now
                    )
                } else {
                    if (newLaps == totalLaps - 1) {
                        voice = VoiceEvent.LastLap(a.number)
                    } else if (!fromQrScan) {
                        // 扫码记圈不播报"X号记一圈"，手动记圈才播
                        voice = VoiceEvent.OneLap(a.number, newLaps, totalLaps)
                    }
                    a.copy(completedLaps = newLaps, lastLapTimeMs = now)
                }
            }
            s.copy(athletes = updated, voiceEvent = voice)
        }
        persistState()
        checkAllDone()
    }

    /** 误记减一圈 */
    fun subtractLap(number: String) {
        _uiState.update { s ->
            val updated = s.athletes.map { a ->
                if (a.number != number) return@map a
                when {
                    a.status == AthleteStatus.FINISHED -> {
                        a.copy(
                            completedLaps = (a.completedLaps - 1).coerceAtLeast(0),
                            status = AthleteStatus.ACTIVE,
                            lastLapTimeMs = 0L
                        )
                    }
                    a.status == AthleteStatus.ACTIVE && a.completedLaps > 0 -> {
                        a.copy(completedLaps = a.completedLaps - 1)
                    }
                    else -> a
                }
            }
            s.copy(athletes = updated, voiceEvent = null, toastMessage = "${number}号减一圈")
        }
        persistState()
    }

    /** 运动员退赛 */
    fun markDnf(number: String) {
        _uiState.update { s ->
            val updated = s.athletes.map { a ->
                if (a.number != number || a.status != AthleteStatus.ACTIVE) return@map a
                a.copy(status = AthleteStatus.DNF, dnfAtLap = a.completedLaps)
            }
            s.copy(athletes = updated, toastMessage = "${number}号已退赛")
        }
        persistState()
    }
    // endregion

    // region 成绩
    fun buildResults(): List<RaceResult> {
        val state = _uiState.value
        val totalLaps = state.totalLaps
        val startTs = state.startTimestamp
        // 完赛者按完赛时间(最后记圈时间)排序
        val finished = state.athletes
            .filter { it.status == AthleteStatus.FINISHED }
            .sortedBy { it.lastLapTimeMs }
        // 退赛者按已完成圈数降序
        val dnf = state.athletes
            .filter { it.status == AthleteStatus.DNF }
            .sortedByDescending { it.completedLaps }
        // 弃权者
        val dns = state.athletes
            .filter { it.status == AthleteStatus.DNS }
            .sortedBy { it.number }
        // 仍在进行中的(比赛未结束就查看)
        val active = state.athletes
            .filter { it.status == AthleteStatus.ACTIVE }
            .sortedByDescending { it.completedLaps }

        val results = mutableListOf<RaceResult>()
        var rank = 1
        for (a in finished) {
            // 完赛用时 = 最后记圈时间 - 比赛开始时间
            val elapsedMs = if (startTs > 0 && a.lastLapTimeMs > startTs) a.lastLapTimeMs - startTs else 0L
            results += RaceResult(
                rank = rank++, number = a.number, name = a.name, team = a.team,
                completedLaps = a.completedLaps, totalLaps = totalLaps,
                statusLabel = "完赛", finishTimeMs = elapsedMs,
                isFinished = true, isDnf = false, isDns = false
            )
        }
        for (a in dnf) {
            results += RaceResult(
                rank = rank, number = a.number, name = a.name, team = a.team,
                completedLaps = a.completedLaps, totalLaps = totalLaps,
                statusLabel = "退赛", finishTimeMs = 0L,
                isFinished = false, isDnf = true, isDns = false
            )
        }
        for (a in active) {
            results += RaceResult(
                rank = rank, number = a.number, name = a.name, team = a.team,
                completedLaps = a.completedLaps, totalLaps = totalLaps,
                statusLabel = "进行中", finishTimeMs = 0L,
                isFinished = false, isDnf = false, isDns = false
            )
        }
        for (a in dns) {
            results += RaceResult(
                rank = rank, number = a.number, name = a.name, team = a.team,
                completedLaps = 0, totalLaps = totalLaps,
                statusLabel = "弃权", finishTimeMs = 0L,
                isFinished = false, isDnf = false, isDns = true
            )
        }
        return results
    }
    // endregion

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        // 应用退出时保存数据
        persistState()
    }
}
