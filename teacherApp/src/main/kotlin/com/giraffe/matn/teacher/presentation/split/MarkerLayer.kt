package com.giraffe.matn.teacher.presentation.split

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import com.giraffe.matn.presentation.theme.MatnShapes
import com.giraffe.matn.presentation.theme.MatnSpacing
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * The interactive layer over the waveform: seeking the transport playhead and placing the armed
 * verse boundary, with live timestamp readouts so a position can be judged *before* release
 * (FR-014).
 *
 * **No re-decode during the gesture** (FR-041): dragging only converts x → milliseconds against the
 * already-loaded peaks and duration. Nothing here touches the source file.
 *
 * Pinned left-to-right in both interface languages, matching [WaveformCanvas] (research D11) —
 * mirroring the drag axis would invert the meaning of dragging "forward".
 */
@Composable
fun MarkerLayer(
    durationMs: Long,
    /** The armed boundary's position: the pointer's while dragging, its committed value once
     * released. Non-null for as long as a field holds focus, so the readout persists. */
    markerMs: Long?,
    playheadMs: Long,
    onScrub: (Long) -> Unit,
    onScrubEnd: () -> Unit,
    /** Fired once when a press is resolved as a playhead drag, before any position is reported —
     * the caller uses it to stop playback and drop focus from any Start/End field. */
    onSeekStart: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // Read inside the gesture instead of keying `pointerInput` on them. Keying on a value that the
    // gesture itself changes tears the detector down and relaunches it mid-drag — the playhead
    // moved on the first reported position, which restarted the handler and killed the drag on its
    // first pixel. `rememberUpdatedState` gives the running gesture the latest value with no
    // restart.
    val currentPlayheadMs by rememberUpdatedState(playheadMs)
    val currentMarkerMs by rememberUpdatedState(markerMs)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // `fillMaxWidth` is load-bearing, not cosmetic: with only a height this box wrapped its
        // content, its sole child used `matchParentSize()` (which does not contribute to sizing),
        // and `maxWidth` measured 0 — so the `widthPx > 0` guard below rejected every gesture and
        // dragging silently did nothing at all.
        BoxWithConstraints(modifier = modifier.fillMaxWidth().height(MatnSpacing.unit * 12)) {
            val widthPx = with(density) { maxWidth.toPx() }
            val laneHeightPx = with(density) { SEEK_LANE_HEIGHT.toPx() }

            // A dedicated strip along the top that always seeks, whatever is focused. Grabbing a
            // 3px-wide playhead line is a precision-aiming task, and when a boundary is armed the
            // rest of the surface belongs to that boundary — without a reserved lane the transport
            // would be effectively undraggable exactly when the teacher is mid-edit. The playhead's
            // timestamp sits in this strip, so the label doubles as the visible handle.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SEEK_LANE_HEIGHT)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f))
                    .align(Alignment.TopStart),
            )

            if (durationMs > 0 && widthPx > 0) {
                val toMs: (Float) -> Long = { x -> ((x / widthPx).coerceIn(0f, 1f) * durationMs).toLong() }
                val grabPx = with(density) { GRAB_TOLERANCE.toPx() }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        // One handler for press + drag + release rather than separate tap and drag
                        // detectors, which both see the same events and would double-report the end
                        // of a gesture.
                        //
                        // What a drag moves is decided **once, at press**, and never reconsidered
                        // mid-gesture — otherwise a drag could hand off as it swept past another
                        // marker and a seek would silently rewrite a verse boundary.
                        //
                        // Precedence, so there is never ambiguity:
                        //  1. pressed in the seek lane, or on the playhead → seek (and disarm)
                        //  2. a Start/End field is focused                → move that boundary
                        //  3. nothing focused                             → seek
                        .pointerInput(durationMs, widthPx) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown()
                                    val playheadX = (currentPlayheadMs.toFloat() / durationMs).coerceIn(0f, 1f) * widthPx
                                    val inSeekLane = down.position.y <= laneHeightPx
                                    val grabbedPlayhead = abs(down.position.x - playheadX) <= grabPx
                                    val movesBoundary = !inSeekLane && !grabbedPlayhead && currentMarkerMs != null
                                    if (!movesBoundary) onSeekStart()

                                    val report: (Float) -> Unit = { x ->
                                        if (movesBoundary) onScrub(toMs(x)) else onSeek(toMs(x))
                                    }
                                    report(down.position.x)
                                    down.consume()
                                    var active = true
                                    while (active) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) {
                                            active = false
                                        } else {
                                            report(change.position.x)
                                            change.consume()
                                        }
                                    }
                                    if (movesBoundary) onScrubEnd() else onSeekEnd()
                                }
                            }
                        },
                )
            }

            // Two independent readouts rather than one shared slot: while a clip plays with a field
            // still focused, the teacher needs *both* — where the boundary is pinned and where the
            // audio currently is. A single label showed only the armed value and hid the playback
            // position exactly when it was most useful.
            if (durationMs > 0 && widthPx > 0) {
                // Playhead's time sits in the seek lane, directly above its line — always shown,
                // because the transport position is always meaningful, playing or not.
                TimestampLabel(
                    ms = playheadMs,
                    durationMs = durationMs,
                    widthPx = widthPx,
                    background = MaterialTheme.colorScheme.tertiary,
                    foreground = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                // The armed boundary's sits along the bottom, so the two never collide even when
                // the playhead sweeps past the marker.
                if (markerMs != null) {
                    TimestampLabel(
                        ms = markerMs,
                        durationMs = durationMs,
                        widthPx = widthPx,
                        background = MaterialTheme.colorScheme.primary,
                        foreground = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                }
            }
        }
    }
}

