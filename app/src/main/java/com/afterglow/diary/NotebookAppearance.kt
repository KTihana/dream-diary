package com.afterglow.diary

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

internal data class WritingFont(val name: String, val typeface: String, val family: FontFamily)
@OptIn(ExperimentalTextApi::class)
internal val WritingFonts = listOf(
    WritingFont("Book", "Lora", DiarySerif),
    WritingFont("Handwritten", "Caveat", Handwritten),
    WritingFont("Journal", "Kalam", FontFamily(Font(R.font.kalam))),
    WritingFont("Cursive", "Dancing Script", FontFamily(Font(R.font.dancingscript))),
    WritingFont("Literary", "Crimson Text", FontFamily(Font(R.font.crimsontext))),
    WritingFont("Typewriter", "Courier Prime", FontFamily(Font(R.font.courierprime))),
    WritingFont("Rounded", "Nunito", FontFamily(Font(R.font.nunito, variationSettings = FontVariation.Settings(FontVariation.weight(400))))),
    WritingFont("Simple", "System sans", FontFamily.SansSerif),
)
internal fun writingFont(name: String) = WritingFonts.firstOrNull { it.name == name } ?: WritingFonts.first()

internal val PaperTones = listOf("Ivory", "White", "Sand", "Rose")
internal val InkColors = listOf("Charcoal", "Blue", "Sepia", "Plum")
internal val WritingSpacing = listOf("Compact", "Comfortable", "Airy")
internal val PageMarkers = listOf("Linen", "Rose", "Midnight", "Lavender", "Clay")

internal fun paperToneColor(tone: String) = when (tone) {
    "White" -> Color(0xFFFFFEFC)
    "Sand" -> Color(0xFFF3E9D7)
    "Rose" -> Color(0xFFF8ECE8)
    else -> Color(0xFFFCFAF4)
}
internal fun writingInk(settings: NotebookPreferences) = if (settings.night) when (settings.inkColor) {
    "Blue" -> Color(0xFFBAD3EC)
    "Sepia" -> Color(0xFFE5C5A2)
    "Plum" -> Color(0xFFDCC2E9)
    else -> Color(0xFFE0D8CA)
} else when (settings.inkColor) {
    "Blue" -> Color(0xFF304F78)
    "Sepia" -> Color(0xFF70513B)
    "Plum" -> Color(0xFF6A486D)
    else -> Color(0xFF292D30)
}
internal fun pageMarkerColor(name: String) = when (name) {
    "Rose" -> Color(0xFF925862)
    "Midnight" -> Color(0xFF536A88)
    "Lavender" -> Color(0xFF8F7DA6)
    "Clay" -> Color(0xFFAD7458)
    else -> Color(0xFF7F6B57)
}
internal val NotebookPreferences.lineHeight: Int get() = textSize + when (spacing) { "Compact" -> 10; "Airy" -> 20; else -> 14 }
internal fun notebookWritingStyle(settings: NotebookPreferences) = TextStyle(
    fontFamily = writingFont(settings.font).family, color = writingInk(settings),
    fontSize = settings.textSize.sp, lineHeight = settings.lineHeight.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** Only appearance is reset; reminders, language and saved dreams are untouched. */
internal fun NotebookPreferences.resetAppearance() = copy(ruling = "Lined", font = "Book", cover = "Linen", night = false,
    textSize = 18, paperTone = "Ivory", inkColor = "Charcoal", spacing = "Comfortable", matchTitle = false)
