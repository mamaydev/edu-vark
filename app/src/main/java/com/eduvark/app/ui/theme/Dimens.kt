package com.eduvark.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.eduvark.app.ui.theme.dimenSp

/**
 * Resource accessors for layout values.
 *
 * All spacing, sizes and radii live in `res/values/dimens.xml`; screens use
 * [dimen] instead of writing `12.dp` inline. Text sizes live there as well and
 * are converted back to `sp` by [dimenSp] (the resource is stored in sp, so the
 * conversion only divides the physical size by the font scale).
 */
@Composable
@ReadOnlyComposable
fun dimen(id: Int): Dp = dimensionResource(id)

/** Reads a dimens entry declared in `sp` and returns it as a [TextUnit]. */
@Composable
@ReadOnlyComposable
fun dimenSp(id: Int): TextUnit {
    val fontScale = LocalDensity.current.fontScale
    return (dimensionResource(id).value / fontScale).sp
}

/**
 * Resolves resource dimens outside a composition. Needed by immutable, static
 * design artefacts (e.g. the local [ImageVector] icon set) that are built once
 * and cannot call the composable [dimen]. Installed by EduVarkApplication.
 */
object EduResources {
    private lateinit var appContext: android.content.Context

    fun install(context: android.content.Context) {
        appContext = context.applicationContext
    }

    fun dp(id: Int): Dp {
        val res = appContext.resources
        return Dp(res.getDimension(id) / res.displayMetrics.density)
    }
}
