package com.alekseevich47.callmemo.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.alekseevich47.callmemo.domain.model.CallDirection
import com.alekseevich47.callmemo.telecom.DialerRoleHelper
import com.alekseevich47.callmemo.ui.calendar.CalendarRoute
import com.alekseevich47.callmemo.ui.calls.CallDetailRoute
import com.alekseevich47.callmemo.ui.calls.CallsRoute
import com.alekseevich47.callmemo.ui.calls.DialpadSheet
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
import com.alekseevich47.callmemo.ui.theme.CallMemoBlue
import com.alekseevich47.callmemo.ui.theme.CallMemoMist
import com.alekseevich47.callmemo.util.PhoneNumberUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.net.URLDecoder

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CallMemoAppRoot(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    initialDialNumber: String? = null,
    telecomAction: String? = null,
    telecomName: String? = null,
    telecomNumber: String? = null,
    telecomDirection: String? = null,
    dialerRoleHelper: DialerRoleHelper? = null,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route.orEmpty()
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var dialpadOpen by rememberSaveable { mutableStateOf(false) }
    var dialpadSeed by rememberSaveable { mutableStateOf("") }

    val permissionState = rememberMultiplePermissionsState(
        permissions = buildList {
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
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
            dialpadOpen = true
        }
    }

    LaunchedEffect(telecomAction, telecomNumber) {
        when (telecomAction) {
            "incoming" -> {
                val name = telecomName ?: telecomNumber.orEmpty()
                val number = telecomNumber.orEmpty()
                if (number.isNotBlank()) {
                    navController.navigate(Routes.incoming(name, number))
                }
            }
            "in_call" -> {
                val name = telecomName ?: telecomNumber.orEmpty()
                val number = telecomNumber.orEmpty()
                val direction = telecomDirection ?: CallDirection.OUTGOING.name
                if (number.isNotBlank()) {
                    navController.navigate(Routes.inCall(name, number, direction))
                }
            }
        }
    }

    val topLevel = TopLevelDestination.entries.firstOrNull { route == it.route }
    val inCallChrome = route.startsWith("in_call") || route.startsWith("incoming")
    val hideTabs = inCallChrome || route == Routes.SETTINGS || route.startsWith("call_detail")
    val showOverlayChrome = !inCallChrome
    val showTabs = topLevel != null && !hideTabs
    val showPlayer = showOverlayChrome && player.visible && route != Routes.SETTINGS
    val showDialFab = topLevel == TopLevelDestination.CALLS && !dialpadOpen && !inCallChrome
    // Keep chrome block mounted as one unit so tabs/FAB/player don't pop in ahead of content.
    val showBottomChrome = showOverlayChrome && (showTabs || showPlayer)

    val tabsReserve = if (showTabs) 78.dp else 0.dp
    val playerReserve = if (showPlayer) 124.dp else 0.dp
    val chromeBottom = tabsReserve + playerReserve + 12.dp

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
        dialpadOpen = false
        navController.navigate(dest.route) {
            popUpTo(TopLevelDestination.CALLS.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
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
            modifier = Modifier.fillMaxSize(),
        ) {
            composable(TopLevelDestination.CALLS.route) {
                CallsRoute(
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenDetail = { id -> navController.navigate(Routes.callDetail(id)) },
                    onCallNumber = { name, number -> placeCall(name, number) },
                    listBottomPadding = chromeBottom + 72.dp,
                    hasCallLogPermission = permissionState.permissions
                        .any {
                            it.permission == Manifest.permission.READ_CALL_LOG &&
                                it.status.isGranted
                        },
                    onRequestPermissions = { permissionState.launchMultiplePermissionRequest() },
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
                        navController.popBackStack()
                        navController.navigate(
                            Routes.inCall(name, number, CallDirection.INCOMING.name),
                        )
                    },
                    onDecline = { navController.popBackStack() },
                )
            }
        }

        AnimatedVisibility(
            visible = dialpadOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            DialpadSheet(
                visible = true,
                bottomContentPadding = PaddingValues(bottom = chromeBottom),
                initialNumber = dialpadSeed,
                onDismiss = { dialpadOpen = false },
                onCall = { number -> placeCall(null, number) },
            )
        }

        // Single chrome layer — tabs + player (+ FAB sibling) share the same visibility gate.
        androidx.compose.animation.AnimatedVisibility(
            visible = showBottomChrome || showDialFab,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (showBottomChrome) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 4.dp),
                    ) {
                        if (showPlayer) {
                            StickyPlayerBar(
                                state = player,
                                onToggle = playerViewModel::toggle,
                                onSeek = playerViewModel::seekTo,
                                onCycleSpeed = playerViewModel::cycleSpeed,
                                onOpenDetail = {
                                    player.callId?.let { id ->
                                        navController.navigate(Routes.callDetail(id))
                                    }
                                },
                                onDismiss = playerViewModel::hide,
                                barModifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        if (showTabs) {
                            GlassBottomBar(
                                current = topLevel!!,
                                onSelect = ::selectTab,
                            )
                        }
                    }
                }
                if (showDialFab) {
                    FloatingActionButton(
                        onClick = {
                            dialpadSeed = ""
                            dialpadOpen = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(end = 22.dp, bottom = chromeBottom + 8.dp),
                        shape = CircleShape,
                        containerColor = CallMemoBlue,
                        contentColor = Color.White,
                    ) {
                        Icon(Icons.Outlined.Dialpad, contentDescription = "Набор номера")
                    }
                }
            }
        }
    }
}

private fun decode(value: String): String =
    runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
