package com.focusforceplus.app.ui.common

import com.focusforceplus.app.ui.components.verticalScrollWithHint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LocalPostOffice
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bathtub
import androidx.compose.material.icons.filled.Bed
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BreakfastDining
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Cabin
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DinnerDining
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DryCleaning
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Nature
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Shower
import androidx.compose.material.icons.filled.Soap
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Data model ────────────────────────────────────────────────────────────────

data class AppIcon(
    val key: String,
    val vector: ImageVector,
    val label: String,
)

data class IconCategory(
    val label: String,
    val icons: List<AppIcon>,
)

// ── Icon catalog ──────────────────────────────────────────────────────────────

object RoutineIcons {

    val categories: List<IconCategory> = listOf(
        IconCategory("Morning", listOf(
            AppIcon("wb_sunny",          Icons.Filled.WbSunny,              "Sunrise"),
            AppIcon("alarm",             Icons.Filled.Alarm,                "Alarm"),
            AppIcon("hotel",             Icons.Filled.Hotel,                "Sleep"),
            AppIcon("bedtime",           Icons.Filled.Bedtime,              "Bedtime"),
            AppIcon("nightlight",        Icons.Filled.Nightlight,           "Night"),
            AppIcon("brightness_5",      Icons.Filled.Brightness5,          "Morning light"),
            AppIcon("self_improvement",  Icons.Filled.SelfImprovement,      "Meditation"),
        )),
        IconCategory("Bathroom & Hygiene", listOf(
            AppIcon("bathtub",           Icons.Filled.Bathtub,              "Bath"),
            AppIcon("shower",            Icons.Filled.Shower,               "Shower"),
            AppIcon("soap",              Icons.Filled.Soap,                 "Soap"),
            AppIcon("face_retouching",   Icons.Filled.FaceRetouchingNatural,"Skincare"),
            AppIcon("checkroom",         Icons.Filled.Checkroom,            "Clothing"),
            AppIcon("dry_cleaning",      Icons.Filled.DryCleaning,          "Dry cleaning"),
            AppIcon("cleaning_services", Icons.Filled.CleaningServices,     "Cleaning"),
        )),
        IconCategory("Sport & Fitness", listOf(
            AppIcon("fitness_center",    Icons.Filled.FitnessCenter,        "Gym"),
            AppIcon("directions_run",    Icons.AutoMirrored.Filled.DirectionsRun,  "Running"),
            AppIcon("directions_bike",   Icons.AutoMirrored.Filled.DirectionsBike, "Cycling"),
            AppIcon("pool",              Icons.Filled.Pool,                 "Swimming"),
            AppIcon("sports_soccer",     Icons.Filled.SportsSoccer,         "Soccer"),
            AppIcon("rowing",            Icons.Filled.Rowing,               "Rowing"),
            AppIcon("hiking",            Icons.Filled.Hiking,               "Hiking"),
            AppIcon("accessibility",     Icons.Filled.Accessibility,        "Stretching"),
        )),
        IconCategory("Food & Drink", listOf(
            AppIcon("restaurant",        Icons.Filled.Restaurant,           "Restaurant"),
            AppIcon("local_cafe",        Icons.Filled.LocalCafe,            "Coffee"),
            AppIcon("breakfast_dining",  Icons.Filled.BreakfastDining,      "Breakfast"),
            AppIcon("lunch_dining",      Icons.Filled.LunchDining,          "Lunch"),
            AppIcon("dinner_dining",     Icons.Filled.DinnerDining,         "Dinner"),
            AppIcon("fastfood",          Icons.Filled.Fastfood,             "Fast food"),
            AppIcon("local_pizza",       Icons.Filled.LocalPizza,           "Pizza"),
            AppIcon("emoji_food",        Icons.Filled.EmojiFoodBeverage,    "Snack"),
            AppIcon("water_drop",        Icons.Filled.WaterDrop,            "Water"),
        )),
        IconCategory("Work & Productivity", listOf(
            AppIcon("work",              Icons.Filled.Work,                 "Work"),
            AppIcon("laptop",            Icons.Filled.Laptop,               "Laptop"),
            AppIcon("assignment",        Icons.AutoMirrored.Filled.Assignment,  "Assignment"),
            AppIcon("article",           Icons.AutoMirrored.Filled.Article,     "Article"),
            AppIcon("description",       Icons.Filled.Description,          "Document"),
            AppIcon("edit_note",         Icons.Filled.EditNote,             "Notes"),
            AppIcon("task_alt",          Icons.Filled.TaskAlt,              "Task"),
            AppIcon("business_center",   Icons.Filled.BusinessCenter,       "Business"),
        )),
        IconCategory("Home & Chores", listOf(
            AppIcon("home",              Icons.Filled.Home,                 "Home"),
            AppIcon("local_laundry",     Icons.Filled.LocalLaundryService,  "Laundry"),
            AppIcon("cleaning_home",     Icons.Filled.CleaningServices,     "Tidying up"),
            AppIcon("kitchen",           Icons.Filled.Kitchen,              "Kitchen"),
            AppIcon("yard",              Icons.Filled.Yard,                 "Garden"),
            AppIcon("bed",               Icons.Filled.Bed,                  "Bed"),
            AppIcon("weekend",           Icons.Filled.Weekend,              "Relax"),
            AppIcon("cabin",             Icons.Filled.Cabin,                "Cabin"),
        )),
        IconCategory("Health & Wellness", listOf(
            AppIcon("medication",        Icons.Filled.Medication,           "Medication"),
            AppIcon("medical_services",  Icons.Filled.MedicalServices,      "Doctor"),
            AppIcon("monitor_heart",     Icons.Filled.MonitorHeart,         "Heart rate"),
            AppIcon("spa",               Icons.Filled.Spa,                  "Spa"),
            AppIcon("favorite",          Icons.Filled.Favorite,             "Health"),
            AppIcon("psychology",        Icons.Filled.Psychology,           "Mental health"),
            AppIcon("water_health",      Icons.Filled.WaterDrop,            "Hydration"),
        )),
        IconCategory("Learning & Growth", listOf(
            AppIcon("school",            Icons.Filled.School,               "School"),
            AppIcon("menu_book",         Icons.AutoMirrored.Filled.MenuBook,      "Reading"),
            AppIcon("library_books",     Icons.AutoMirrored.Filled.LibraryBooks, "Library"),
            AppIcon("science",           Icons.Filled.Science,              "Science"),
            AppIcon("calculate",         Icons.Filled.Calculate,            "Math"),
            AppIcon("lightbulb",         Icons.Filled.Lightbulb,            "Ideas"),
            AppIcon("language",          Icons.Filled.Language,             "Language"),
            AppIcon("music_note",        Icons.Filled.MusicNote,            "Music"),
        )),
        IconCategory("Errands & Shopping", listOf(
            AppIcon("local_grocery_store", Icons.Filled.LocalGroceryStore,  "Groceries"),
            AppIcon("local_pharmacy",    Icons.Filled.LocalPharmacy,        "Pharmacy"),
            AppIcon("local_mall",        Icons.Filled.LocalMall,            "Shopping bag"),
            AppIcon("card_giftcard",     Icons.Filled.CardGiftcard,         "Gift"),
            AppIcon("handyman",          Icons.Filled.Handyman,             "Repairs"),
            AppIcon("local_post_office", Icons.Filled.LocalPostOffice,      "Post office"),
            AppIcon("local_gas_station", Icons.Filled.LocalGasStation,      "Fuel"),
            AppIcon("receipt",           Icons.Filled.Receipt,              "Receipt"),
        )),
        IconCategory("Money", listOf(
            AppIcon("savings",           Icons.Filled.Savings,              "Savings"),
            AppIcon("payments",          Icons.Filled.Payments,             "Payments"),
            AppIcon("account_balance",   Icons.Filled.AccountBalance,       "Bank"),
            AppIcon("credit_card",       Icons.Filled.CreditCard,           "Card"),
        )),
        IconCategory("Travel", listOf(
            AppIcon("flight",            Icons.Filled.Flight,               "Flight"),
            AppIcon("luggage",           Icons.Filled.Luggage,              "Luggage"),
            AppIcon("map",               Icons.Filled.Map,                  "Map"),
            AppIcon("beach_access",      Icons.Filled.BeachAccess,          "Beach"),
            AppIcon("directions_bus",    Icons.Filled.DirectionsBus,        "Bus"),
        )),
        IconCategory("People & Family", listOf(
            AppIcon("family_restroom",   Icons.Filled.FamilyRestroom,       "Family"),
            AppIcon("child_care",        Icons.Filled.ChildCare,            "Kids"),
            AppIcon("pets",              Icons.Filled.Pets,                 "Pets"),
            AppIcon("celebration",       Icons.Filled.Celebration,          "Party"),
            AppIcon("cake",              Icons.Filled.Cake,                 "Birthday"),
            AppIcon("forum",             Icons.Filled.Forum,                "Chat"),
            AppIcon("mail",              Icons.Filled.Mail,                 "Mail"),
            AppIcon("call",              Icons.Filled.Call,                 "Call"),
            AppIcon("event",             Icons.Filled.Event,                "Appointment"),
        )),
        IconCategory("Screens & Free time", listOf(
            AppIcon("smartphone",        Icons.Filled.Smartphone,           "Phone"),
            AppIcon("sports_esports",    Icons.Filled.SportsEsports,        "Games"),
            AppIcon("tv",                Icons.Filled.Tv,                   "TV"),
            AppIcon("headphones",        Icons.Filled.Headphones,           "Headphones"),
            AppIcon("photo_camera",      Icons.Filled.PhotoCamera,          "Camera"),
            AppIcon("brush",             Icons.Filled.Brush,                "Art"),
            AppIcon("palette",           Icons.Filled.Palette,              "Creative"),
            AppIcon("local_movies",      Icons.Filled.LocalMovies,          "Movies"),
        )),
        IconCategory("Nature", listOf(
            AppIcon("local_florist",     Icons.Filled.LocalFlorist,         "Flowers"),
            AppIcon("park",              Icons.Filled.Park,                 "Park"),
            AppIcon("eco",               Icons.Filled.Eco,                  "Leaf"),
            AppIcon("recycling",         Icons.Filled.Recycling,            "Recycling"),
            AppIcon("water",             Icons.Filled.Water,                "Water"),
        )),
        IconCategory("Other", listOf(
            AppIcon("block",             Icons.Filled.Block,                "Block"),
            AppIcon("lock",              Icons.Filled.Lock,                 "Lock"),
            AppIcon("flag",              Icons.Filled.Flag,                 "Flag"),
            AppIcon("push_pin",          Icons.Filled.PushPin,              "Pin"),
            AppIcon("emoji_events",      Icons.Filled.EmojiEvents,          "Goal"),
            AppIcon("local_fire_department", Icons.Filled.LocalFireDepartment, "Fire"),
            AppIcon("star",              Icons.Filled.Star,                 "Star"),
            AppIcon("bolt",              Icons.Filled.Bolt,                 "Energy"),
            AppIcon("people",            Icons.Filled.People,               "Social"),
            AppIcon("directions_car",    Icons.Filled.DirectionsCar,        "Car"),
            AppIcon("train",             Icons.Filled.Train,                "Transport"),
            AppIcon("shopping_cart",     Icons.Filled.ShoppingCart,         "Shopping"),
            AppIcon("phone",             Icons.Filled.Phone,                "Phone"),
            AppIcon("timer",             Icons.Filled.Timer,                "Timer"),
            AppIcon("nature",            Icons.Filled.Nature,               "Nature"),
        )),
    )

