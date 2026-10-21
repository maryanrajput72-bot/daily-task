package com.zabii.dailytask

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val GRID_W = 16
private const val GRID_H = 22

private val DirUp = IntOffset(0, -1)
private val DirDown = IntOffset(0, 1)
private val DirLeft = IntOffset(-1, 0)
private val DirRight = IntOffset(1, 0)

@Composable
fun SnakeGameScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("snake_game", 0) }

    var snake by remember { mutableStateOf(listOf(IntOffset(8, 11), IntOffset(7, 11), IntOffset(6, 11))) }
    var dir by remember { mutableStateOf(DirRight) }
    var pendingDir by remember { mutableStateOf(DirRight) }
    var fruit by remember { mutableStateOf(IntOffset(12, 8)) }
    var fruitType by remember { mutableStateOf(0) }
    var score by remember { mutableStateOf(0) }
    var best by remember { mutableStateOf(prefs.getInt("best", 0)) }
    var playing by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }

    fun randomFruit(s: List<IntOffset>): IntOffset {
        while (true) {
            val p = IntOffset(Random.nextInt(GRID_W), Random.nextInt(GRID_H))
            if (p !in s) return p
        }
    }

    fun resetGame() {
        snake = listOf(IntOffset(8, 11), IntOffset(7, 11), IntOffset(6, 11))
        dir = DirRight
        pendingDir = DirRight
        score = 0
        gameOver = false
        fruitType = Random.nextInt(3)
        fruit = randomFruit(snake)
    }

    fun setDirection(d: IntOffset) {
        // Ulti direction pe nahi mud sakta
        if (d.x + dir.x != 0 || d.y + dir.y != 0) pendingDir = d
    }

    val tickMs = (185 - minOf(85, score * 2)).toLong()

    LaunchedEffect(playing, tickMs) {
        while (playing) {
            delay(tickMs)
            dir = pendingDir
            val head = snake.first()
            val newHead = IntOffset(head.x + dir.x, head.y + dir.y)
            val hitWall = newHead.x !in 0 until GRID_W || newHead.y !in 0 until GRID_H
            val hitSelf = newHead in snake.dropLast(1)
            if (hitWall || hitSelf) {
                playing = false
                gameOver = true
                if (score > best) {
                    best = score
                    prefs.edit().putInt("best", best).apply()
                }
            } else if (newHead == fruit) {
                snake = listOf(newHead) + snake
                score += 10
                fruitType = Random.nextInt(3)
                fruit = randomFruit(snake)
            } else {
                snake = listOf(newHead) + snake.dropLast(1)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF5F7FB)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF1C2333))
            }
            Text("Snake Game", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C2333))
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScoreChip("Score", score.toString())
            ScoreChip("Best", best.toString())
            ScoreChip("Length", snake.size.toString())
        }
        Spacer(Modifier.height(12.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val boardW = maxWidth
            val boardH = boardW * GRID_H / GRID_W
            Box(
                Modifier.size(boardW, boardH)
                    .clip(RoundedCornerShape(18.dp))
                    .pointerInput(Unit) {
                        var accX = 0f
                        var accY = 0f
                        detectDragGestures(
                            onDragStart = { accX = 0f; accY = 0f },
                            onDrag = { change, drag ->
                                change.consume()
                                accX += drag.x
                                accY += drag.y
                                val threshold = 42f
                                if (kotlin.math.abs(accX) > kotlin.math.abs(accY) && kotlin.math.abs(accX) > threshold) {
                                    setDirection(if (accX > 0) DirRight else DirLeft)
                                    accX = 0f; accY = 0f
                                } else if (kotlin.math.abs(accY) > threshold) {
                                    setDirection(if (accY > 0) DirDown else DirUp)
                                    accX = 0f; accY = 0f
                                }
                            }
                        )
                    }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val cell = size.width / GRID_W
                    // Board: lawn green checker (3D feel ke liye halka gehra/halka rang)
                    for (x in 0 until GRID_W) {
                        for (y in 0 until GRID_H) {
                            drawRect(
                                color = if ((x + y) % 2 == 0) Color(0xFF2E7D32) else Color(0xFF2A722E),
                                topLeft = Offset(x * cell, y * cell),
                                size = Size(cell, cell)
                            )
                        }
                    }
                    // Fruit
                    drawFruit(fruit, fruitType, cell)
                    // Snake: peeche se sar tak - glossy 3D segments
                    for (i in snake.indices.reversed()) {
                        val seg = snake[i]
                        val cx = (seg.x + 0.5f) * cell
                        val cy = (seg.y + 0.5f) * cell
                        val isHead = i == 0
                        val r = cell * if (isHead) 0.52f else 0.44f
                        // shadow
                        drawCircle(Color.Black.copy(alpha = 0.22f), r, Offset(cx, cy + cell * 0.08f))
                        // body
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(if (isHead) Color(0xFF9CCC65) else Color(0xFF81C784), Color(0xFF2E7D32)),
                                center = Offset(cx - r * 0.3f, cy - r * 0.3f),
                                radius = r * 1.5f
                            ),
                            radius = r, center = Offset(cx, cy)
                        )
                        // chamak (highlight)
                        drawCircle(
                            Color.White.copy(alpha = 0.38f), r * 0.32f,
                            Offset(cx - r * 0.34f, cy - r * 0.38f)
                        )
                        if (isHead) {
                            // Aankhen - direction ki taraf
                            val ex = dir.x * r * 0.34f
                            val ey = dir.y * r * 0.34f
                            val px = -dir.y * r * 0.36f
                            val py = -dir.x * r * 0.36f
                            for (s in listOf(-1f, 1f)) {
                                val eyeC = Offset(cx + ex + px * s, cy + ey + py * s)
                                drawCircle(Color.White, r * 0.21f, eyeC)
                                drawCircle(Color(0xFF1B1B1B), r * 0.11f, Offset(eyeC.x + dir.x * r * 0.06f, eyeC.y + dir.y * r * 0.06f))
                            }
                        }
                    }
                }

                if (!playing && !gameOver) {
                    OverlayBox("Snake Game", "Fruit khao, sanp lamba karo.\nDewaar aur apne aap se bacho!", "Play") {
                        resetGame()
                        playing = true
                    }
                }
                if (gameOver) {
                    OverlayBox("Game Over", "Score: $score   -   Best: $best", "Play Again") {
                        resetGame()
                        playing = true
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        // D-Pad buttons
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            PadButton(Icons.Filled.KeyboardArrowUp, "Up") { setDirection(DirUp) }
            Row(horizontalArrangement = Arrangement.spacedBy(52.dp)) {
                PadButton(Icons.Filled.KeyboardArrowLeft, "Left") { setDirection(DirLeft) }
                PadButton(Icons.Filled.KeyboardArrowRight, "Right") { setDirection(DirRight) }
            }
            PadButton(Icons.Filled.KeyboardArrowDown, "Down") { setDirection(DirDown) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Swipe bhi kar sakte ho, buttons bhi hain",
            color = Color.Gray, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun ScoreChip(label: String, value: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 11.sp, color = Color.Gray)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
        }
    }
}

