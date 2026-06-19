package com.shenlong.sports.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenlong.sports.data.RaceConfig
import com.shenlong.sports.ui.components.GradientHeader
import com.shenlong.sports.ui.theme.DragonDark
import com.shenlong.sports.ui.theme.DragonGold
import com.shenlong.sports.ui.theme.DragonOrange
import com.shenlong.sports.ui.theme.DragonRed

@Composable
fun RaceSetupScreen(
    config: RaceConfig,
    onSave: (RaceConfig) -> Unit,
    onNext: () -> Unit
) {
    var name by remember(config) { mutableStateOf(config.name) }
    var group by remember(config) { mutableStateOf(config.group) }
    var distance by remember(config) { mutableStateOf(if (config.distanceMeters > 0) config.distanceMeters.toString() else "") }
    var trackLength by remember(config) { mutableStateOf(if (config.trackLengthMeters > 0) config.trackLengthMeters.toString() else "400") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val totalLaps = remember(distance, trackLength) {
        val d = distance.toIntOrNull() ?: 0
        val t = trackLength.toIntOrNull() ?: 0
        if (t <= 0) 0 else (d + t - 1) / t
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        GradientHeader(title = "比赛设置")

        Spacer(modifier = Modifier.height(16.dp))

        // 设置卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "基本信息",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                SetupField(
                    icon = Icons.Filled.Flag,
                    label = "比赛名称",
                    value = name,
                    placeholder = "如：2024春季运动会",
                    onValueChange = { name = it }
                )
                Spacer(modifier = Modifier.height(12.dp))

                SetupField(
                    icon = Icons.Filled.Groups,
                    label = "比赛组别",
                    value = group,
                    placeholder = "如：男子组 / 女子组",
                    onValueChange = { group = it }
                )
                Spacer(modifier = Modifier.height(12.dp))

                SetupField(
                    icon = Icons.Filled.SportsScore,
                    label = "比赛距离 (米)",
                    value = distance,
                    placeholder = "如：5000",
                    keyboardType = KeyboardType.Number,
                    onValueChange = { distance = it.filter { c -> c.isDigit() } }
                )
                Spacer(modifier = Modifier.height(12.dp))

                SetupField(
                    icon = Icons.Filled.DirectionsRun,
                    label = "跑道长度 (米)",
                    value = trackLength,
                    placeholder = "如：400",
                    keyboardType = KeyboardType.Number,
                    onValueChange = { trackLength = it.filter { c -> c.isDigit() } }
                )
            }
        }

        // 总圈数预览
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DragonDark),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "预计总圈数",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$totalLaps 圈",
                        color = DragonGold,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(DragonRed, DragonOrange)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = totalLaps.toString(),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    val newConfig = RaceConfig(
                        name = name,
                        group = group,
                        distanceMeters = distance.toIntOrNull() ?: 0,
                        trackLengthMeters = trackLength.toIntOrNull() ?: 400
                    )
                    onSave(newConfig)
                    scope.launch {
                        snackbarHostState.showSnackbar("设置已保存")
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF546E7A)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Text("保存设置", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Button(
                onClick = {
                    onSave(
                        RaceConfig(
                            name = name,
                            group = group,
                            distanceMeters = distance.toIntOrNull() ?: 0,
                            trackLengthMeters = trackLength.toIntOrNull() ?: 400
                        )
                    )
                    onNext()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DragonRed),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("下一步", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    }
}

@Composable
private fun SetupField(
    icon: ImageVector,
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DragonRed,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}
