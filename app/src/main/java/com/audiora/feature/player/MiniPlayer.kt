package com.audiora.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PauseCircleFilled
import androidx.compose.material.icons.rounded.PlayCircleFilled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiora.domain.model.Audiobook
import com.audiora.ui.theme.PrimaryPurple
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun MiniPlayer(
    book: Audiobook,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onTogglePlayPause: () -> Unit,
    onDismiss: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 120.dp.toPx() }
    val scope = rememberCoroutineScope()
    val offsetAnimatable = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetAnimatable.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (abs(offsetAnimatable.value) >= swipeThreshold) {
                                    onDismiss()
                                } else {
                                    offsetAnimatable.animateTo(0f)
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetAnimatable.animateTo(0f) }
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            scope.launch {
                                offsetAnimatable.snapTo(
                                    (offsetAnimatable.value + amount)
                                        .coerceIn(-swipeThreshold * 2, swipeThreshold * 2)
                                )
                            }
                        }
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Cover art thumbnail
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .padding(start = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrimaryPurple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = PrimaryPurple,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Title and author
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToPlayer() }
            ) {
                Text(
                    text = book.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = book.author,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // Play/Pause button
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = PrimaryPurple,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))
        }

        // Thin progress bar at bottom
        val progress = if (duration > 0) {
            (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.BottomCenter),
            color = PrimaryPurple,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
        )
    }
}
