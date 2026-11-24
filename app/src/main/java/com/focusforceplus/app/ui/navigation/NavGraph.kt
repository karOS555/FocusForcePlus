package com.focusforceplus.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.focusforceplus.app.ui.screens.blocker.BlockerListScreen
import com.focusforceplus.app.ui.screens.blocker.BlockerSettingsScreen
import com.focusforceplus.app.ui.screens.blocker.DisclosureScreen
import com.focusforceplus.app.ui.screens.blocker.DisclosureType
import com.focusforceplus.app.ui.screens.focus.ActiveFocusScreen
import com.focusforceplus.app.ui.screens.focus.CreateFocusScreen
import com.focusforceplus.app.ui.screens.focus.FocusListScreen
import com.focusforceplus.app.ui.screens.home.HomeScreen
import com.focusforceplus.app.ui.screens.onboarding.OnboardingScreen
import com.focusforceplus.app.ui.screens.routine.ActiveRoutineScreen
import com.focusforceplus.app.ui.screens.routine.CreateRoutineScreen
import com.focusforceplus.app.ui.screens.routine.RoutineListScreen
import com.focusforceplus.app.ui.screens.settings.SettingsScreen
import com.focusforceplus.app.ui.screens.sleep.SleepScreen
import com.focusforceplus.app.ui.screens.sleep.SleepViewModel
import com.focusforceplus.app.ui.screens.stats.StatsScreen
import com.focusforceplus.app.ui.screens.todo.CreateTodoScreen
import com.focusforceplus.app.ui.screens.todo.TodoListScreen

/**
 * Goes back from the screen [from], once.
 *
 * A screen can ask to be left twice: a second tap on its back arrow, or a check
 * like "this list is gone, so leave" that fires again while the screen is already
 * sliding out. By then the screen below is the current one, and a plain pop would
 * take that one away too, down to an empty stack and a blank page. Asking who
 * wants to leave makes the second call do nothing.
 *
 * A screen with nothing below it goes to Home instead. Popping it would empty the
 * stack while the screen stays on show, and from then on nothing on it could close
 * it: that is how the first routine's editor, opened straight from the setup guide,
 * saved the routine and then stayed where it was.
 */
