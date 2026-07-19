package com.autoexpense.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Paid
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.autoexpense.app.data.CustomCategoryRepository

fun cleanCategoryName(raw: String): String {
    val cleaned = raw.replace(Regex("[^a-zA-Z0-9 &/,-]"), "").trim()
    return if (cleaned.isBlank()) "Other" else cleaned
}

fun getCustomIconVector(iconName: String): ImageVector {
    return when (iconName) {
        "ShoppingBag" -> Icons.Outlined.ShoppingBag
        "SportsEsports" -> Icons.Outlined.SportsEsports
        "School" -> Icons.Outlined.School
        "FitnessCenter" -> Icons.Outlined.FitnessCenter
        "Subscriptions" -> Icons.Outlined.Subscriptions
        "Paid" -> Icons.Outlined.Paid
        "StarOutline" -> Icons.Outlined.StarOutline
        "FavoriteBorder" -> Icons.Outlined.FavoriteBorder
        "WorkOutline" -> Icons.Outlined.WorkOutline
        "Restaurant" -> Icons.Outlined.Restaurant
        "DirectionsCar" -> Icons.Outlined.DirectionsCar
        "Home" -> Icons.Outlined.Home
        "Flight" -> Icons.Outlined.Flight
        "LocalHospital" -> Icons.Outlined.LocalHospital
        "Movie" -> Icons.Outlined.Movie
        "SwapHoriz" -> Icons.Outlined.SwapHoriz
        "Category" -> Icons.Outlined.Category
        else -> Icons.Outlined.Category
    }
}

fun getCategoryIcon(category: String): ImageVector {
    val clean = cleanCategoryName(category)
    val customList = CustomCategoryRepository.customCategories.value
    val match = customList.find { cleanCategoryName(it.name).equals(clean, ignoreCase = true) }
    if (match != null) {
        return getCustomIconVector(match.iconName)
    }
    val lower = clean.lowercase()
    return when {
        lower.contains("food") || lower.contains("dining") -> Icons.Outlined.Restaurant
        lower.contains("transport") || lower.contains("car") || lower.contains("fuel") -> Icons.Outlined.DirectionsCar
        lower.contains("groceries") || lower.contains("grocery") -> Icons.Outlined.ShoppingCart
        lower.contains("shopping") -> Icons.Outlined.ShoppingBag
        lower.contains("entertainment") || lower.contains("movie") -> Icons.Outlined.Movie
        lower.contains("healthcare") || lower.contains("health") || lower.contains("medical") -> Icons.Outlined.LocalHospital
        lower.contains("rent") || lower.contains("bills") || lower.contains("home") -> Icons.Outlined.Home
        lower.contains("travel") || lower.contains("flight") -> Icons.Outlined.Flight
        lower.contains("personal") || lower.contains("transfer") -> Icons.Outlined.SwapHoriz
        lower.contains("gaming") -> Icons.Outlined.SportsEsports
        lower.contains("education") -> Icons.Outlined.School
        lower.contains("fitness") -> Icons.Outlined.FitnessCenter
        lower.contains("subscriptions") -> Icons.Outlined.Subscriptions
        else -> Icons.Outlined.Category
    }
}

@Composable
fun AutoExpenseLogo(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tint: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(size)
            .border(1.5.dp, tint, RoundedCornerShape(size * 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Insights,
            contentDescription = "Zors logo",
            tint = tint,
            modifier = Modifier.size(size * 0.58f)
        )
    }
}
