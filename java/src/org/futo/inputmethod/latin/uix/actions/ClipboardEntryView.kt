package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.theme.Typography

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardEntryView(
    modifier: Modifier,
    clipboardEntry: ClipboardEntry,
    onPaste: (ClipboardEntry) -> Unit,
    onRemove: (ClipboardEntry) -> Unit,
    onPin: (ClipboardEntry) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .padding(2.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(),
                enabled = true,
                onClick = { onPaste(clipboardEntry) },
                onLongClick = { onPin(clipboardEntry) }
            ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            Row(modifier = Modifier.padding(0.dp)) {
                IconButton(
                    onClick = { onPin(clipboardEntry) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painterResource(id = R.drawable.push_pin),
                        contentDescription = if (clipboardEntry.pinned) {
                            stringResource(R.string.action_clipboard_manager_unpin_item)
                        } else {
                            stringResource(R.string.action_clipboard_manager_pin_item)
                        },
                        tint = if (clipboardEntry.pinned) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1.0f))

                IconButton(
                    onClick = { onRemove(clipboardEntry) },
                    modifier = Modifier.size(32.dp),
                    enabled = !clipboardEntry.pinned
                ) {
                    Icon(
                        painterResource(id = R.drawable.close),
                        contentDescription = stringResource(R.string.action_clipboard_manager_remove_item),
                        tint = if (clipboardEntry.pinned) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            val text = (clipboardEntry.text ?: "").let {
                if (it.length > 256) {
                    it.substring(0, 256) + "..."
                } else {
                    it
                }
            }

            Text(text, modifier = Modifier.padding(8.dp, 2.dp), style = Typography.SmallMl)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ClipboardEntryViewPreview() {
    val sampleText = listOf(
        "This is an entry",
        "Copying text a lot",
        "hunter2",
        "https://www.example.com/forum/viewpost/1234573193.html?parameter=1234"
    )
    LazyVerticalStaggeredGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = StaggeredGridCells.Adaptive(160.dp),
        verticalItemSpacing = 4.dp,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(sampleText.size) {
            ClipboardEntryView(
                modifier = Modifier,
                clipboardEntry = ClipboardEntry(0L, it % 2 == 0, sampleText[it], null, listOf()),
                onPin = {},
                onPaste = {},
                onRemove = {}
            )
        }
    }
}
