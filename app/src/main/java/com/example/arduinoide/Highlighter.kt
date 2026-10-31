package com.example.arduinoide

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Realce de sintaxe C++ simples, cores do tema claro. */
class CppHighlighter : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val styled = buildAnnotatedString {
            append(s)
            for (m in RX.findAll(s)) {
                val c = when {
                    m.groups[1] != null -> Color(0xFF007A00)
                    m.groups[2] != null -> Color(0xFFA31515)
                    m.groups[3] != null -> Color(0xFFAF00DB)
                    m.groups[4] != null -> Color(0xFF098658)
                    m.groups[5] != null -> Color(0xFF267F99)
                    m.groups[6] != null -> Color(0xFFAF00DB)
                    else -> Color(0xFF795E26)
                }
                addStyle(SpanStyle(color = c), m.range.first, m.range.last + 1)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    private companion object {
        val RX = Regex(
            """(//[^\n]*|/\*[\s\S]*?\*/)|("(?:\\.|[^"\\\n])*")|(^[ \t]*#\w+)|(\b\d+(?:\.\d+)?\b|\b(?:HIGH|LOW|INPUT|OUTPUT|INPUT_PULLUP|LED_BUILTIN|true|false)\b)|(\b(?:void|int|long|char|byte|bool|boolean|float|double|unsigned|short|const|static|String|uint8_t|uint16_t|uint32_t)\b)|(\b(?:if|else|for|while|do|switch|case|break|continue|return|default)\b)|(\b[A-Za-z_]\w*(?=\s*\())""",
            RegexOption.MULTILINE
        )
    }
}
