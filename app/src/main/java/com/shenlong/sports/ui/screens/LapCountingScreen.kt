package com.shenlong.sports.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenlong.sports.data.Athlete
import com.shenlong.sports.data.AthleteStatus
import com.shenlong.sports.ui.components.GradientHeader
import com.shenlong.sports.ui.components.RankBadge
import com.shenlong.sports.ui.components.StatusChip
import com.shenlong.sports.ui.components.StopwatchDisplay
import com.shenlong.sports.ui.theme.DragonDark
import com.shenlong.sports.ui.theme.DragonGold
import com.shenlong.sports.ui.theme.DragonGreen
import com.shenlong.sports.ui.theme.DragonOrange
import com.shenlong.sports.ui.theme.DragonRed
import com.shenlong.sports.viewmodel.RaceUiState
import com.shenlong.sports.viewmodel.VoiceEvent

@Composable
fun LapCountingScreen(
    state: RaceUiState,
    ttsReady: Boolean,
    toneEnabled: Boolean,
    voiceEnabled: Boolean,
    vibrationEnabled: Boolean,
    onStart: () -> Unit,
    onReset: () -> Unit,
    onAddLapAll: () -> Unit,
    onAddLap: (String) -> Unit,
    onSubtractLap: (String) -> Unit,
    onDnf: (String) -> Unit,
    onConsumeVoice: () -> Unit,
    onConsumeToast: () -> Unit,
    onVoiceEvent: (VoiceEvent) -> Unit,
    onGoResults: () -> Unit,
    onToneToggle: (Boolean) -> Unit,
    onVoiceToggle: (Boolean) -> Unit,
    onVibrationToggle: (Boolean) -> Unit
) {
    var confirmReset by remember { mutableStateOf(false) }
    var confirmDnfNumber by remember { mutableStateOf<String?>(null) }

    // 开关状态用mutableStateOf管理，初始化后由本地状态驱动UI
    var localToneEnabled by remember { mutableStateOf(toneEnabled) }
    var localVoiceEnabled by remember { mutableStateOf(voiceEnabled) }
    var localVibrationEnabled by remember { mutableStateOf(vibrationEnabled) }

    // 处理语音事件
    LaunchedEffect(state.voiceEvent) {
        state.voiceEvent?.let {
            onVoiceEvent(it)
            onConsumeVoice()
        }
    }

    // 处理 Toast
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            onConsumeToast()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        GradientHeader(title = "记圈", subtitle = state.config.name.ifEmpty { "蜃龙体育长跑记圈系统" })

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)
        ) {
            // 秒表
            item {
                StopwatchDisplay(
                    elapsedMs = state.elapsedMs,
                    isRunning = state.isRunning,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // 反馈开关栏
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // 提示音开关
                        FeedbackSwitch(
                            label = "提示音",
                            checked = localToneEnabled,
                            onCheckedChange = {
                                localToneEnabled = it
                                onToneToggle(it)
                            },
                            activeColor = DragonGreen
                        )
                        // 语音开关（本地TTS或网络TTS均可使用）
                        FeedbackSwitch(
                            label = "语音",
                            checked = localVoiceEnabled,
                            onCheckedChange = {
                                localVoiceEnabled = it
                                onVoiceToggle(it)
                            },
                            activeColor = DragonGreen
                        )
                        // 震动开关
                        FeedbackSwitch(
                            label = "震动",
                            checked = localVibrationEnabled,
                            onCheckedChange = {
                                localVibrationEnabled = it
                                onVibrationToggle(it)
                            },
                            activeColor = DragonOrange
                        )
                    }
                }
            }

            // 控制按钮
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlButton(
                        text = if (state.isRunning) "记圈中" else "开始记圈",
                        icon = Icons.Filled.PlayArrow,
                        color = DragonGreen,
                        enabled = !state.isRunning,
                        onClick = onStart,
                        modifier = Modifier.weight(1f)
                    )
                    ControlButton(
                        text = "清零复位",
                        icon = Icons.Filled.Refresh,
                        color = DragonOrange,
                        enabled = true,
                        onClick = { confirmReset = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 所有人记一圈按钮
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { onAddLapAll() },
                    enabled = state.isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DragonRed,
                        disabledContainerColor = DragonRed.copy(alpha = 0.5f),
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.Group,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "所有运动员记一圈",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 快捷记圈号码板
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "快捷记圈（点击号码 +1 圈）",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // 用 FlowLayout 效果：多行排列号码按钮
                        val activeAthletes = state.countingAthletes.filter {
                            it.status == AthleteStatus.ACTIVE
                        }
                        if (activeAthletes.isEmpty()) {
                            Text(
                                text = "暂无参赛运动员",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(8.dp)
                            )
                        } else {
                            // 按行排列，每行最多放4个
                            val chunked = activeAthletes.chunked(4)
                            chunked.forEach { rowAthletes ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowAthletes.forEach { athlete ->
                                        Button(
                                            onClick = { onAddLap(athlete.number) },
                                            enabled = state.isRunning,
                                            modifier = Modifier.weight(1f).height(40.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = DragonRed,
                                                disabledContainerColor = DragonRed.copy(alpha = 0.35f),
                                                contentColor = Color.White,
                                                disabledContentColor = Color.White.copy(alpha = 0.6f)
                                            ),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                text = "${athlete.number}号",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    // 不足4个时用空占位保持对齐
                                    repeat(4 - rowAthletes.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 运动员列表标题
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "实时排名",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "共 ${state.countingAthletes.size} 人",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 运动员排名列表
            val ranked = state.rankedAthletes
            val totalLaps = state.totalLaps

            items(ranked, key = { it.second.number }) { (rank, athlete) ->
                AthleteLapCard(
                    rank = rank,
                    athlete = athlete,
                    totalLaps = totalLaps,
                    isRunning = state.isRunning,
                    onAddLap = { onAddLap(athlete.number) },
                    onSubtractLap = { onSubtractLap(athlete.number) },
                    onDnf = { confirmDnfNumber = athlete.number }
                )
            }

            // 查看成绩按钮
            if (state.allDone) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable { onGoResults() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = DragonDark),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "比赛结束 - 查看成绩",
                                color = DragonGold,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // 确认复位对话框
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("确认清零复位", fontWeight = FontWeight.Bold) },
            text = { Text("将清除所有记圈数据和计时，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    onReset()
                    confirmReset = false
                }) { Text("确认复位", color = DragonRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("取消") }
            }
        )
    }

    // 确认退赛对话框
    confirmDnfNumber?.let { number ->
        AlertDialog(
            onDismissRequest = { confirmDnfNumber = null },
            title = { Text("确认退赛", fontWeight = FontWeight.Bold) },
            text = { Text("$number 号运动员将标记为退赛，不再记圈。") },
            confirmButton = {
                TextButton(onClick = {
                    onDnf(number)
                    confirmDnfNumber = null
                }) { Text("确认退赛", color = DragonOrange, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDnfNumber = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ControlButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = color.copy(alpha = 0.4f),
            contentColor = Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.6f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (enabled) 4.dp else 0.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AthleteLapCard(
    rank: Int,
    athlete: Athlete,
    totalLaps: Int,
    isRunning: Boolean,
    onAddLap: () -> Unit,
    onSubtractLap: () -> Unit,
    onDnf: () -> Unit
) {
    val isFinished = athlete.status == AthleteStatus.FINISHED
    val isDnf = athlete.status == AthleteStatus.DNF
    val isActive = athlete.status == AthleteStatus.ACTIVE
    val remaining = (totalLaps - athlete.completedLaps).coerceAtLeast(0)
    val progress = if (totalLaps > 0) athlete.completedLaps.toFloat() / totalLaps else 0f

    val cardColor = when {
        isFinished -> DragonGreen.copy(alpha = 0.1f)
        isDnf -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        rank == 1 -> DragonGold.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 排名
                RankBadge(rank = if (isDnf) 0 else rank)

                Spacer(modifier = Modifier.width(10.dp))

                // 号码牌
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isFinished -> DragonGreen
                                isDnf -> Color.Gray
                                else -> DragonRed
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = athlete.number,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // 姓名和进度
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = athlete.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDnf) Color.Gray else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "已完成 ${athlete.completedLaps}/$totalLaps 圈" +
                            if (isActive && remaining > 0) " · 剩余 $remaining 圈" else "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 状态
                when {
                    isFinished -> StatusChip(label = "完赛")
                    isDnf -> StatusChip(label = "退赛")
                }
            }

            // 进度条
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(DragonRed, DragonOrange, DragonGold)
                            )
                        )
                )
            }

            // 操作按钮
            if (isActive) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 记一圈
                    Button(
                        onClick = { onAddLap() },
                        enabled = isRunning,
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DragonRed,
                            disabledContainerColor = DragonRed.copy(alpha = 0.4f),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text("记一圈", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    // 减一圈
                    Button(
                        onClick = { onSubtractLap() },
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF546E7A),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("减一圈", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    // 退赛
                    Button(
                        onClick = { onDnf() },
                        modifier = Modifier.weight(1f).height(36.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DragonOrange,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("退赛", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onCheckedChange(!checked) }
    ) {
        Switch(
            checked = checked,
            onCheckedChange = null, // 点击由外部Modifier.clickable处理
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = activeColor,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color.Gray.copy(alpha = 0.4f),
                uncheckedThumbColor = Color.White,
                disabledCheckedTrackColor = activeColor.copy(alpha = 0.4f),
                disabledUncheckedTrackColor = Color.Gray.copy(alpha = 0.2f)
            ),
            modifier = Modifier.height(24.dp)
        )
        Text(
            text = if (!enabled) "$label(不可用)" else label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else Color.Gray
        )
    }
}
