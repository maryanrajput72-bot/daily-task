package com.zabii.dailytask

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

data class TaskItem(val id: Long, val title: String, var done: Boolean = false)

private const val CURRENT_VERSION_CODE = 1
private const val VERSION_URL = "https://maryanrajput72-bot.github.io/daily-task/version.json"

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
                HomeScreen()
            }
        }
    }
}

@Composable
fun HomeScreen() {
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
    var updateInfo by remember { mutableStateOf<Pair<Int, String>?>(null) } // versionCode to apkUrl
    var checkedText by remember { mutableStateOf("") }

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
            // offline / repo not ready: chup chaap aage barho
        }
    }

    val doneCount = tasks.count { it.done }
    val today = remember { SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.ENGLISH).format(Date()) }
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..21 -> "Good Evening"
        else -> "Good Night"
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = Color(0xFF4F7CFF),
                contentColor = Color.White,
                shape = CircleShape
            ) { Icon(Icons.Filled.Add, contentDescription = "Add Task") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            item {
                // Header with gradient
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
                // Progress card
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
                            progress = if (tasks.isEmpty()) 0f else doneCount.toFloat() / tasks.size,
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
                        Text("No tasks yet. Tap + to add one.", color = Color.Gray)
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
                    "Version 1.0" + if (checkedText.isNotEmpty()) "  •  $checkedText" else "",
                    modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
                    color = Color.Gray, fontSize = 12.sp
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
                    if (t.isNotEmpty()) {
                        tasks.add(TaskItem(System.currentTimeMillis(), t))
                    }
                    newText = ""; showAdd = false
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
