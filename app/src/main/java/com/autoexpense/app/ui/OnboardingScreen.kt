package com.autoexpense.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

@Composable
fun OnboardingScreen(
    onGetStarted: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    
    val backgroundColor = Color(0xFFF8F9FA)
    val primaryColor = Color(0xFF1A73E8)
    val textPrimary = Color(0xFF202124)
    val textSecondary = Color(0xFF5F6368)
    val surfaceColor = Color.White
    val borderColor = Color.Black.copy(alpha = 0.05f)

    val infiniteTransition = rememberInfiniteTransition(label = "financeBackground")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f, // 2 * PI
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    var dotProgress by remember { mutableStateOf(-1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            val dotAnim = Animatable(0f)
            dotAnim.animateTo(1f, tween(5000, easing = LinearEasing)) {
                dotProgress = this.value
            }
            dotProgress = -1f
            delay(2000)
        }
    }

    val subtitleAlpha = remember { Animatable(0f) }
    val subtitleOffset = remember { Animatable(10f) }
    val cardAlpha = remember { Animatable(0f) }
    val cardOffset = remember { Animatable(40f) }

    LaunchedEffect(Unit) {
        delay(900)
        launch {
            subtitleAlpha.animateTo(1f, tween(800, easing = LinearEasing))
        }
        launch {
            subtitleOffset.animateTo(0f, tween(800, easing = FastOutSlowInEasing))
        }
        
        delay(600)
        launch {
            cardAlpha.animateTo(1f, tween(600, easing = LinearEasing))
        }
        launch {
            cardOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .padding(top = 64.dp),
            horizontalAlignment = Alignment.Start
        ) {
            AutoExpenseLogo(size = 40.dp, tint = primaryColor)
            Spacer(modifier = Modifier.height(24.dp))
            
            AnimatedStaggeredText(
                text = "Welcome to",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary
                ),
                delayStart = 100
            )
            AnimatedStaggeredText(
                text = "AutoExpense",
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                ),
                delayStart = 400
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Track every rupee.\nUnderstand every habit.",
                color = textSecondary,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                modifier = Modifier
                    .alpha(subtitleAlpha.value)
                    .offset(y = subtitleOffset.value.dp)
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                
                val path = Path()
                val steps = 200
                for (i in 0..steps) {
                    val normalizedX = i.toFloat() / steps
                    val x = w * normalizedX
                    
                    val wave1 = sin(normalizedX * 4f - phase) * 0.15f
                    val wave2 = sin(normalizedX * 8f - phase * 1.5f) * 0.08f
                    val wave3 = sin(normalizedX * 14f - phase * 0.5f) * 0.04f
                    
                    val y = h * 0.5f + (wave1 + wave2 + wave3) * h * 0.8f
                    
                    if (i == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }
                
                drawPath(
                    path = path,
                    color = primaryColor.copy(alpha = 0.15f),
                    style = Stroke(width = 3.dp.toPx())
                )
                
                if (dotProgress >= 0f) {
                    val normalizedX = dotProgress
                    val x = w * normalizedX
                    val wave1 = sin(normalizedX * 4f - phase) * 0.15f
                    val wave2 = sin(normalizedX * 8f - phase * 1.5f) * 0.08f
                    val wave3 = sin(normalizedX * 14f - phase * 0.5f) * 0.04f
                    val y = h * 0.5f + (wave1 + wave2 + wave3) * h * 0.8f
                    
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.2f),
                        radius = 8.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = 4.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(24.dp)
                .padding(bottom = 16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = cardOffset.value.dp)
                    .alpha(cardAlpha.value)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color.Black.copy(alpha = 0.04f),
                        spotColor = Color.Black.copy(alpha = 0.1f)
                    ),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "What should we call you?",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Your Name", color = textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryColor,
                            unfocusedBorderColor = borderColor,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            cursorColor = primaryColor
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val buttonScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.98f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "buttonScale"
                    )

                    Button(
                        onClick = {
                            val finalName = if (name.isNotBlank()) name.trim() else "User"
                            onGetStarted(finalName)
                        },
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .scale(buttonScale),
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Continue",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Continue",
                                modifier = Modifier.size(18.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedStaggeredText(text: String, style: TextStyle, delayStart: Long = 0) {
    val characters = text.map { it.toString() }
    val alphas = characters.map { remember { Animatable(0f) } }
    val offsets = characters.map { remember { Animatable(20f) } }

    LaunchedEffect(Unit) {
        delay(delayStart)
        characters.forEachIndexed { index, _ ->
            launch {
                delay(index * 30L)
                alphas[index].animateTo(1f, tween(400, easing = LinearEasing))
            }
            launch {
                delay(index * 30L)
                offsets[index].animateTo(0f, tween(400, easing = FastOutSlowInEasing))
            }
        }
    }

    Row {
        characters.forEachIndexed { index, char ->
            Text(
                text = char,
                style = style,
                modifier = Modifier
                    .alpha(alphas[index].value)
                    .offset(y = offsets[index].value.dp)
            )
        }
    }
}
