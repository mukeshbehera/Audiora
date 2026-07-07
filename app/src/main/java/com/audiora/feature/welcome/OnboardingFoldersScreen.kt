package com.audiora.feature.welcome

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.audiora.core.design.GlassmorphicCard
import com.audiora.core.design.SectionHeader
import com.audiora.domain.model.AudiobookFolder
import com.audiora.domain.util.toDisplayPath
import com.audiora.domain.repository.BookRepository
import com.audiora.domain.repository.SettingsRepository
import com.audiora.feature.settings.FolderViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun FolderIntroLogo(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Glowing purple shadow behind
        Canvas(modifier = Modifier.size(200.dp)) {
            drawCircle(
                color = Color(0xFFC084FC).copy(alpha = 0.16f),
                radius = size.minDimension * 0.44f
            )
            drawCircle(
                color = Color(0xFF7C3AED).copy(alpha = 0.08f),
                radius = size.minDimension * 0.35f
            )
        }
        
        // Let's render a folder shape with book/soundwaves on front
        Canvas(modifier = Modifier.size(130.dp)) {
            val w = size.width
            val h = size.height
            
            // Back layer of folder
            val backFolder = Path().apply {
                moveTo(w * 0.15f, h * 0.22f)
                lineTo(w * 0.42f, h * 0.22f)
                lineTo(w * 0.50f, h * 0.30f)
                lineTo(w * 0.88f, h * 0.30f)
                cubicTo(w * 0.93f, h * 0.30f, w * 0.93f, h * 0.36f, w * 0.88f, h * 0.36f)
                lineTo(w * 0.88f, h * 0.82f)
                cubicTo(w * 0.88f, h * 0.86f, w * 0.84f, h * 0.88f, w * 0.80f, h * 0.88f)
                lineTo(w * 0.22f, h * 0.88f)
                cubicTo(w * 0.18f, h * 0.88f, w * 0.15f, h * 0.86f, w * 0.15f, h * 0.82f)
                close()
            }
            drawPath(
                backFolder,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFE9D5FF), Color(0xFFC084FC))
                )
            )
            
            // Front flap layer of folder with an elegant offset and lighter color
            val frontFolder = Path().apply {
                moveTo(w * 0.15f, h * 0.36f)
                lineTo(w * 0.88f, h * 0.36f)
                lineTo(w * 0.92f, h * 0.82f)
                cubicTo(w * 0.92f, h * 0.86f, w * 0.88f, h * 0.89f, w * 0.82f, h * 0.89f)
                lineTo(w * 0.22f, h * 0.89f)
                cubicTo(w * 0.16f, h * 0.89f, w * 0.12f, h * 0.86f, w * 0.12f, h * 0.82f)
                close()
            }
            drawPath(
                frontFolder,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFF3E8FF), Color(0xFFD8B4FE))
                )
            )
            
            // Draw book & soundwave outline on the front flap (like the logo)
            val logoCenterX = w * 0.52f
            val logoCenterY = h * 0.64f
            
            // Miniature book pages
            val leftPage = Path().apply {
                moveTo(logoCenterX, logoCenterY)
                cubicTo(
                    logoCenterX - w * 0.08f, logoCenterY - h * 0.04f,
                    logoCenterX - w * 0.14f, logoCenterY - h * 0.03f,
                    logoCenterX - w * 0.16f, logoCenterY + h * 0.02f
                )
                lineTo(logoCenterX - w * 0.16f, logoCenterY + h * 0.10f)
                cubicTo(
                    logoCenterX - w * 0.14f, logoCenterY + h * 0.06f,
                    logoCenterX - w * 0.08f, logoCenterY + h * 0.04f,
                    logoCenterX, logoCenterY + h * 0.08f
                )
                close()
            }
            
            val rightPage = Path().apply {
                moveTo(logoCenterX, logoCenterY)
                cubicTo(
                    logoCenterX + w * 0.08f, logoCenterY - h * 0.04f,
                    logoCenterX + w * 0.14f, logoCenterY - h * 0.03f,
                    logoCenterX + w * 0.16f, logoCenterY + h * 0.02f
                )
                lineTo(logoCenterX + w * 0.16f, logoCenterY + h * 0.10f)
                cubicTo(
                    logoCenterX + w * 0.14f, logoCenterY + h * 0.06f,
                    logoCenterX + w * 0.08f, logoCenterY + h * 0.04f,
                    logoCenterX, logoCenterY + h * 0.08f
                )
                close()
            }
            
            drawPath(leftPage, color = Color(0xFF7C3AED))
            drawPath(rightPage, color = Color(0xFFA855F7))
            
            // Miniature soundwave lines rising
            val barCount = 3
            val barSpacing = w * 0.04f
            val barHeights = floatArrayOf(0.08f, 0.15f, 0.07f)
            val startX = logoCenterX - ((barCount - 1) * barSpacing) / 2f
            
            for (i in 0 until barCount) {
                val x = startX + i * barSpacing
                val height = h * barHeights[i]
                drawLine(
                    color = Color.White,
                    start = androidx.compose.ui.geometry.Offset(x, logoCenterY - height - h * 0.02f),
                    end = androidx.compose.ui.geometry.Offset(x, logoCenterY - h * 0.02f),
                    strokeWidth = w * 0.015f,
                    cap = StrokeCap.Round
                )
            }
        }
        
        // Stars floating around matches references
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.28f))
            drawCircle(Color.White, radius = 3.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.24f))
            drawCircle(Color.White, radius = 1.8.dp.toPx(), center = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.82f))
        }
    }
}

