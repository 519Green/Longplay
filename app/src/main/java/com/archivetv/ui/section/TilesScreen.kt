package com.archivetv.ui.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.archivetv.ArchiveTvApp
import com.archivetv.domain.CategoryNode
import com.archivetv.ui.common.LabelCard
import com.archivetv.ui.theme.Dimens

/** "Browse by" grid: one tile per child of a group (genres, decades, shows...). */
@Composable
fun TilesScreen(
    groupId: String,
    onOpenGroup: (String) -> Unit,
    onOpenLeaf: (String) -> Unit,
) {
    val app = LocalContext.current.applicationContext as ArchiveTvApp
    val group = app.container.catalog.group(groupId) ?: run {
        Text("Unknown category")
        return
    }
    val parent = remember(groupId) {
        app.container.catalog.sections.firstOrNull { s -> s.children.any { it.id == groupId } }
    }
    val firstFocus = remember { FocusRequester() }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!initialFocusDone) {
            runCatching { firstFocus.requestFocus() }
            initialFocusDone = true
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = Dimens.OverscanHorizontal, top = Dimens.OverscanVertical),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            listOfNotNull(parent?.title, group.title).joinToString(" · "),
            style = MaterialTheme.typography.headlineSmall,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(
                top = Dimens.RowFocusRoom,
                end = Dimens.OverscanHorizontal,
                bottom = Dimens.OverscanVertical,
            ),
            horizontalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
        ) {
            itemsIndexed(group.children, key = { _, n -> n.id }) { index, node ->
                LabelCard(
                    label = node.title,
                    width = null,
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = {
                        when (node) {
                            is CategoryNode.Group -> onOpenGroup(node.id)
                            is CategoryNode.Leaf -> onOpenLeaf(node.id)
                        }
                    },
                )
            }
        }
    }
}
