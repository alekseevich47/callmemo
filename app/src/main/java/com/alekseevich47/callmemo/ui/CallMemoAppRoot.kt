package com.alekseevich47.callmemo.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alekseevich47.callmemo.domain.model.CallDirection
import com.alekseevich47.callmemo.player.ProximityPlaybackEffect
import com.alekseevich47.callmemo.telecom.DialerRoleHelper
import com.alekseevich47.callmemo.ui.calendar.CalendarRoute
import com.alekseevich47.callmemo.ui.calls.CallDetailRoute
import com.alekseevich47.callmemo.ui.calls.CallsRoute
import com.alekseevich47.callmemo.ui.calls.DialpadSheet
import com.alekseevich47.callmemo.ui.components.ChromeFab
import com.alekseevich47.callmemo.ui.components.ChromeFabBottomPadding
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.net.URLDecoder

private const val TabAnimMs = 160

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CallMemoAppRoot(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    initialDialNumber: String? = null,
    telecomAction: String? = null,
    telecomName: String? = null,
    telecomNumber: String? = null,
    telecomDirection: String? = null,
    openCallDetailId: Long? = null,
    dialerRoleHelper: DialerRoleHelper? = null,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Calls opens with dialpad expanded; scroll collapses it; FAB stays when closed.
    var dialpadOpen by rememberSaveable { mutableStateOf(true) }
    var dialpadMounted by remember { mutableStateOf(true) }
    var dialpadSeed by rememberSaveable { mutableStateOf("") }
    var playerSwipeDismissing by remember { mutableStateOf(false) }
    val hazeState = remember { HazeState() }

    val permissionState = rememberMultiplePermissionsState(
        permissions = buildList {
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        },
    )

    val dialerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
        val helper = dialerRoleHelper
        if (helper != null && !helper.isDefaultDialer()) {
            helper.registerPhoneAccount()
            helper.createRequestIntent()?.let { dialerLauncher.launch(it) }
        }
    }

    LaunchedEffect(initialDialNumber) {
        if (!initialDialNumber.isNullOrBlank()) {
            dialpadSeed = initialDialNumber
            dialpadMounted = true
            dialpadOpen = true
        }
    }

    LaunchedEffect(openCallDetailId) {
        openCallDetailId?.let { id ->
            navController.navigate(Routes.callDetail(id))
        }
    }

    LaunchedEffect(telecomAction, telecomNumber) {
        when (telecomAction) {
            "incoming" -> {
                val name = telecomName ?: telecomNumber.orEmpty()
                val number = telecomNumber.orEmpty()
                if (number.isNotBlank()) navController.navigate(Routes.incoming(name, number))
            }
            "in_call" -> {
                val name = telecomName ?: telecomNumber.orEmpty()
                val number = telecomNumber.orEmpty()
                val direction = telecomDirection ?: CallDirection.OUTGOING.name
                if (number.isNotBlank()) navController.navigate(Routes.inCall(name, number, direction))
            }
        }
    }

    ProximityPlaybackEffect(active = player.visible && player.playing) { near ->
        playerViewModel.setEarpiece(near)
    }

    val topLevel = TopLevelDestination.entries.firstOrNull { route == it.route }
    // Keep last tab for glass bar during AnimatedVisibility exit (avoids NPE on settings/detail).
    var lastTopLevel by remember { mutableStateOf(TopLevelDestination.CALLS) }
    if (topLevel != null) lastTopLevel = topLevel

    val inCallChrome = route.startsWith("in_call") || route.startsWith("incoming")
    val onCallDetail = route.startsWith("call_detail")
    val detailCallId = if (onCallDetail) backStack?.arguments?.getLong("callId") else null
    val onPlayingDetail = player.visible && detailCallId != null && detailCallId == player.callId

    val hideTabs = inCallChrome || route == Routes.SETTINGS || onCallDetail
    val showOverlayChrome = !inCallChrome
    val showTabs = topLevel != null && !hideTabs
    // Sticky bar only outside the detail of the recording being played.
    val showPlayer = showOverlayChrome &&
        player.visible &&
        !onPlayingDetail &&
        route != Routes.SETTINGS
    val showDialFab = topLevel == TopLevelDestination.CALLS &&
        !dialpadOpen &&
        !dialpadMounted &&
        !inCallChrome

    // Chip plate height only — bottom bar never uses layout halo (stable Y).
    val tabsReserve = if (showTabs) 64.dp else 0.dp
    val playerClearance = tabsReserve + 12.dp
    val playerReserve = if (showPlayer && !dialpadMounted) 72.dp + 12.dp else 0.dp
    val chromeBottom = tabsReserve + playerReserve + 12.dp
    // Inside dialpad white fill: lift keypad/call FAB just above the chip plate.
    val dialpadBottomPad = tabsReserve + 8.dp

    fun openDialpad(seed: String = "") {
        dialpadSeed = seed
        dialpadMounted = true
        dialpadOpen = true
    }

    fun placeCall(name: String?, number: String) {
        val dial = PhoneNumberUtils.normalizeForDial(number)
        if (!PhoneNumberUtils.isValidDialNumber(dial)) return
        dialpadOpen = false
        val activity = context as? android.app.Activity
        val placed = if (activity != null && dialerRoleHelper != null) {
            dialerRoleHelper.placeCall(activity, dial)
        } else {
            false
        }
        if (!placed) {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$dial"))
            runCatching { context.startActivity(callIntent) }
                .onFailure {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dial")))
                }
        }
        navController.navigate(Routes.inCall(name ?: dial, dial, CallDirection.OUTGOING.name))
    }

    fun selectTab(dest: TopLevelDestination) {
        if (dest == TopLevelDestination.CALLS) {
            openDialpad()
        } else {
            // Leave Calls: drop sheet immediately so remount is clean on return.
            dialpadOpen = false
            dialpadMounted = false
        }
        navController.navigate(dest.route) {
            popUpTo(TopLevelDestination.CALLS.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(showPlayer) {
        if (showPlayer) playerSwipeDismissing = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CallMemoMist)
            .statusBarsPadding(),
    ) {
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.CALLS.route,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
            enterTransition = {
                fadeIn(animationSpec = tween(TabAnimMs)) +
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(TabAnimMs),
                        initialOffset = { it / 28 },
                    )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(TabAnimMs)) +
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(TabAnimMs),
                        targetOffset = { it / 28 },
                    )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(TabAnimMs)) +
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(TabAnimMs),
                        initialOffset = { it / 28 },
                    )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(TabAnimMs)) +
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(TabAnimMs),
                        targetOffset = { it / 28 },
                    )
            },
        ) {
            composable(TopLevelDestination.CALLS.route) {
                CallsRoute(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenDetail = { id -> navController.navigate(Routes.callDetail(id)) },
                    onCallNumber = { name, number -> placeCall(name, number) },
                    listBottomPadding = chromeBottom + 72.dp,
                    hasCallLogPermission = permissionState.permissions.any {
                        it.permission == Manifest.permission.READ_CALL_LOG && it.status.isGranted
                    },
                    onRequestPermissions = { permissionState.launchMultiplePermissionRequest() },
                    onCallsListScrolled = {
                        if (dialpadOpen || dialpadMounted) dialpadOpen = false
                    },
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
                    onOpenCall = { id ->
                        val currentId = backStack?.arguments?.getLong("callId")
                        navController.navigate(Routes.callDetail(id)) {
                            if (currentId != null) {
                                popUpTo(Routes.callDetail(currentId)) { inclusive = true }
                            }
                        }
                    },
                    onPlay = { callId, contactName, number, uri, durationMs, startedAt ->
                        playerViewModel.play(callId, contactName, number, uri, durationMs, startedAt)
                    },
                    playback = player,
                    onTogglePlayback = playerViewModel::toggle,
                    onSeekPlayback = playerViewModel::seekTo,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsRoute(
                    onBack = { navController.popBackStack() },
                    onTestCall = {
                        navController.navigate(
                            Routes.incoming("Тестовый абонент", "+7 900 000-00-01"),
                        )
                    },
                    onRequestDefaultDialer = {
                        dialerRoleHelper?.registerPhoneAccount()
                        dialerRoleHelper?.createRequestIntent()?.let { dialerLauncher.launch(it) }
                    },
                    isDefaultDialer = dialerRoleHelper?.isDefaultDialer() == true,
                )
            }
            composable(
                route = Routes.IN_CALL,
                arguments = listOf(
                    navArgument("name") { type = NavType.StringType },
                    navArgument("number") { type = NavType.StringType },
                    navArgument("direction") { type = NavType.StringType },
                ),
            ) { entry ->
                val name = decode(entry.arguments?.getString("name").orEmpty())
                val number = decode(entry.arguments?.getString("number").orEmpty())
                val direction = runCatching {
                    CallDirection.valueOf(entry.arguments?.getString("direction").orEmpty())
                }.getOrDefault(CallDirection.OUTGOING)
                InCallRoute(
                    name = name,
                    number = number,
                    direction = direction,
                    onEnd = {
                        navController.popBackStack(
                            route = TopLevelDestination.CALLS.route,
                            inclusive = false,
                        )
                    },
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
                        // Telecom answer is invoked inside IncomingRoute; navigate to in-call UI.
                        navController.popBackStack()
                        navController.navigate(
                            Routes.inCall(name, number, CallDirection.INCOMING.name),
                        )
                    },
                    onDecline = { navController.popBackStack() },
                )
            }
        }

        // Player above list, under dialpad; vertically cleared above glass tabs + halo.
        AnimatedVisibility(
            visible = showPlayer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(1f)
                .navigationBarsPadding()
                .padding(bottom = if (showTabs) playerClearance else 8.dp),
            enter = fadeIn(tween(TabAnimMs)) + slideInVertically(tween(TabAnimMs)) { it / 2 },
            // Swipe already moved the bar off-screen — skip exit animation to avoid flash.
            exit = if (playerSwipeDismissing) {
                ExitTransition.None
            } else {
                fadeOut(tween(TabAnimMs)) + slideOutVertically(tween(TabAnimMs)) { it / 2 }
            },
        ) {
            StickyPlayerBar(
                state = player,
                onToggle = playerViewModel::toggle,
                onSeek = playerViewModel::seekTo,
                onCycleSpeed = playerViewModel::cycleSpeed,
                onOpenDetail = {
                    player.callId?.let { id -> navController.navigate(Routes.callDetail(id)) }
                },
                onDismiss = {
                    playerSwipeDismissing = true
                    playerViewModel.hide()
                },
            )
        }

        // Bottom-docked wrap-content sheet; white fill runs under the glass tabs.
        if (dialpadMounted && topLevel == TopLevelDestination.CALLS && !inCallChrome) {
            DialpadSheet(
                expanded = dialpadOpen,
                bottomContentPadding = PaddingValues(bottom = dialpadBottomPad),
                initialNumber = dialpadSeed,
                onDismissFinished = {
                    dialpadOpen = false
                    dialpadMounted = false
                },
                onCall = { number -> placeCall(null, number) },
                sheetModifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .zIndex(2f),
            )
        }

        // Tabs above dialpad. Wrap-width, no halo — Y stays fixed open/closed.
        AnimatedVisibility(
            visible = showTabs,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(4f)
                .navigationBarsPadding()
                .padding(bottom = 4.dp),
            enter = fadeIn(tween(TabAnimMs)),
            exit = fadeOut(tween(TabAnimMs)),
        ) {
            GlassBottomBar(
                current = lastTopLevel,
                onSelect = ::selectTab,
                hazeState = hazeState,
            )
        }

        AnimatedVisibility(
            visible = showDialFab,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .zIndex(3f),
            enter = fadeIn(tween(TabAnimMs)),
            exit = fadeOut(tween(TabAnimMs)),
        ) {
            ChromeFab(
                onClick = { openDialpad() },
                icon = Icons.Outlined.Dialpad,
                contentDescription = "Набор номера",
                bottomPadding = when {
                    showPlayer && showTabs -> playerClearance + 64.dp
                    showPlayer -> 72.dp
                    else -> ChromeFabBottomPadding
                },
            )
        }
    }
}

private fun decode(value: String): String =
    runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
