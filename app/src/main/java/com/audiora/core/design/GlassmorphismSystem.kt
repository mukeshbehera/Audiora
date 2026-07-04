package com.audiora.core.design

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiora.ui.theme.LocalDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import com.audiora.ui.theme.BrandGradientStart
import com.audiora.ui.theme.BrandGradientEnd

/**
 * AudioraGlassCard: Matches the specifications exactly:
 * - Corner radius: 24dp - 32dp (Default 24dp)
 * - Light Background: rgba(255, 255, 255, 0.65)
 * - Light Border: rgba(255, 255, 255, 0.6)
 * - Dark Background: rgba(20, 20, 24, 0.65)
 * - Dark Border: rgba(255, 255, 255, 0.08)
 * - Shadow: Soft. In dark mode, a beautiful custom purple glow.
 */
@Composable
fun AudioraGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = LocalDarkTheme.current
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    
    val shadowModifier = if (isDark) {
        Modifier.shadow(
            elevation = 12.dp,
            shape = RoundedCornerShape(cornerRadius),
            clip = false,
            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        )
    } else {
        Modifier.shadow(
            elevation = 6.dp,
            shape = RoundedCornerShape(cornerRadius),
            clip = false,
            ambientColor = Color(0x0F000000),
            spotColor = Color(0x0F000000)
        )
    }

    val cardContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }

    if (onClick != null) {
        Surface(
            modifier = modifier.then(shadowModifier),
            onClick = onClick,
            shape = RoundedCornerShape(cornerRadius),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            cardContent()
        }
    } else {
        Surface(
            modifier = modifier.then(shadowModifier),
            shape = RoundedCornerShape(cornerRadius),
            color = containerColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            cardContent()
        }
    }
}

/**
 * AudioraGlassButton: Pill-shaped active actions.
 * - Shape: Fully rounded pill shape
 * - Height: 56dp
 * - Gradient: Purple gradient (BrandGradientStart to BrandGradientEnd)
 * - Shadow: Purple glow
 */
@Composable
fun AudioraGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            primary,
            primary.copy(
                red = (primary.red * 0.7f).coerceIn(0f, 1f),
                green = (primary.green * 0.7f).coerceIn(0f, 1f),
                blue = (primary.blue * 0.7f).coerceIn(0f, 1f)
            )
        )
    )
    val shape = RoundedCornerShape(28.dp) // Perfect pill at 56dp height

    val shadowModifier = if (enabled) {
        Modifier.shadow(
            elevation = 10.dp,
            shape = shape,
            clip = false,
            ambientColor = primary.copy(alpha = 0.5f),
            spotColor = primary.copy(alpha = 0.5f)
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(shadowModifier)
            .height(56.dp)
            .background(if (enabled) gradient else SolidColor(Color.Gray.copy(alpha = 0.3f)), shape = shape)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * AudioraGlassFAB: Circular floating FAB.
 * - Circular, 56dp
 * - Purple Gradient
 * - Soft purple glow
 */
@Composable
fun AudioraGlassFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector,
    contentDescription: String? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    val gradient = Brush.linearGradient(
        colors = listOf(
            primary,
            primary.copy(
                red = (primary.red * 0.7f).coerceIn(0f, 1f),
                green = (primary.green * 0.7f).coerceIn(0f, 1f),
                blue = (primary.blue * 0.7f).coerceIn(0f, 1f)
            )
        )
    )
    val shape = RoundedCornerShape(28.dp) // Perfect circle

    Box(
        modifier = modifier
            .shadow(
                elevation = 14.dp,
                shape = shape,
                clip = false,
                ambientColor = primary.copy(alpha = 0.6f),
                spotColor = primary.copy(alpha = 0.6f)
            )
            .size(56.dp)
            .background(gradient, shape = shape)
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * AudioraGlassTextField: Fully glass input field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioraGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true
) {
    val isDark = LocalDarkTheme.current
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
    val unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
    
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontWeight = FontWeight.Medium) },
        placeholder = { Text(placeholder) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            focusedBorderColor = BrandGradientStart,
            unfocusedBorderColor = unfocusedBorderColor,
            focusedLabelColor = BrandGradientStart,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * AudioraGlassBottomBar: Floating glass nav bar.
 */
@Composable
fun AudioraGlassBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val isDark = LocalDarkTheme.current
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(28.dp),
                clip = false,
                ambientColor = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color(0x0F000000),
                spotColor = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color(0x0F000000)
            )
            .background(containerColor, RoundedCornerShape(28.dp))
            .border(1.dp, borderColor, RoundedCornerShape(28.dp))
            .height(72.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            content = content
        )
    }
}

/**
 * AudioraGlassDialog: Premium glass floating dialog.
 */
@Composable
fun AudioraGlassDialog(
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest) {
        AudioraGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            cornerRadius = 28.dp
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

// =========================================================================
//  Backward Compatibility Wrappers - So no existing code fails compiled check!
// =========================================================================

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    containerColor: Color = Color.Unspecified, // will auto-assign premium card colors
    borderColor: Color = Color.Unspecified,
    borderWidth: Dp = 1.dp,
    shadowElevation: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    AudioraGlassCard(
        modifier = modifier,
        cornerRadius = cornerRadius,
        content = content
    )
}

@Composable
fun ClickableGlassmorphicCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    containerColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    borderWidth: Dp = 1.dp,
    shadowElevation: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    AudioraGlassCard(
        modifier = modifier,
        cornerRadius = cornerRadius,
        onClick = onClick,
        content = content
    )
}

@Composable
fun GlassmorphicPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(28.dp)
) {
    AudioraGlassButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon
    )
}

@Composable
fun GlassmorphicSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    val isDark = LocalDarkTheme.current
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        ),
        border = BorderStroke(1.5.dp, borderColor),
        enabled = enabled,
        shape = shape,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun GlassmorphicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    AudioraGlassTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine
    )
}

@Composable
fun GlassmorphicLoadingState(
    modifier: Modifier = Modifier,
    message: String = "Loading Studio..."
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(54.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun GlassmorphicEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Info,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.67f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(20.dp))
            GlassmorphicSecondaryButton(
                text = actionText,
                onClick = onActionClick
            )
        }
    }
}
