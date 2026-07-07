package com.audiora.core.design.glass

import android.os.Build
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Scope for building a RenderEffect chain (blur → lens → vibrancy).
 * Stores the Android platform [android.graphics.RenderEffect] directly
 * to avoid Compose/Android conversion issues.
 */
class BackdropEffectScopeImpl : Density {

    override var density: Float = 1f
    override var fontScale: Float = 1f

    var size: Size = Size.Unspecified
    var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    var padding: Float = 0f

    /**
     * The compiled Android platform RenderEffect chain.
     * Set by [applyEffects] then read to assign to a GraphicsLayer.renderEffect.
     */
    var platformRenderEffect: android.graphics.RenderEffect? = null

    private val runtimeShaders = mutableMapOf<String, android.graphics.RuntimeShader>()

    /**
     * Sync density/size/layoutDirection from a DrawScope.
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
     * Reset and rebuild the effect chain.
     */
    fun applyEffects(block: BackdropEffectScopeImpl.() -> Unit) {
        padding = 0f
        platformRenderEffect = null
        block()
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
        platformRenderEffect = null
        runtimeShaders.clear()
    }

    // ---- Internal ----

    private fun addEffect(effect: android.graphics.RenderEffect) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        platformRenderEffect = if (platformRenderEffect != null) {
            android.graphics.RenderEffect.createChainEffect(effect, platformRenderEffect!!)
        } else {
            effect
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

        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("offset", 0f, 0f)
        val maxR = size.minDimension / 2f
        shader.setFloatUniform("cornerRadii", floatArrayOf(maxR, maxR, maxR, maxR))
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
     * Subtle color boost — increased saturation + contrast.
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