    private val index: Map<String, AppIcon> =
        categories.flatMap { it.icons }.associateBy { it.key }

    fun find(key: String?): AppIcon? = key?.let { index[it] }
}

// ── Icon picker composables ───────────────────────────────────────────────────

/**
 * A tappable icon-in-a-box that opens [IconPickerDialog] when clicked.
 * Shows the currently selected icon, or a "+" placeholder if none is set.
 */
@Composable
fun IconPickerButton(
    selectedKey: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    onSelect: (String?) -> Unit,
) {
    val colors = com.focusforceplus.app.ui.theme.FfTheme.colors
    var showPicker by remember { mutableStateOf(false) }
    val appIcon = RoutineIcons.find(selectedKey)
    val hasIcon = appIcon != null

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            // Aero: milk glass, like the text fields beside it.
            .background((if (hasIcon) colors.container else colors.surfaceHigh).copy(alpha = if (colors.isAero) 0.70f else 1f))
            .clickable { showPicker = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = appIcon?.vector ?: Icons.Filled.Add,
            contentDescription = if (hasIcon) "Icon: ${appIcon!!.label}. Change it" else "Add an icon",
            tint = if (hasIcon) colors.onContainer else colors.textMuted,
            modifier = Modifier.size(size * 0.5f),
        )
    }

    if (showPicker) {
        IconPickerDialog(
            selectedKey = selectedKey,
            onSelect = { key -> onSelect(key); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * Every icon, grouped, with a search over names and groups. Tapping one picks it
 * and closes the dialog; "Remove icon" appears when one is already set.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPickerDialog(
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = com.focusforceplus.app.ui.theme.FfTheme.colors
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    val shown = RoutineIcons.categories.mapNotNull { category ->
        if (q.isEmpty() || category.label.lowercase().contains(q)) {
            category
        } else {
            val icons = category.icons.filter { it.label.lowercase().contains(q) || it.key.replace('_', ' ').contains(q) }
            if (icons.isEmpty()) null else category.copy(icons = icons)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceVariant,
        title = { Text("Choose an icon", style = MaterialTheme.typography.titleLarge, color = colors.text) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                com.focusforceplus.app.ui.components.SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search icons",
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScrollWithHint(),
                ) {
                    if (shown.isEmpty()) {
                        Text(
                            "Nothing called \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    shown.forEach { category ->
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            category.icons.forEach { appIcon ->
                                val isSelected = appIcon.key == selectedKey
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) colors.primary else colors.surface)
                                        .clickable { onSelect(appIcon.key) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = appIcon.vector,
                                        contentDescription = appIcon.label,
                                        tint = if (isSelected) colors.onPrimary else colors.textSecondary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedKey != null) {
                TextButton(onClick = { onSelect(null) }) {
                    Text("Remove icon", style = MaterialTheme.typography.labelLarge, color = colors.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        },
    )
}
