package app.lawnchair.lunchheir

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Windows Phone 7 "Metro"-inspired kinetic typography primitives (Compose).
 *
 * Two signature WP7 motions, reusable on any text:
 *  - [Modifier.wp7Turnstile] — the page-entrance "turnstile": each item swings in around its left
 *    edge and fades, staggered by index. Fast-out / gentle-settle ([Wp7Decelerate]).
 *  - [Modifier.wp7Tilt] — the WP7 "tilt effect": on press the element tilts in 3D toward the finger
 *    and springs back.
 *
 * [KineticWord] composes both into a single big Metro word. Used by the Hax surfaces ([HaxSystem]).
 * The AzNavRail-rendered Hax menu gets its kinetic type from the library itself; these cover the
 * surfaces AzNavRail doesn't.
 */

/** WP7 decelerate: fast out, gentle settle — the snappy turnstile feel. */
val Wp7Decelerate = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)

/**
 * WP7 turnstile entrance: swing in around the left edge (rotationY) and fade, staggered by [index].
 * Runs once when it enters composition (e.g. each time a sheet/menu opens).
 */
@Composable
fun Modifier.wp7Turnstile(index: Int, startAngle: Float = 70f, staggerMs: Int = 50): Modifier {
    val angle = remember { Animatable(startAngle) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { angle.animateTo(0f, tween(360, index * staggerMs, Wp7Decelerate)) }
        launch { alpha.animateTo(1f, tween(260, index * staggerMs, Wp7Decelerate)) }
    }
    return graphicsLayer {
        rotationY = angle.value
        this.alpha = alpha.value
        transformOrigin = TransformOrigin(0f, 0.5f) // hinge on the left edge — the "turnstile"
        cameraDistance = 14f * density
    }
}

/**
 * WP7 tilt-on-press: the element tilts in 3D toward the touch point and springs back on release.
 * [onClick] fires when the press is released inside the element.
 */
fun Modifier.wp7Tilt(maxTiltDegrees: Float = 10f, onClick: () -> Unit): Modifier = composed {
    val rx = remember { Animatable(0f) }
    val ry = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            val w = if (size.width <= 0) 1f else size.width.toFloat()
            val h = if (size.height <= 0) 1f else size.height.toFloat()
            val nx = (down.position.x / w) * 2f - 1f
            val ny = (down.position.y / h) * 2f - 1f
            scope.launch { ry.animateTo(nx * maxTiltDegrees, tween(110)) }
            scope.launch { rx.animateTo(-ny * maxTiltDegrees, tween(110)) }
            scope.launch { scale.animateTo(0.96f, tween(110)) }
            val up = waitForUpOrCancellation()
            val settle = spring<Float>(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            )
            scope.launch { ry.animateTo(0f, settle) }
            scope.launch { rx.animateTo(0f, settle) }
            scope.launch { scale.animateTo(1f, settle) }
            if (up != null) onClick()
        }
    }.graphicsLayer {
        rotationX = rx.value
        rotationY = ry.value
        scaleX = scale.value
        scaleY = scale.value
        cameraDistance = 12f * density
    }
}

/** A single big Metro word that turnstiles in (staggered by [index]) and tilts on press. */
@Composable
fun KineticWord(
    text: String,
    index: Int,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 32.sp,
    fontWeight: FontWeight = FontWeight.Light,
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        letterSpacing = 1.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .wp7Turnstile(index = index)
            .wp7Tilt(onClick = onClick),
    )
}
