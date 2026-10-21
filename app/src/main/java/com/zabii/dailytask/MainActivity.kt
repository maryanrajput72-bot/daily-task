package com.zabii.dailytask

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class TaskItem(val id: Long, val title: String, var done: Boolean = false)

private const val CURRENT_VERSION_CODE = 7
private const val CURRENT_VERSION_NAME = "1.6"
private const val VERSION_URL = "https://maryanrajput72-bot.github.io/daily-task/version.json"

private val PinkBar = Color(0xFFFCE7F1)
private val DarkIcon = Color(0xFF263238)
private val BluePrimary = Color(0xFF4F7CFF)

// Update ki halat - Settings page aur popup dono yahi use karte hain
enum class UpdatePhase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, NEED_PERMISSION, DOWNLOADING, INSTALLING, FAILED }

class UpdateUiState {
    var phase by mutableStateOf(UpdatePhase.IDLE)
    var latest by mutableStateOf<LatestInfo?>(null)
    var progress by mutableStateOf(0)
    var statusText by mutableStateOf("Abhi check nahi hua")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = BluePrimary,
                    background = Color(0xFFF5F7FB),
                    surface = Color.White
                )
            ) {
                AppShell()
            }
        }
    }
}

data class NavEntry(val label: String, val icon: ImageVector)

@Composable
fun AppShell() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tasks = remember {
        mutableStateListOf(
            TaskItem(1, "Welcome to Daily Task"),
            TaskItem(2, "Add your first task with + button"),
            TaskItem(3, "Tap the circle to mark done")
        )
    }
    var showAdd by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var showSnake by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val updateState = remember { UpdateUiState() }

    fun startUpdate() {
        val info = updateState.latest ?: return
        if (!UpdateManager.canInstall(context)) {
            updateState.phase = UpdatePhase.NEED_PERMISSION
            updateState.statusText = "Pehle ek dafa install ki permission Allow karein"
            UpdateManager.openInstallPermissionSettings(context)
            return
        }
        updateState.phase = UpdatePhase.DOWNLOADING
        updateState.progress = 0
        updateState.statusText = "Download ho raha hai..."
        scope.launch {
            val file = UpdateManager.downloadApk(context, info.apkUrl) { pct ->
                updateState.progress = pct
            }
            if (file == null) {
                updateState.phase = UpdatePhase.FAILED
                updateState.statusText = "Download fail ho gaya - internet check karke dobara try karein"
                return@launch
            }
            updateState.phase = UpdatePhase.INSTALLING
            updateState.statusText = "Install ho raha hai..."
            val ok = UpdateManager.installWithPackageInstaller(context, file)
            if (ok) {
                Toast.makeText(context, "Update install ho raha hai", Toast.LENGTH_SHORT).show()
            } else {
                updateState.phase = UpdatePhase.FAILED
                updateState.statusText = "Install start nahi ho saka - dobara try karein"
            }
        }
    }

    fun checkUpdate(auto: Boolean) {
        if (updateState.phase == UpdatePhase.CHECKING || updateState.phase == UpdatePhase.DOWNLOADING) return
        updateState.phase = UpdatePhase.CHECKING
        updateState.statusText = "Check ho raha hai..."
        scope.launch {
            val latest = UpdateManager.fetchLatest(VERSION_URL)
            when {
                latest == null -> {
                    updateState.phase = UpdatePhase.FAILED
                    updateState.statusText = "Check nahi ho saka - internet check karein"
                }
                latest.versionCode > CURRENT_VERSION_CODE && latest.apkUrl.isNotEmpty() -> {
                    updateState.latest = latest
                    updateState.phase = UpdatePhase.AVAILABLE
                    updateState.statusText = "Naya version ${latest.versionName} aa gaya hai"
                    if (auto) showUpdateDialog = true
                }
                else -> {
                    updateState.phase = UpdatePhase.UP_TO_DATE
                    updateState.statusText = "App bilkul up to date hai"
                }
            }
        }
    }

    // STICKY RULE: 5 second baad update check, lazmi
    LaunchedEffect(Unit) {
        delay(5000)
        checkUpdate(auto = true)
    }

    val navItems = listOf(
        NavEntry("Home", Icons.Filled.Home),
        NavEntry("Likes", Icons.Filled.Favorite),
        NavEntry("Add", Icons.Filled.Add),
        NavEntry("Profile", Icons.Filled.Person),
        NavEntry("Settings", Icons.Filled.Settings)
    )

    Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FB))) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                if (showSnake) {
                    SnakeGameScreen(onBack = { showSnake = false })
                } else {
                    when (selectedTab) {
                        0 -> HomeContent(tasks, updateState, onOpenSnake = { showSnake = true })
                        4 -> SettingsPage(updateState, onCheck = { checkUpdate(auto = false) }, onUpdate = { startUpdate() })
                        else -> PlaceholderPage(navItems[selectedTab])
                    }
                }
            }
            if (!showSnake) {
                BouncingNavBar(
                    items = navItems,
                    selected = selectedTab,
                    onSelect = { idx ->
                        if (idx == 2) showAdd = true else selectedTab = idx
                    }
                )
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add Task") },
            text = {
                OutlinedTextField(
                    value = newText, onValueChange = { newText = it },
                    placeholder = { Text("Write your task") }, singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = newText.trim()
                    if (t.isNotEmpty()) tasks.add(TaskItem(System.currentTimeMillis(), t))
                    newText = ""; showAdd = false; selectedTab = 0
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } }
        )
    }

    if (showUpdateDialog && updateState.latest != null) {
        val downloading = updateState.phase == UpdatePhase.DOWNLOADING || updateState.phase == UpdatePhase.INSTALLING
        AlertDialog(
            onDismissRequest = { if (!downloading) showUpdateDialog = false },
            title = { Text("Update Available") },
            text = {
                Column {
                    Text("Daily Task ka naya version ${updateState.latest!!.versionName} aa gaya hai.")
                    if (downloading) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { updateState.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (updateState.phase == UpdatePhase.INSTALLING) "Install ho raha hai..."
                            else "Download: ${updateState.progress}%",
                            fontSize = 13.sp, color = Color.Gray
                        )
                    }
                }
            },
            confirmButton = {
                if (!downloading) {
                    TextButton(onClick = { startUpdate() }) { Text("Update") }
                }
            },
            dismissButton = {
                if (!downloading) {
                    TextButton(onClick = { showUpdateDialog = false }) { Text("Later") }
                }
            }
        )
    }
}

