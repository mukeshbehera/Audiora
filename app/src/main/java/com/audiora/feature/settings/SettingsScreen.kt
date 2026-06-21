package com.audiora.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.audiora.core.design.GlassmorphicCard
import com.audiora.domain.model.PlaybackSettings
import com.audiora.ui.theme.PrimaryPurple

enum class SettingsDialogType {
    THEME,
    COLOR_SCHEME,
    SKIP_AMOUNT,
    AUTO_REWIND,
    PLAYBACK_SPEED,
    SLEEP_TIMER,
    LICENSES,
    PRIVACY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    var showFolderManagement by remember { mutableStateOf(false) }

    if (showFolderManagement) {
        FolderManagementView(
            onBack = { showFolderManagement = false },
            modifier = modifier
        )
        return
    }

    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val colorSchemeName by viewModel.colorSchemeName.collectAsStateWithLifecycle()
    val playbackSettings by viewModel.playbackSettings.collectAsStateWithLifecycle()

    var activeDialog by remember { mutableStateOf<SettingsDialogType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Audiora Settings",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryPurple,
                            modifier = Modifier.testTag("settings_top_app_bar_title")
                        )
                        Text(
                            text = "Manage your listening studio configuration",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // SECTION 1: Audiobook Folders
            item {
                Text(
                    text = "Audiobook Folders",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryPurple,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showFolderManagement = true }
                                .padding(16.dp)
                                .testTag("folders_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Storage,
                                    contentDescription = "Folders",
                                    tint = PrimaryPurple
                                )
                                Column {
                                    Text("Configure Library Folders", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Add, remove, and rescan audiobook directories", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Open Folders",
                                tint = PrimaryPurple
                            )
                        }
                    }
                }
            }

            // SECTION 2 & 3: Appearance & Theme Customization
            item {
                Text(
                    text = "Appearance & Style",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryPurple
                )
            }
            item {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Theme Switch Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeDialog = SettingsDialogType.THEME }
                                .padding(16.dp)
                                .testTag("appearance_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DarkMode,
                                    contentDescription = "Theme Mode",
                                    tint = PrimaryPurple
                                )
                                Column {
                                    Text("Theme Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        text = when(themeMode) {
                                            "LIGHT" -> "Light"
                                            "DARK" -> "Dark"
                                            else -> "System Default"
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Choose Theme Mode",
                                tint = PrimaryPurple
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

                        // Color Scheme Selection Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeDialog = SettingsDialogType.COLOR_SCHEME }
                                .padding(16.dp)
                                .testTag("color_scheme_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = "Color Options",
                                    tint = PrimaryPurple
                                )
                                Column {
                                    Text("Color Scheme", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        text = when(colorSchemeName) {
                                            "DYNAMIC" -> "Dynamic Color"
                                            "CRIMSON_RED" -> "Crimson Red"
                                            "OCEAN_BLUE" -> "Ocean Blue"
                                            "EMERALD_GREEN" -> "Emerald Green"
                                            "SUNSET_ORANGE" -> "Sunset Orange"
                                            else -> "Audiora Purple"
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Choose Color Scheme",
                                tint = PrimaryPurple
                            )
                        }
                    }
                }
            }

            // SECTION 4: Playback Configuration
            item {
                Text(
                    text = "Playback Settings",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryPurple
                )
            }
            item {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // Skip Amount Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeDialog = SettingsDialogType.SKIP_AMOUNT }
                                .padding(16.dp)
                                .testTag("playback_skip_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Forward,
                                    contentDescription = "Skip Forward/Backward",
                                    tint = PrimaryPurple
                                )
                                Column {
                                    Text("Skip Amount", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("${playbackSettings.skipAmount} seconds", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Configure Skip Limit",
                                tint = PrimaryPurple
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

                        // Auto Rewind Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeDialog = SettingsDialogType.AUTO_REWIND }
                                .padding(16.dp)
                                .testTag("playback_rewind_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Restore,
                                    contentDescription = "Auto Rewind",
                                    tint = PrimaryPurple
                                )
                                Column {
                                    Text("Auto Rewind on Resume", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        text = if (playbackSettings.autoRewind == 0) "Off" else "${playbackSettings.autoRewind} seconds",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Configure Auto Rewind Limit",
                                tint = PrimaryPurple
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

                        // Default Speed Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeDialog = SettingsDialogType.PLAYBACK_SPEED }
                                .padding(16.dp)
                                .testTag("playback_speed_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Speed,
                                    contentDescription = "Default Playback Speed",
                                    tint = PrimaryPurple
                                )
                                Column {
                                    Text("Default Playback Speed", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("${playbackSettings.defaultSpeed}x", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Configure Default Speed",
                                tint = PrimaryPurple
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

                        // Sleep Timer Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeDialog = SettingsDialogType.SLEEP_TIMER }
                                .padding(16.dp)
                                .testTag("playback_sleep_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Timer,
                                    contentDescription = "Sleep Timer Default",
                                    tint = PrimaryPurple
                                )
                                Column {
                                    Text("Sleep Timer Default", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        text = if (playbackSettings.sleepTimerDefault == 0) "Off" else "${playbackSettings.sleepTimerDefault} minutes",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Configure Sleep Timer Default",
                                tint = PrimaryPurple
                            )
                        }
                    }
                }
            }

            // SECTION 5: About Screen / Credits
            item {
                Text(
                    text = "About Audiora",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryPurple
                )
            }
            item {
                GlassmorphicCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        // App Version Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .testTag("about_version_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = "Version Information",
                                    tint = PrimaryPurple
                                )
                                Column {
                                    Text("App Version", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Version 1.1.0 (Release Candidate)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

                        // GitHub Repository Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { /* Open GitHub in mock / Toast or simple action */ }
                                .padding(16.dp)
                                .testTag("about_github_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Code,
                                    contentDescription = "GitHub Repository",
                                    tint = PrimaryPurple
                                )
                                Column {
                                    Text("GitHub Repository", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("https://github.com/audiora/audiobook-player", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.OpenInNew,
                                contentDescription = "Open Link",
                                tint = PrimaryPurple,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

                        // Open Source Licenses Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeDialog = SettingsDialogType.LICENSES }
                                .padding(16.dp)
                                .testTag("about_licenses_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountBalance,
                                    contentDescription = "Open Source Licenses",
                                    tint = PrimaryPurple
                                )
                                Column {
                                    Text("Open Source Licenses", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("View active third-party legal guidelines", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "View Licenses",
                                tint = PrimaryPurple
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))

                        // Privacy Policy Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeDialog = SettingsDialogType.PRIVACY }
                                .padding(16.dp)
                                .testTag("about_privacy_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "Privacy Policy",
                                    tint = PrimaryPurple
                                )
                                Column {
                                    Text("Privacy Policy", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Information collection and protection terms", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "View Privacy Policy",
                                tint = PrimaryPurple
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog Handlers
    when (activeDialog) {
        SettingsDialogType.THEME -> {
            val options = listOf("SYSTEM" to "System Default", "LIGHT" to "Light", "DARK" to "Dark")
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Choose Theme Mode") },
                text = {
                    Column {
                        options.forEach { (optionCode, optionLabel) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setThemeMode(optionCode)
                                        activeDialog = null
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                                    .testTag("theme_option_$optionCode"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(optionLabel, fontSize = 15.sp)
                                RadioButton(
                                    selected = themeMode == optionCode,
                                    onClick = {
                                        viewModel.setThemeMode(optionCode)
                                        activeDialog = null
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryPurple)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        SettingsDialogType.COLOR_SCHEME -> {
            val options = listOf(
                "AUDIORA_PURPLE" to "Audiora Purple",
                "DYNAMIC" to "Dynamic Color (Android 12+)",
                "CRIMSON_RED" to "Crimson Red",
                "OCEAN_BLUE" to "Ocean Blue",
                "EMERALD_GREEN" to "Emerald Green",
                "SUNSET_ORANGE" to "Sunset Orange"
            )
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Choose Color Scheme") },
                text = {
                    Column {
                        options.forEach { (optionCode, optionLabel) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setColorScheme(optionCode)
                                        activeDialog = null
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp)
                                    .testTag("scheme_option_$optionCode"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(optionLabel, fontSize = 15.sp)
                                RadioButton(
                                    selected = colorSchemeName == optionCode,
                                    onClick = {
                                        viewModel.setColorScheme(optionCode)
                                        activeDialog = null
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryPurple)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        SettingsDialogType.SKIP_AMOUNT -> {
            val list = listOf(10, 15, 30, 45, 60)
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Choose Skip Amount") },
                text = {
                    Column {
                        list.forEach { secs ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setSkipAmount(secs)
                                        activeDialog = null
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                                    .testTag("skip_option_$secs"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("$secs seconds", fontSize = 15.sp)
                                RadioButton(
                                    selected = playbackSettings.skipAmount == secs,
                                    onClick = {
                                        viewModel.setSkipAmount(secs)
                                        activeDialog = null
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryPurple)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        SettingsDialogType.AUTO_REWIND -> {
            val list = listOf(0, 3, 5, 10)
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Auto Rewind on Resume") },
                text = {
                    Column {
                        list.forEach { secs ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setAutoRewind(secs)
                                        activeDialog = null
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                                    .testTag("rewind_option_$secs"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (secs == 0) "Off" else "$secs seconds", fontSize = 15.sp)
                                RadioButton(
                                    selected = playbackSettings.autoRewind == secs,
                                    onClick = {
                                        viewModel.setAutoRewind(secs)
                                        activeDialog = null
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryPurple)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        SettingsDialogType.PLAYBACK_SPEED -> {
            val list = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Choose Default Speed") },
                text = {
                    Column {
                        list.forEach { speed ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setDefaultSpeed(speed)
                                        activeDialog = null
                                    }
                                    .padding(vertical = 10.dp, horizontal = 8.dp)
                                    .testTag("speed_option_${speed.toString().replace('.', '_')}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${speed}x", fontSize = 15.sp)
                                RadioButton(
                                    selected = playbackSettings.defaultSpeed == speed,
                                    onClick = {
                                        viewModel.setDefaultSpeed(speed)
                                        activeDialog = null
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryPurple)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        SettingsDialogType.SLEEP_TIMER -> {
            val list = listOf(0, 15, 30, 45, 60)
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Sleep Timer Default") },
                text = {
                    Column {
                        list.forEach { mins ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setSleepTimerDefault(mins)
                                        activeDialog = null
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp)
                                    .testTag("sleep_option_$mins"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (mins == 0) "Off" else "$mins minutes", fontSize = 15.sp)
                                RadioButton(
                                    selected = playbackSettings.sleepTimerDefault == mins,
                                    onClick = {
                                        viewModel.setSleepTimerDefault(mins)
                                        activeDialog = null
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryPurple)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        SettingsDialogType.LICENSES -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Open Source Licenses") },
                text = {
                    LazyColumn(
                        modifier = Modifier.height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Column {
                                Text("Jetpack Compose", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Apache License 2.0\nCopyright 2026 The Android Open Source Project", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        item {
                            Column {
                                Text("Room Database System", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Apache License 2.0\nCopyright 2026 Room Contributors", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        item {
                            Column {
                                Text("Jetpack DataStore & Preferences", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Apache License 2.0\nCopyright 2026 The Android Open Source Project", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                        item {
                            Column {
                                Text("Timber Logger", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Apache License 2.0\nCopyright 2026 Jake Wharton", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Close")
                    }
                }
            )
        }

        SettingsDialogType.PRIVACY -> {
            AlertDialog(
                onDismissRequest = { activeDialog = null },
                title = { Text("Privacy Policy") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Audiora Audiobook Player respects your local metadata data security.", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(
                            "We do not collect, transmit, trace, or share any informational records, logs, listening habits, files, or local bookmarks. Everything remains contained safely offline inside your physical unit's private database layer.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { activeDialog = null }) {
                        Text("Close")
                    }
                }
            )
        }

        else -> {}
    }
}
