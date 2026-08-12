package com.cdnhunter.app.ui

// ── Connect-bar flag artwork ───────────────────────────────────────────────────
// The bundled assets are HatScripts/circle-flags: a full 512×512 flag drawn under
// `<mask id="a"><circle r="256"/></mask>`, so the artwork is complete but
// everything outside the inscribed circle is transparent, and every straight edge
// of the real flag is bowed outward by roughly 5% at its midpoint so the flag
// reads as wrapped around a sphere once the circle crops it.
//
// Both of those are wrong for the connect bar, which shows the flag flat and
// full-bleed across an 88dp-tall, ~300dp-wide pill:
//
//   • The mask has to go, or cropping the circle into that pill lands entirely
//     inside one band — for Germany the red one, i.e. "the bar is solid red".
//     Dropping the mask element and the `mask="url(#a)"` reference gives back the
//     rectangular flag the paths already describe.
//
//   • The bow has to go, or the bands visibly sag. Germany's gold band is drawn
//     `m0 345 256.7-25.5L512 345 …`: one extra vertex, 25.5 units (5% of 512)
//     above the line between its neighbours. Stretched to the pill that is a 4dp
//     dip over 150dp — a band that reads as tilted rather than level.
//     [dropBulges] removes exactly those vertices, and only those: in a path built
//     from straight lines, a vertex is dropped when the segment joining its two
//     neighbours is axis-aligned, spans at least half the shape, and the vertex
//     sits inside that span within [BULGE_TOLERANCE] of it. A flag edge that long
//     and that straight-but-for-one-point is a bowed edge; the constraints are what
//     keep the US stripes' 32-unit notches, Nepal's pennant corners and the Union
//     Jack's diagonals, none of which meet all three. Neighbouring bands share the
//     same endpoints, so they straighten onto the same line and no seam opens up
//     between them. Over the 265 bundled assets this straightens 110 flags and
//     leaves the rest byte-identical.
//
// Everything is best-effort: an asset that doesn't parse, or holds curves, is used
// exactly as shipped, so an unusual flag degrades to the sphere-bowed circular
// version rather than to nothing.

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Side length, in px, the flag SVG is rasterised at for the connect bar.
 *
 * The pill is ~900×264px on a 3x panel, and Coil sizes an SVG request to fit the
 * target — which, for a square document in a 3.4:1 box, means 264×264 stretched
 * out to 900 wide, i.e. a 3.4x horizontal upscale and the blur that comes with it.
 * Asking for a large square over-samples both axes instead: horizontal detail
 * (vertical stripes, emblems) is rendered above the pill's own width and the
 * vertical axis is downsampled, which is the direction that stays crisp.
 *
 * 1152 rather than 1024 so the horizontal axis is still a downsample on a 3.5x
 * panel, where the pill is ~1050px wide — at 1024 the widest phones were stretching
 * the raster back up by a few percent, which is exactly the soft edge this constant
 * exists to avoid. Coil holds one bitmap per country at ~5MB; the bar shows one
 * flag at a time.
 */
internal const val FLAG_RENDER_PX = 1152

private val CIRCLE_MASK = Regex("<mask\\s+id=\"a\">.*?</mask>", RegexOption.DOT_MATCHES_ALL)
private val CIRCLE_MASK_REF = Regex("\\s*mask=\"url\\(#a\\)\"")
// The leading \s is what keeps this off `id="a"`, which contains `d="a"`.
private val PATH_DATA = Regex("\\sd=\"([^\"]*)\"")
private const val CURVE_COMMANDS = "cCsSqQtTaA"

/** Fraction of a subpath's own size a bowed vertex may sit off its edge. */
private const val BULGE_TOLERANCE = 0.12f

/** How far from horizontal or vertical, in user units, an edge may run and still count. */
private const val AXIS_SLACK = 1.0f

/** Fraction of the subpath's size the edge under a bowed vertex has to span. */
private const val MIN_EDGE_SPAN = 0.5f

/**
 * The country's flag as rectangular, flat-edged artwork, ready to be stretched
 * across the connect bar: a Coil model, or null when no country is known.
 */
