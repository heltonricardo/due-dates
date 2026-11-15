package app.duedates

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

val ReminderIcons: List<Pair<String, ImageVector>> = listOf(
    "bill" to Icons.Filled.Receipt,
    "card" to Icons.Filled.CreditCard,
    "home" to Icons.Filled.Home,
    "cart" to Icons.Filled.ShoppingCart,
    "power" to Icons.Filled.Bolt,
    "water" to Icons.Filled.WaterDrop,
    "internet" to Icons.Filled.Wifi,
    "phone" to Icons.Filled.Phone,
    "car" to Icons.Filled.DirectionsCar,
    "health" to Icons.Filled.MedicalServices,
    "school" to Icons.Filled.School,
    "tv" to Icons.Filled.Tv,
    "building" to Icons.Filled.Business,
    "pet" to Icons.Filled.Pets,
    "money" to Icons.Filled.Payments,
    "bank" to Icons.Filled.AccountBalance,
    "gym" to Icons.Filled.FitnessCenter,
    "travel" to Icons.Filled.Flight
)

val ReminderColors: List<Color> = listOf(
    Color(0xFF4C8DFF),
    Color(0xFF2FB36D),
    Color(0xFFF08C2E),
    Color(0xFFE5558A),
    Color(0xFF8B6BEF),
    Color(0xFF19A8A8)
)

fun iconFor(key: String): ImageVector = ReminderIcons.firstOrNull { it.first == key }?.second ?: ReminderIcons[0].second

fun colorFor(index: Int): Color = ReminderColors[index.coerceIn(0, ReminderColors.size - 1)]
