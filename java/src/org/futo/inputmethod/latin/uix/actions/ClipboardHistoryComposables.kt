package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import android.util.Log
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.DialogRequestItem
// Replaced UixManager with KeyboardManagerForAction as per Action.kt definition
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.actions.ClipboardShowPinnedOnTop
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.pages.ParagraphText
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurface
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurfaceHeading
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
// Removed TextField imports as search now uses ActionTextEditor
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import org.futo.inputmethod.latin.uix.ActionTextEditor
import org.futo.inputmethod.latin.uix.UriThumbnail
import kotlin.math.min

private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
    val lhsLen = lhs.length
    val rhsLen = rhs.length
    var cost = IntArray(lhsLen + 1) { it }
    for (i in 1..rhsLen) {
        val newCost = IntArray(lhsLen + 1)
        newCost[0] = i
        for (j in 1..lhsLen) {
            val match = if (lhs[j - 1].lowercaseChar() == rhs[i - 1].lowercaseChar()) 0 else 1
            val costReplace = cost[j - 1] + match
            val costInsert = cost[j] + 1
            val costDelete = newCost[j - 1] + 1
            newCost[j] = minOf(costInsert, costDelete, costReplace)
        }
        cost = newCost
    }
    return cost[lhsLen]
}

private fun findAllOccurrences(text: String, query: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var startIndex = 0
    while (startIndex < text.length) {
        val index = text.indexOf(query, startIndex, ignoreCase = true)
        if (index == -1) break
        ranges.add(index until (index + query.length))
        startIndex = index + query.length
    }
    return ranges
}

private fun regexMatchRanges(text: String, pattern: String): List<IntRange>? {
    return try {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        val ranges = regex.findAll(text).map { it.range }.toList()
        if (ranges.isNotEmpty()) ranges else null
    } catch (e: Exception) {
        null
    }
}

private fun fuzzyMatchRange(text: String, query: String): IntRange? {
    val normalizedText = text.lowercase()
    val normalizedQuery = query.lowercase()
    val queryLength = normalizedQuery.length
    if (queryLength == 0) return null

    val maxDistance = (queryLength * 0.4).toInt().coerceAtLeast(1)

    if (normalizedText.length <= queryLength) {
        val distance = levenshteinDistance(normalizedQuery, normalizedText)
        return if (distance <= maxDistance) {
            0 until normalizedText.length
        } else {
            null
        }
    }

    var bestRange: IntRange? = null
    var bestDistance = Int.MAX_VALUE
    for (i in 0..normalizedText.length - queryLength) {
        val window = normalizedText.substring(i, i + queryLength)
        val distance = levenshteinDistance(normalizedQuery, window)
        if (distance < bestDistance) {
            bestDistance = distance
            bestRange = i until (i + queryLength)
            if (bestDistance == 0) break
        }
    }

    return if (bestDistance <= maxDistance) bestRange else null
}

private data class ClipboardSearchResult(
    val entry: ClipboardEntry,
    val highlightRanges: List<IntRange>,
    val score: Double
)

private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
    if (ranges.isEmpty()) return emptyList()

    val sorted = ranges.sortedBy { it.first }
    val merged = mutableListOf<IntRange>()
    var current = sorted.first()

    for (i in 1 until sorted.size) {
        val next = sorted[i]
        current = if (next.first <= current.last + 1) {
            IntRange(current.first, maxOf(current.last, next.last))
        } else {
            merged.add(current)
            next
        }
    }

    merged.add(current)
    return merged
}

private fun evaluateClipboardSearch(entry: ClipboardEntry, query: String): ClipboardSearchResult? {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) {
        return ClipboardSearchResult(entry, emptyList(), 0.0)
    }

    val text = entry.text ?: return null

    // Prefer regex if the user explicitly provided a pattern that compiles.
    val regexRanges = regexMatchRanges(text, trimmedQuery)
    if (!regexRanges.isNullOrEmpty()) {
        return ClipboardSearchResult(entry, regexRanges, 3.0 + regexRanges.size)
    }

    val terms = trimmedQuery.split(" ").filter { it.isNotBlank() }
    if (terms.isEmpty()) {
        return ClipboardSearchResult(entry, emptyList(), 0.0)
    }

    val matchedRanges = mutableListOf<IntRange>()
    var score = 0.0

    for (term in terms) {
        val directMatches = findAllOccurrences(text, term)
        if (directMatches.isNotEmpty()) {
            matchedRanges.addAll(directMatches)
            score += 2.0 + (directMatches.size * 0.1)
            continue
        }

        val fuzzyRange = fuzzyMatchRange(text, term)
        if (fuzzyRange != null) {
            matchedRanges.add(fuzzyRange)
            val matchedText = text.substring(fuzzyRange.first, fuzzyRange.last + 1)
            val distance = levenshteinDistance(matchedText, term)
            val similarity = 1.0 - (distance.toDouble() / maxOf(matchedText.length, term.length))
            score += 1.0 + similarity
            continue
        }

        // Fail fast if any individual term cannot be matched at all.
        return null
    }

    val mergedRanges = mergeRanges(matchedRanges)
    return ClipboardSearchResult(entry, mergedRanges, score)
}

