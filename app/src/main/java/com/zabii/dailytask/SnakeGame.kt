package com.zabii.dailytask

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

private const val GRID_W = 15

private val DirUp = IntOffset(0, -1)
private val DirDown = IntOffset(0, 1)
private val DirLeft = IntOffset(-1, 0)
private val DirRight = IntOffset(1, 0)

// Snake ke rang: (gehra, asal, halka) - Settings se change hote hain
data class SnakeColorSet(val dark: Color, val main: Color, val light: Color, val name: String)

val SnakeColors = listOf(
    SnakeColorSet(Color(0xFF1B5E20), Color(0xFF43A047), Color(0xFFA5D6A7), "Green"),
    SnakeColorSet(Color(0xFF0D47A1), Color(0xFF1E88E5), Color(0xFF90CAF9), "Blue"),
    SnakeColorSet(Color(0xFF4A148C), Color(0xFF8E24AA), Color(0xFFCE93D8), "Purple"),
    SnakeColorSet(Color(0xFFE65100), Color(0xFFFB8C00), Color(0xFFFFCC80), "Orange"),
    SnakeColorSet(Color(0xFF880E4F), Color(0xFFEC407A), Color(0xFFF8BBD0), "Pink")
)

fun snakeBaseTickMs(speed: String): Long = when (speed) {
    "CLASSIC" -> 225L
    "HIGH" -> 118L
    else -> 165L // MEDIUM
}

@Composable
fun SnakeGameScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("snake_game", 0) }
    val speed = remember { prefs.getString("speed", "MEDIUM") ?: "MEDIUM" }
    val colorIdx = remember { prefs.getInt("colorIdx", 0).coerceIn(0, SnakeColors.lastIndex) }
    val colors = SnakeColors[colorIdx]

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FB))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1C2333))
            }
            Text("Snake Plus", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C2333))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val gridH = remember(maxWidth, maxHeight) {
                (GRID_W * maxHeight.value / maxWidth.value).toInt().coerceIn(20, 32)
            }
            GameBoard(gridH = gridH, colors = colors, speed = speed, prefs = prefs)
        }
    }
}

