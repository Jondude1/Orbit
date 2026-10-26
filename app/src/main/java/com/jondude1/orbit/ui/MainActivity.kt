package com.jondude1.orbit.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.jondude1.orbit.data.Settings
import com.jondude1.orbit.data.ThoughtStore
import com.jondude1.orbit.notify.ReviewScheduler
import com.jondude1.orbit.update.Updater

enum class Dest(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Rounded.Home),
    REVIEW("Review", Icons.Rounded.AutoAwesome),
    LIBRARY("All thoughts", Icons.Rounded.Inventory2),
    SETTINGS("Settings", Icons.Rounded.Settings),
}

class MainActivity : ComponentActivity() {
    private val pendingDest = mutableStateOf<String?>(null)

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        ReviewScheduler.schedule(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingDest.value = intent?.getStringExtra("dest")

        if (Build.VERSION.SDK_INT >= 33 && !Settings.getBool("askedNotif") && !ReviewScheduler.canNotify(this)) {
            Settings.putBool("askedNotif", true)
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            OrbitTheme {
                OrbitShell(pendingDest.value, consumeDest = { pendingDest.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("dest")?.let { pendingDest.value = it }
    }

    override fun onResume() {
        super.onResume()
        Updater.checkQuietly()
        ReviewScheduler.schedule(this)
    }
}

@Composable
fun OrbitShell(requestedDest: String?, consumeDest: () -> Unit) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var dest by rememberSaveable { mutableStateOf(Dest.HOME) }
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var librarySelection by rememberSaveable { mutableStateOf<Long?>(null) }
    var reviewSession by rememberSaveable { mutableStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val all by ThoughtStore.thoughts.collectAsState()
    val dueCount = remember(all) { ThoughtStore.due(list = all).size }

    fun go(d: Dest) {
        if (d == Dest.REVIEW && dest != Dest.REVIEW) reviewSession++
        dest = d
    }

    LaunchedEffect(requestedDest) {
        when (requestedDest) {
            "review" -> go(Dest.REVIEW)
            "library" -> go(Dest.LIBRARY)
            "home" -> go(Dest.HOME)
        }
        if (requestedDest != null) consumeDest()
    }

    val navIcon: @Composable (Dest) -> Unit = { d ->
        if (d == Dest.REVIEW && dueCount > 0) {
            BadgedBox(badge = { Badge { Text("$dueCount") } }) { Icon(d.icon, d.label) }
        } else {
            Icon(d.icon, d.label)
        }
    }

    val content: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            UpdateBanner()
            when (dest) {
                Dest.HOME -> HomeScreen(
                    wide = wide,
                    snackbar = snackbar,
                    onReview = { go(Dest.REVIEW) },
                    onOpen = { id -> openId = id },
                    onLibrary = { go(Dest.LIBRARY) },
                )
                Dest.REVIEW -> androidx.compose.runtime.key(reviewSession) {
                    ReviewScreen(onOpen = { id -> openId = id }, onDone = { go(Dest.HOME) })
                }
                Dest.LIBRARY -> LibraryScreen(wide, librarySelection) { librarySelection = it }
                Dest.SETTINGS -> SettingsScreen()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!wide) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Dest.entries.forEach { d ->
                        NavigationBarItem(
                            selected = dest == d,
                            onClick = { go(d) },
                            icon = { navIcon(d) },
                            label = { Text(d.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        if (wide) {
            Row(Modifier.fillMaxSize().padding(pad)) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.background,
                    windowInsets = WindowInsets(0),
                    header = {
                        Spacer(Modifier.height(12.dp))
                        FloatingActionButton(onClick = { go(Dest.HOME) }) { Icon(Icons.Rounded.Add, "Catch a thought") }
                        Spacer(Modifier.height(12.dp))
                    },
                ) {
                    Dest.entries.forEach { d ->
                        NavigationRailItem(
                            selected = dest == d,
                            onClick = { go(d) },
                            icon = { navIcon(d) },
                            label = { Text(d.label) },
                        )
                    }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                content(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            content(Modifier.fillMaxSize().padding(pad))
        }
    }

    openId?.let { id -> ThoughtEditorDialog(id) { openId = null } }
}
