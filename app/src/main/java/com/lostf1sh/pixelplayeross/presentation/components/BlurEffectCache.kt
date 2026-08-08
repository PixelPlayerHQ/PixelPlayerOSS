package com.lostf1sh.pixelplayeross.presentation.components

import android.os.Build
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Caches the (expensive) RenderEffect Java object so a blurred graphicsLayer doesn't allocate a
 * new native blur every animation frame. Callers should quantize the radius (e.g. to 2px steps)
 * before calling [get] so this only rebuilds when the blur visibly crosses a step, instead of on
 * every frame of a continuous animation.
 *
 * `RenderEffect` arrived in API 31 while the app supports 30, so [get] returns null below that.
 * A null `renderEffect` on a graphicsLayer simply draws unblurred, which is the intended fallback
 * — every call site layers the blur over content that already reads correctly without it.
 */
class BlurEffectCache {
    private var lastRadiusPx: Float = Float.NaN
    private var cached: RenderEffect? = null

    fun get(radiusPx: Float): RenderEffect? {
        if (radiusPx <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            lastRadiusPx = 0f
            cached = null
            return null
        }
        if (radiusPx != lastRadiusPx) {
            lastRadiusPx = radiusPx
            cached = AndroidRenderEffect
                .createBlurEffect(radiusPx, radiusPx, AndroidShader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
        return cached
    }
}
