package player.vvvion.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import player.vvvion.app.R

@Composable
fun MessageBar(
    message: UiMessage?,
    onDismiss: (Long) -> Unit,
    onUndo: (UiMessage) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (message == null) return
    LaunchedEffect(message.id, message.dismissMs) {
        if (message.dismissMs > 0) {
            delay(message.dismissMs)
            onDismiss(message.id)
        }
    }
    val background = when (message.kind) {
        MessageKind.ERROR -> MaterialTheme.colorScheme.errorContainer
        MessageKind.SUCCESS -> Accent
        MessageKind.INFO -> MaterialTheme.colorScheme.secondaryContainer
    }
    val foreground = when (message.kind) {
        MessageKind.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        MessageKind.SUCCESS -> AccentInk
        MessageKind.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Row(
        modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .background(background, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = messageText(message),
            color = foreground,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (message.code == "group_removed") {
            Text(
                text = stringResource(R.string.undo),
                color = foreground,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .tvControl()
                    .clickable {
                        onUndo(message)
                        onDismiss(message.id)
                    }
                    .padding(8.dp),
            )
        }
        Text(
            text = stringResource(R.string.message_close),
            color = foreground,
            modifier = Modifier
                .padding(start = 12.dp)
                .focusProperties { canFocus = false }
                .clickable { onDismiss(message.id) }
                .padding(8.dp),
        )
    }
}

@Composable
fun BusyStrip(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    LinearProgressIndicator(
        modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false },
        color = Accent,
        trackColor = InkLine,
    )
}

@Composable
private fun messageText(message: UiMessage): String {
    val mapped = when (message.code) {
        "pin" -> stringResource(R.string.pin_wrong)
        "clipboard" -> stringResource(R.string.clipboard_empty)
        "cache" -> stringResource(R.string.cache_cleared)
        "update_none" -> stringResource(R.string.update_none)
        "update_failed" -> stringResource(R.string.update_failed)
        "install_permission" -> stringResource(R.string.update_install_permission)
        "url_invalid_scheme" -> stringResource(R.string.url_invalid_scheme)
        "url_invalid_empty" -> stringResource(R.string.url_invalid_empty)
        "playlist_added" -> stringResource(R.string.playlist_added)
        "playlist_updated" -> stringResource(R.string.playlist_updated)
        "phone_import_ok" -> stringResource(R.string.phone_import_ok)
        "group_removed" -> stringResource(R.string.group_removed, message.raw.orEmpty())
        "group_restored" -> stringResource(R.string.group_restored, message.raw.orEmpty())
        "groups_restored" -> stringResource(R.string.groups_restored)
        else -> null
    }
    return mapped ?: message.raw.orEmpty()
}
