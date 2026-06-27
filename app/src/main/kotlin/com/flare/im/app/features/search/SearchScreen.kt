package com.flare.im.app.features.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flare.im.app.R
import com.flare.im.app.core.FlareAppStore
import com.flare.im.app.core.designsystem.FlareTheme
import com.flare.im.app.features.shell.SectionTitle

/** 搜索屏：关键词 → searchMessages/ByQuery/InConversation 结果。 */
@Composable
fun SearchScreen(store: FlareAppStore) {
    val tk = FlareTheme.tokens
    val colors = FlareTheme.colors
    val vm = store.searchViewModel
    val draft by vm.draft.collectAsState()
    val results by vm.results.collectAsState()
    Column(Modifier.fillMaxSize().padding(tk.lg)) {
        SectionTitle(stringResource(R.string.nav_search))
        OutlinedTextField(draft.keyword, { v -> vm.updateDraft { it.copy(keyword = v) } }, label = { Text("Keyword") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(tk.sm))
        Button(onClick = { vm.search() }, colors = ButtonDefaults.buttonColors(containerColor = colors.brand)) { Text(stringResource(R.string.nav_search), color = colors.outgoingText) }
        Spacer(Modifier.height(tk.md))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(tk.sm)) {
            items(results, key = { it.appStableId }) { m ->
                Column { Text(m.senderTitle, style = FlareTheme.type.captionStrong, color = colors.brand); Text(m.previewText, style = FlareTheme.type.callout, color = colors.textPrimary) }
            }
        }
    }
}
