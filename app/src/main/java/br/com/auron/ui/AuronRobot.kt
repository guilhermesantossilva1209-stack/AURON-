package br.com.auron.ui

import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.auron.ui.theme.AuronColors
import br.com.auron.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

internal const val AURON_INTRO_DURATION_MS = 5200L

/** System accessibility animation setting, refreshed when returning to the app. */
@Composable
internal fun rememberSystemAnimationsEnabled(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by remember { mutableStateOf(runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(false)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(false)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return enabled
}

/** Original articulated vector mascot. Holograms are decoration, not invented system metrics. */
@Composable
internal fun AuronRobot(
    modifier: Modifier = Modifier,
    motionEnabled: Boolean = true,
    greetingProgress: (() -> Float)? = null,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val animate = rememberSystemAnimationsEnabled() && motionEnabled && resumed
    val phase: State<Float> = if (animate) {
        val transition = rememberInfiniteTransition(label = "robot-articulation")
        transition.animateFloat(
            0f, (2 * PI).toFloat(),
            infiniteRepeatable(tween(6400, easing = LinearEasing)),
            label = "breathing-and-holograms",
        )
    } else rememberUpdatedState(0f)
    val artwork = remember { RobotArtwork() }
    val gold = AuronColors.Gold
    // Read animation state inside drawing: layout and text do not recompose every frame.
    Canvas(modifier.semantics {
        contentDescription = "AURON: robô amigável de corpo inteiro, preto e dourado, com painéis holográficos decorativos"
    }) {
        val cycle = if (animate) phase.value else 0f
        val breath = if (animate) sin(cycle) else 0f
        val progress = if (animate) greetingProgress?.invoke()?.coerceIn(0f, 1f) else null
        val raisedArm = if (progress == null) 0f else smooth((progress - 0.08f) / 0.22f) * (1f - smooth((progress - 0.72f) / 0.23f))
        val wave = if (progress == null) 0f else sin(((progress - 0.3f) * PI * 9).toFloat()) * raisedArm
        val nod = if (progress == null) 0f else sin((progress * PI * 2).toFloat()) * 2f
        val panelEntry = progress?.let { smooth((it - 0.16f) / 0.28f) } ?: 1f
        val glow = 0.86f + breath * 0.12f
        val unit = minOf(size.width / 140f, size.height / 154f)
        withTransform({
            translate((size.width - unit * 140f) / 2, (size.height - unit * 154f) / 2)
            scale(unit, unit, Offset.Zero)
            translate(20f, 7f)
        }) {
            drawCircle(artwork.aura, 62f, Offset(50f, 65f), alpha = glow)
            drawOval(gold.copy(alpha = 0.1f), Offset(17f, 129f), Size(66f, 8f))
            drawOval(gold.copy(alpha = 0.48f), Offset(23f, 130f), Size(54f, 5f), style = Stroke(0.5f))
            drawArc(gold.copy(alpha = 0.22f), cycle * 12f, 105f, false, Offset(10f, 126f), Size(80f, 12f), style = Stroke(0.5f))
            hologram(artwork, gold, -18f - (1f - panelEntry) * 6f, 24f + breath, panelEntry, cycle)
            hologram(artwork, gold, 87f + (1f - panelEntry) * 6f, 70f - breath, panelEntry, cycle + 1.2f)
            // A few anchored circuit traces frame the silhouette without competing with controls.
            drawPath(artwork.leftCircuit, gold.copy(alpha = 0.24f * panelEntry), style = Stroke(0.55f))
            drawPath(artwork.rightCircuit, gold.copy(alpha = 0.24f * panelEntry), style = Stroke(0.55f))
            drawCircle(gold.copy(alpha = 0.65f * panelEntry), 1.2f, Offset(-10f, 100f))
            drawCircle(gold.copy(alpha = 0.65f * panelEntry), 1.2f, Offset(108f, 55f))

            // Stable feet and legs. Only the upper body breathes, so the robot stays grounded.
            plate(artwork.leftThigh, artwork, gold)
            plate(artwork.rightThigh, artwork, gold)
            drawRoundRect(artwork.joint, Offset(34f, 106f), Size(12f, 6f), CornerRadius(1.6f))
            drawRoundRect(artwork.joint, Offset(54f, 106f), Size(12f, 6f), CornerRadius(1.6f))
            plate(artwork.leftShin, artwork, gold)
            plate(artwork.rightShin, artwork, gold)
            plate(artwork.leftBoot, artwork, gold)
            plate(artwork.rightBoot, artwork, gold)
            drawLine(gold.copy(alpha = glow), Offset(37f, 115f), Offset(36f, 122f), 1f, StrokeCap.Round)
            drawLine(gold.copy(alpha = glow), Offset(63f, 115f), Offset(64f, 122f), 1f, StrokeCap.Round)

            withTransform({
                rotate(breath * 0.7f, Offset(50f, 88f))
                scale(1f, 1f + breath * 0.009f, Offset(50f, 88f))
            }) {
                drawRoundRect(artwork.joint, Offset(44f, 31f), Size(12f, 9f), CornerRadius(2f))
                withTransform({ rotate(breath * 1.7f, Offset(27f, 43f)) }) {
                    plate(artwork.leftShoulder, artwork, gold)
                    plate(artwork.leftUpperArm, artwork, gold)
                    drawCircle(artwork.joint, 4f, Offset(23f, 67f))
                    plate(artwork.leftForearm, artwork, gold)
                    drawRoundRect(artwork.joint, Offset(17f, 84f), Size(9f, 9f), CornerRadius(2f))
                    drawLine(gold, Offset(19f, 73f), Offset(19f, 80f), 1.2f, StrokeCap.Round)
                }
                // Shoulder, elbow and wrist have separate pivots: an actual greeting, not an image pulse.
                withTransform({ rotate(-36f * raisedArm - breath, Offset(73f, 43f)) }) {
                    plate(artwork.rightShoulder, artwork, gold)
                    plate(artwork.rightUpperArm, artwork, gold)
                    drawCircle(artwork.joint, 4f, Offset(77f, 67f))
                    drawCircle(gold.copy(alpha = 0.7f), 2.1f, Offset(77f, 67f), style = Stroke(0.6f))
                    withTransform({ rotate(-112f * raisedArm + wave * 9f, Offset(77f, 67f)) }) {
                        plate(artwork.rightForearm, artwork, gold)
                        drawLine(gold, Offset(81f, 73f), Offset(81f, 80f), 1.2f, StrokeCap.Round)
                        withTransform({ rotate(wave * 7f, Offset(78.5f, 84f)) }) {
                            drawRoundRect(artwork.joint, Offset(74f, 84f), Size(9f, 9f), CornerRadius(2f))
                            drawRoundRect(gold.copy(alpha = 0.7f), Offset(74f, 84f), Size(9f, 9f), CornerRadius(2f), style = Stroke(0.55f))
                            for (finger in 0..2) {
                                val x = 76f + finger * 2.3f
                                drawLine(gold.copy(alpha = 0.4f), Offset(x, 89f), Offset(x, 92f), 0.4f)
                            }
                        }
                    }
                }
                plate(artwork.chest, artwork, gold)
                plate(artwork.abdomen, artwork, gold)
                plate(artwork.pelvis, artwork, gold)
                drawLine(gold.copy(alpha = 0.65f), Offset(38f, 44f), Offset(45f, 47f), 0.8f)
                drawLine(gold.copy(alpha = 0.65f), Offset(55f, 47f), Offset(62f, 44f), 0.8f)
                drawPath(artwork.emblem, gold.copy(alpha = glow), style = Stroke(1.5f))
                drawCircle(AuronColors.GoldBright.copy(alpha = glow), 2f, Offset(50f, 53f))
                drawLine(gold.copy(alpha = 0.5f), Offset(43f, 75f), Offset(57f, 75f), 0.8f)

                withTransform({ rotate(breath * 1.5f + nod, Offset(50f, 32f)) }) {
                    plate(artwork.helmet, artwork, gold)
                    drawRoundRect(Color(0xFF04060A), Offset(40f, 16f), Size(20f, 8f), CornerRadius(3f))
                    drawLine(gold.copy(alpha = glow), Offset(43f, 20.5f), Offset(48f, 20f), 1.6f, StrokeCap.Round)
                    drawLine(gold.copy(alpha = glow), Offset(52f, 20f), Offset(57f, 20.5f), 1.6f, StrokeCap.Round)
                    // A subtle upward mouth curve softens the original armored silhouette.
                    drawPath(artwork.smile, gold.copy(alpha = 0.65f), style = Stroke(0.8f, cap = StrokeCap.Round))
                    drawLine(gold, Offset(36f, 14f), Offset(36f, 23f), 1.1f)
                    drawLine(gold, Offset(64f, 14f), Offset(64f, 23f), 1.1f)
                }
            }
        }
    }
}

private fun smooth(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

private fun DrawScope.plate(path: Path, artwork: RobotArtwork, gold: Color) {
    drawPath(path, artwork.metal)
    drawPath(path, gold.copy(alpha = 0.78f), style = Stroke(0.65f))
}

private fun DrawScope.hologram(artwork: RobotArtwork, gold: Color, x: Float, y: Float, visibility: Float, phase: Float) {
    withTransform({ translate(x, y) }) {
        drawRoundRect(gold.copy(alpha = 0.045f * visibility), Offset.Zero, Size(30f, 26f), CornerRadius(2f))
        drawRoundRect(gold.copy(alpha = 0.45f * visibility), Offset.Zero, Size(30f, 26f), CornerRadius(2f), style = Stroke(0.45f))
        drawLine(gold.copy(alpha = 0.75f * visibility), Offset(4f, 4f), Offset(17f, 4f), 0.7f)
        drawCircle(gold.copy(alpha = 0.7f * visibility), 0.8f, Offset(25f, 4f))
        drawLine(gold.copy(alpha = 0.2f * visibility), Offset(4f, 8f), Offset(26f, 8f), 0.4f)
        drawPath(artwork.chart, gold.copy(alpha = 0.55f * visibility), style = Stroke(0.65f, cap = StrokeCap.Round))
        val sweep = 4f + (sin(phase) + 1f) * 10f
        drawLine(gold.copy(alpha = 0.16f * visibility), Offset(sweep, 10f), Offset(sweep, 22f), 0.6f)
        drawLine(gold.copy(alpha = 0.2f * visibility), Offset(4f, 23f), Offset(26f, 23f), 0.4f)
    }
}

/** Paths and brushes are retained; animation only changes transforms and a few scalar values. */
private class RobotArtwork {
    val joint = Color(0xFF323740)
    val metal = Brush.linearGradient(
        listOf(Color(0xFF353A43), Color(0xFF0D1017), Color(0xFF242A34)),
        start = Offset(15f, 20f), end = Offset(85f, 120f),
    )
    val aura = Brush.radialGradient(
        listOf(AuronColors.Gold.copy(alpha = 0.11f), Color.Transparent),
        center = Offset(50f, 65f), radius = 62f,
    )
    val leftShoulder = polygon(23f, 39f, 33f, 35f, 38f, 43f, 32f, 52f, 22f, 48f)
    val rightShoulder = polygon(67f, 35f, 77f, 39f, 78f, 48f, 68f, 52f, 62f, 43f)
    val leftUpperArm = polygon(22f, 48f, 31f, 51f, 28f, 67f, 19f, 65f)
    val rightUpperArm = polygon(69f, 51f, 78f, 48f, 81f, 65f, 72f, 67f)
    val leftForearm = polygon(18f, 69f, 28f, 70f, 26f, 85f, 17f, 83f)
    val rightForearm = polygon(72f, 70f, 82f, 69f, 83f, 83f, 74f, 85f)
    val chest = polygon(35f, 37f, 65f, 37f, 69f, 47f, 63f, 65f, 50f, 70f, 37f, 65f, 31f, 47f)
    val abdomen = polygon(38f, 67f, 50f, 72f, 62f, 67f, 60f, 78f, 40f, 78f)
    val pelvis = polygon(39f, 80f, 61f, 80f, 64f, 88f, 56f, 94f, 50f, 89f, 44f, 94f, 36f, 88f)
    val leftThigh = polygon(37f, 91f, 47f, 94f, 46f, 108f, 34f, 108f)
    val rightThigh = polygon(53f, 94f, 63f, 91f, 66f, 108f, 54f, 108f)
    val leftShin = polygon(34f, 112f, 46f, 112f, 44f, 126f, 33f, 126f)
    val rightShin = polygon(54f, 112f, 66f, 112f, 67f, 126f, 56f, 126f)
    val leftBoot = polygon(33f, 126f, 44f, 126f, 45f, 131f, 28f, 131f, 28f, 129f)
    val rightBoot = polygon(56f, 126f, 67f, 126f, 72f, 129f, 72f, 131f, 55f, 131f)
    val helmet = polygon(38f, 11f, 44f, 7f, 56f, 7f, 62f, 11f, 62f, 26f, 56f, 33f, 44f, 33f, 38f, 26f)
    val emblem = polygon(50f, 45f, 57f, 53f, 50f, 61f, 43f, 53f)
    val smile = Path().apply { moveTo(46.5f, 27.5f); quadraticBezierTo(50f, 30f, 53.5f, 27.5f) }
    val chart = Path().apply { moveTo(4f, 20f); lineTo(9f, 15f); lineTo(15f, 18f); lineTo(21f, 12f); lineTo(26f, 14f) }
    val leftCircuit = Path().apply { moveTo(-10f, 56f); lineTo(-10f, 84f); lineTo(2f, 92f); lineTo(2f, 100f); lineTo(-10f, 100f) }
    val rightCircuit = Path().apply { moveTo(88f, 15f); lineTo(108f, 15f); lineTo(108f, 55f) }
}

private fun polygon(vararg xy: Float) = Path().apply {
    moveTo(xy[0], xy[1])
    var index = 2
    while (index < xy.size) {
        lineTo(xy[index], xy[index + 1])
        index += 2
    }
    close()
}

@Composable
internal fun AuronIntro(deadlineElapsedMs: Long? = null, onSkip: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var reference by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(context) {
        reference = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeResource(context.resources, R.drawable.auron_intro_reference,
                    BitmapFactory.Options().apply { inScaled = false })?.asImageBitmap()
            }.getOrNull()
        }
    }
    val camera = remember { Animatable(0f) }
    val animationsEnabled = rememberSystemAnimationsEnabled()
    LaunchedEffect(animationsEnabled, reference) {
        if (!animationsEnabled) camera.snapTo(1f)
        else if (reference != null) {
            // Leave time to show the complete artwork even if decoding is slow.
            val remaining = deadlineElapsedMs?.let { it - android.os.SystemClock.elapsedRealtime() }
                ?: AURON_INTRO_DURATION_MS
            val hold = if (remaining > 1000L) 200 else 0
            val duration = (remaining - hold - 500L).coerceIn(0L, 4500L).toInt()
            if (duration == 0) camera.snapTo(1f)
            else camera.animateTo(1f, tween(duration, delayMillis = hold, easing = FastOutSlowInEasing))
        }
    }
    Surface(color = AuronColors.Background, modifier = Modifier.fillMaxSize().testTag("auron_intro")) {
        Box(Modifier.fillMaxSize().clipToBounds()) {
            val picture = reference
            if (picture != null) {
                Image(
                    bitmap = picture,
                    contentDescription = "AURON: imagem oficial do robô preto e dourado de corpo inteiro, em fundo preto",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().testTag("intro_reference").graphicsLayer {
                        val frame = OpeningCamera.frame(size.width, size.height, camera.value)
                        transformOrigin = TransformOrigin(frame.pivotX, frame.pivotY)
                        scaleX = frame.scale
                        scaleY = frame.scale
                        translationX = frame.translationX
                        translationY = frame.translationY
                    },
                )
            } else {
                // Loading/decoding failure never prevents access: the app owns a fixed
                // timeout and the Skip button is available independently of this asset.
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    AuronRobot(Modifier.weight(1f).widthIn(max = 500.dp).fillMaxWidth(),
                        motionEnabled = animationsEnabled)
                    Text("AURON", style = MaterialTheme.typography.headlineLarge, color = AuronColors.Gold)
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp),
                color = AuronColors.Background.copy(alpha = 0.88f),
                shape = RoundedCornerShape(24.dp),
            ) {
                TextButton(onClick = onSkip, modifier = Modifier.testTag("intro_skip")) { Text("Pular abertura") }
            }
        }
    }
}