@Composable
fun SettingsPage(state: UpdateUiState, onCheck: () -> Unit, onUpdate: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C2333))
        Spacer(Modifier.height(18.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("App Update", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Installed Version: $CURRENT_VERSION_NAME", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Text(state.statusText, fontSize = 14.sp, color = Color(0xFF1C2333))
                if (state.phase == UpdatePhase.DOWNLOADING || state.phase == UpdatePhase.INSTALLING) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { state.progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                        color = BluePrimary, trackColor = Color(0xFFE3E9F7)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (state.phase == UpdatePhase.INSTALLING) "Install ho raha hai..." else "Download: ${state.progress}%",
                        fontSize = 13.sp, color = Color.Gray
                    )
                }
                Spacer(Modifier.height(14.dp))
                val busy = state.phase == UpdatePhase.CHECKING ||
                    state.phase == UpdatePhase.DOWNLOADING || state.phase == UpdatePhase.INSTALLING
                Button(
                    onClick = {
                        if (state.phase == UpdatePhase.AVAILABLE || state.phase == UpdatePhase.NEED_PERMISSION) onUpdate() else onCheck()
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        when (state.phase) {
                            UpdatePhase.CHECKING -> "Checking..."
                            UpdatePhase.DOWNLOADING -> "Downloading..."
                            UpdatePhase.INSTALLING -> "Installing..."
                            UpdatePhase.AVAILABLE -> "Update Now (${state.latest?.versionName ?: ""})"
                            UpdatePhase.NEED_PERMISSION -> "Permission de kar Update karein"
                            else -> "Check for Update"
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Update app ke andar hi download aur install hoti hai - browser nahi khulta.",
                    fontSize = 12.sp, color = Color.Gray
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        SnakeSettingsCard()
    }
}

@Composable
fun SnakeSettingsCard() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("snake_game", 0) }
    var speed by remember { mutableStateOf(prefs.getString("speed", "MEDIUM") ?: "MEDIUM") }
    var colorIdx by remember { mutableStateOf(prefs.getInt("colorIdx", 0).coerceIn(0, SnakeColors.lastIndex)) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("Snake Game", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text("Speed", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1C2333))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((value, label) in listOf("CLASSIC" to "Classic", "MEDIUM" to "Medium", "HIGH" to "High")) {
                    FilterChip(
                        selected = speed == value,
                        onClick = {
                            speed = value
                            prefs.edit().putString("speed", value).apply()
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFC8E6C9),
                            selectedLabelColor = Color(0xFF1B5E20)
                        )
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Snake Color", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1C2333))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SnakeColors.forEachIndexed { idx, cs ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(CircleShape)
                                .background(cs.main)
                                .border(
                                    width = if (idx == colorIdx) 3.dp else 0.dp,
                                    color = if (idx == colorIdx) Color(0xFF1C2333) else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    colorIdx = idx
                                    prefs.edit().putInt("colorIdx", idx).apply()
                                }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(cs.name, fontSize = 11.sp, color = if (idx == colorIdx) Color(0xFF1C2333) else Color.Gray,
                            fontWeight = if (idx == colorIdx) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Speed aur color game me foran lag jaye ga.", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun BouncingNavBar(items: List<NavEntry>, selected: Int, onSelect: (Int) -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp, top = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(PinkBar)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { idx, item ->
                NavBarItem(item, idx == selected) { onSelect(idx) }
            }
        }
    }
}

// Video jaisi animation (frame-by-frame analyze karke):
// Poora item (icon + naam) pehle upar uchhalta hai, phir neeche girta hai,
// zameen pe halka sa dabta (squash) hai aur ek chhota bounce le ke rukta hai.
private val ItemJumpY = keyframes<Float> {
    durationMillis = 620
    0f at 0
    -12f at 150 with FastOutSlowInEasing  // upar uchhalna
    0f at 320 with LinearEasing           // neeche girna
    -5f at 430 with FastOutSlowInEasing   // chhota bounce
    0f at 520 with LinearEasing
    -1.5f at 570 with FastOutSlowInEasing
    0f at 620
}
private val ItemSquash = keyframes<Float> {
    durationMillis = 620
    1f at 0
    1f at 310
    0.84f at 360   // zameen pe dabna
    1.05f at 440   // wapas phailna
    1f at 520
    0.97f at 565
    1f at 620
}

@Composable
fun NavBarItem(item: NavEntry, isSelected: Boolean, onClick: () -> Unit) {
    val jumpY = remember { Animatable(0f) }
    val squash = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.width(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                scope.launch {
                    jumpY.stop(); jumpY.snapTo(0f); jumpY.animateTo(0f, ItemJumpY)
                }
                scope.launch {
                    squash.stop(); squash.snapTo(1f); squash.animateTo(1f, ItemSquash)
                }
                onClick()
            }
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                translationY = jumpY.value
                scaleY = squash.value
                scaleX = 1f + (1f - squash.value) * 0.9f
            }
        ) {
            Icon(
                item.icon, contentDescription = item.label,
                tint = DarkIcon,
                modifier = Modifier.size(23.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                item.label, fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = DarkIcon
            )
        }
    }
}

