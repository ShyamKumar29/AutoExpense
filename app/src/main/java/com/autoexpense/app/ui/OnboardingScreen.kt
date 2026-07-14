package com.autoexpense.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoexpense.app.ColorBg0
import com.autoexpense.app.ColorBg1
import com.autoexpense.app.ColorBg3
import com.autoexpense.app.ColorOrange
import com.autoexpense.app.ColorText1
import com.autoexpense.app.ColorText2
import com.autoexpense.app.ColorText3
import kotlin.math.sin

@Composable
fun OnboardingScreen(
    onGetStarted: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "financeBackground")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f, // 2 * PI
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg0)
    ) {
        // Animated finance-themed canvas background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Background subtle financial chart lines (sine/wave paths moving with phase)
            val path1 = Path()
            val path2 = Path()
            val steps = 30
            for (i in 0..steps) {
                val x = (w / steps) * i
                val y1 = h * 0.35f + sin((i * 0.3f) + phase) * (h * 0.08f)
                val y2 = h * 0.65f + sin((i * 0.25f) - phase * 0.8f) * (h * 0.1f)
                if (i == 0) {
                    path1.moveTo(x, y1.toFloat())
                    path2.moveTo(x, y2.toFloat())
                } else {
                    path1.lineTo(x, y1.toFloat())
                    path2.lineTo(x, y2.toFloat())
                }
            }

            drawPath(
                path = path1,
                brush = Brush.horizontalGradient(
                    colors = listOf(ColorOrange.copy(alpha = 0.05f), ColorOrange.copy(alpha = 0.22f), Color.Transparent)
                ),
                style = Stroke(width = 2.dp.toPx())
            )
            drawPath(
                path = path2,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.12f), ColorOrange.copy(alpha = 0.18f))
                ),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Soft glowing orange particles and floating data points
            val particleCount = 8
            for (i in 0 until particleCount) {
                val px = (w / particleCount) * i + (w / particleCount) * 0.5f
                val py = h * 0.5f + sin(phase + i * 1.2f) * (h * 0.28f)
                val alpha = (0.08f + 0.12f * sin(phase * 2f + i)).coerceIn(0.04f, 0.2f)
                val radius = (3.dp.toPx() + 2.dp.toPx() * sin(phase + i)).coerceAtLeast(2.dp.toPx())

                drawCircle(
                    color = ColorOrange.copy(alpha = alpha.toFloat()),
                    radius = radius.toFloat(),
                    center = Offset(px, py.toFloat())
                )
            }
        }

        // Main content box sitting cleanly over the background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.widthIn(max = 400.dp)
            ) {
                AutoExpenseLogo(size = 52.dp, tint = Color.White)

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "AutoExpense",
                    color = ColorText1,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Track spending automatically. Understand where your money goes.",
                    color = ColorText2,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(36.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = ColorBg1),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, ColorBg3),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "What should we call you?",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorText1
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Your Name") },
                            placeholder = { Text("Enter your name", color = ColorText3) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorOrange,
                                unfocusedBorderColor = ColorBg3,
                                focusedLabelColor = ColorOrange,
                                unfocusedLabelColor = ColorText2,
                                focusedTextColor = ColorText1,
                                unfocusedTextColor = ColorText1
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                val finalName = if (name.isNotBlank()) name.trim() else "User"
                                onGetStarted(finalName)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Get Started",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
