package com.alekseevich47.callmemo.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alekseevich47.callmemo.ui.calendar.CalendarRoute
import com.alekseevich47.callmemo.ui.calls.CallDetailRoute
import com.alekseevich47.callmemo.ui.calls.CallsRoute
import com.alekseevich47.callmemo.ui.calls.DialpadRoute
import com.alekseevich47.callmemo.ui.components.GlassBottomBar
import com.alekseevich47.callmemo.ui.components.StickyPlayerBar
import com.alekseevich47.callmemo.ui.contacts.ContactsRoute
import com.alekseevich47.callmemo.ui.incall.InCallRoute
import com.alekseevich47.callmemo.ui.incall.IncomingRoute
import com.alekseevich47.callmemo.ui.navigation.Routes
import com.alekseevich47.callmemo.ui.navigation.TopLevelDestination
import com.alekseevich47.callmemo.ui.notes.NotesRoute
import com.alekseevich47.callmemo.ui.player.PlayerViewModel
import com.alekseevich47.callmemo.ui.settings.SettingsRoute
import com.alekseevich47.callmemo.ui.theme.CallMemoMist
import com.alekseevich47.callmemo.util.PhoneNumberUtils
import java.net.URLDecoder

@Composable
fun CallMemoAppRoot(
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val topLevel = TopLevelDestination.entries.firstOrNull { route == it.route }
    val hideChrome = route.startsWith("in_call") ||
        route.startsWith("incoming") ||
        route == Routes.SETTINGS ||
        route == Routes.DIALPAD ||
        route.startsWith("call_detail")

    fun placeCall(name: String?, number: String) {
        val dial = PhoneNumberUtils.normalizeForDial(number)
        if (!PhoneNumberUtils.isValidDialNumber(dial)) return
        val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$dial"))
        runCatching { context.startActivity(callIntent) }
            .onFailure {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dial")))
            }
        navController.navigate(Routes.inCall(name ?: dial, dial))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = CallMemoMist,
        bottomBar = {
            if (!hideChrome) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 4.dp),
                ) {
                    if (player.visible) {
                        StickyPlayerBar(
                            title = player.title,
                            subtitle = player.subtitle,
                            playing = player.playing,
                            onToggle = playerViewModel::toggle,
                            barModifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (topLevel != null) {
                        GlassBottomBar(
                            current = topLevel,
                            onSelect = { dest ->
                                navController.navigate(dest.route) {
                                    popUpTo(TopLevelDestination.CALLS.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CallMemoMist)
                .padding(padding)
                .statusBarsPadding(),
        ) {
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.CALLS.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(TopLevelDestination.CALLS.route) {
                    CallsRoute(
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenDialpad = { navController.navigate(Routes.DIALPAD) },
                        onOpenDetail = { id -> navController.navigate(Routes.callDetail(id)) },
                        onCallNumber = { name, number -> placeCall(name, number) },
                    )
                }
                composable(TopLevelDestination.CONTACTS.route) {
                    ContactsRoute(
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onCall = { name, number -> placeCall(name, number) },
                    )
                }
                composable(TopLevelDestination.CALENDAR.route) {
                    CalendarRoute(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
                }
                composable(TopLevelDestination.NOTES.route) {
                    NotesRoute(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
                }
                composable(
                    route = Routes.CALL_DETAIL,
                    arguments = listOf(navArgument("callId") { type = NavType.LongType }),
                ) {
                    CallDetailRoute(
                        onBack = { navController.popBackStack() },
                        onCall = { name, number -> placeCall(name, number) },
                        onPlay = { title, subtitle, uri -> playerViewModel.play(title, subtitle, uri) },
                    )
                }
                composable(Routes.DIALPAD) {
                    DialpadRoute(
                        onBack = { navController.popBackStack() },
                        onCall = { number -> placeCall(null, number) },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsRoute(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.IN_CALL,
                    arguments = listOf(
                        navArgument("name") { type = NavType.StringType },
                        navArgument("number") { type = NavType.StringType },
                    ),
                ) { entry ->
                    val name = decode(entry.arguments?.getString("name").orEmpty())
                    val number = decode(entry.arguments?.getString("number").orEmpty())
                    InCallRoute(
                        name = name,
                        number = number,
                        onEnd = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.INCOMING,
                    arguments = listOf(
                        navArgument("name") { type = NavType.StringType },
                        navArgument("number") { type = NavType.StringType },
                    ),
                ) { entry ->
                    val name = decode(entry.arguments?.getString("name").orEmpty())
                    val number = decode(entry.arguments?.getString("number").orEmpty())
                    IncomingRoute(
                        name = name,
                        number = number,
                        onAccept = {
                            navController.popBackStack()
                            navController.navigate(Routes.inCall(name, number))
                        },
                        onDecline = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private fun decode(value: String): String =
    runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