@OptIn(ExperimentalFoundationApi::class) // Restored OptIn for combinedClickable
@Composable
fun ClipboardEntryView(modifier: Modifier, clipboardEntry: ClipboardEntry, highlightRanges: List<IntRange>, onPaste: (ClipboardEntry) -> Unit, onRemove: (ClipboardEntry) -> Unit, onPin: (ClipboardEntry) -> Unit) {
    val textToDisplay = clipboardEntry.text ?: ""
    val validRanges = highlightRanges.mapNotNull { range ->
        val clampedStart = range.first.coerceAtLeast(0)
        val clampedEnd = min(range.last, textToDisplay.length - 1)
        if (clampedStart <= clampedEnd) IntRange(clampedStart, clampedEnd) else null
    }.sortedBy { it.first }

    val annotatedText = buildAnnotatedString {
        if (validRanges.isNotEmpty()) {
            var currentIndex = 0
            validRanges.forEach { range ->
                if (currentIndex < range.first) {
                    append(textToDisplay.substring(currentIndex, range.first))
                }
                val endExclusive = (range.last + 1).coerceAtMost(textToDisplay.length)
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, background = Color.Yellow.copy(alpha = 0.5f))) {
                    append(textToDisplay.substring(range.first, endExclusive))
                }
                currentIndex = endExclusive
            }
            if (currentIndex < textToDisplay.length) {
                append(textToDisplay.substring(currentIndex))
            }
        } else {
            append(textToDisplay)
        }
    }

    val displayedText = if (annotatedText.text.length > 256) {
        buildAnnotatedString {
            append(annotatedText.subSequence(0, 256))
        }
    } else {
        annotatedText
    }

    val hasImage = clipboardEntry.uri != null

    // Track how many characters fit in the first line
    var firstLineEndIndex by remember { mutableStateOf(displayedText.text.length) }

    // Calculate remaining text after first line
    val remainingText = remember(displayedText, firstLineEndIndex) {
        if (firstLineEndIndex < displayedText.text.length) {
            displayedText.subSequence(firstLineEndIndex, displayedText.text.length)
        } else {
            null
        }
    }

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
            // Header row: Pin icon, first line of text, Close icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Pin icon
                IconButton(
                    onClick = { onPin(clipboardEntry) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painterResource(id = R.drawable.push_pin),
                        contentDescription = if(clipboardEntry.pinned) {
                            stringResource(R.string.action_clipboard_manager_unpin_item)
                        } else {
                            stringResource(R.string.action_clipboard_manager_pin_item)
                        },
                        tint = if(clipboardEntry.pinned) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }

                // First line of text between icons
                Text(
                    displayedText,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp, bottom = 0.dp),
                    style = Typography.SmallMl,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                    onTextLayout = { textLayoutResult ->
                        // Get the end offset of the first line
                        if (textLayoutResult.lineCount > 0) {
                            val lineEnd = textLayoutResult.getLineEnd(0, visibleEnd = true)
                            if (lineEnd != firstLineEndIndex) {
                                firstLineEndIndex = lineEnd
                            }
                        }
                    }
                )

                // Close icon
                IconButton(
                    onClick = { onRemove(clipboardEntry) },
                    modifier = Modifier.size(32.dp),
                    enabled = !clipboardEntry.pinned
                ) {
                    Icon(
                        painterResource(id = R.drawable.close),
                        contentDescription = stringResource(R.string.action_clipboard_manager_remove_item),
                        tint = if(clipboardEntry.pinned) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Remaining text below icons - only show what wasn't in header
            // Use negative offset to reduce gap caused by IconButton height in the Row
            if (remainingText != null && remainingText.text.isNotEmpty()) {
                Text(
                    remainingText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-8).dp)
                        .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                    style = Typography.SmallMl
                )
            }

            if (hasImage) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    UriThumbnail(
                        uri = clipboardEntry.uri!!,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ClipboardEntryViewPreview() {
    val sampleText = listOf("This is an entry with searchterm", "Copying text a lot", "searchterm here too", "https://www.example.com/forum/viewpost/1234573193.html?parameter=1234")
    val searchQuery = "searchterm"
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
                highlightRanges = findAllOccurrences(sampleText[it], searchQuery),
                onPin = {},
                onPaste = {},
                onRemove = {}
            )
        }
    }
}