@Composable
private fun TimestampLabel(
    ms: Long,
    durationMs: Long,
    widthPx: Float,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fraction = (ms.toFloat() / durationMs).coerceIn(0f, 1f)
    val xDp = with(density) { (fraction * widthPx).toDp() }
    // Near the right edge the label would run off the timeline, so it flips to sit before its line
    // instead of after it. `LABEL_WIDTH` is an estimate for the widest `m:ss.mmm` — measuring the
    // text to place it exactly is not worth a layout pass for a hint that moves every frame.
    val placed = if (xDp + LABEL_WIDTH > with(density) { widthPx.toDp() }) xDp - LABEL_WIDTH else xDp + MatnSpacing.unit / 2
    CopyableTimestamp(
        ms = ms,
        color = foreground,
        modifier = modifier
            .offset(x = placed.coerceAtLeast(MatnSpacing.unit / 2))
            .background(background, MatnShapes.lg)
            .padding(horizontal = MatnSpacing.unit / 2),
    )
}

/**
 * A timestamp that reads as `m:ss.mmm` but hands over its **raw millisecond count** when clicked —
 * because that is the unit the Start/End fields take, and transcribing `1:07.480` into `67480` by
 * hand for every boundary is both tedious and easy to get wrong.
 *
 * Shows what it copied for a moment afterwards, so the click has a visible result and the teacher
 * can see the exact number now sitting on the clipboard.
 */
@Composable
internal fun CopyableTimestamp(
    ms: Long,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    // A tick rather than a boolean: clicking again while the confirmation is still up must restart
    // the window, and a boolean that is already `true` would not re-launch the effect.
    var copies by remember { mutableIntStateOf(0) }
    var showingCopied by remember { mutableStateOf(false) }
    LaunchedEffect(copies) {
        if (copies == 0) return@LaunchedEffect
        showingCopied = true
        delay(COPIED_FEEDBACK_MS)
        showingCopied = false
    }
    Text(
        text = if (showingCopied) "$ms ✓" else formatTimestamp(ms),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier.clickable {
            clipboard.setText(AnnotatedString(ms.toString()))
            copies++
        },
    )
}

/** How long a copied timestamp shows its millisecond value before returning to `m:ss.mmm`. */
private const val COPIED_FEEDBACK_MS = 1_400L

/** `m:ss.mmm` — milliseconds shown because the range fields are in milliseconds, so the readout and
 * the field the teacher is filling speak the same units. */
internal fun formatTimestamp(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = ms % 1000
    return "$minutes:${seconds.toString().padStart(2, '0')}.${millis.toString().padStart(3, '0')}"
}

/** Estimated width of the widest `m:ss.mmm` label, as a spacing-token multiple (Ground Rule 5 —
 * no bare `.dp` in `:teacherApp`). */
private val LABEL_WIDTH = MatnSpacing.unit * 8

/** How close a press must land to the playhead to grab it rather than edit a boundary. */
private val GRAB_TOLERANCE = MatnSpacing.unit * 2

/** Height of the always-seek strip along the top of the waveform. */
private val SEEK_LANE_HEIGHT = MatnSpacing.unit * 3
