package com.assistant.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.assistant.app.data.ContentType
import com.assistant.app.data.GeminiApiClient
import com.assistant.app.data.TaskItem
import com.assistant.app.data.TaskRepository
import com.assistant.app.service.AutoScheduleManager
import com.assistant.app.service.SocialMessage
import com.assistant.app.service.SocialNotificationListenerService
import com.assistant.app.util.PdfExporter
import com.assistant.app.worker.AutoAssistantWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var taskRepository: TaskRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskRepository = TaskRepository(this)

        val prefs = getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("auto_generation_enabled", true)) {
            AutoScheduleManager.setupDailySchedule(this)
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1565C0),
                    secondary = Color(0xFF0288D1),
                    background = Color(0xFFF8F9FA)
                )
            ) {
                MainScreen(taskRepository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(taskRepo: TaskRepository) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE) }

    var apiKey by remember { mutableStateOf(prefs.getString("gemini_api_key", "") ?: "") }
    var customBaseUrl by remember { mutableStateOf(prefs.getString("custom_base_url", "") ?: "") }
    var proxyHost by remember { mutableStateOf(prefs.getString("proxy_host", "") ?: "") }
    var proxyPort by remember { mutableStateOf(prefs.getInt("proxy_port", 0).let { if (it > 0) it.toString() else "" }) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دستیار هوشمند خودکار", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "تنظیمات شبکه و API")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "تولید خودکار") },
                    label = { Text("تولید خودکار") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.NotificationsActive, contentDescription = "شبکه‌ها") },
                    label = { Text("پیام‌ها") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Alarm, contentDescription = "یادآورها") },
                    label = { Text("یادآورها") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> AutoContentScreen(apiKey, customBaseUrl, proxyHost, proxyPort.toIntOrNull())
                1 -> SocialNotificationsScreen()
                2 -> RemindersScreen(taskRepo)
            }
        }

        if (showSettingsDialog) {
            var tempKey by remember { mutableStateOf(apiKey) }
            var tempBaseUrl by remember { mutableStateOf(customBaseUrl) }
            var tempProxyHost by remember { mutableStateOf(proxyHost) }
            var tempProxyPort by remember { mutableStateOf(proxyPort) }

            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("تنظیمات اتصال و دور زدن محدودیت") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("کلید Gemini API:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        OutlinedTextField(
                            value = tempKey,
                            onValueChange = { tempKey = it },
                            placeholder = { Text("AIzaSy...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("سرور واسط (Reverse Proxy / Cloudflare):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("برای استفاده از دامنه‌های اختصاصی به جای دامنه گوگل", fontSize = 11.sp, color = Color.Gray)
                        OutlinedTextField(
                            value = tempBaseUrl,
                            onValueChange = { tempBaseUrl = it },
                            placeholder = { Text("https://my-proxy.workers.dev") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("تنظیمات پروکسی محلی (Proxy Host & Port):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = tempProxyHost,
                                onValueChange = { tempProxyHost = it },
                                placeholder = { Text("127.0.0.1") },
                                singleLine = true,
                                modifier = Modifier.weight(2f)
                            )
                            OutlinedTextField(
                                value = tempProxyPort,
                                onValueChange = { tempProxyPort = it },
                                placeholder = { Text("10808") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        apiKey = tempKey.trim()
                        customBaseUrl = tempBaseUrl.trim()
                        proxyHost = tempProxyHost.trim()
                        proxyPort = tempProxyPort.trim()

                        val pInt = proxyPort.toIntOrNull() ?: 0

                        prefs.edit()
                            .putString("gemini_api_key", apiKey)
                            .putString("custom_base_url", customBaseUrl)
                            .putString("proxy_host", proxyHost)
                            .putInt("proxy_port", pInt)
                            .apply()

                        showSettingsDialog = false
                        Toast.makeText(context, "تنظیمات با موفقیت ذخیره شد", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("ذخیره")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("انصراف")
                    }
                }
            )
        }
    }
}

@Composable
fun AutoContentScreen(
    apiKey: String,
    customBaseUrl: String,
    proxyHost: String,
    proxyPort: Int?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE) }

    var isAutoEnabled by remember { mutableStateOf(prefs.getBoolean("auto_generation_enabled", true)) }

    var latestTopic by remember { mutableStateOf(prefs.getString("latest_auto_topic", "") ?: "") }
    var latestContent by remember { mutableStateOf(prefs.getString("latest_auto_content", "") ?: "") }
    var latestType by remember { mutableStateOf(prefs.getString("latest_auto_type", "") ?: "") }
    var isGeneratingNow by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ۱. کارت وضعیت اجرای خودکار
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isAutoEnabled) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isAutoEnabled) "وضعیت: تولید خودکار فعال است" else "وضعیت: تولید خودکار لغو شده است",
                        fontWeight = FontWeight.Bold,
                        color = if (isAutoEnabled) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontSize = 15.sp
                    )
                    Icon(
                        if (isAutoEnabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (isAutoEnabled) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (isAutoEnabled)
                        "دستیار به صورت کاملاً خودکار و روزانه، مقالات، جزوات و کتابچه‌های تخصصی (فیبر نوری، هوشمندسازی BMS و استانداردهای برق) را تدوین کرده و در قالب PDF ذخیره می‌نماید. نیازی به ورود دستی درخواست از سوی شما نیست."
                    else
                        "تولید خودکار در حال حاضر متوقف است. برای شروع مجدد، دکمه فعال‌سازی را بزنید.",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Color.DarkGray
                )

                Spacer(modifier = Modifier.height(14.dp))

                // دکمه کنسل / فعال‌سازی
                if (isAutoEnabled) {
                    Button(
                        onClick = {
                            isAutoEnabled = false
                            prefs.edit().putBoolean("auto_generation_enabled", false).apply()
                            AutoScheduleManager.cancelSchedule(context)
                            Toast.makeText(context, "تولید خودکار لغو و متوقف شد", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("لغو و توقف تولید خودکار (Cancel)")
                    }
                } else {
                    Button(
                        onClick = {
                            isAutoEnabled = true
                            prefs.edit().putBoolean("auto_generation_enabled", true).apply()
                            AutoScheduleManager.setupDailySchedule(context)
                            Toast.makeText(context, "تولید خودکار مجدداً فعال شد", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فعال‌سازی مجدد تولید خودکار")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ۲. دکمه تولید فوری با یک کلیک بدون نیاز به تایپ موضوع
        OutlinedButton(
            onClick = {
                if (apiKey.isBlank()) {
                    Toast.makeText(context, "لطفاً ابتدا از آیکون چرخ‌دنده، کلید API را وارد کنید", Toast.LENGTH_LONG).show()
                    return@OutlinedButton
                }
                isGeneratingNow = true
                scope.launch {
                    val topics = AutoAssistantWorker.TOPICS
                    val nextIndex = (prefs.getInt("last_topic_index", 0) + 1) % topics.size
                    prefs.edit().putInt("last_topic_index", nextIndex).apply()
                    val selectedTopic = topics[nextIndex]
                    val selectedType = when (nextIndex % 3) {
                        0 -> ContentType.ARTICLE
                        1 -> ContentType.LECTURE_NOTES
                        else -> ContentType.BOOKLET
                    }

                    val client = GeminiApiClient(apiKey, customBaseUrl, proxyHost, proxyPort)
                    val res = client.generateContent(selectedTopic, selectedType)

                    withContext(Dispatchers.Main) {
                        isGeneratingNow = false
                        res.onSuccess { text ->
                            latestTopic = selectedTopic
                            latestContent = text
                            latestType = selectedType.titleFa

                            prefs.edit()
                                .putString("latest_auto_topic", selectedTopic)
                                .putString("latest_auto_content", text)
                                .putString("latest_auto_type", selectedType.titleFa)
                                .apply()

                            PdfExporter.exportToPdf(context, selectedTopic, text)
                            Toast.makeText(context, "محتوا با موفقیت تدوین و PDF ذخیره شد", Toast.LENGTH_SHORT).show()
                        }.onFailure { e ->
                            Toast.makeText(context, "خطا: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isGeneratingNow
        ) {
            if (isGeneratingNow) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("در حال تدوین خودکار موضوع بعدی...")
            } else {
                Icon(Icons.Default.Bolt, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("تولید آنی موضوع بعدی (با یک کلیک بدون نوشتن)")
            }
        }

        // ۳. نمایش آخرین محتوای تولیدشده
        if (latestContent.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("آخرین خروجی خودکار:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (latestType.isNotEmpty()) {
                        Text("قالب: $latestType", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Button(
                    onClick = {
                        PdfExporter.exportToPdf(context, latestTopic, latestContent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("خروجی PDF")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(latestTopic, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = latestContent,
                        fontSize = 13.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SocialNotificationsScreen() {
    val context = LocalContext.current
    val messages by SocialNotificationListenerService.messages.collectAsState()

    fun isNotificationAccessGranted(): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return !TextUtils.isEmpty(flat) && flat.contains(packageName)
    }

    var hasPermission by remember { mutableStateOf(isNotificationAccessGranted()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("پیام‌های دریافتی شبکه‌ها", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            if (!hasPermission) {
                Button(
                    onClick = {
                        context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text("فعال‌سازی دسترسی", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "پیام‌های جدید تلگرام، واتساپ و سایر شبکه‌ها به صورت خودکار در این قسمت ثبت می‌شوند. با لمس هر پیام، مستقیم وارد آن گفتگو می‌شوید.",
            fontSize = 13.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("هنوز پیامی دریافت نشده است.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages) { msg ->
                    SocialMessageCard(msg)
                }
            }
        }
    }
}

@Composable
fun SocialMessageCard(msg: SocialMessage) {
    val context = LocalContext.current
    val timeFormatted = remember(msg.timestamp) {
        SimpleDateFormat("HH:mm - yyyy/MM/dd", Locale.getDefault()).format(Date(msg.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                try {
                    if (msg.contentIntent != null) {
                        msg.contentIntent.send()
                    } else {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(msg.packageName)
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        } else {
                            Toast.makeText(context, "اپلیکیشن روی گوشی یافت نشد", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "خطا در باز کردن برنامه: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(msg.appName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                Text(timeFormatted, fontSize = 11.sp, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (msg.sender.isNotEmpty()) {
                Text(msg.sender, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            if (msg.content.isNotEmpty()) {
                Text(msg.content, fontSize = 13.sp, color = Color.DarkGray, maxLines = 3)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("برای باز شدن در برنامه ضربه بزنید", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun RemindersScreen(taskRepo: TaskRepository) {
    val context = LocalContext.current
    val tasks by taskRepo.tasks.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("یادآورها و وظایف روزانه", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.size(45.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "افزودن یادآور")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (tasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("هیچ وظیفه یا یادآوری ثبت نشده است.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks) { task ->
                    TaskCard(task, onToggle = { taskRepo.toggleTask(task.id) }, onDelete = { taskRepo.deleteTask(task.id) })
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var minutesDelay by remember { mutableStateOf("10") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("افزودن یادآور جدید") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("عنوان کار") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("توضیحات (اختیاری)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = minutesDelay,
                        onValueChange = { minutesDelay = it },
                        label = { Text("یادآوری بعد از چند دقیقه؟") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isBlank()) return@TextButton
                    val mins = minutesDelay.toLongOrNull() ?: 10L
                    val triggerAt = System.currentTimeMillis() + (mins * 60 * 1000)
                    taskRepo.addTask(title, desc, triggerAt)
                    showAddDialog = false
                    Toast.makeText(context, "یادآور برای $mins دقیقه دیگر تنظیم شد", Toast.LENGTH_SHORT).show()
                }) {
                    Text("ذخیره و زمان‌بندی")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
fun TaskCard(task: TaskItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    val dateStr = remember(task.scheduledTime) {
        SimpleDateFormat("HH:mm - yyyy/MM/dd", Locale.getDefault()).format(Date(task.scheduledTime))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) Color(0xFFE8F5E9) else Color.White
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (task.isCompleted) Color.Gray else Color.Black
                )
                if (task.description.isNotEmpty()) {
                    Text(task.description, fontSize = 13.sp, color = Color.DarkGray)
                }
                Text("زمان هشدار: $dateStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red)
            }
        }
    }
}
