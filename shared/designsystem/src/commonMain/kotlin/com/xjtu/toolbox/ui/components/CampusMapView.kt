package com.xjtu.toolbox.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjtu.toolbox.campusmap.CampusMapData
import com.xjtu.toolbox.campusmap.CampusMaps
import com.xjtu.toolbox.campusmap.CampusPlace
import com.xjtu.toolbox.campusmap.PlaceKind
import com.xjtu.toolbox.campusmap.decodePolyline
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** 简图上要标的一个点：[label] 画在点旁边，[order] 非空时画成带序号的圆。 */
class MapMark(val place: CampusPlace, val label: String, val order: Int? = null)

/**
 * 校区简图（OpenStreetMap 数据，离线）：边界、道路、楼宇轮廓按类别着色，标出 [marks]，
 * [route] 为 true 时把 [marks] 按顺序连成虚线。视野框住所有标记（没有标记就整个校区）。
 */
@Composable
fun CampusMapView(
    map: CampusMapData,
    marks: List<MapMark> = emptyList(),
    route: Boolean = false,
    height: Dp = 220.dp,
    modifier: Modifier = Modifier,
) {
    val decoded = remember(map) {
        Triple(
            map.boundary.map(::decodePolyline),
            map.paths.map(::decodePolyline),
            map.places.mapNotNull { p -> p.outline.takeIf { it.isNotEmpty() }?.let { p to decodePolyline(it) } },
        )
    }
    val (boundary, paths, outlines) = decoded
    val colors = MiuixTheme.colorScheme
    val accent = colors.primary
    val measurer = rememberTextMeasurer()
    val kindColor = { k: PlaceKind ->
        when (k) {
            PlaceKind.CANTEEN -> Color(0xFFF2A65A)
            PlaceKind.LIBRARY -> Color(0xFF7C8CF8)
            PlaceKind.DORM -> Color(0xFF9CC5A1)
            PlaceKind.SPORT -> Color(0xFF6CC3B5)
            PlaceKind.BUILDING -> Color(0xFFB8B8C8)
            else -> Color(0xFFCFCFCF)
        }
    }
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(16.dp))) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            // 视野：标记的外包框（外扩 40%），没有标记就整个校区
            val pts = marks.map { it.place.lat to it.place.lon }
            var minLat = if (pts.isEmpty()) map.minLat else pts.minOf { it.first }
            var maxLat = if (pts.isEmpty()) map.maxLat else pts.maxOf { it.first }
            var minLon = if (pts.isEmpty()) map.minLon else pts.minOf { it.second }
            var maxLon = if (pts.isEmpty()) map.maxLon else pts.maxOf { it.second }
            val k = cos((minLat + maxLat) / 2 * PI / 180)
            val padLat = max((maxLat - minLat) * 0.4, 0.0012)
            val padLon = max((maxLon - minLon) * 0.4, 0.0012 / k)
            minLat -= padLat; maxLat += padLat; minLon -= padLon; maxLon += padLon
            val scale = min(size.width / ((maxLon - minLon) * k), size.height / (maxLat - minLat))
            val cx = (minLon + maxLon) / 2
            val cy = (minLat + maxLat) / 2
            fun at(lat: Double, lon: Double) = Offset(
                (size.width / 2 + (lon - cx) * k * scale).toFloat(),
                (size.height / 2 - (lat - cy) * scale).toFloat(),
            )
            fun pathOf(line: List<Pair<Double, Double>>, close: Boolean) = Path().apply {
                line.forEachIndexed { i, (la, lo) -> at(la, lo).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
                if (close) close()
            }
            drawRect(colors.surfaceContainer)
            boundary.forEach { drawPath(pathOf(it, true), colors.surfaceVariant) }
            paths.forEach { drawPath(pathOf(it, false), colors.outline.copy(alpha = 0.5f), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round)) }
            outlines.forEach { (p, line) -> drawPath(pathOf(line, true), kindColor(p.kind).copy(alpha = 0.55f)) }
            if (route && marks.size >= 2) {
                val r = Path()
                marks.forEachIndexed { i, m -> at(m.place.lat, m.place.lon).let { if (i == 0) r.moveTo(it.x, it.y) else r.lineTo(it.x, it.y) } }
                drawPath(r, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            }
            marks.forEach { m ->
                val c = at(m.place.lat, m.place.lon)
                drawCircle(Color.White, 9.dp.toPx(), c)
                drawCircle(accent, 7.dp.toPx(), c)
                m.order?.let { n ->
                    val t = measurer.measure("$n", TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
                }
                val label = measurer.measure(m.label, TextStyle(color = colors.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                val lx = (c.x + 11.dp.toPx()).coerceAtMost(size.width - label.size.width - 2f)
                drawText(label, topLeft = Offset(lx, c.y - label.size.height / 2f))
            }
        }
        Text(
            CampusMaps.ATTRIBUTION,
            style = MiuixTheme.textStyles.footnote2.copy(fontSize = 9.sp),
            color = colors.onSurfaceVariantSummary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
        )
    }
}
