package com.audiora.core.design.glass

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toIntSize

/**
 * Holds a captured GraphicsLayer and its window position.
 * The GraphicsLayer is created internally when the capture modifier is attached.
 */
class BackdropLayer {
    internal var graphicsLayer: GraphicsLayer? = null
    internal var windowPosition: Offset = Offset.Zero
}

@Composable
fun rememberBackdropLayer(): BackdropLayer = remember { BackdropLayer() }

// ---- Capture modifier ----

/**
 * Record this composable's rendered content into [layer] for use as a backdrop.
 * Tracks global window position for offset alignment when drawing elsewhere.
 */
fun Modifier.backdropCapture(layer: BackdropLayer): Modifier =
    this then CaptureBackdropElement(layer)

private class CaptureBackdropElement(
    val layer: BackdropLayer
) : ModifierNodeElement<CaptureBackdropNode>() {

    override fun create() = CaptureBackdropNode(layer)
    override fun update(node: CaptureBackdropNode) { node.layer = layer; node.invalidateDraw() }
    override fun InspectorInfo.inspectableProperties() { name = "backdropCapture" }
    override fun equals(other: Any?) = other is CaptureBackdropElement && other.layer === layer
    override fun hashCode() = System.identityHashCode(layer)
}

private class CaptureBackdropNode(
    var layer: BackdropLayer
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    override fun ContentDrawScope.draw() {
        drawContent()
        val gLayer = layer.graphicsLayer
        if (gLayer != null) {
            val s = size.toIntSize()
            if (s.width > 0 && s.height > 0) {
                val scope = this
                gLayer.record(s) { scope.drawContent() }
            }
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        layer.windowPosition = coordinates.positionInWindow()
        invalidateDraw()
    }

    override fun onAttach() {
        layer.graphicsLayer = requireGraphicsContext().createGraphicsLayer()
    }

    override fun onDetach() {
        layer.graphicsLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer.graphicsLayer = null
    }
}

// ---- Glass backdrop drawing modifier ----

/**
 * Render a liquid-glass backdrop behind this element's content.
 *
 * Drawing order:
 * 1. Backdrop texture (blurred/lensed via RenderEffect GraphicsLayer)
 * 2. Container color overlay
 * 3. Border outline
 * 4. Actual content (nav items)
 *
 * API level degradation:
 * - API 33+: vibrancy + blur + lens refraction + optional chromatic aberration
 * - API 31-32: vibrancy + blur
 * - API < 31: container color + content only (no effects)
 */
fun Modifier.glassBackdrop(
    backdropLayer: BackdropLayer,
    shape: Shape,
    containerColor: Color,
    borderColor: Color = Color.Transparent,
    borderWidthPx: Float = 0f,
    blurRadiusPx: Float = 24f,
    refractionHeightPx: Float = 72f,
    refractionAmountPx: Float = 72f,
    chromaticAberration: Boolean = false
): Modifier = this then GlassBackdropElement(
    backdropLayer = backdropLayer,
    shape = shape,
    containerColor = containerColor,
    borderColor = borderColor,
    borderWidthPx = borderWidthPx,
    blurRadiusPx = blurRadiusPx,
    refractionHeightPx = refractionHeightPx,
    refractionAmountPx = refractionAmountPx,
    chromaticAberration = chromaticAberration
)

private class GlassBackdropElement(
    val backdropLayer: BackdropLayer,
    val shape: Shape,
    val containerColor: Color,
    val borderColor: Color,
    val borderWidthPx: Float,
    val blurRadiusPx: Float,
    val refractionHeightPx: Float,
    val refractionAmountPx: Float,
    val chromaticAberration: Boolean
) : ModifierNodeElement<GlassBackdropNode>() {

    override fun create() = GlassBackdropNode(
        backdropLayer = backdropLayer,
        shape = shape,
        containerColor = containerColor,
        borderColor = borderColor,
        borderWidthPx = borderWidthPx,
        blurRadiusPx = blurRadiusPx,
        refractionHeightPx = refractionHeightPx,
        refractionAmountPx = refractionAmountPx,
        chromaticAberration = chromaticAberration
    )
    override fun update(node: GlassBackdropNode) {
        node.backdropLayer = backdropLayer; node.shape = shape
        node.containerColor = containerColor; node.borderColor = borderColor
        node.borderWidthPx = borderWidthPx
        node.blurRadiusPx = blurRadiusPx; node.refractionHeightPx = refractionHeightPx
        node.refractionAmountPx = refractionAmountPx; node.chromaticAberration = chromaticAberration
        node.needsEffectRebuild = true; node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() { name = "glassBackdrop" }
    override fun equals(other: Any?) = other is GlassBackdropElement && other.backdropLayer === backdropLayer
    override fun hashCode() = System.identityHashCode(backdropLayer)
}

private class GlassBackdropNode(
    var backdropLayer: BackdropLayer,
    var shape: Shape,
    var containerColor: Color,
    var borderColor: Color,
    var borderWidthPx: Float,
    var blurRadiusPx: Float,
    var refractionHeightPx: Float,
    var refractionAmountPx: Float,
    var chromaticAberration: Boolean
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var effectLayer: GraphicsLayer? = null
    private val effectScope = BackdropEffectScopeImpl()
    var needsEffectRebuild = true
    private var nodeWindowPosition: Offset = Offset.Zero

    override fun ContentDrawScope.draw() {
        val eLayer = effectLayer

        // 1. Backdrop with effects (API 31+)
        if (eLayer != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (effectScope.update(this) || needsEffectRebuild) {
                rebuildEffects(); needsEffectRebuild = false
            }
            eLayer.renderEffect = effectScope.platformRenderEffect?.asComposeRenderEffect()

            val sourceGL = backdropLayer.graphicsLayer
            if (sourceGL != null) {
                val sourceSize = sourceGL.size
                if (sourceSize.width > 0 && sourceSize.height > 0) {
                    val dx = nodeWindowPosition.x - backdropLayer.windowPosition.x
                    val dy = nodeWindowPosition.y - backdropLayer.windowPosition.y
                    eLayer.record(size.toIntSize()) {
                        drawContext.canvas.translate(-dx, -dy)
                        drawLayer(sourceGL)
                    }
                    eLayer.topLeft = IntOffset.Zero
                    drawLayer(eLayer)
                }
            }
        }

        // 2. Container color overlay — narrow edge fades, bottom stays opaque to mask refraction
        drawRect(
            brush = Brush.verticalGradient(
                0f to containerColor.copy(alpha = 0f),
                0.03f to containerColor,
                0.95f to containerColor,
                0.99f to containerColor.copy(alpha = 0.55f),
                1f to containerColor.copy(alpha = 0.85f)
            ),
            size = size
        )

        // 3. Border
        if (borderWidthPx > 0f && borderColor.alpha > 0f) {
            val outline = shape.createOutline(size, layoutDirection, this)
            drawOutline(
                outline = outline,
                color = borderColor,
                style = Stroke(width = borderWidthPx)
            )
        }

        // 4. Actual content (nav items)
        drawContent()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        nodeWindowPosition = coordinates.positionInWindow()
        invalidateDraw()
    }

    private fun rebuildEffects() {
        effectScope.applyEffects {
            // createChainEffect(new, prev): prev(outer) runs first on source, then new(inner).
            // So call order is pipeline order: blur → lens → vibrancy
            blur(blurRadiusPx)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeightPx = refractionHeightPx,
                    refractionAmountPx = refractionAmountPx,
                    chromaticAberration = chromaticAberration
                )
            }
            vibrancy()
        }
    }

    override fun onAttach() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            effectLayer = requireGraphicsContext().createGraphicsLayer()
        }
    }

    override fun onDetach() {
        effectLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        effectLayer = null
        effectScope.reset()
    }
}