internal fun rectangularFlag(context: android.content.Context, countryCode: String): Any? {
    val cc = countryCode.lowercase().trim()
    if (cc.isBlank()) return null
    val path = "flags/$cc.svg"
    return runCatching {
        val svg = context.assets.open(path).use { it.readBytes().decodeToString() }
        java.nio.ByteBuffer.wrap(flattenFlagSvg(svg).toByteArray())
    }.getOrElse { "file:///android_asset/$path" }
}

/** Mask off, sphere-bow off — see the file header. */
internal fun flattenFlagSvg(svg: String): String {
    val unmasked = svg.replace(CIRCLE_MASK, "").replace(CIRCLE_MASK_REF, "")
    return PATH_DATA.replace(unmasked) { match ->
        val data = match.groupValues[1]
        " d=\"${straightenEdges(data) ?: data}\""
    }
}

/** One closed or open run of straight lines. */
private class Subpath(val points: MutableList<Offset>, val closed: Boolean)

/**
 * [pathData] with its bowed midpoints removed, or null when there is nothing to
 * straighten and when the path is anything this deliberately doesn't touch —
 * curves, arcs, or syntax the small parser below doesn't recognise.
 */
private fun straightenEdges(pathData: String): String? {
    if (pathData.any { it in CURVE_COMMANDS }) return null
    val subpaths = parseLinePath(pathData) ?: return null
    if (subpaths.isEmpty()) return null
    var straightened = false
    for (subpath in subpaths) if (dropBulges(subpath)) straightened = true
    if (!straightened) return null
    return subpaths.joinToString(" ") { it.toPathData() }
}

/**
 * The subset of SVG path syntax the flag assets are written in: moveto, lineto,
 * their horizontal and vertical forms, closepath, relative or absolute, with
 * implicit command repetition. Anything else returns null and the path is left
 * alone.
 */
private fun parseLinePath(pathData: String): List<Subpath>? {
    val subpaths = mutableListOf<Subpath>()
    var points = mutableListOf<Offset>()
    var closed = false
    var x = 0f
    var y = 0f
    var startX = 0f
    var startY = 0f
    var index = 0
    var command = ' '

    fun skipSeparators() {
        while (index < pathData.length && pathData[index].let {
                it == ' ' || it == ',' || it == '\t' || it == '\n' || it == '\r'
            }
        ) index++
    }

    // SVG numbers: an optional sign, digits, an optional fraction, an optional
    // exponent — and no separator required before a leading '-'.
    fun readNumber(): Float? {
        skipSeparators()
        val start = index
        if (index < pathData.length && (pathData[index] == '-' || pathData[index] == '+')) index++
        var digits = false
        while (index < pathData.length && pathData[index].isDigit()) { index++; digits = true }
        if (index < pathData.length && pathData[index] == '.') {
            index++
            while (index < pathData.length && pathData[index].isDigit()) { index++; digits = true }
        }
        if (!digits) { index = start; return null }
        if (index < pathData.length && (pathData[index] == 'e' || pathData[index] == 'E')) {
            val mark = index
            index++
            if (index < pathData.length && (pathData[index] == '-' || pathData[index] == '+')) index++
            var exponent = false
            while (index < pathData.length && pathData[index].isDigit()) { index++; exponent = true }
            if (!exponent) index = mark
        }
        return pathData.substring(start, index).toFloatOrNull()
    }

    fun endSubpath() {
        // Every subpath is kept, however short: this list is what the path is
        // rebuilt from, so dropping one would drop artwork.
        if (points.isNotEmpty()) subpaths += Subpath(points, closed)
        points = mutableListOf()
        closed = false
    }

    while (true) {
        skipSeparators()
        if (index >= pathData.length) break
        if (pathData[index].isLetter()) {
            command = pathData[index]
            index++
        }
        when (command) {
            'M', 'm' -> {
                val dx = readNumber() ?: return null
                val dy = readNumber() ?: return null
                if (points.isNotEmpty()) endSubpath()
                x = if (command == 'm') x + dx else dx
                y = if (command == 'm') y + dy else dy
                startX = x
                startY = y
                points.add(Offset(x, y))
                // Coordinate pairs that follow a moveto are linetos.
                command = if (command == 'm') 'l' else 'L'
            }
            'L', 'l' -> {
                val dx = readNumber() ?: return null
                val dy = readNumber() ?: return null
                x = if (command == 'l') x + dx else dx
                y = if (command == 'l') y + dy else dy
                points.add(Offset(x, y))
            }
            'H', 'h' -> {
                val dx = readNumber() ?: return null
                x = if (command == 'h') x + dx else dx
                points.add(Offset(x, y))
            }
            'V', 'v' -> {
                val dy = readNumber() ?: return null
                y = if (command == 'v') y + dy else dy
                points.add(Offset(x, y))
            }
            'Z', 'z' -> {
                closed = true
                x = startX
                y = startY
                endSubpath()
                command = ' '
            }
            else -> return null
        }
    }
    endSubpath()
    return subpaths
}

