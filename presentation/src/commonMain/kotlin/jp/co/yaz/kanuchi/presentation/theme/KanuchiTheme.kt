package jp.co.yaz.kanuchi.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.noto_sans_jp
import org.jetbrains.compose.resources.Font

/**
 * Kanuchi (鍛冶) 全体で使うテーマ。
 *
 * 本アプリは日本語のみの利用を想定しているため、日本語の標準的な等幅ではないゴシック体である
 * Noto Sans JP (SIL Open Font License 1.1) を全テキストスタイルに適用する。
 * ライセンス全文は presentation/licenses/NotoSansJP-OFL.txt を参照。
 */
@Composable
fun KanuchiTheme(content: @Composable () -> Unit) {
    val notoSansJp = FontFamily(Font(Res.font.noto_sans_jp))
    val typography = kanuchiTypography(notoSansJp)

    MaterialTheme(typography = typography, content = content)
}

private fun kanuchiTypography(fontFamily: FontFamily): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
    )
}
