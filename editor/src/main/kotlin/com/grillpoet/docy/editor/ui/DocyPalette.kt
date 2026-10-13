package com.grillpoet.docy.editor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.grillpoet.docy.editor.highlight.Highlighter

object DocyPalettes {

    @Composable
    fun mono(): Highlighter.Palette {
        val cs = MaterialTheme.colorScheme
        val base = cs.onSurface
        return remember(cs) {
            Highlighter.Palette(
                keyword = base,
                type = base.copy(alpha = 0.86f),
                function = base.copy(alpha = 0.82f),
                literal = base.copy(alpha = 0.72f),
                number = base.copy(alpha = 0.66f),
                string = base.copy(alpha = 0.58f),
                comment = base.copy(alpha = 0.42f),
                operator = base.copy(alpha = 0.78f),
                library = base.copy(alpha = 0.90f),
                annotation = base.copy(alpha = 0.88f),
                punctuation = base.copy(alpha = 0.55f)
            )
        }
    }

    @Composable
    fun colorfulDark(): Highlighter.Palette = remember {
        Highlighter.Palette(
            keyword = Color(0xFFC678DD),
            type = Color(0xFFE5C07B),
            function = Color(0xFF61AFEF),
            literal = Color(0xFFD19A66),
            number = Color(0xFFD19A66),
            string = Color(0xFF98C379),
            comment = Color(0xFF5C6370),
            operator = Color(0xFF56B6C2),
            library = Color(0xFF61AFEF),
            annotation = Color(0xFFE5C07B),
            punctuation = Color(0xFFABB2BF)
        )
    }

    @Composable
    fun colorfulLight(): Highlighter.Palette = remember {
        Highlighter.Palette(
            keyword = Color(0xFFA626A4),
            type = Color(0xFFC18401),
            function = Color(0xFF4078F2),
            literal = Color(0xFF986801),
            number = Color(0xFF986801),
            string = Color(0xFF50A14F),
            comment = Color(0xFFA0A1A7),
            operator = Color(0xFF0184BC),
            library = Color(0xFF4078F2),
            annotation = Color(0xFFC18401),
            punctuation = Color(0xFF383A42)
        )
    }
}

@Composable
fun docyPalette(colorful: Boolean): Highlighter.Palette {
    if (!colorful) return DocyPalettes.mono()
    val dark = isSystemInDarkTheme()
    return if (dark) DocyPalettes.colorfulDark() else DocyPalettes.colorfulLight()
}
