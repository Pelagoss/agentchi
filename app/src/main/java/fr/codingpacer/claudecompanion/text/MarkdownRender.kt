package fr.codingpacer.claudecompanion.text

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/** Texte enrichi pour les notifications (spans Android). */
fun Markdown.Rich.toSpanned(): CharSequence {
    val out = SpannableStringBuilder(text)
    spans.forEach { s ->
        val span: Any = when (s.style) {
            Markdown.Style.BOLD -> StyleSpan(Typeface.BOLD)
            Markdown.Style.ITALIC -> StyleSpan(Typeface.ITALIC)
            Markdown.Style.CODE -> TypefaceSpan("monospace")
            Markdown.Style.STRIKE -> StrikethroughSpan()
        }
        out.setSpan(span, s.start, s.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return out
}

/** Texte enrichi pour les écrans Compose. */
fun Markdown.Rich.toAnnotated(): AnnotatedString = buildAnnotatedString {
    append(text)
    spans.forEach { s ->
        val style = when (s.style) {
            Markdown.Style.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            Markdown.Style.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            Markdown.Style.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x33FFFFFF))
            Markdown.Style.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        }
        addStyle(style, s.start, s.end)
    }
}

fun markdownSpanned(markdown: String): CharSequence = Markdown.parse(markdown).toSpanned()

fun markdownAnnotated(markdown: String): AnnotatedString = Markdown.parse(markdown).toAnnotated()
