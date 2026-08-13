package com.cdnhunter.app.ui

// ── Header flag artwork ────────────────────────────────────────────────────────
// The bundled assets are HatScripts/circle-flags: a full 512×512 flag drawn under
// `<mask id="a"><circle r="256"/></mask>`, so the artwork is complete but
// everything outside the inscribed circle is transparent, and every straight edge
// of the real flag is bowed outward by roughly 5% at its midpoint so the flag
// reads as wrapped around a sphere once the circle crops it.
//
// Both of those are wrong for the header, which shows the flag flat and full-bleed
// across the whole top of the screen — the panel's full width, about 1.3:1:
//
//   • The mask has to go, or the flag arrives as an ellipse: the circle stretched
//     into the panel, with the corners of the header transparent and the page
//     showing through them. Dropping the mask element and the `mask="url(#a)"`
//     reference gives back the rectangular flag the paths already describe.
//
//   • The bow has to go, or the bands visibly sag. Germany's gold band is drawn
//     `m0 345 256.7-25.5L512 345 …`: one extra vertex, 25.5 units (5% of 512)
//     above the line between its neighbours. Stretched across the header that is a
//     14dp dip over 195dp — a band that reads as sagging rather than level, and at
//     this size it is the first thing the eye finds.
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

// ── flagcdn artwork ───────────────────────────────────────────────────────────
// For the countries a VPN actually sells exits in, the flag is fetched from
// flagcdn.com instead of drawn from the bundled circle-flags asset:
//
//   • it is already the real flag's rectangle (de.svg is `viewBox="0 0 5 3"`, us.svg
//     `0 0 7410 3900`), so nothing has to be unmasked and nothing has to be
//     de-bowed — the header gets true 3:2 artwork rather than a square document
//     stretched into the panel, and [flattenFlagSvg] never has to touch it;
//   • the paths are the authoritative specification for the flag, including the
//     details circle-flags simplifies away at badge size (the US canton's 50 stars,
//     the crest on Spain's, the emblem on Turkey's).
//
// [VPN_FLAG_COUNTRIES] is the whole surface of this: only well-known exit countries
// are fetched. Anything outside that set — an obscure territory, an unresolved code —
// keeps drawing the bundled asset it always drew, which is also what every country
// falls back to when the device is offline or flagcdn is unreachable (see
// [CountryFlagBadge] and [HeaderFlag], which both fall back on Coil's error
// callback). So the flags get better where it matters and nothing gets worse
// anywhere else.

/**
 * The countries this app fetches high-quality flags for: the exits a commercial VPN
 * actually lists — Europe, the big Asian and North American hubs, the Gulf, and the
 * region this app is used in most (IR, TR and their neighbours). Deliberately not
 * every ISO code: an obscure territory would be a network request that almost never
 * pays off, and the bundled asset already draws it.
 */
private val VPN_FLAG_COUNTRIES = setOf(
    // North America
    "US", "CA", "MX",
    // Western + Northern Europe
    "GB", "IE", "FR", "DE", "NL", "BE", "LU", "CH", "AT", "IT", "ES", "PT",
    "SE", "NO", "DK", "FI", "IS",
    // Central + Eastern Europe
    "PL", "CZ", "SK", "HU", "RO", "BG", "GR", "HR", "SI", "RS", "EE", "LV",
    "LT", "UA", "MD", "RU", "TR", "CY",
    // Middle East + Caucasus + Central Asia
    "AE", "SA", "QA", "IL", "IR", "IQ", "OM", "KW", "BH", "GE", "AM", "AZ", "KZ",
    // Asia-Pacific
    "JP", "SG", "HK", "KR", "TW", "IN", "MY", "TH", "VN", "ID", "PH", "AU", "NZ",
    // Africa + South America
    "ZA", "EG", "NG", "KE", "BR", "AR", "CL", "CO", "PE",
)

/**
 * `https://flagcdn.com/<cc>.svg` for a well-known exit country, or null when the code
 * is unknown, is outside [VPN_FLAG_COUNTRIES], or has no name this app can draw
 * beside it.
 *
 * SVG rather than `w320/<cc>.png`: the flag is drawn at two very different sizes here
 * — a 36dp circular badge and a full-width header panel — and one vector serves both
 * crisply, where a 320px raster would be upscaled roughly 4x across a 1080px-wide
 * header. Coil already has [coil.decode.SvgDecoder] registered on the flag loader for
 * the bundled assets, so this needs no new decoding path.
 */
internal fun remoteFlagUrl(countryCode: String): String? {
    val cc = canonicalCountryCode(countryCode) ?: return null
    if (cc !in VPN_FLAG_COUNTRIES) return null
    return "https://flagcdn.com/${cc.lowercase()}.svg"
}

/**
 * Side length, in px, the flag SVG is rasterised at for the header panel.
 *
 * The panel is as wide as the screen and about 1.3:1 — ~1080×830px on a 3x 360dp
 * phone, ~1344×1030px on a 1440-wide one — and Coil sizes an SVG request to fit the
 * target, which for a square document means the shorter side: it would rasterise
 * ~1030 square and stretch that out to 1344 wide, a 1.3x horizontal upscale and the
 * blur that comes with it. Asking for a large square over-samples both axes instead,
 * so every panel only ever downsamples, which is the direction that stays crisp.
 *
 * 1440 because that is the widest phone panel shipping (1440×3120) and the header is
 * never wider than the screen it is in. It was 1152 while the flag was only the
 * connect bar's background, ~1050px wide at its widest; the header reaches 1344px, so
 * the old constant would be upscaled by a few percent on exactly the panels it was
 * raised to 1152 to protect. Coil holds one bitmap per country at ~8MB — two while a
 * crossfade is in flight — and the header shows one flag at a time.
 */
internal const val FLAG_RENDER_PX = 1440

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
 * The country's flag as rectangular, flat-edged artwork from the *bundled* asset,
 * ready to be stretched across the header: a Coil model, or null when no country is
 * known.
 *
 * This is the fallback path now — [remoteFlagUrl] is what the header asks for first
 * for a well-known exit country, and that artwork is already rectangular, so none of
 * the unmasking and de-bowing below applies to it. It is still what draws every
 * country outside [VPN_FLAG_COUNTRIES], and what every country falls back to when the
 * fetch fails.
 */
internal fun rectangularFlag(context: android.content.Context, countryCode: String): Any? {
    // Same gate as the circular badge, so the header and the badge always show the
    // same country's artwork for the same code — see [canonicalCountryCode].
    val cc = canonicalCountryCode(countryCode)?.lowercase() ?: return null
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