@Composable
fun PlaceholderPage(item: NavEntry) {
    Column(
        modifier = Modifier.fillMaxSize().padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(90.dp).clip(CircleShape).background(PinkBar),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, contentDescription = item.label, tint = Color(0xFFE91E63), modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(item.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C2333))
        Spacer(Modifier.height(6.dp))
        Text("This page will be built next.", color = Color.Gray, fontSize = 14.sp)
    }
}

@Composable
fun HomeContent(tasks: MutableList<TaskItem>, updateState: UpdateUiState, onOpenSnake: () -> Unit) {
    // Home pe SIRF ek sab se bara Snake card - baaki sab gayab (uska order)
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f).clickable { onOpenSnake() },
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF2E7D32), Color(0xFF43A047), Color(0xFF66BB6A))))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(10.dp))
                // Bara maze wala 3D-look sanp (smooth jism, koi dabe hisse nahi)
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(240.dp)) {
                    val w = size.width
                    val h = size.height
                    val pts = listOf(
                        Offset(w * 0.10f, h * 0.78f),
                        Offset(w * 0.28f, h * 0.52f),
                        Offset(w * 0.44f, h * 0.74f),
                        Offset(w * 0.58f, h * 0.48f),
                        Offset(w * 0.72f, h * 0.62f)
                    )
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (p in pts.drop(1)) lineTo(p.x, p.y)
                    }
                    val bw = h * 0.17f
                    drawPath(path, Color.Black.copy(alpha = 0.20f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = bw, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    drawPath(path, Color(0xFF1B5E20),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = bw, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    drawPath(path, Color(0xFF43A047),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = bw * 0.84f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    val lightPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(pts[0].x, pts[0].y - bw * 0.16f)
                        for (p in pts.drop(1)) lineTo(p.x, p.y - bw * 0.16f)
                    }
                    drawPath(lightPath, Color(0xFFA5D6A7).copy(alpha = 0.6f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = bw * 0.26f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    // Sar
                    val head = Offset(w * 0.82f, h * 0.44f)
                    val hr = h * 0.16f
                    drawCircle(Color.Black.copy(alpha = 0.2f), hr, Offset(head.x, head.y + 6f))
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFA5D6A7), Color(0xFF43A047), Color(0xFF1B5E20)),
                            center = Offset(head.x - hr * 0.35f, head.y - hr * 0.35f),
                            radius = hr * 1.7f
                        ),
                        radius = hr, center = head
                    )
                    // Zuban
                    drawLine(Color(0xFFE53935), Offset(head.x + hr * 0.9f, head.y + hr * 0.1f), Offset(head.x + hr * 1.45f, head.y + hr * 0.16f), strokeWidth = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(Color(0xFFE53935), Offset(head.x + hr * 1.45f, head.y + hr * 0.16f), Offset(head.x + hr * 1.75f, head.y - hr * 0.08f), strokeWidth = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(Color(0xFFE53935), Offset(head.x + hr * 1.45f, head.y + hr * 0.16f), Offset(head.x + hr * 1.78f, head.y + hr * 0.38f), strokeWidth = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    // Aankhen
                    for (s in listOf(-1f, 1f)) {
                        val eyeC = Offset(head.x + hr * 0.25f, head.y - hr * 0.28f + s * hr * 0.34f)
                        drawCircle(Color.White, hr * 0.23f, eyeC)
                        drawCircle(Color(0xFF141414), hr * 0.12f, Offset(eyeC.x + hr * 0.06f, eyeC.y))
                    }
                    // Seb (apple)
                    val ap = Offset(w * 0.16f, h * 0.18f)
                    val ar = h * 0.10f
                    drawCircle(Color.Black.copy(alpha = 0.2f), ar, Offset(ap.x, ap.y + 5f))
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFE57373), Color(0xFFC62828)),
                            center = Offset(ap.x - ar * 0.3f, ap.y - ar * 0.3f),
                            radius = ar * 1.5f
                        ),
                        radius = ar, center = ap
                    )
                    drawCircle(Color.White.copy(alpha = 0.5f), ar * 0.25f, Offset(ap.x - ar * 0.35f, ap.y - ar * 0.4f))
                    drawOval(Color(0xFF2E7D32), topLeft = Offset(ap.x + ar * 0.1f, ap.y - ar * 1.35f), size = androidx.compose.ui.geometry.Size(ar * 0.8f, ar * 0.42f))
                }
                Spacer(Modifier.height(8.dp))
                Text("Snake Plus", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Nokia wali game - ab 3D style me",
                    color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp
                )
                Text(
                    "Fruit khao, lamba ho - nevle se bacho!",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .padding(horizontal = 44.dp, vertical = 13.dp)
                ) {
                    Text("PLAY", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 19.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Version $CURRENT_VERSION_NAME" +
                if (updateState.phase == UpdatePhase.UP_TO_DATE) "  -  App is up to date" else "",
            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
            color = Color.Gray, fontSize = 12.sp
        )
    }
}
