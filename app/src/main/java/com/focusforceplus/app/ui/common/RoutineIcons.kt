package com.focusforceplus.app.ui.common

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
        IconCategory("Other", listOf(
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
    var showPicker by remember { mutableStateOf(false) }
    val appIcon = RoutineIcons.find(selectedKey)
    val hasIcon = appIcon != null

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (hasIcon) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable { showPicker = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = appIcon?.vector ?: Icons.Filled.Add,
            contentDescription = if (hasIcon) appIcon!!.label else "Add icon",
            tint = if (hasIcon) MaterialTheme.colorScheme.onPrimaryContainer
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(size * 0.5f),
        )
    }

    if (showPicker) {
        IconPickerDialog(
            selectedKey = selectedKey,
            onSelect    = { key -> onSelect(key); showPicker = false },
            onDismiss   = { showPicker = false },
        )
    }
}

/**
 * Full-screen scrollable dialog showing all icon categories as a grid.
 * Tapping an icon selects it and closes the dialog.
 * A "Remove icon" option appears at the top when an icon is already selected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPickerDialog(
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose an icon") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (selectedKey != null) {
                    TextButton(
                        onClick  = { onSelect(null) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Remove icon",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                RoutineIcons.categories.forEach { category ->
                    Text(
                        text     = category.label,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement   = Arrangement.spacedBy(8.dp),
                    ) {
                        category.icons.forEach { appIcon ->
                            val isSelected = appIcon.key == selectedKey
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    .clickable { onSelect(appIcon.key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector     = appIcon.vector,
                                    contentDescription = appIcon.label,
                                    tint            = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                      else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier        = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = null,
    )
}
