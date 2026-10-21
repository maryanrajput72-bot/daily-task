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

private const val CURRENT_VERSION_CODE = 5
private const val CURRENT_VERSION_NAME = "1.4"
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
                when (selectedTab) {
                    0 -> HomeContent(tasks, updateState)
                    4 -> SettingsPage(updateState, onCheck = { checkUpdate(auto = false) }, onUpdate = { startUpdate() })
                    else -> PlaceholderPage(navItems[selectedTab])
                }
            }
            BouncingNavBar(
                items = navItems,
                selected = selectedTab,
                onSelect = { idx ->
                    if (idx == 2) showAdd = true else selectedTab = idx
                }
            )
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
    Column(Modifier.fillMaxSize().padding(20.dp)) {
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
fun HomeContent(tasks: MutableList<TaskItem>, updateState: UpdateUiState) {
    val doneCount = tasks.count { it.done }
    val today = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.ENGLISH).format(Date()) }
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..21 -> "Good Evening"
        else -> "Good Night"
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF4F7CFF), Color(0xFF7B61FF))))
                    .padding(start = 22.dp, end = 22.dp, top = 30.dp, bottom = 28.dp)
            ) {
                Column {
                    Text(greeting, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
                    Text("Daily Task", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(today, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Today's Progress", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("$doneCount / ${tasks.size} done", color = BluePrimary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { if (tasks.isEmpty()) 0f else doneCount.toFloat() / tasks.size },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                        color = BluePrimary, trackColor = Color(0xFFE3E9F7)
                    )
                }
            }
            Text("My Tasks", modifier = Modifier.padding(start = 20.dp, bottom = 6.dp),
                fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (tasks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    Text("No tasks yet. Tap + Add in the bar below.", color = Color.Gray)
                }
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = task.done,
                            onCheckedChange = { checked ->
                                val idx = tasks.indexOf(task)
                                if (idx >= 0) tasks[idx] = task.copy(done = checked)
                            },
                            colors = CheckboxDefaults.colors(checkedColor = BluePrimary)
                        )
                        Text(
                            task.title, modifier = Modifier.weight(1f), fontSize = 15.sp,
                            color = if (task.done) Color.Gray else Color(0xFF1C2333),
                            textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                        )
                        IconButton(onClick = { tasks.remove(task) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color(0xFFE35D6A))
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(14.dp))
            Text(
                "Version $CURRENT_VERSION_NAME" +
                    if (updateState.phase == UpdatePhase.UP_TO_DATE) "  -  App is up to date" else "",
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
                color = Color.Gray, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