/**
 * Drops every bowed midpoint — a vertex that interrupts one long, axis-aligned
 * edge — and nothing that carries shape. See the file header for why all three
 * conditions are needed.
 *
 * The tolerance is measured against the subpath's own size, not the document's, so
 * a band spanning the whole flag gives up its 25-unit bow while an emblem keeps
 * detail of the same absolute size. A closed subpath is walked cyclically, because
 * the bowed vertex of a left or right edge is often the last one in the run.
 */
private fun dropBulges(subpath: Subpath): Boolean {
    val points = subpath.points
    if (points.size < 3) return false
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (point in points) {
        if (point.x < minX) minX = point.x
        if (point.y < minY) minY = point.y
        if (point.x > maxX) maxX = point.x
        if (point.y > maxY) maxY = point.y
    }
    val span = maxOf(maxX - minX, maxY - minY)
    if (span <= 0f) return false
    val tolerance = span * BULGE_TOLERANCE

    var dropped = false
    var index = 0
    while (points.size > 3 && index < points.size) {
        val endpoint = !subpath.closed && (index == 0 || index == points.lastIndex)
        if (endpoint) {
            index++
            continue
        }
        val vertex = points[index]
        val from = points[(index - 1 + points.size) % points.size]
        val to = points[(index + 1) % points.size]
        val dx = to.x - from.x
        val dy = to.y - from.y
        val length = hypot(dx, dy)
        val axisAligned = abs(dy) <= AXIS_SLACK || abs(dx) <= AXIS_SLACK
        // Where along the edge the vertex falls: a bow sits in the middle of it, a
        // corner sits at or past one of its ends.
        val along = if (length > 0f) {
            ((vertex.x - from.x) * dx + (vertex.y - from.y) * dy) / (length * length)
        } else {
            -1f
        }
        if (axisAligned &&
            length >= MIN_EDGE_SPAN * span &&
            along > 0.05f &&
            along < 0.95f &&
            distanceToSegment(vertex, from, to) < tolerance
        ) {
            points.removeAt(index)
            dropped = true
        } else {
            index++
        }
    }
    return dropped
}

private fun distanceToSegment(point: Offset, from: Offset, to: Offset): Float {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val lengthSq = dx * dx + dy * dy
    if (lengthSq == 0f) return hypot(point.x - from.x, point.y - from.y)
    val t = (((point.x - from.x) * dx + (point.y - from.y) * dy) / lengthSq).coerceIn(0f, 1f)
    return hypot(point.x - (from.x + t * dx), point.y - (from.y + t * dy))
}

private fun Subpath.toPathData(): String = buildString {
    points.forEachIndexed { index, point ->
        append(if (index == 0) "M" else "L")
        append(format(point.x))
        append(' ')
        append(format(point.y))
        if (index < points.lastIndex) append(' ')
    }
    if (closed) append("Z")
}

/** Two decimals, and no trailing ".0" on whole numbers. */
private fun format(value: Float): String {
    val rounded = (value * 100f).roundToInt() / 100f
    val whole = rounded.roundToInt()
    return if (rounded == whole.toFloat()) whole.toString() else rounded.toString()
}
