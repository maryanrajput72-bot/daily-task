package com.zabii.dailytask

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

data class TaskItem(val id: Long, val title: String, var done: Boolean = false)

private const val CURRENT_VERSION_CODE = 3
private const val VERSION_URL = "https://maryanrajput72-bot.github.io/daily-task/version.json"

private val PinkBar = Color(0xFFFCE7F1)
private val PinkAccent = Color(0xFFE91E63)
private val DarkIcon = Color(0xFF263238)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF4F7CFF),
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
    val tasks = remember {
        mutableStateListOf(
            TaskItem(1, "Welcome to Daily Task"),
            TaskItem(2, "Add your first task with + button"),
            TaskItem(3, "Tap the circle to mark done")
        )
    }
    var showAdd by remember { mutableStateOf(false) }
    var newText by remember { mutableStateOf("") }
    var updateInfo by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var checkedText by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }

    // STICKY RULE: 5 second baad update check, lazmi
    LaunchedEffect(Unit) {
        delay(5000)
        try {
            val result = withContext(Dispatchers.IO) {
                val json = URL(VERSION_URL).openConnection().apply {
                    connectTimeout = 8000; readTimeout = 8000
                }.getInputStream().bufferedReader().readText()
                val obj = JSONObject(json)
                obj.getInt("versionCode") to obj.optString("apkUrl", "")
            }
            if (result.first > CURRENT_VERSION_CODE && result.second.isNotEmpty()) {
                updateInfo = result
            } else {
                checkedText = "App is up to date"
            }
        } catch (e: Exception) {
            // offline: chup chaap aage barho
        }
    }

    val navItems = listOf(
        NavEntry("Home", Icons.Filled.Home),
        NavEntry("Likes", Icons.Filled.Favorite),
        NavEntry("Add", Icons.Filled.Add),
        NavEntry("Profile", Icons.Filled.Person),
        NavEntry("Alerts", Icons.Filled.Notifications)
    )

    Box(Modifier.fillMaxSize().background(Color(0xFFF5F7FB))) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                if (selectedTab == 0) {
                    HomeContent(tasks, checkedText)
                } else {
                    PlaceholderPage(navItems[selectedTab])
                }
            }
            // Third-wali bouncing navigation bar (video wali)
            BouncingNavBar(
                items = navItems,
                selected = selectedTab,
                onSelect = { idx ->
                    if (idx == 2) {
                        showAdd = true
                    } else {
                        selectedTab = idx
                    }
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

    updateInfo?.let { (_, apkUrl) ->
        AlertDialog(
            onDismissRequest = { updateInfo = null },
            title = { Text("Update Available") },
            text = { Text("Daily Task ka naya version aa gaya hai. Abhi update karein?") },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)))
                    updateInfo = null
                }) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { updateInfo = null }) { Text("Later") } }
        )
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

@Composable
fun NavBarItem(item: NavEntry, isSelected: Boolean, onClick: () -> Unit) {
    val bounceY = remember { Animatable(0f) }
    val bounceScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.width(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Har tap pe bounce - chahe pehle se selected hi kyun na ho
                scope.launch {
                    bounceY.snapTo(-13f)
                    bounceY.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = 240f))
                }
                scope.launch {
                    bounceScale.snapTo(1.45f)
                    bounceScale.animateTo(1f, spring(dampingRatio = 0.28f, stiffness = 240f))
                }
                onClick()
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(40.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color.White else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                item.icon, contentDescription = item.label,
                tint = if (isSelected) PinkAccent else DarkIcon,
                modifier = Modifier.size(23.dp).graphicsLayer {
                    translationY = bounceY.value
                    scaleX = bounceScale.value
                    scaleY = bounceScale.value
                }
            )
        }
        Text(
            item.label, fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) PinkAccent else DarkIcon
        )
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
            Icon(item.icon, contentDescription = item.label, tint = PinkAccent, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(item.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C2333))
        Spacer(Modifier.height(6.dp))
        Text("This page will be built next.", color = Color.Gray, fontSize = 14.sp)
    }
}

@Composable
fun HomeContent(tasks: MutableList<TaskItem>, checkedText: String) {
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
                        Text("$doneCount / ${tasks.size} done", color = Color(0xFF4F7CFF), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { if (tasks.isEmpty()) 0f else doneCount.toFloat() / tasks.size },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(8.dp)),
                        color = Color(0xFF4F7CFF), trackColor = Color(0xFFE3E9F7)
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
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4F7CFF))
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
                "Version 1.2" + if (checkedText.isNotEmpty()) "  -  $checkedText" else "",
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
                color = Color.Gray, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
