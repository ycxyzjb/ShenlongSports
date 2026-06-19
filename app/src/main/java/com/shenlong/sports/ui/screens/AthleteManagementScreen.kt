package com.shenlong.sports.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.shenlong.sports.data.Athlete
import com.shenlong.sports.data.AthleteStatus
import com.shenlong.sports.ui.components.GradientHeader
import com.shenlong.sports.ui.components.StatusChip
import com.shenlong.sports.ui.theme.DragonOrange
import com.shenlong.sports.ui.theme.DragonRed
import com.shenlong.sports.util.QrCodeGenerator

@Composable
fun AthleteManagementScreen(
    athletes: List<Athlete>,
    onAdd: (String, String, String) -> Unit,
    onBatchImport: (List<String>) -> Unit,
    onToggleDns: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNext: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showBatchDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var selectedQrNumber by remember { mutableStateOf<String?>(null) }

    val activeCount = athletes.count { it.status != AthleteStatus.DNS }
    val dnsCount = athletes.count { it.status == AthleteStatus.DNS }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = DragonRed,
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = "添加运动员")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            GradientHeader(title = "运动员管理")

            // 统计卡片
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "参赛人数",
                    value = activeCount.toString(),
                    color = DragonRed,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "弃权人数",
                    value = dnsCount.toString(),
                    color = DragonOrange,
                    modifier = Modifier.weight(1f)
                )
            }

            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    text = "批量导入",
                    icon = Icons.Filled.Upload,
                    onClick = { showBatchDialog = true },
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = "号码布",
                    icon = Icons.Filled.QrCode,
                    onClick = { showQrDialog = true },
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = "进入记圈",
                    icon = Icons.Filled.Add,
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                    primary = true
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 运动员列表
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 80.dp)
            ) {
                items(athletes, key = { it.number }) { athlete ->
                    AthleteCard(
                        athlete = athlete,
                        onToggleDns = { onToggleDns(athlete.number) },
                        onDelete = { onDelete(athlete.number) }
                    )
                }
            }
        }
    }

    // 添加对话框
    if (showAddDialog) {
        AddAthleteDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { num, name, team ->
                onAdd(num, name, team)
                showAddDialog = false
            }
        )
    }

    // 批量导入对话框
    if (showBatchDialog) {
        BatchImportDialog(
            onDismiss = { showBatchDialog = false },
            onConfirm = { text ->
                onBatchImport(text.split("\n"))
                showBatchDialog = false
            }
        )
    }

    // 号码布二维码对话框
    if (showQrDialog) {
        QrBibDialog(
            athletes = athletes,
            onDismiss = { showQrDialog = false }
        )
    }
}

@Composable
private fun StatCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) DragonRed else Color(0xFF546E7A),
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AthleteCard(
    athlete: Athlete,
    onToggleDns: () -> Unit,
    onDelete: () -> Unit
) {
    val isDns = athlete.status == AthleteStatus.DNS
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDns) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 号码圆牌
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isDns) Color.Gray else DragonRed),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = athlete.number,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            // 姓名单位
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = athlete.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDns) Color.Gray else MaterialTheme.colorScheme.onSurface
                )
                if (athlete.team.isNotEmpty()) {
                    Text(
                        text = athlete.team,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isDns) {
                StatusChip(label = "弃权")
            }
            // 弃权按钮
            IconButton(onClick = onToggleDns) {
                Text(
                    text = if (isDns) "恢复" else "弃权",
                    fontSize = 12.sp,
                    color = if (isDns) MaterialTheme.colorScheme.primary else DragonOrange,
                    fontWeight = FontWeight.SemiBold
                )
            }
            // 删除
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AddAthleteDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var number by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var team by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加运动员", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("号码") },
                    placeholder = { Text("如：001") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名") },
                    placeholder = { Text("如：张三") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = team,
                    onValueChange = { team = it },
                    label = { Text("单位/队伍") },
                    placeholder = { Text("如：第一代表队") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (number.isNotBlank() && name.isNotBlank()) onConfirm(number.trim(), name.trim(), team.trim()) }
            ) { Text("确认", color = DragonRed, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun BatchImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current

    // 文件选择器
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""
                text = content
            } catch (e: Exception) {
                // 读取失败则忽略
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量导入", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "每行一位，格式：号码,姓名,单位",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                // 文件导入按钮
                Button(
                    onClick = {
                        fileLauncher.launch(arrayOf("text/*", "text/csv", "text/plain", "application/vnd.ms-excel"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF546E7A),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("从文件导入 (TXT/CSV)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "或手动输入：",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("001,张三,第一代表队\n002,李四,第二代表队") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("导入", color = DragonRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 号码布二维码预览对话框
 */
@Composable
private fun QrBibDialog(
    athletes: List<Athlete>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var previewNumber by remember { mutableStateOf(athletes.firstOrNull()?.number) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("号码布二维码", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "将二维码印在号码布上，扫码记圈时自动识别",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 二维码预览
                if (previewNumber != null) {
                    val previewAthlete = athletes.find { it.number == previewNumber }
                    val bitmap = remember(previewNumber) {
                        QrCodeGenerator.generate(previewNumber!!, previewAthlete?.name ?: "", 256)
                    }
                    Card(
                        modifier = Modifier.size(200.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "二维码",
                                modifier = Modifier.size(160.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${previewNumber}号 运动员",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = DragonRed
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 号码选择
                LazyColumn(
                    modifier = Modifier.height(120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(athletes.size) { index ->
                        val athlete = athletes[index]
                        TextButton(
                            onClick = { previewNumber = athlete.number },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${athlete.number}号 ${athlete.name}",
                                color = if (athlete.number == previewNumber) DragonRed else Color.Gray,
                                fontWeight = if (athlete.number == previewNumber) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 保存所有二维码到公共相册
                Button(
                    onClick = {
                        try {
                            var savedCount = 0
                            athletes.forEach { athlete ->
                                val bmp = QrCodeGenerator.generate(athlete.number, athlete.name, 512)
                                val filename = "号码布_${athlete.number}号.png"

                                val contentValues = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/蜃龙体育号码布")
                                        put(MediaStore.Images.Media.IS_PENDING, 1)
                                    }
                                }

                                val resolver = context.contentResolver
                                val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                                if (uri != null) {
                                    resolver.openOutputStream(uri)?.use { os ->
                                        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, os)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        contentValues.clear()
                                        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                                        resolver.update(uri, contentValues, null, null)
                                    }
                                    savedCount++
                                }
                            }
                            Toast.makeText(context, "已保存${savedCount}个号码布到 相册/蜃龙体育号码布", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DragonRed)
                ) {
                    Text("保存所有号码布图片")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