private fun NavHostController.leave(from: androidx.navigation.NavBackStackEntry) {
    if (currentBackStackEntry?.id != from.id) return
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigate(AppModule.HOME.route) {
            popUpTo(from.destination.id) { inclusive = true }
            launchSingleTop = true
        }
    }
}

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = AppModule.HOME.route,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(AppModule.HOME.route) {
            fun goToTab(route: String) {
                navController.navigate(route) {
                    popUpTo(AppModule.HOME.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            HomeScreen(
                onStartRoutine       = { id -> navController.navigate(RoutineRoutes.active(id)) },
                onOpenActiveRoutine  = { id -> navController.navigate(RoutineRoutes.active(id)) },
                onOpenActiveFocus    = { id -> navController.navigate(FocusRoutes.active(id)) },
                onGoToRoutines       = { goToTab(RoutineRoutes.LIST) },
                onGoToTodos          = { goToTab(TodoRoutes.LIST) },
                onGoToFocus          = { goToTab(FocusRoutes.LIST) },
                onGoToBlocker        = { goToTab(BlockerRoutes.LIST) },
                onNewTodo            = { navController.navigate(TodoRoutes.create()) },
                onNewRoutine         = { navController.navigate(RoutineRoutes.CREATE) },
                onOpenStats          = { navController.navigate(StatsRoutes.STATS) },
                onGoToLists          = { goToTab(ListRoutes.LIST) },
                onOpenList           = { id -> navController.navigate(ListRoutes.detail(id)) },
                onGoToSleep          = { goToTab(SleepRoutes.SLEEP) },
                onEditTodo           = { id -> navController.navigate(TodoRoutes.edit(id)) },
            )
        }

        // ── Statistics ────────────────────────────────────────────────────────
        composable(StatsRoutes.STATS) { entry ->
            StatsScreen(
                onNavigateBack = { navController.leave(entry) },
                onOpenUsageDisclosure = { navController.navigate(BlockerRoutes.disclosure(DisclosureType.USAGE_ACCESS)) },
            )
        }

        // ── Onboarding (first launch only) ────────────────────────────────────
        composable(OnboardingRoutes.ONBOARDING) {
            OnboardingScreen(
                onFinish = { createFirstRoutine ->
                    // Home always comes first, also under the first routine's editor:
                    // saving or leaving that one needs a page to land on.
                    navController.navigate(AppModule.HOME.route) {
                        popUpTo(OnboardingRoutes.ONBOARDING) { inclusive = true }
                    }
                    if (createFirstRoutine) navController.navigate(RoutineRoutes.CREATE)
                },
                onOpenDisclosure = { type ->
                    navController.navigate(BlockerRoutes.disclosure(type))
                },
                onOpenPage = { route -> navController.navigate(route) },
            )
        }

        // ── Routines ──────────────────────────────────────────────────────────
        composable(RoutineRoutes.LIST) {
            RoutineListScreen(
                onCreateRoutine = { navController.navigate(RoutineRoutes.CREATE) },
                onEditRoutine   = { id -> navController.navigate(RoutineRoutes.edit(id)) },
                onStartRoutine  = { id -> navController.navigate(RoutineRoutes.active(id)) },
            )
        }

        composable(RoutineRoutes.CREATE) { entry ->
            CreateRoutineScreen(
                onNavigateBack = { navController.leave(entry) },
            )
        }

        composable(
            route = RoutineRoutes.EDIT,
            arguments = listOf(navArgument("routineId") { type = NavType.LongType }),
        ) { entry ->
            CreateRoutineScreen(
                onNavigateBack = { navController.leave(entry) },
            )
        }

        composable(
            route = RoutineRoutes.ACTIVE,
            arguments = listOf(navArgument("routineId") { type = NavType.LongType }),
        ) {
            ActiveRoutineScreen(
                onBack = {
                    navController.navigate(RoutineRoutes.LIST) {
                        popUpTo(RoutineRoutes.LIST) { inclusive = true }
                    }
                },
            )
        }

        // ── Todos ─────────────────────────────────────────────────────────────
        composable(TodoRoutes.LIST) {
            TodoListScreen(
                onCreateTodo = { navController.navigate(TodoRoutes.create()) },
                onEditTodo   = { id -> navController.navigate(TodoRoutes.edit(id)) },
            )
        }

        composable(
            route = TodoRoutes.CREATE_PATTERN,
            arguments = listOf(
                navArgument("listId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("title") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            CreateTodoScreen(onNavigateBack = { navController.leave(entry) })
        }

        composable(
            route = TodoRoutes.EDIT,
            arguments = listOf(navArgument("todoId") { type = NavType.LongType }),
        ) { entry ->
            CreateTodoScreen(onNavigateBack = { navController.leave(entry) })
        }

        // ── App Blocker ───────────────────────────────────────────────────────
        composable(BlockerRoutes.LIST) {
            BlockerListScreen(
                onOpenSettings   = { navController.navigate(BlockerRoutes.SETTINGS) },
                onOpenDisclosure = { type -> navController.navigate(BlockerRoutes.disclosure(type)) },
                onNewBlock       = { navController.navigate(BlockerRoutes.block()) },
                onEditBlock      = { name -> navController.navigate(BlockerRoutes.block(name)) },
            )
        }

        composable(
            route = BlockerRoutes.BLOCK,
            arguments = listOf(
                navArgument("name") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            com.focusforceplus.app.ui.screens.blocker.BlockEditorScreen(
                groupName = backStackEntry.arguments?.getString("name"),
                onBack = { navController.leave(backStackEntry) },
            )
        }

        composable(BlockerRoutes.SETTINGS) { entry ->
            BlockerSettingsScreen(onNavigateBack = { navController.leave(entry) })
        }

        composable(
            route = BlockerRoutes.DISCLOSURE,
            arguments = listOf(navArgument("type") { type = NavType.StringType }),
        ) { backStackEntry ->
            DisclosureScreen(
                type = backStackEntry.arguments?.getString("type") ?: "",
                onNavigateBack = { navController.leave(backStackEntry) },
            )
        }

        // ── Focus Mode ────────────────────────────────────────────────────────
        composable(FocusRoutes.LIST) {
            FocusListScreen(
                onCreateSession = { navController.navigate(FocusRoutes.CREATE) },
                onEditSession   = { id -> navController.navigate(FocusRoutes.edit(id)) },
                onOpenActive    = { id -> navController.navigate(FocusRoutes.active(id)) },
            )
        }

        composable(FocusRoutes.CREATE) { entry ->
            CreateFocusScreen(onNavigateBack = { navController.leave(entry) })
        }

        composable(
            route = FocusRoutes.EDIT,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) { entry ->
            CreateFocusScreen(onNavigateBack = { navController.leave(entry) })
        }

        composable(
            route = FocusRoutes.ACTIVE,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
        ) {
            ActiveFocusScreen(
                onBack = {
                    navController.navigate(FocusRoutes.LIST) {
                        popUpTo(FocusRoutes.LIST) { inclusive = true }
                    }
                },
            )
        }

        // ── Sleep Mode ────────────────────────────────────────────────────────
        composable(SleepRoutes.SLEEP) { entry ->
            SleepScreen(
                onNavigateBack = { navController.leave(entry) },
                topLevel = true,
                onCreateRoutine = { linkTarget ->
                    navController.navigate(SleepRoutes.createLinkedRoutine(linkTarget))
                },
            )
        }

        // Creating a routine from the sleep screen links it on save. The sleep
        // entry is still on the back stack, so its ViewModel does the linking and
        // the picker already shows the new routine when we pop back.
        composable(
            route = SleepRoutes.CREATE_LINKED_ROUTINE,
            arguments = listOf(navArgument("linkTarget") { type = NavType.StringType }),
        ) { backStackEntry ->
            val linkTarget = backStackEntry.arguments?.getString("linkTarget") ?: "bedtime"
            val sleepEntry = remember(backStackEntry) {
                navController.getBackStackEntry(SleepRoutes.SLEEP)
            }
            val sleepViewModel: SleepViewModel = hiltViewModel(sleepEntry)
            CreateRoutineScreen(
                onNavigateBack = { navController.leave(backStackEntry) },
                onRoutineSaved = { savedId ->
                    if (linkTarget == "bedtime") sleepViewModel.setBedtimeRoutine(savedId)
                    else sleepViewModel.setMorningRoutine(savedId)
                },
            )
        }

        // ── Lists ─────────────────────────────────────────────────────────────
        composable(ListRoutes.LIST) {
            com.focusforceplus.app.ui.screens.lists.ListsScreen(
                onOpenList = { id -> navController.navigate(ListRoutes.detail(id)) },
            )
        }

        composable(
            route = ListRoutes.DETAIL,
            arguments = listOf(navArgument("listId") { type = NavType.LongType }),
        ) { entry ->
            com.focusforceplus.app.ui.screens.lists.ListDetailScreen(
                onBack = { navController.leave(entry) },
                onRemind = { listId, title -> navController.navigate(TodoRoutes.create(listId, title)) },
                onEditTodo = { id -> navController.navigate(TodoRoutes.edit(id)) },
                onOpenList = { id ->
                    navController.navigate(ListRoutes.detail(id)) { popUpTo(ListRoutes.DETAIL) { inclusive = true } }
                },
            )
        }

        // ── Settings ──────────────────────────────────────────────────────────
        composable(SettingsRoutes.SETTINGS) { entry ->
            SettingsScreen(
                onNavigateBack = { navController.leave(entry) },
                onOpenBlockerSettings = { navController.navigate(BlockerRoutes.SETTINGS) },
                onOpenDisclosure = { type -> navController.navigate(BlockerRoutes.disclosure(type)) },
                onReplayOnboarding = { navController.navigate(OnboardingRoutes.ONBOARDING) },
                onOpenSleepMode = { navController.navigate(SleepRoutes.SLEEP) },
                onOpenSpeedBumps = { navController.navigate(SettingsRoutes.SPEED_BUMPS) },
                onOpenTrash = { navController.navigate(TrashRoutes.TRASH) },
                onOpenIntegrations = { navController.navigate(SettingsRoutes.INTEGRATIONS) },
                onOpenAutoBackup = { navController.navigate(SettingsRoutes.AUTO_BACKUP) },
                onOpenAppearance = { navController.navigate(SettingsRoutes.APPEARANCE) },
                onOpenCalendar = { navController.navigate(SettingsRoutes.CALENDAR) },
                onOpenTaskSync = { navController.navigate(SettingsRoutes.TASK_SYNC) },
            )
        }

        composable(SettingsRoutes.TASK_SYNC) { entry ->
            com.focusforceplus.app.ui.screens.settings.TaskSyncScreen(
                onNavigateBack = { navController.leave(entry) },
                onOpenDisclosure = { type -> navController.navigate(BlockerRoutes.disclosure(type)) },
            )
        }

        composable(SettingsRoutes.CALENDAR) { entry ->
            com.focusforceplus.app.ui.screens.settings.CalendarScreen(
                onNavigateBack = { navController.leave(entry) },
                onOpenDisclosure = { type -> navController.navigate(BlockerRoutes.disclosure(type)) },
            )
        }

        composable(SettingsRoutes.APPEARANCE) { entry ->
            com.focusforceplus.app.ui.screens.settings.AppearanceScreen(
                onNavigateBack = { navController.leave(entry) },
            )
        }

        composable(SettingsRoutes.AUTO_BACKUP) { entry ->
            com.focusforceplus.app.ui.screens.settings.AutoBackupScreen(
                onNavigateBack = { navController.leave(entry) },
            )
        }

        composable(SettingsRoutes.INTEGRATIONS) { entry ->
            com.focusforceplus.app.ui.screens.settings.IntegrationsScreen(
                onNavigateBack = { navController.leave(entry) },
            )
        }

        composable(SettingsRoutes.SPEED_BUMPS) { entry ->
            com.focusforceplus.app.ui.screens.settings.SpeedBumpsScreen(
                onNavigateBack = { navController.leave(entry) },
            )
        }

        // ── Trash ─────────────────────────────────────────────────────────────
        composable(TrashRoutes.TRASH) { entry ->
            com.focusforceplus.app.ui.screens.trash.TrashScreen(
                onNavigateBack = { navController.leave(entry) },
            )
        }
    }
}
