package com.shenlong.sports.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shenlong.sports.data.RaceConfig
import com.shenlong.sports.data.RaceResult
import com.shenlong.sports.ui.components.GradientHeader
import com.shenlong.sports.ui.components.RankBadge
import com.shenlong.sports.ui.components.StatusChip
import com.shenlong.sports.ui.theme.DragonDark
import com.shenlong.sports.ui.theme.DragonGold
import com.shenlong.sports.ui.theme.DragonGreen
import com.shenlong.sports.ui.theme.DragonOrange
import com.shenlong.sports.ui.theme.DragonRed
import com.shenlong.sports.util.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ResultsScreen(
    config: RaceConfig,
    results: List<RaceResult>,
    elapsedMs: Long
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var generating by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        GradientHeader(title = "比赛成绩", subtitle = config.name.ifEmpty { "蜃龙体育长跑记圈系统" })

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            // 比赛信息摘要
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DragonDark),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.SportsScore, contentDescription = null, tint = DragonGold, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = config.name.ifEmpty { "未命名比赛" },
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoLine(label = "组别", value = config.group.ifEmpty { "-" })
                        InfoLine(label = "距离", value = "${config.distanceMeters} 米")
                        InfoLine(label = "跑道", value = "${config.trackLengthMeters} 米 / 圈")
                        InfoLine(label = "总圈数", value = "${config.totalLaps} 圈")
                        InfoLine(label = "用时", value = PdfGenerator.formatElapsed(elapsedMs))
                    }
                }
            }

            // 颁奖台 - 前三名
            val finished = results.filter { it.isFinished }
            if (finished.size >= 1) {
                item {
                    PodiumCard(results = finished.take(3))
                }
            }

            // 操作按钮
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButtonCard(
                        text = "导出PDF",
                        icon = Icons.Filled.PictureAsPdf,
                        color = DragonRed,
                        loading = generating,
                        onClick = {
                            scope.launch {
                                generating = true
                                withContext(Dispatchers.IO) {
                                    pdfUri = PdfGenerator.generateResultsPdf(context, config, results, elapsedMs)
                                }
                                generating = false
                                pdfUri?.let { uri ->
                                    // 打开PDF
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(viewIntent, "查看成绩单PDF"))
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ActionButtonCard(
                        text = "一键分享",
                        icon = Icons.Filled.Share,
                        color = DragonOrange,
                        loading = false,
                        onClick = {
                            scope.launch {
                                if (pdfUri == null) {
                                    generating = true
                                    withContext(Dispatchers.IO) {
                                        pdfUri = PdfGenerator.generateResultsPdf(context, config, results, elapsedMs)
                                    }
                                    generating = false
                                }
                                pdfUri?.let { uri ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, "${config.name} 成绩单")
                                        putExtra(Intent.EXTRA_TEXT, "蜃龙体育长跑记圈系统 - ${config.name} 比赛成绩单")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "分享成绩单"))
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 表头
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DragonRed)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("名次", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(50.dp))
                    Text("号码", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                    Text("姓名", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("单位", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
                    Text("圈数", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
                    Text("状态", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                }
            }

            // 成绩列表
            items(results, key = { it.number }) { result ->
                ResultRow(result)
            }

            if (results.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("暂无成绩数据", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "$label：", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.width(48.dp))
        Text(text = value, color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun PodiumCard(results: List<RaceResult>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("颁奖台", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                // 第二名
                if (results.size >= 2) {
                    PodiumColumn(result = results[1], height = 80, color = Color(0xFFC0C0C0), rank = 2)
                } else {
                    Spacer(modifier = Modifier.width(60.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                // 第一名
                PodiumColumn(result = results[0], height = 110, color = DragonGold, rank = 1)
                Spacer(modifier = Modifier.width(8.dp))
                // 第三名
                if (results.size >= 3) {
                    PodiumColumn(result = results[2], height = 60, color = Color(0xFFCD7F32), rank = 3)
                } else {
                    Spacer(modifier = Modifier.width(60.dp))
                }
            }
        }
    }
}

@Composable
private fun PodiumColumn(result: RaceResult, height: Int, color: Color, rank: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
        Text(result.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        Text("${result.number}号", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(height.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(color, color.copy(alpha = 0.6f))
                    )
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = rank.toString(),
                color = DragonDark,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ActionButtonCard(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = !loading,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = color.copy(alpha = 0.5f),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (loading) "生成中..." else text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ResultRow(result: RaceResult) {
    val rowColor = when {
        result.rank == 1 -> DragonGold.copy(alpha = 0.08f)
        result.rank == 2 -> Color(0xFFC0C0C0).copy(alpha = 0.08f)
        result.rank == 3 -> Color(0xFFCD7F32).copy(alpha = 0.08f)
        result.isDns -> Color(0xFF9E9E9E).copy(alpha = 0.12f)
        result.isDnf -> DragonOrange.copy(alpha = 0.06f)
        else -> MaterialTheme.colorScheme.surface
    }

    val textColor = when {
        result.isDns -> Color.Gray
        result.isDnf -> DragonOrange
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(rowColor)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 名次
        if (result.isFinished && result.rank <= 3) {
            RankBadge(rank = result.rank, modifier = Modifier.size(28.dp))
        } else {
            Text(
                text = if (result.isDns || result.isDnf) "-" else result.rank.toString(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                modifier = Modifier.width(50.dp)
            )
        }
        // 号码
        Text(
            text = result.number,
            fontSize = 13.sp,
            color = textColor,
            modifier = Modifier.width(56.dp)
        )
        // 姓名
        Text(
            text = result.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        // 单位
        Text(
            text = result.team.ifEmpty { "-" },
            fontSize = 12.sp,
            color = textColor.copy(alpha = 0.7f),
            modifier = Modifier.width(90.dp),
            maxLines = 1
        )
        // 圈数
        Text(
            text = if (result.isDns) "-" else "${result.completedLaps}/${result.totalLaps}",
            fontSize = 13.sp,
            color = textColor,
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Center
        )
        // 状态
        Box(modifier = Modifier.width(56.dp), contentAlignment = Alignment.Center) {
            StatusChip(label = result.statusLabel)
        }
    }
}
