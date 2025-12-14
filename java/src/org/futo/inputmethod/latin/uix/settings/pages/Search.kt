package org.futo.inputmethod.latin.uix.settings.pages

import android.icu.text.Transliterator
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.LocalNavController
import org.futo.inputmethod.latin.uix.SettingsTextEdit
import org.futo.inputmethod.latin.uix.settings.BottomSpacer
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.SettingsMenus
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.theme.Typography

private val LATIN_ASCII = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    Transliterator.getInstance("Latin-ASCII")
} else {
    null
}

private fun normalizeString(s: String): String {
    return (LATIN_ASCII?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            it.transliterate(s)
        } else {
            null
        }
    } ?: s).lowercase()
}

private fun levenshteinDistance(a: String, b: String): Int {
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    val previousRow = IntArray(b.length + 1) { it }
    val currentRow = IntArray(b.length + 1)

    for (i in a.indices) {
        currentRow[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            currentRow[j + 1] = minOf(
                currentRow[j] + 1,
                previousRow[j + 1] + 1,
                previousRow[j] + cost
            )
        }
        for (j in currentRow.indices) {
            previousRow[j] = currentRow[j]
        }
    }

    return previousRow.last()
}

private fun jaroWinklerSimilarity(s1: String, s2: String): Double {
    if (s1 == s2) return 1.0
    if (s1.isEmpty() || s2.isEmpty()) return 0.0

    val maxDist = (maxOf(s1.length, s2.length) / 2) - 1
    val s1Matches = BooleanArray(s1.length)
    val s2Matches = BooleanArray(s2.length)

    var matches = 0
    var transpositions = 0

    for (i in s1.indices) {
        val start = (i - maxDist).coerceAtLeast(0)
        val end = (i + maxDist + 1).coerceAtMost(s2.length)

        for (j in start until end) {
            if (s2Matches[j]) continue
            if (s1[i] != s2[j]) continue
            s1Matches[i] = true
            s2Matches[j] = true
            matches++
            break
        }
    }

    if (matches == 0) return 0.0

    var k = 0
    for (i in s1.indices) {
        if (!s1Matches[i]) continue
        while (!s2Matches[k]) k++
        if (s1[i] != s2[k]) transpositions++
        k++
    }

    val m = matches.toDouble()
    val jaro = (m / s1.length + m / s2.length + (m - transpositions / 2.0) / m) / 3.0

    var prefix = 0
    while (prefix < minOf(4, s1.length, s2.length) && s1[prefix] == s2[prefix]) {
        prefix++
    }

    return jaro + prefix * 0.1 * (1 - jaro)
}

private fun fuzzyMatches(query: String, candidate: String): Boolean {
    if (candidate.contains(query) || query.contains(candidate)) return true

    val distanceThreshold = (query.length / 4).coerceAtLeast(1).coerceAtMost(3)
    val tokens = candidate.split("\n", " ").filter { it.isNotBlank() }

    val levenshteinHit = tokens.any { levenshteinDistance(query, it) <= distanceThreshold }
    val jaroWinklerHit = tokens.any { jaroWinklerSimilarity(query, it) >= 0.82 }

    return levenshteinHit || jaroWinklerHit
}

private fun matchesQuery(query: String, searchableParts: List<String>): Boolean {
    if (query.isBlank()) return false

    val queryTokens = query.split(" ").filter { it.isNotBlank() }

    val regex = runCatching { query.toRegex() }.getOrNull()
    if (regex != null && searchableParts.any { regex.containsMatchIn(it) }) return true

    // Require every query token to match at least one searchable token to better support
    // multi-word queries while still allowing minor mistakes.
    return queryTokens.all { token ->
        searchableParts.any { fuzzyMatches(token, it) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Preview(showBackground = true)
@Composable
fun SearchScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val textFieldValue = remember { mutableStateOf("") }

    val searchTagsByMenu = remember {
        SettingsMenus
            .flatMap { it.settings }
            .filter { it.name != 0 }
            .associate {
                it to run {
                    buildList {
                        add(normalizeString(context.getString(it.name)))
                        it.searchTagList?.forEach { tag -> add(normalizeString(context.getString(tag))) }
                        it.searchTags?.let { tag -> add(normalizeString(context.getString(tag))) }
                        it.subtitle?.let { subtitle -> add(normalizeString(context.getString(subtitle))) }
                    }.filter { value -> value.isNotBlank() }
                }
            }
    }

    val query = normalizeString(textFieldValue.value)
    val results = remember(query) {
        SettingsMenus.map { menu ->
            menu to menu.settings
                .filter { it.name != 0 && it.appearsInSearch }
                .filter { matchesQuery(query, searchTagsByMenu[it].orEmpty()) }
        }
    }.filter {
        it.first.visibilityCheck?.invoke() != false
    }.map { v ->
        v.first to v.second.mapNotNull {
            if(it.visibilityCheck?.invoke() == false) {
                if(it.appearInSearchIfVisibilityCheckFailed) {
                    userSettingDecorationOnly {
                        val nav = LocalNavController.current
                        NavigationItem(
                            title = stringResource(it.name),
                            style = NavigationItemStyle.MiscNoArrow,
                            subtitle = stringResource(
                                R.string.settings_search_option_exists_but_disabled,
                                stringResource(v.first.title)
                            ),
                            navigate = {
                                nav!!.navigate(v.first.navPath)
                            }
                        )
                    }
                } else {
                    null
                }
            } else {
                it
            }
        }
    }.filter {
        it.second.isNotEmpty()
    }

    LazyColumn {
        item {
            Box(Modifier.padding(8.dp)) {
                SettingsTextEdit(
                    text = textFieldValue,
                    multiline = true,
                    icon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.settings_search_menu_title)
                        )
                    },
                    autofocus = true
                )
            }
        }


        if(query.isBlank()) {
            item {
                Text(
                    stringResource(R.string.settings_search_enter_your_search),
                    style = Typography.Heading.Medium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                )
            }
        } else if(results.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.settings_search_no_options_found),
                    style = Typography.Heading.Medium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                )
            }
        } else {
            results.forEach {
                val menu = it.first
                val settings = it.second
                item {
                    val nav = LocalNavController.current
                    Row(Modifier
                        .clickable {
                            nav!!.navigate(menu.navPath)
                        }
                        .padding(16.dp)) {
                        Text(
                            stringResource(menu.title),
                            style = Typography.Heading.Medium,
                            modifier = Modifier
                                .align(CenterVertically)
                                .weight(1.0f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
                items(settings) {
                    it.component()
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                }
            }

            item { BottomSpacer() }
        }
    }
}