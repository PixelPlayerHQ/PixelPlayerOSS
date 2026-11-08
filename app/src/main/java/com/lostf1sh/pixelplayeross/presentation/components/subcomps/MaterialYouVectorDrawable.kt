package com.lostf1sh.pixelplayeross.presentation.components.subcomps

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.ui.theme.LocalPixelPlayerDarkTheme

/**
 * Inflates an XML vector that depends on Android theme attrs using the app's
 * actual light/dark mode, not that of the underlying Activity.
 */
@Composable
fun MaterialYouVectorDrawable(
    modifier: Modifier = Modifier,
    @DrawableRes drawableResId: Int,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val isDarkTheme = LocalPixelPlayerDarkTheme.current
    val themedContext = remember(context, isDarkTheme) {
        context.createVectorThemedContext(isDarkTheme = isDarkTheme)
    }
    val drawable = remember(themedContext, drawableResId) {
        AppCompatResources.getDrawable(themedContext, drawableResId)?.mutate()
    }

    drawable?.let {
        Image(
            painter = rememberDrawablePainter(it),
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

private fun Context.createVectorThemedContext(isDarkTheme: Boolean): Context {
    val themedConfiguration = Configuration(resources.configuration).apply {
        uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (isDarkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
    }
    val modeContext = createConfigurationContext(themedConfiguration)
    return ContextThemeWrapper(modeContext, R.style.Theme_PixelPlayer)
}
