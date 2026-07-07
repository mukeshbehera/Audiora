package com.audiora.core.design.glass

import android.os.Build
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asAndroidRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Scope for building a RenderEffect chain (blur → lens → vibrancy).
 * Ported from AndroidLiquidGlass's BackdropEffectScope.
 */
class BackdropEffectScopeImpl : Density {

    override var density: Float = 1f
    override var fontScale: Float = 1f

    var size: Size = Size.Unspecified
    var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    var padding: Float = 0f

    /**
     * The compiled Compose RenderEffect chain.
     * Set by [apply] then read to assign to a GraphicsLayer.
     */
    var renderEffect: RenderEffect? = null

    private val runtimeShaders = mutableMapOf<String, android.graphics.RuntimeShader>()

    /**
     * Sync density/size/layoutDirection from a DrawScope.
     * Returns true if any value changed (caller should rebuild effects).
     */
    fun update(scope: DrawScope): Boolean {
        val changed = scope.density != density ||
                scope.fontScale != fontScale ||
                scope.size != size ||
                scope.layoutDirection != layoutDirection
        if (changed) {
            density = scope.density
            fontScale = scope.fontScale
            size = scope.size
            layoutDirection = scope.layoutDirection
        }
        return changed
    }

    /**
     * Reset and rebuild the effect chain from scratch.
     */
    fun apply(effects: BackdropEffectScope.() -> Unit) {
        padding = 0f
        renderEffect = null
        effects()
    }

    /**
     * Full reset (typically on detach).
     */
    fun reset() {
        density = 1f
        fontScale = 1f
        size = Size.Unspecified
        layoutDirection = LayoutDirection.Ltr
        padding = 0f
        renderEffect = null
        runtimeShaders.clear()
    }

    // ---- Internal effect chain building ----

    /**
     * Chain an Android platform [android.graphics.RenderEffect] onto the pipeline.
     * API 31+ guard is applied internally.
     */
    private fun addEffect(effect: android.graphics.RenderEffect) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val current = renderEffect?.asAndroidRenderEffect()
        renderEffect = if (current != null) {
            android.graphics.RenderEffect.createChainEffect(effect, current).asComposeRenderEffect()
        } else {
            effect.asComposeRenderEffect()
        }
    }

    // ---- Public effect functions ----

    /**
     * Standard frosted-glass blur.
     * API 31+. No-op below API 31.
     */
    fun blur(radiusPx: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || radiusPx <= 0f) return
        addEffect(
            android.graphics.RenderEffect.createBlurEffect(
                radiusPx, radiusPx, android.graphics.Shader.TileMode.DECAL
            )
        )
    }

    /**
     * Liquid-glass lens refraction via AGSL runtime shader.
     * API 33+. No-op below API 33.
     *
     * @param refractionHeightPx Edge region height in px (how far from edge the refraction extends)
     * @param refractionAmountPx Max displacement in px of refracted content
     * @param depthEffect Apply depth-based normal perturbation
     * @param chromaticAberration Enable chromatic dispersion (7 color samples)
     */
    fun lens(
        refractionHeightPx: Float = 72f,
        refractionAmountPx: Float = 72f,
        depthEffect: Boolean = true,
        chromaticAberration: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val shaderString = if (chromaticAberration) {
            RoundedRectRefractionWithDispersionShaderString
        } else {
            RoundedRectRefractionShaderString
        }

        val shader = runtimeShaders.getOrPut("lens") {
            android.graphics.RuntimeShader(shaderString)
        }

        // Position
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("offset", 0f, 0f)

        // Corner radii (uniform for all 4 corners when using RoundedCornerShape)
        val maxR = size.minDimension / 2f
        shader.setFloatUniform("cornerRadii", floatArrayOf(maxR, maxR, maxR, maxR))

        // Refraction params
        shader.setFloatUniform("refractionHeight", refractionHeightPx)
        shader.setFloatUniform("refractionAmount", refractionAmountPx)
        shader.setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)

        if (chromaticAberration) {
            shader.setFloatUniform("chromaticAberration", 1f)
        }

        addEffect(
            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "content")
        )
    }

    /**
     * Subtle color boost — slightly increased saturation + contrast.
     * API 31+. No-op below API 31.
     */
    fun vibrancy() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val cm = android.graphics.ColorMatrix().apply {
            setSaturation(1.15f)
            setScale(1.05f, 1.05f, 1.05f, 1f)
        }
        addEffect(
            android.graphics.RenderEffect.createColorFilterEffect(
                android.graphics.ColorMatrixColorFilter(cm)
            )
        )
    }
}
