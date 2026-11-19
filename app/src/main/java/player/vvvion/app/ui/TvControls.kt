package player.vvvion.app.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import player.vvvion.app.R

val LocalTelevision = compositionLocalOf { false }

private val FocusShape = RoundedCornerShape(14.dp)

/**
 * Visible TV/remote focus: accent border, slightly raised background, tiny scale.
 * No animation — weak TVs hitch on implicit transitions.
 */
fun Modifier.tvFocusable(
    enabled: Boolean = true,
    shape: Shape = FocusShape,
    onFocused: () -> Unit = {},
): Modifier = composed {
    if (!enabled) return@composed this
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    this
        .onFocusChanged { state ->
            val now = state.isFocused
            if (now && !focused) onFocused()
            focused = now
        }
        .graphicsLayer {
            val scale = if (focused) 1.03f else 1f
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(if (focused) scheme.surfaceContainerHighest else Color.Transparent, shape)
        .border(
            width = if (focused) 3.dp else 0.dp,
            color = if (focused) Accent else Color.Transparent,
            shape = shape,
        )
}

@Composable
fun ProvideTelevision(television: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTelevision provides television, content = content)
}

/**
 * A value row that is focusable with the D-pad. The IME opens only after OK/Enter
 * (or a click). Back or IME Done leaves edit mode and returns focus to the row.
 * On phones the regular text field is used so typing stays immediate.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onEditingChange: (Boolean) -> Unit = {},
) {
    val television = LocalTelevision.current
    if (!television) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged { onEditingChange(it.isFocused) },
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
        )
        return
    }

    var editing by remember { mutableStateOf(false) }
    var restoreRow by remember { mutableStateOf(false) }
    val rowFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }

    DisposableEffect(editing) {
        onEditingChange(editing)
        onDispose { if (editing) onEditingChange(false) }
    }

    if (editing) {
        BackHandler { restoreRow = true; editing = false }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = modifier
                .fillMaxWidth()
                .focusRequester(fieldFocus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val code = event.nativeKeyEvent.keyCode
                    if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                        restoreRow = true; editing = false
                        true
                    } else if (code == KeyEvent.KEYCODE_ENTER && singleLine) {
                        restoreRow = true; editing = false
                        true
                    } else {
                        false
                    }
                },
            singleLine = singleLine,
            keyboardOptions = keyboardOptions.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { restoreRow = true; editing = false }),
            visualTransformation = visualTransformation,
        )
        LaunchedEffect(Unit) {
            runCatching { fieldFocus.requestFocus() }
        }
    } else {
        LaunchedEffect(restoreRow) {
            if (restoreRow) {
                runCatching { rowFocus.requestFocus() }
                restoreRow = false
            }
        }
        val masked = if (visualTransformation === VisualTransformation.None) {
            value
        } else {
            visualTransformation.filter(androidx.compose.ui.text.AnnotatedString(value)).text.text
        }
        Column(
            modifier
                .fillMaxWidth()
                .focusRequester(rowFocus)
                .tvFocusable()
                .focusable()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = { editing = true },
                )
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val code = event.nativeKeyEvent.keyCode
                    if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
                        editing = true
                        true
                    } else {
                        false
                    }
                },
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Muted)
            Text(
                masked.ifBlank { stringResource(R.string.press_ok_to_edit) },
                style = MaterialTheme.typography.bodyLarge,
                color = if (masked.isBlank()) Muted else MaterialTheme.colorScheme.onSurface,
                maxLines = if (singleLine) 1 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun TvToggleRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .tvFocusable(shape = RoundedCornerShape(12.dp))
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = { onChange(!checked) },
            ),
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                modifier = Modifier.focusProperties { canFocus = false },
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun TvChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = modifier
            .tvFocusable(shape = RoundedCornerShape(8.dp))
            .heightIn(min = 44.dp),
    )
}

fun Modifier.tvControl(onFocused: () -> Unit = {}): Modifier = composed {
    val television = LocalTelevision.current
    this
        .then(if (television) Modifier.heightIn(min = 48.dp) else Modifier)
        .tvFocusable(enabled = television, shape = RoundedCornerShape(12.dp), onFocused = onFocused)
}