@Composable
fun OnboardingFoldersScreen(
    booksRepository: BookRepository,
    settingsRepository: SettingsRepository,
    onNavigateToLibrary: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FolderViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val secondaryTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    val detailTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    val iconBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val folderItemBgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val audiobooks by booksRepository.getAudiobooks().collectAsStateWithLifecycle(initialValue = emptyList())
    
    // SAF Folder Tree Picker launcher
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                // Take persistable permission to safely survive app restarts
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val docFile = DocumentFile.fromTreeUri(context, uri)
                val folderName = docFile?.name ?: uri.lastPathSegment ?: "Audiobook Folder"
                viewModel.addFolder(uri.toString(), folderName)
                Toast.makeText(context, "Folder Added & Scanning Scheduled!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Timber.e(e, "Failed to persist SAF permission or fetch display name")
                Toast.makeText(context, "Error adding folder standard permission.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Modal management states
    var folderToRename by remember { mutableStateOf<AudiobookFolder?>(null) }
    var folderToRemove by remember { mutableStateOf<AudiobookFolder?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("onboarding_folders_screen")
    ) {
        // 1. Shared swirling background motif
        SwirlingBackground(modifier = Modifier.fillMaxSize())

        // 2. Scrollable content — fills full screen, scrolls under floating bars
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 8.dp,
                bottom = 140.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                // Header folder logo
                item {
                    FolderIntroLogo(
                        modifier = Modifier
                            .size(150.dp)
                            .padding(bottom = 6.dp)
                    )
                }

                // Header Title and Description
                item {
                    Text(
                        text = "Select Your Audiobook Folders",
                        style = MaterialTheme.typography.headlineLarge,
                        color = primaryTextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Audiora scans the folders you choose to find your audiobooks and keep your library up to date.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryTextColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                // Folders list header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SectionHeader(text = "Your Audiobook Folders")

                        // Add Folder custom gradient button
                        Box(
                            modifier = Modifier
                                .clickable(
                                    onClick = { folderLauncher.launch(null) },
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(color = Color.White)
                                )
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                    ),
                                    shape = CircleShape
                                )
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                .testTag("add_folder_btn"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Add Folder",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // If no folders added, display pristine Onboarding Empty state Card
                if (folders.isEmpty()) {
                    item {
                        GlassmorphicCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 20.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp, horizontal = 20.dp)
                                    .testTag("empty_folders_card"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(iconBgColor, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderOpen,
                                        contentDescription = "No Folders",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = "No Audiobook Folders Added",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Add one or more folders that contain your M4B audiobook collection.",
                                    fontSize = 12.sp,
                                    color = secondaryTextColor,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedButton(
                                    onClick = { folderLauncher.launch(null) },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.testTag("empty_add_folder_btn")
                                ) {
                                    Icon(Icons.Rounded.Add, "Add", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Folder", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                } else {
                    // Render folders item list
                    items(folders) { folder ->
                        val folderBookCount = audiobooks.count { it.folderUri == folder.uri }
                        var showDropdownMenu by remember { mutableStateOf(false) }

                        GlassmorphicCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("folder_card_${folder.id}"),
                            cornerRadius = 20.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .background(folderItemBgColor, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Folder,
                                            contentDescription = "Folder",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = folder.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = toDisplayPath(folder.uri),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = detailTextColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Book,
                                                contentDescription = "Books count",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "$folderBookCount books",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }

                                Box {
                                    IconButton(
                                        onClick = { showDropdownMenu = true },
                                        modifier = Modifier.testTag("folder_menu_trigger_${folder.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MoreVert,
                                            contentDescription = "More options",
                                            tint = primaryTextColor.copy(alpha = 0.6f)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showDropdownMenu,
                                        onDismissRequest = { showDropdownMenu = false },
                                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Rescan Folder", color = primaryTextColor) },
                                            leadingIcon = { Icon(Icons.Rounded.Sync, "Rescan", tint = MaterialTheme.colorScheme.primary) },
                                            onClick = {
                                                showDropdownMenu = false
                                                viewModel.rescanFolder(folder.uri)
                                                Toast.makeText(context, "Scanning folder contents...", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Rename Display Name", color = primaryTextColor) },
                                            leadingIcon = { Icon(Icons.Rounded.Edit, "Rename", tint = MaterialTheme.colorScheme.primary) },
                                            onClick = {
                                                showDropdownMenu = false
                                                renameInputText = folder.name
                                                folderToRename = folder
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Remove Folder", color = Color.Red) },
                                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, "Remove", tint = Color.Red) },
                                            onClick = {
                                                showDropdownMenu = false
                                                folderToRemove = folder
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Why add folders? Info Card at bottom
                item {
                    GlassmorphicCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        cornerRadius = 20.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(top = 2.dp)
                            )

                            Column {
                                Text(
                                    text = "Why add folders?",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryTextColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Audiora automatically scans your selected folders for M4B audiobooks and keeps your library synchronized.",
                                    fontSize = 12.sp,
                                    color = secondaryTextColor,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
                
                // Extra breathing spacer
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // Floating Bottom gradient Action Panel (Continue to Library) — transparent, overlays scrolling content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Enabled only when at least 1 folder is configured
                val isContinueEnabled = folders.isNotEmpty()
                val buttonInteractionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(56.dp)
                        .shadow(
                            elevation = if (isContinueEnabled) 8.dp else 0.dp,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            spotColor = MaterialTheme.colorScheme.primary
                        )
                        .background(
                            brush = if (isContinueEnabled) {
                                Brush.horizontalGradient(colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)))
                            } else {
                                Brush.horizontalGradient(colors = listOf(Color(0xFFD1D5DB), Color(0xFFE5E7EB)))
                            },
                            shape = CircleShape
                        )
                        .clip(CircleShape)
                        .clickable(
                            enabled = isContinueEnabled,
                            interactionSource = buttonInteractionSource,
                            indication = if (isContinueEnabled) ripple(color = Color.White) else null,
                            onClick = {
                                coroutineScope.launch {
                                    settingsRepository.setOnboardingCompleted(true)
                                    onNavigateToLibrary()
                                }
                            }
                        )
                        .testTag("continue_to_library_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Continue to Library",
                            color = if (isContinueEnabled) Color.White else Color(0xFF9CA3AF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Forward Action",
                            tint = if (isContinueEnabled) Color.White else Color(0xFF9CA3AF),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom security disclosure
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Privacy Check",
                        tint = primaryTextColor.copy(alpha = 0.4f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Your files stay on your device. We value your privacy.",
                        fontSize = 11.sp,
                        color = primaryTextColor.copy(alpha = 0.4f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 3. Floating top action bar — placed after LazyColumn so it sits on top in z-order
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("onboarding_back_arrow")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = primaryTextColor
                    )
                }

                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            settingsRepository.setOnboardingCompleted(true)
                            onNavigateToLibrary()
                        }
                    },
                    modifier = Modifier.testTag("onboarding_skip_button")
                ) {
                    Text(
                        text = "Skip",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

    // Modal AlertDialog for Renaming Folder
    folderToRename?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = {
                Text(
                    text = "Rename Display Name",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = primaryTextColor
                )
            },
            text = {
                Column {
                    Text(
                        text = "Choose a custom display name for your audiobook directory.",
                        fontSize = 13.sp,
                        color = secondaryTextColor
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        singleLine = true,
                        placeholder = { Text("e.g. My Audiobooks") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rename_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!TextUtils.isEmpty(renameInputText.trim())) {
                            viewModel.renameFolder(folder.id, renameInputText.trim())
                            folderToRename = null
                        } else {
                            Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    // Modal AlertDialog for Folder Removal conformation
    folderToRemove?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToRemove = null },
            title = {
                Text(
                    text = "Remove folder location?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = primaryTextColor
                )
            },
            text = {
                Text(
                    text = "Removing this folder will delete all compiled M4B audiobooks scanned from '${folder.name}' from your Audiora studio library.",
                    fontSize = 13.sp,
                    color = secondaryTextColor
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeFolder(folder.id, folder.uri)
                        folderToRemove = null
                        Toast.makeText(context, "Folder location removed.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Remove", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRemove = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}
