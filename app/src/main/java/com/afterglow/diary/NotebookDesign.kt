package com.afterglow.diary

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paper is for dreams. The control layer uses a small, consistent set of neutral surfaces.
internal val Accent: Color @Composable get() = if (LocalNotebookPreferences.current.night) Color(0xFFB4CCDF) else Color(0xFF385D79)
internal val PanelPaper: Color @Composable get() = if (LocalNotebookPreferences.current.night) Color(0xFF202225) else Color(0xFFF3F3F0)
internal val ControlPaper: Color @Composable get() = if (LocalNotebookPreferences.current.night) Color(0xFF2C2F33) else Color(0xFFFFFEFB)
internal val AccentWash: Color @Composable get() = if (LocalNotebookPreferences.current.night) Color(0xFF2E3C47) else Color(0xFFE5EDF2)
internal val Hairline: Color @Composable get() = Ink.copy(alpha = .09f)

@Composable internal fun PageHeading(title: String, subtitle: String? = null) {
    Text(title, Modifier.padding(top = 18.dp, bottom = 6.dp), color = Ink,
        fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.8).sp)
    if (subtitle != null) Text(subtitle, color = Pencil, fontSize = 15.sp, lineHeight = 22.sp,
        modifier = Modifier.padding(bottom = 20.dp))
}

@Composable internal fun SectionCaption(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        if (trailing != null) Text(trailing, fontSize = 12.sp, color = Pencil)
    }
}

@Composable internal fun GroupedSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ControlPaper), content = content)
}

@Composable internal fun ToolRow(title: String, subtitle: String? = null, icon: ImageVector? = null,
    modifier: Modifier = Modifier, divider: Boolean = true, onClick: () -> Unit) {
    Column(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            if (icon != null) Box(Modifier.size(34.dp).background(AccentWash, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Accent, modifier = Modifier.size(19.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                if (subtitle != null) Text(subtitle, color = Pencil, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 3.dp))
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Pencil.copy(alpha = .65f), modifier = Modifier.size(20.dp))
        }
        if (divider) HorizontalDivider(Modifier.padding(start = if (icon == null) 16.dp else 63.dp), color = Hairline)
    }
}

@Composable internal fun AwarenessDial(modifier: Modifier = Modifier, progress: Float = 0f, label: String? = null) {
    val accent = Accent
    val track = accent.copy(alpha = .13f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp)) {
            val stroke = 3.dp.toPx()
            repeat(4) { i ->
                drawArc(track, -86f + 90f * i, 77f, false, style = Stroke(stroke))
            }
            if (progress > 0) drawArc(accent, -86f, 350f * progress.coerceIn(0f, 1f), false, style = Stroke(stroke))
            if (label == null) {
                val radius = size.minDimension * .22f
                val center = Offset(size.width / 2, size.height / 2)
                drawCircle(accent, radius, center, style = Stroke(1.5.dp.toPx()))
                drawLine(accent, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), 1.5.dp.toPx())
                drawArc(accent.copy(alpha = .16f), -90f, 180f, true,
                    topLeft = Offset(center.x - radius, center.y - radius), size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
            }
        }
        if (label != null) Text(label, color = accent, fontSize = 34.sp, fontWeight = FontWeight.Light,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
    }
}

