package com.autoexpense.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.autoexpense.app.ui.theme.AeElevation
import com.autoexpense.app.ui.theme.AeMotion
import com.autoexpense.app.ui.theme.AeRadius
import com.autoexpense.app.ui.theme.AeSpacing
import com.autoexpense.app.ui.theme.AeType
import com.autoexpense.app.ui.theme.aePalette
import com.autoexpense.app.ui.theme.categoryColor

@Composable
fun AeStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    subText: String? = null,
    icon: ImageVector? = null,
    accent: Color? = null
) {
    val palette = aePalette()
    val accentColor = accent ?: palette.primary
    AeCard(modifier = modifier, elevated = true) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accentColor.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(AeSpacing.Sm))
        }
        Text(value, color = palette.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = palette.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (subText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(subText, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AeCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = aePalette()
    Card(
        colors = CardDefaults.cardColors(containerColor = palette.surface),
        shape = RoundedCornerShape(AeRadius.Lg),
        border = BorderStroke(1.dp, palette.border.copy(alpha = 0.72f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (elevated) AeElevation.Md else 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(AeSpacing.Md), content = content)
    }
}

@Composable
fun AeGradientCard(
    brush: Brush,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(AeRadius.Xl),
        elevation = CardDefaults.cardElevation(defaultElevation = AeElevation.Lg),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .background(brush)
                .padding(AeSpacing.Lg),
            content = content
        )
    }
}

@Composable
fun AeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null
) {
    val palette = aePalette()
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        colors = ButtonDefaults.buttonColors(containerColor = palette.primary, contentColor = Color.White),
        shape = RoundedCornerShape(AeRadius.Md),
        modifier = modifier.height(44.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(AeSpacing.Sm))
            }
            Text(text, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AeOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val palette = aePalette()
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(1.dp, palette.primary.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.primary),
        shape = RoundedCornerShape(AeRadius.Md),
        modifier = modifier.height(44.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(AeSpacing.Sm))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AeScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val palette = aePalette()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AeSpacing.Md, vertical = AeSpacing.Md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = AeType.ScreenTitle, color = palette.textPrimary)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, style = AeType.Body, color = palette.textSecondary)
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun AeSectionHeader(
    title: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = aePalette()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = AeType.Section, color = palette.textPrimary)
        if (actionText != null && onAction != null) {
            Text(
                actionText,
                color = palette.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

@Composable
fun AeSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val palette = aePalette()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = palette.textTertiary) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.textTertiary) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.primary,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = palette.surface,
            unfocusedContainerColor = palette.surface,
            focusedTextColor = palette.textPrimary,
            unfocusedTextColor = palette.textPrimary
        ),
        shape = RoundedCornerShape(AeRadius.Md),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun AeFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = aePalette()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AeRadius.Full))
            .background(if (selected) palette.primary else palette.surface)
            .border(1.dp, if (selected) palette.primary else palette.border, RoundedCornerShape(AeRadius.Full))
            .clickable(onClick = onClick)
            .padding(horizontal = AeSpacing.Md, vertical = AeSpacing.Sm)
    ) {
        Text(
            label,
            color = if (selected) Color.White else palette.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun AeCategoryBadge(
    category: String,
    modifier: Modifier = Modifier,
    compact: Boolean = true
) {
    val color = categoryColor(category)
    val palette = aePalette()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AeRadius.Full))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 16.dp else 20.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.ShoppingBag, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (compact) 9.dp else 11.dp))
        }
        Text(category, color = color, fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AeMerchantLogo(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.ShoppingBag
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .background(color.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun AeTransactionCard(
    merchant: String,
    amount: String,
    date: String,
    paymentMethod: String,
    category: String,
    modifier: Modifier = Modifier,
    isExpense: Boolean = true,
    autoDetected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val palette = aePalette()
    val amountColor = if (isExpense) palette.expense else palette.success
    val clickableModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    AeCard(modifier = clickableModifier, elevated = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AeSpacing.Md)) {
            AeMerchantLogo(label = merchant, color = amountColor)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(merchant, color = palette.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("$date - $paymentMethod", color = palette.textSecondary, fontSize = 12.sp)
                AeCategoryBadge(category = category)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, color = amountColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (autoDetected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .background(palette.primary.copy(alpha = 0.14f), CircleShape)
                            .padding(4.dp)
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = "Auto detected", tint = palette.primary, modifier = Modifier.size(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AePaymentCard(
    title: String,
    subtitle: String,
    amount: String,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    statusColor: Color? = null,
    icon: ImageVector = Icons.Outlined.ShoppingBag,
    iconColor: Color? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    val palette = aePalette()
    val resolvedStatusColor = statusColor ?: palette.success
    val resolvedIconColor = iconColor ?: palette.warning
    AeCard(modifier = modifier, elevated = true) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AeSpacing.Md)) {
                AeMerchantLogo(label = title, color = resolvedIconColor, icon = icon)
                Column {
                    Text(title, color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = palette.textSecondary, fontSize = 14.sp)
                }
            }
            Text(amount, color = palette.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (statusText != null) {
            Spacer(modifier = Modifier.height(AeSpacing.Sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(resolvedStatusColor, CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text(statusText, color = resolvedStatusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (content != null) {
            Spacer(modifier = Modifier.height(AeSpacing.Sm))
            content()
        }
    }
}

@Composable
fun AeBudgetCard(
    title: String,
    spent: String,
    limit: String,
    progress: Float,
    remainingText: String,
    modifier: Modifier = Modifier,
    progressColor: Color? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null
) {
    val palette = aePalette()
    val resolvedProgressColor = progressColor ?: palette.primary
    AeCard(modifier = modifier, elevated = true) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = palette.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(remainingText, color = if (progress > 1f) palette.error else palette.success, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(AeSpacing.Sm))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(spent, color = palette.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("of $limit", color = palette.textSecondary, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(AeSpacing.Md))
        AeProgressBar(progress = progress, color = resolvedProgressColor)
        Text("${(progress * 100).toInt()}% used", color = palette.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = AeSpacing.Sm))
        if (actions != null) {
            Spacer(modifier = Modifier.height(AeSpacing.Md))
            actions()
        }
    }
}

@Composable
fun AeEmptyState(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    action: (@Composable () -> Unit)? = null
) {
    val palette = aePalette()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AeSpacing.Xxl, horizontal = AeSpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = palette.textTertiary, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(AeSpacing.Lg))
        Text(title, color = palette.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (description != null) {
            Spacer(modifier = Modifier.height(AeSpacing.Sm))
            Text(description, color = palette.textSecondary, fontSize = 16.sp, lineHeight = 24.sp, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(modifier = Modifier.height(AeSpacing.Lg))
            action()
        }
    }
}

@Composable
fun AeLoadingState(visible: Boolean, modifier: Modifier = Modifier) {
    val palette = aePalette()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = AeMotion.standard()),
        exit = fadeOut(animationSpec = AeMotion.standard()),
        modifier = modifier
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(AeSpacing.Xl), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = palette.primary)
        }
    }
}

@Composable
fun AeProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    val palette = aePalette()
    val resolvedColor = color ?: palette.primary
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(AeRadius.Full)),
        color = resolvedColor,
        trackColor = palette.surfaceAlt
    )
}
