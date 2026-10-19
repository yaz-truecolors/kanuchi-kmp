package jp.co.yaz.kanuchi.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_data_load_error_message
import kanuchi.presentation.generated.resources.common_retry_button
import kanuchi.presentation.generated.resources.ic_check_circle
import kanuchi.presentation.generated.resources.ic_error
import kanuchi.presentation.generated.resources.ic_inbox
import kanuchi.presentation.generated.resources.ic_refresh
import kanuchi.presentation.generated.resources.ic_warning
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// アイコン (composeResources/drawable/ic_*.xml。Material Symbols Outlined) を使う共通の部品。
// アクセシビリティ対応を行わない方針のため、アイコンの contentDescription は付けない (null にする)。

/**
 * ボタンの中身 (アイコン＋文字)。`Button { ButtonLabel(...) }` のようにボタンの content に置く。
 * [inProgress] の間 (保存中等) は、アイコンの代わりに同じ大きさの読み込み中の表示を出す (ボタンの大きさを変えないため)。
 */
@Composable
fun ButtonLabel(
    icon: DrawableResource,
    text: String,
    inProgress: Boolean = false,
) {
    if (inProgress) {
        CircularProgressIndicator(
            modifier = Modifier.size(ButtonDefaults.IconSize),
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
        )
    } else {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    }
    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
    Text(text)
}

/**
 * 標準の大きさ (24dp)・今の文字色のアイコン。入力欄の先頭 (`OutlinedTextField(leadingIcon = { SimpleIcon(...) })`) や
 * 確認ダイアログの上部 (`AlertDialog(icon = { SimpleIcon(...) })`) に置く。
 */
@Composable
fun SimpleIcon(icon: DrawableResource) {
    Icon(painterResource(icon), contentDescription = null)
}

/**
 * アイコン付きの文字 (状態の表示・見出し等)。アイコンは文字の1行目にそろえる。
 *
 * @param iconTint アイコンの色。指定しなければ [color] (それも無ければ今の文字色) と同じにする
 */
@Composable
fun IconText(
    icon: DrawableResource,
    text: String,
    color: Color = Color.Unspecified,
    iconTint: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    iconSize: Dp = 18.dp,
) {
    val textColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = iconTint.takeOrElse { textColor },
        )
        Text(text = text, color = color, style = style)
    }
}

/** 画面内の区切り (セクション) の見出し。 */
@Composable
fun SectionHeader(
    icon: DrawableResource,
    text: String,
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
    IconText(icon = icon, text = text, style = style, iconTint = MaterialTheme.colorScheme.primary, iconSize = 24.dp)
}

/** [StatusMessage] の種類。種類ごとにアイコンと色が決まる。 */
enum class StatusKind {
    /** 保存できた等 */
    SUCCESS,

    /** 保存できなかった等 (操作をやり直す必要がある) */
    ERROR,

    /** 過不足がある等 (保存はできるが、確認してほしい) */
    WARNING,
}

/** 操作の結果・注意を知らせるメッセージ (アイコン＋文字)。エラー・警告は文字も赤にする。 */
@Composable
fun StatusMessage(
    text: String,
    kind: StatusKind,
) {
    val error = MaterialTheme.colorScheme.error
    when (kind) {
        StatusKind.SUCCESS ->
            IconText(Res.drawable.ic_check_circle, text, iconTint = MaterialTheme.colorScheme.primary, iconSize = 20.dp)
        StatusKind.ERROR -> IconText(Res.drawable.ic_error, text, color = error, iconSize = 20.dp)
        StatusKind.WARNING -> IconText(Res.drawable.ic_warning, text, color = error, iconSize = 20.dp)
    }
}

/** 一覧に表示するものが無いとき (「案件はまだありません。」等) の表示。 */
@Composable
fun EmptyMessage(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painterResource(Res.drawable.ic_inbox),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** データの読み込みに失敗したときの表示 (共通のエラー文言と「再読み込み」ボタン)。 */
@Composable
fun LoadErrorMessage(onRetry: () -> Unit) {
    StatusMessage(stringResource(Res.string.common_data_load_error_message), StatusKind.ERROR)
    TextButton(onClick = onRetry) {
        ButtonLabel(Res.drawable.ic_refresh, stringResource(Res.string.common_retry_button))
    }
}