@Composable
private fun PadButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF43A047))
    ) {
        Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(30.dp))
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
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1C2333))
                Spacer(Modifier.height(8.dp))
                Text(message, fontSize = 14.sp, color = Color.Gray)
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFruit(pos: IntOffset, type: Int, cell: Float) {
    val cx = (pos.x + 0.5f) * cell
    val cy = (pos.y + 0.5f) * cell
    when (type) {
        0 -> { // Apple - laal seb
            drawCircle(Color.Black.copy(alpha = 0.2f), cell * 0.4f, Offset(cx, cy + cell * 0.07f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFE57373), Color(0xFFC62828)),
                    center = Offset(cx - cell * 0.12f, cy - cell * 0.12f),
                    radius = cell * 0.6f
                ),
                radius = cell * 0.4f, center = Offset(cx, cy)
            )
            drawCircle(Color.White.copy(alpha = 0.4f), cell * 0.1f, Offset(cx - cell * 0.15f, cy - cell * 0.16f))
            // dandi
            drawLine(Color(0xFF6D4C41), Offset(cx, cy - cell * 0.36f), Offset(cx + cell * 0.06f, cy - cell * 0.52f), strokeWidth = cell * 0.07f, cap = StrokeCap.Round)
            // patta
            drawOval(Color(0xFF43A047), topLeft = Offset(cx + cell * 0.05f, cy - cell * 0.58f), size = Size(cell * 0.3f, cell * 0.16f))
        }
        1 -> { // Orange - narangi
            drawCircle(Color.Black.copy(alpha = 0.2f), cell * 0.4f, Offset(cx, cy + cell * 0.07f))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFB74D), Color(0xFFEF6C00)),
                    center = Offset(cx - cell * 0.12f, cy - cell * 0.12f),
                    radius = cell * 0.6f
                ),
                radius = cell * 0.4f, center = Offset(cx, cy)
            )
            drawCircle(Color.White.copy(alpha = 0.4f), cell * 0.1f, Offset(cx - cell * 0.15f, cy - cell * 0.16f))
            drawOval(Color(0xFF43A047), topLeft = Offset(cx - cell * 0.05f, cy - cell * 0.52f), size = Size(cell * 0.28f, cell * 0.15f))
        }
        else -> { // Grapes - angoor ka guccha
            drawCircle(Color.Black.copy(alpha = 0.2f), cell * 0.38f, Offset(cx, cy + cell * 0.08f))
            val grape = Color(0xFF7B1FA2)
            val grapeLight = Color(0xFFAB47BC)
            val spots = listOf(
                Offset(-0.18f, -0.08f), Offset(0.18f, -0.08f),
                Offset(-0.09f, 0.12f), Offset(0.1f, 0.12f),
                Offset(0f, 0.3f)
            )
            for ((i, o) in spots.withIndex()) {
                drawCircle(if (i % 2 == 0) grapeLight else grape, cell * 0.19f, Offset(cx + o.x * cell, cy + o.y * cell))
            }
            drawLine(Color(0xFF6D4C41), Offset(cx, cy - cell * 0.18f), Offset(cx + cell * 0.08f, cy - cell * 0.42f), strokeWidth = cell * 0.06f, cap = StrokeCap.Round)
            drawOval(Color(0xFF43A047), topLeft = Offset(cx + cell * 0.06f, cy - cell * 0.5f), size = Size(cell * 0.26f, cell * 0.14f))
        }
    }
}
