package com.jiancuoti.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.jiancuoti.app.data.Store
import com.jiancuoti.app.net.Supabase
import com.jiancuoti.app.ui.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            var themeMode by remember {
                mutableStateOf(
                    when (Store.settings["theme"]) {
                        "light" -> ThemeMode.LIGHT
                        "dark" -> ThemeMode.DARK
                        else -> ThemeMode.AUTO
                    }
                )
            }
            AppTheme(mode = themeMode) {
                MainScaffold(
                    themeMode = themeMode,
                    onThemeMode = { m ->
                        themeMode = m
                        Store.settings["theme"] = when (m) {
                            ThemeMode.LIGHT -> "light"
                            ThemeMode.DARK -> "dark"
                            else -> "auto"
                        }
                        Store.saveSettings()
                    }
                )
            }
        }
        // 启动时尝试云同步
        if (Supabase.configured) {
            lifecycleScope.launch { Supabase.pull() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(themeMode: ThemeMode, onThemeMode: (ThemeMode) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }
    val bump: () -> Unit = { version++ }

    val tabs: List<Pair<String, ImageVector>> = listOf(
        "错题库" to Icons.Default.Book,
        "组卷" to Icons.Default.Assignment,
        "提取" to Icons.Default.AddCircle,
        "统计" to Icons.Default.BarChart,
        "我的" to Icons.Default.Person
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            if (i == 2) {
                                Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = SkyPrimaryDeep,
                                    modifier = Modifier.size(52.dp).offset(y = (-12).dp),
                                    shadowElevation = 6.dp
                                ) {
                                    Box(Modifier.padding(10.dp)) {
                                        Icon(icon, null, tint = Color.White)
                                    }
                                }
                            } else {
                                Icon(icon, null)
                            }
                        },
                        label = { Text(label, fontSize = 10.5.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> LibraryScreen(onChanged = bump)
                1 -> ComposeScreen(onChanged = bump)
                2 -> ImportScreen(onChanged = bump)
                3 -> StatsScreen()
                4 -> SettingsScreen(themeMode = themeMode, onThemeMode = onThemeMode)
            }
        }
    }
}