@Composable
fun ClipboardHistoryWindowTitleBar(
    rowScope: RowScope,
    manager: KeyboardManagerForAction, // Changed UixManager to KeyboardManagerForAction
    clipboardHistoryManager: ClipboardHistoryManager,
    unlocked: Boolean
) {
    val context = LocalContext.current
    val clipboardHistoryEnabledState = useDataStore(ClipboardHistoryEnabled, blocking = true)
    val showPinnedOnTopState = useDataStore(ClipboardShowPinnedOnTop, blocking = true)

    // Keep the search query state in sync with the manager so the text editor
    // can live in the title bar.
    val searchText = remember { mutableStateOf(manager.getClipboardSearchQuery()) }
    LaunchedEffect(manager.getClipboardSearchQuery()) {
        val managerText = manager.getClipboardSearchQuery()
        if (managerText != searchText.value) {
            searchText.value = managerText
        }
    }
    LaunchedEffect(searchText.value) {
        manager.setClipboardSearchQuery(searchText.value)
    }

    if (!clipboardHistoryEnabledState.value) return

    with(rowScope) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = LocalKeyboardScheme.current.keyboardContainer,
                contentColor = LocalKeyboardScheme.current.onKeyboardContainer,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    // Automatically focus the search field just like the emoji search bar
                    ActionTextEditor(text = searchText)
                }
            }

            if (unlocked && !clipboardHistoryManager.clipboardIOFailure.value) {
                IconButton(onClick = {
                    val numUnpinnedItems = clipboardHistoryManager.clipboardHistory.count { !it.pinned }
                    if (clipboardHistoryManager.clipboardHistory.isEmpty()) {
                        manager.requestDialog(
                            context.getString(R.string.action_clipboard_manager_disable_text),
                            listOf(
                                DialogRequestItem(context.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                DialogRequestItem(context.getString(R.string.action_clipboard_manager_disable_button)) {
                                    clipboardHistoryEnabledState.setValue(false)
                                },
                            ),
                            {}
                        )
                    } else if (numUnpinnedItems == 0) {
                        manager.requestDialog(
                            context.getString(R.string.action_clipboard_manager_unpin_all_items_text),
                            listOf(
                                DialogRequestItem(context.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                DialogRequestItem(context.getString(R.string.action_clipboard_manager_unpin_all_items_button)) {
                                    clipboardHistoryManager.clipboardHistory.toList().forEach {
                                        if (it.pinned) {
                                            clipboardHistoryManager.onPin(it)
                                        }
                                    }
                                },
                            ),
                            {}
                        )
                    } else {
                        manager.requestDialog(
                            context.getString(R.string.action_clipboard_manager_clear_unpinned_items_text),
                            listOf(
                                DialogRequestItem(context.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                DialogRequestItem(context.getString(R.string.action_clipboard_manager_clear_unpinned_items_button)) {
                                    clipboardHistoryManager.clipboardHistory.toList().forEach {
                                        if (!it.pinned) {
                                            clipboardHistoryManager.onRemove(it)
                                        }
                                    }
                                },
                            ),
                            {}
                        )
                    }
                }) {
                    Icon(
                        painterResource(id = R.drawable.close),
                        contentDescription = stringResource(R.string.action_clipboard_manager_clear_clipboard)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardHistoryWindowContent(
    manager: KeyboardManagerForAction, // Changed UixManager to KeyboardManagerForAction
    clipboardHistoryManager: ClipboardHistoryManager,
    unlocked: Boolean,
    keyboardShown: Boolean
) {
    val view = LocalView.current
    val context = LocalContext.current
    val clipboardHistoryEnabledState = useDataStore(ClipboardHistoryEnabled, blocking = true)
    val showPinnedOnTopState = useDataStore(ClipboardShowPinnedOnTop, blocking = true)

    Column(modifier = Modifier.fillMaxWidth()) {
        // Title bar now owns the search field; keep focus reset on close.
        DisposableEffect(Unit) {
            onDispose {
                manager.setClipboardSearchFocus(false)
            }
        }

        // Focus is handled internally by ActionTextEditor so we no longer
        // manage focus with a FocusRequester.

        // Keep this for general composition logging
        LaunchedEffect(Unit) {
            Log.d("ClipboardSearch", "[TestLE ClipboardHistoryWindowContent] Composed. This shows the composable itself is being composed.")
        }

        // Keep this to observe the manager's clipboard search focus state for diagnostics
        // This is the LCE I referred to as "[State LaunchedEffect...]" in previous comments
        val isClipboardSearchFocusedStateObserved = manager.isClipboardSearchFocusedState()
        LaunchedEffect(isClipboardSearchFocusedStateObserved.value) { // Note: isClipboardSearchFocusedStateObserved.value is the same as shouldSearchBeFocused
            Log.d("ClipboardSearch", "[StateLE isClipboardSearchFocused] manager.isClipboardSearchFocusedState changed to: ${isClipboardSearchFocusedStateObserved.value}")
            // No actions here, just logging this state's changes.
        }

        if (!unlocked) {
            ScrollableList {
                PaymentSurface(isPrimary = true) {
                PaymentSurfaceHeading(title = stringResource(R.string.action_clipboard_manager_error_device_locked_title))
                ParagraphText(stringResource(R.string.action_clipboard_manager_error_device_locked_text))
            }
        }
    } else if (clipboardHistoryManager.clipboardIOFailure.value) {
        ScrollableList {
            PaymentSurface(isPrimary = true) {
                PaymentSurfaceHeading(title = stringResource(R.string.action_clipboard_manager_error_general_title))
                ParagraphText(
                    stringResource(
                        R.string.action_clipboard_manager_error_general_text,
                        clipboardHistoryManager.clipboardIOFailureReason
                    )
                )
                Button(onClick = {
                    manager.activateAction(BugViewerAction)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_clipboard_manager_inspect_error_via_bugs_action))
                }
                Button(onClick = {
                    clipboardHistoryManager.saveClipboard()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_clipboard_manager_retry_saving_loading))
                }
                Button(onClick = {
                    manager.requestDialog(
                        context.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_text),
                        listOf(
                            DialogRequestItem(context.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                            DialogRequestItem(context.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_button)) {
                                clipboardHistoryManager.clipboardIOFailure.value = false
                                clipboardHistoryEnabledState.setValue(false)
                                clipboardHistoryManager.deleteClipboard()
                            },
                        ),
                        {}
                    )
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(context.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_button))
                }
            }
        }
    } else if (!clipboardHistoryEnabledState.value) {
        ScrollableList {
            PaymentSurface(isPrimary = true) {
                PaymentSurfaceHeading(title = stringResource(R.string.action_clipboard_manager_error_clipboard_history_disabled_title))
                ParagraphText(
                    stringResource(
                        R.string.action_clipboard_manager_error_clipboard_history_disabled_text_v2,
                        context.getSetting(ClipboardHistoryItemsToKeep),
                        (context.getSetting(ClipboardHistoryTimeToKeep) / 24.0f).roundToInt()
                    )
                )
                Button(onClick = {
                    clipboardHistoryEnabledState.setValue(true)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_clipboard_manager_enable_clipboard_history_button))
                }
            }
        }
    } else {
        val currentSearchQuery = manager.getClipboardSearchQuery()
        val sortComparator = if (showPinnedOnTopState.value) {
            compareByDescending<ClipboardSearchResult> { it.score }
                .thenByDescending { it.entry.pinned }
                .thenByDescending { it.entry.timestamp }
        } else {
            compareByDescending<ClipboardSearchResult> { it.score }
                .thenByDescending { it.entry.timestamp }
        }

        val filteredList = clipboardHistoryManager.clipboardHistory.toList()
            .mapNotNull { evaluateClipboardSearch(it, currentSearchQuery) }
            .sortedWith(sortComparator)

        LazyVerticalStaggeredGrid(
            modifier = Modifier.fillMaxWidth(),
            columns = StaggeredGridCells.Adaptive(140.dp),
            verticalItemSpacing = 4.dp,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                items = filteredList,
                key = { entry ->
                    // Ensure unique keys, especially if text can be null or identical
                    (entry.entry.text ?: "") + entry.entry.timestamp.toString()
                }
            ) { entry ->
                ClipboardEntryView(
                    modifier = Modifier.animateItemPlacement(),
                    clipboardEntry = entry.entry,
                    highlightRanges = entry.highlightRanges,
                    onPaste = {
                        if (it.uri != null) {
                            manager.typeUri(it.uri, it.mimeTypes)
                        } else if (it.text != null) {
                            manager.typeText(it.text)
                        }
                        clipboardHistoryManager.onPaste(it)
                        manager.performHapticAndAudioFeedback(Constants.CODE_OUTPUT_TEXT, view)
                    },
                    onRemove = {
                        clipboardHistoryManager.onRemove(it)
                        manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)
                    },
                    onPin = {
                        clipboardHistoryManager.onPin(it)
                        manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)
                    }
                )
            }
        }
    }
}
}