@Composable
private fun GameBoard(
    gridH: Int,
    colors: SnakeColorSet,
    speed: String,
    prefs: android.content.SharedPreferences
) {
    var snake by remember { mutableStateOf(listOf(IntOffset(7, 12), IntOffset(6, 12), IntOffset(5, 12))) }
    var dir by remember { mutableStateOf(DirRight) }
    var pendingDir by remember { mutableStateOf(DirRight) }
    var fruit by remember { mutableStateOf(IntOffset(11, 7)) }
    var fruitType by remember { mutableStateOf(0) }
    var score by remember { mutableStateOf(0) }
    var best by remember { mutableStateOf(prefs.getInt("best", 0)) }
    var playing by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }
    var overReason by remember { mutableStateOf("") }
    var tickCount by remember { mutableStateOf(0) }

    // Villain (nevla) ki halat
    var villain by remember { mutableStateOf<IntOffset?>(null) }
    var villainTicksLeft by remember { mutableStateOf(0) }
    var nextVillainInTicks by remember { mutableStateOf(80) }

    val baseTick = snakeBaseTickMs(speed)
    val tickMs = (baseTick - minOf(28, score / 2)).coerceAtLeast(92)

    fun randomFreeCell(s: List<IntOffset>, extra: IntOffset? = null): IntOffset {
        while (true) {
            val p = IntOffset(Random.nextInt(GRID_W), Random.nextInt(gridH))
            if (p !in s && p != fruit && p != extra) return p
        }
    }

    fun resetGame() {
        snake = listOf(IntOffset(7, 12), IntOffset(6, 12), IntOffset(5, 12))
        dir = DirRight
        pendingDir = DirRight
        score = 0
        gameOver = false
        overReason = ""
        tickCount = 0
        villain = null
        villainTicksLeft = 0
        nextVillainInTicks = (14000 / tickMs).toInt()
        fruitType = Random.nextInt(3)
        fruit = randomFreeCell(snake)
    }

    fun setDirection(d: IntOffset) {
        if (d.x + dir.x != 0 || d.y + dir.y != 0) pendingDir = d
    }

    fun endGame(reason: String) {
        playing = false
        gameOver = true
        overReason = reason
        if (score > best) {
            best = score
            prefs.edit().putInt("best", best).apply()
        }
    }

    LaunchedEffect(playing, tickMs) {
        while (playing) {
            delay(tickMs)
            tickCount++
            dir = pendingDir
            val head = snake.first()
            val newHead = IntOffset(head.x + dir.x, head.y + dir.y)
            val hitWall = newHead.x !in 0 until GRID_W || newHead.y !in 0 until gridH
            val hitSelf = newHead in snake.dropLast(1)
            when {
                hitWall -> endGame("Dewaar se takra gaye!")
                hitSelf -> endGame("Khud se takra gaye!")
                villain != null && newHead == villain -> endGame("Nevle ne pakar liya!")
                else -> {
                    if (newHead == fruit) {
                        snake = listOf(newHead) + snake
                        score += 10
                        fruitType = Random.nextInt(3)
                        fruit = randomFreeCell(snake, villain)
                    } else {
                        snake = listOf(newHead) + snake.dropLast(1)
                    }
                    // --- Villain ka hisaab ---
                    if (villain == null) {
                        nextVillainInTicks--
                        if (nextVillainInTicks <= 0) {
                            // Sar se sab se door kone pe aye ga
                            val corners = listOf(
                                IntOffset(0, 0), IntOffset(GRID_W - 1, 0),
                                IntOffset(0, gridH - 1), IntOffset(GRID_W - 1, gridH - 1)
                            )
                            villain = corners.maxByOrNull {
                                abs(it.x - snake.first().x) + abs(it.y - snake.first().y)
                            }
                            villainTicksLeft = (5000 / tickMs).toInt() // sirf 5 second rahe ga
                        }
                    } else {
                        villainTicksLeft--
                        if (villainTicksLeft <= 0) {
                            villain = null
                            nextVillainInTicks = (16000 / tickMs).toInt()
                        } else if (tickCount % 2 == 0) {
                            // Har dusre tick pe 1 qadam sar ki taraf bhagta hai
                            val v = villain!!
                            val dx = snake.first().x - v.x
                            val dy = snake.first().y - v.y
                            var nv = v
                            if (abs(dx) >= abs(dy) && dx != 0) nv = IntOffset(v.x + if (dx > 0) 1 else -1, v.y)
                            else if (dy != 0) nv = IntOffset(v.x, v.y + if (dy > 0) 1 else -1)
                            if (nv.x in 0 until GRID_W && nv.y in 0 until gridH && nv != fruit) {
                                villain = nv
                                if (nv == snake.first()) endGame("Nevle ne pakar liya!")
                            }
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cellPx = minOf(maxWidth / GRID_W, maxHeight / gridH)
        val boardW = cellPx * GRID_W
        val boardH = cellPx * gridH
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Score: $score", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 15.sp)
                Text("Best: $best", color = Color.Gray, fontSize = 14.sp)
                if (villain != null) {
                    Text("Nevla aa gaya - bhago!", color = Color(0xFFC62828), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Box(
                Modifier.size(boardW, boardH).clip(RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            // Tap control: sar ke jis taraf tap, sanp usi taraf
                            val cell = size.width / GRID_W
                            val headCx = (snake.first().x + 0.5f) * cell
                            val headCy = (snake.first().y + 0.5f) * cell
                            val dx = offset.x - headCx
                            val dy = offset.y - headCy
                            if (abs(dx) > abs(dy)) {
                                setDirection(if (dx > 0) DirRight else DirLeft)
                            } else {
                                setDirection(if (dy > 0) DirDown else DirUp)
                            }
                        }
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val cell = size.width / GRID_W
                    for (x in 0 until GRID_W) {
                        for (y in 0 until gridH) {
                            drawRect(
                                color = if ((x + y) % 2 == 0) Color(0xFF2E7D32) else Color(0xFF2A722E),
                                topLeft = Offset(x * cell, y * cell),
                                size = Size(cell, cell)
                            )
                        }
                    }
                    drawFruit(fruit, fruitType, cell)
                    villain?.let { drawVillain(it, cell, snake.first()) }
                    drawSnake(snake, dir, cell, colors, tickCount % 2 == 0)
                }
                if (!playing && !gameOver) {
                    OverlayBox(
                        "Snake Plus",
                        "Tap karke sanp ko ghumao - sar ke upar tap = upar, side tap = side.\nFruit khao, aur nevle se bacho - wo 5 second ke liye peeche parta hai!",
                        "Play"
                    ) { resetGame(); playing = true }
                }
                if (gameOver) {
                    OverlayBox(
                        "Game Over",
                        "$overReason\nScore: $score   -   Best: $best",
                        "Play Again"
                    ) { resetGame(); playing = true }
                }
            }
            Text(
                "Board pe tap karo - jis taraf tap, sanp usi taraf",
                color = Color.Gray, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun OverlayBox(title: String, message: String, buttonText: String, onPlay: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.padding(20.dp)
        ) {
            Column(
                Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C2333))
                Spacer(Modifier.height(8.dp))
                Text(message, fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(buttonText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Smooth 3D sanp: ek hi musalsal jism (path), dabe hue hisse NAHI.
// Neeche saya, darmiyan asal rang, upar halki chamak wali line - tube jaisa 3D lagta hai.
private fun DrawScope.drawSnake(
    snake: List<IntOffset>,
    dir: IntOffset,
    cell: Float,
    colors: SnakeColorSet,
    tongueOut: Boolean
) {
    if (snake.isEmpty()) return
    fun center(p: IntOffset) = Offset((p.x + 0.5f) * cell, (p.y + 0.5f) * cell)
    val bodyPath = Path().apply {
        val first = center(snake.first())
        moveTo(first.x, first.y)
        for (seg in snake.drop(1)) {
            val c = center(seg)
            lineTo(c.x, c.y)
        }
    }
    val bodyW = cell * 0.78f
    // Saya
    drawPath(bodyPath, Color.Black.copy(alpha = 0.20f),
        style = Stroke(width = bodyW, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        alpha = 1f)
    // Gehra kinara (neeche ki taraf gehra rang)
    drawPath(bodyPath, colors.dark,
        style = Stroke(width = bodyW, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    // Asal jism
    drawPath(bodyPath, colors.main,
        style = Stroke(width = bodyW * 0.84f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    // Upar chamak (3D tube wali roshni)
    val lightPath = Path().apply {
        val first = center(snake.first())
        moveTo(first.x - bodyW * 0.14f, first.y - bodyW * 0.16f)
        for (seg in snake.drop(1)) {
            val c = center(seg)
            lineTo(c.x - bodyW * 0.14f, c.y - bodyW * 0.16f)
        }
    }
    drawPath(lightPath, colors.light.copy(alpha = 0.55f),
        style = Stroke(width = bodyW * 0.26f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    // Dum ki taraf patla asar: aakhri hisso pe board rang ke halke dabbe nahi,
    // sirf aakhri point pe chhota gol sira
    val tail = center(snake.last())
    drawCircle(colors.dark, bodyW * 0.30f, tail)

    // Sar
    val head = center(snake.first())
    val headR = cell * 0.56f
    drawCircle(Color.Black.copy(alpha = 0.20f), headR, Offset(head.x, head.y + cell * 0.07f))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colors.light, colors.main, colors.dark),
            center = Offset(head.x - headR * 0.35f, head.y - headR * 0.35f),
            radius = headR * 1.6f
        ),
        radius = headR, center = head
    )
    // Zuban (har dusre tick bahar)
    if (tongueOut) {
        val tx = head.x + dir.x * headR
        val ty = head.y + dir.y * headR
        val ex = tx + dir.x * cell * 0.34f
        val ey = ty + dir.y * cell * 0.34f
        drawLine(Color(0xFFE53935), Offset(tx, ty), Offset(ex, ey), strokeWidth = cell * 0.07f, cap = StrokeCap.Round)
        val px = -dir.y * cell * 0.10f
        val py = -dir.x * cell * 0.10f
        drawLine(Color(0xFFE53935), Offset(ex, ey), Offset(ex + px + dir.x * cell * 0.1f, ey + py + dir.y * cell * 0.1f), strokeWidth = cell * 0.055f, cap = StrokeCap.Round)
        drawLine(Color(0xFFE53935), Offset(ex, ey), Offset(ex - px + dir.x * cell * 0.1f, ey - py + dir.y * cell * 0.1f), strokeWidth = cell * 0.055f, cap = StrokeCap.Round)
    }
    // Aankhen
    val exd = dir.x * headR * 0.34f
    val eyd = dir.y * headR * 0.34f
    val pxd = -dir.y * headR * 0.38f
    val pyd = -dir.x * headR * 0.38f
    for (s in listOf(-1f, 1f)) {
        val eyeC = Offset(head.x + exd + pxd * s, head.y + eyd + pyd * s)
        drawCircle(Color.White, headR * 0.24f, eyeC)
        drawCircle(Color(0xFF141414), headR * 0.13f, Offset(eyeC.x + dir.x * headR * 0.07f, eyeC.y + dir.y * headR * 0.07f))
    }
}

// Villain: nevla (mongoose) - sanp ka dushman. Gehra jism, laal aankhen, ghussa.
private fun DrawScope.drawVillain(pos: IntOffset, cell: Float, headPos: IntOffset) {
    val cx = (pos.x + 0.5f) * cell
    val cy = (pos.y + 0.5f) * cell
    val facing = if (headPos.x >= pos.x) 1f else -1f
    // Saya
    drawOval(Color.Black.copy(alpha = 0.22f),
        topLeft = Offset(cx - cell * 0.46f, cy - cell * 0.26f + cell * 0.08f),
        size = Size(cell * 0.92f, cell * 0.56f))
    // Jism
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF8D6E63), Color(0xFF4E342E)),
            center = Offset(cx - cell * 0.15f, cy - cell * 0.15f),
            radius = cell * 0.75f
        ),
        topLeft = Offset(cx - cell * 0.48f, cy - cell * 0.34f),
        size = Size(cell * 0.96f, cell * 0.62f)
    )
    // Dum (peeche ki taraf)
    drawLine(Color(0xFF4E342E),
        Offset(cx - facing * cell * 0.44f, cy + cell * 0.1f),
        Offset(cx - facing * cell * 0.72f, cy - cell * 0.22f),
        strokeWidth = cell * 0.12f, cap = StrokeCap.Round)
    // Sar (aage ki taraf)
    val hx = cx + facing * cell * 0.34f
    drawCircle(Color(0xFF5D4037), cell * 0.30f, Offset(hx, cy - cell * 0.05f))
    // Kaan
    drawCircle(Color(0xFF3E2723), cell * 0.10f, Offset(hx - facing * cell * 0.16f, cy - cell * 0.30f))
    // Laal ghussa aankhen
    for (s in listOf(-1f, 1f)) {
        val eyeC = Offset(hx + facing * cell * 0.10f, cy - cell * 0.10f + s * cell * 0.11f)
        drawCircle(Color(0xFFFFEBEE), cell * 0.085f, eyeC)
        drawCircle(Color(0xFFD50000), cell * 0.05f, eyeC)
    }
    // Naak
    drawCircle(Color(0xFF1B1B1B), cell * 0.055f, Offset(hx + facing * cell * 0.28f, cy - cell * 0.02f))
}

private fun DrawScope.drawFruit(pos: IntOffset, type: Int, cell: Float) {
    val cx = (pos.x + 0.5f) * cell
    val cy = (pos.y + 0.5f) * cell
    when (type) {
        0 -> { // Apple
            drawCircle(Color.Black.copy(alpha = 0.22f), cell * 0.4f, Offset(cx, cy + cell * 0.08f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFE57373), Color(0xFFC62828)),
                    center = Offset(cx - cell * 0.13f, cy - cell * 0.13f),
                    radius = cell * 0.62f
                ),
                radius = cell * 0.41f, center = Offset(cx, cy)
            )
            drawCircle(Color.White.copy(alpha = 0.45f), cell * 0.10f, Offset(cx - cell * 0.16f, cy - cell * 0.17f))
            drawLine(Color(0xFF6D4C41), Offset(cx, cy - cell * 0.37f), Offset(cx + cell * 0.06f, cy - cell * 0.54f), strokeWidth = cell * 0.07f, cap = StrokeCap.Round)
            drawOval(Color(0xFF43A047), topLeft = Offset(cx + cell * 0.05f, cy - cell * 0.60f), size = Size(cell * 0.3f, cell * 0.16f))
        }
        1 -> { // Orange
            drawCircle(Color.Black.copy(alpha = 0.22f), cell * 0.4f, Offset(cx, cy + cell * 0.08f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFB74D), Color(0xFFEF6C00)),
                    center = Offset(cx - cell * 0.13f, cy - cell * 0.13f),
                    radius = cell * 0.62f
                ),
                radius = cell * 0.41f, center = Offset(cx, cy)
            )
            drawCircle(Color.White.copy(alpha = 0.45f), cell * 0.10f, Offset(cx - cell * 0.16f, cy - cell * 0.17f))
            drawOval(Color(0xFF43A047), topLeft = Offset(cx - cell * 0.05f, cy - cell * 0.54f), size = Size(cell * 0.28f, cell * 0.15f))
        }
        else -> { // Grapes
            drawCircle(Color.Black.copy(alpha = 0.22f), cell * 0.38f, Offset(cx, cy + cell * 0.09f))
            val grape = Color(0xFF7B1FA2)
            val grapeLight = Color(0xFFAB47BC)
            val spots = listOf(
                Offset(-0.18f, -0.08f), Offset(0.18f, -0.08f),
                Offset(-0.09f, 0.12f), Offset(0.1f, 0.12f),
                Offset(0f, 0.3f)
            )
            for ((i, o) in spots.withIndex()) {
                drawCircle(if (i % 2 == 0) grapeLight else grape, cell * 0.20f, Offset(cx + o.x * cell, cy + o.y * cell))
            }
            drawCircle(Color.White.copy(alpha = 0.4f), cell * 0.06f, Offset(cx - cell * 0.22f, cy - cell * 0.13f))
            drawLine(Color(0xFF6D4C41), Offset(cx, cy - cell * 0.18f), Offset(cx + cell * 0.08f, cy - cell * 0.42f), strokeWidth = cell * 0.06f, cap = StrokeCap.Round)
            drawOval(Color(0xFF43A047), topLeft = Offset(cx + cell * 0.06f, cy - cell * 0.52f), size = Size(cell * 0.26f, cell * 0.14f))
        }
    }
}