internal object DreamIcons {
    val Eraser by lazy { symbol("Eraser") { moveTo(3f,14f); lineTo(12f,4f); lineTo(21f,12f); lineTo(13f,21f); lineTo(9f,21f); close(); moveTo(7f,10f); lineTo(17f,18f); moveTo(13f,21f); lineTo(22f,21f) } }
    val Highlighter by lazy { symbol("Highlighter") { moveTo(8f,14f); lineTo(15f,3f); lineTo(21f,7f); lineTo(14f,18f); close(); moveTo(8f,14f); lineTo(5f,19f); lineTo(10f,21f); lineTo(14f,18f); moveTo(3f,23f); lineTo(20f,23f) } }
    val Move by lazy { symbol("Move") { moveTo(12f,2f); lineTo(12f,22f); moveTo(2f,12f); lineTo(22f,12f); moveTo(8f,6f); lineTo(12f,2f); lineTo(16f,6f); moveTo(8f,18f); lineTo(12f,22f); lineTo(16f,18f); moveTo(6f,8f); lineTo(2f,12f); lineTo(6f,16f); moveTo(18f,8f); lineTo(22f,12f); lineTo(18f,16f) } }
    val Undo by lazy { symbol("Undo") { moveTo(9f,4f); lineTo(3f,10f); lineTo(9f,16f); moveTo(3f,10f); lineTo(14f,10f); curveTo(23f,10f,23f,21f,14f,21f) } }
    val Redo by lazy { symbol("Redo") { moveTo(15f,4f); lineTo(21f,10f); lineTo(15f,16f); moveTo(21f,10f); lineTo(10f,10f); curveTo(1f,10f,1f,21f,10f,21f) } }
    private fun symbol(name: String, draw: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = null, stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
                strokeLineWidth = 1.65f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
                strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round, pathBuilder = draw)
        }.build()
    val Moon = symbol("Moon") { moveTo(19f, 15f); curveTo(13f, 17f, 7f, 11f, 9f, 5f); curveTo(2f, 7f, 3f, 18f, 10f, 20f); curveTo(14f, 21f, 18f, 19f, 19f, 15f); close() }
    val Eye = symbol("Eye") { moveTo(2f, 12f); curveTo(7f, 4f, 17f, 4f, 22f, 12f); curveTo(17f, 20f, 7f, 20f, 2f, 12f); close(); moveTo(15f, 12f); arcTo(3f,3f,0f,true,true,9f,12f); arcTo(3f,3f,0f,true,true,15f,12f) }
    val Sun = symbol("Sun") { moveTo(16f,12f); arcTo(4f,4f,0f,true,true,8f,12f); arcTo(4f,4f,0f,true,true,16f,12f); moveTo(12f,2f); lineTo(12f,4f); moveTo(12f,20f); lineTo(12f,22f); moveTo(2f,12f); lineTo(4f,12f); moveTo(20f,12f); lineTo(22f,12f); moveTo(5f,5f); lineTo(6.5f,6.5f); moveTo(17.5f,17.5f); lineTo(19f,19f); moveTo(5f,19f); lineTo(6.5f,17.5f); moveTo(17.5f,6.5f); lineTo(19f,5f) }
    val Book = symbol("Book") { moveTo(12f,5f); curveTo(9f,3f,5f,3f,2f,5f); lineTo(2f,20f); curveTo(5f,18f,9f,18f,12f,20f); curveTo(15f,18f,19f,18f,22f,20f); lineTo(22f,5f); curveTo(19f,3f,15f,3f,12f,5f); lineTo(12f,20f) }
    val Pen = symbol("Pen") { moveTo(5f,15f); lineTo(15f,5f); curveTo(17f,3f,21f,7f,19f,9f); lineTo(9f,19f); lineTo(4f,20f); close(); moveTo(13.5f,6.5f); lineTo(17.5f,10.5f) }
    val Microphone = symbol("Microphone") { moveTo(9f,5f); curveTo(9f,1f,15f,1f,15f,5f); lineTo(15f,11f); curveTo(15f,15f,9f,15f,9f,11f); close(); moveTo(6f,10f); lineTo(6f,11f); curveTo(6f,19f,18f,19f,18f,11f); lineTo(18f,10f); moveTo(12f,17f); lineTo(12f,22f); moveTo(9f,22f); lineTo(15f,22f) }
    val Bookmark = symbol("Bookmark") { moveTo(6f,3f); lineTo(18f,3f); lineTo(18f,21f); lineTo(12f,17f); lineTo(6f,21f); close() }
    val Bell = symbol("Bell") { moveTo(6f,9f); curveTo(6f,1f,18f,1f,18f,9f); lineTo(18f,14f); lineTo(20f,17f); lineTo(4f,17f); lineTo(6f,14f); close(); moveTo(10f,20f); curveTo(11f,22f,13f,22f,14f,20f) }
    val Signs = symbol("Signs") { moveTo(12f,3f); lineTo(15f,9f); lineTo(21f,12f); lineTo(15f,15f); lineTo(12f,21f); lineTo(9f,15f); lineTo(3f,12f); lineTo(9f,9f); close() }
    val History = symbol("History") { moveTo(4f,8f); curveTo(8f,-1f,22f,3f,21f,13f); curveTo(20f,22f,7f,24f,3f,16f); moveTo(3f,3f); lineTo(3f,9f); lineTo(9f,9f); moveTo(12f,7f); lineTo(12f,12f); lineTo(16f,14f) }
    val Info = symbol("Info") { moveTo(21f,12f); arcTo(9f,9f,0f,true,true,3f,12f); arcTo(9f,9f,0f,true,true,21f,12f); moveTo(12f,11f); lineTo(12f,17f); moveTo(12f,7f); lineTo(12f,7.1f) }
    val Sound = symbol("Sound") { moveTo(3f,10f); lineTo(7f,10f); lineTo(12f,5f); lineTo(12f,19f); lineTo(7f,14f); lineTo(3f,14f); close(); moveTo(16f,8f); curveTo(19f,10f,19f,14f,16f,16f); moveTo(19f,5f); curveTo(24f,9f,24f,15f,19f,19f) }
}
