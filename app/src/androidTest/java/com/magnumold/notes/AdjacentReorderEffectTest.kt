package com.magnumold.notes

import android.os.SystemClock
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalFoundationApi::class)
@RunWith(AndroidJUnit4::class)
class AdjacentReorderEffectTest {

    @get:Rule
    val composeRule = createComposeRule()
    private lateinit var listState: LazyListState

    private fun note(id: Long, pinned: Boolean = false) =
        Note(id, "", listOf(NoteBlock("note $id")), id, pinned = pinned)

    @Test
    fun firstTwoRowsSwapAndCanReverseAfterLayoutAcknowledgement() {
        val rows = listOf(
            DragRow(listOf(note(1)), "1"),
            DragRow(listOf(note(2)), "2"),
            DragRow(listOf(note(3)), "3")
        )
        var session by mutableStateOf<ReorderSession?>(
            AdjacentReorderEngine.begin(rows.map { it.key }, "1")
        )
        var pointerY by mutableFloatStateOf(0f)
        var dragging by mutableStateOf(true)

        composeRule.setContent {
            val density = LocalDensity.current
            listState = rememberLazyListState()
            val currentRows = session?.keys?.mapNotNull { key -> rows.firstOrNull { it.key == key } }.orEmpty()

            AdjacentReorderEffect(
                isDragging = dragging,
                orderedKeys = currentRows.map { it.key },
                reorderSession = session,
                pointerY = pointerY,
                grabOffsetPx = with(density) { 44.dp.toPx() },
                releaseRequested = false,
                listState = listState,
                density = density,
                onSessionChange = { session = it },
                onFinishDrag = { dragging = false }
            )

            LazyColumn(state = listState) {
                item(key = "notes_reorder_viewport_anchor") { Spacer(Modifier.height(1.dp)) }
                items(currentRows, key = { it.key }) { row ->
                    Box(Modifier.fillMaxWidth().height(88.dp).animateItemPlacement(tween(220))) {
                        Text(row.key)
                    }
                }
            }
        }

        composeRule.waitUntil(2_000) { listState.layoutInfo.visibleItemsInfo.any { it.key == "1" } }
        composeRule.runOnUiThread {
            pointerY = with(composeRule.density) { 134.dp.toPx() }
        }
        composeRule.waitUntil(3_000) {
            session?.phase == ReorderPhase.Tracking &&
                session?.keys == listOf("2", "1", "3")
        }
        composeRule.runOnUiThread {
            pointerY = with(composeRule.density) { 44.dp.toPx() }
        }
        composeRule.waitUntil(3_000) {
            session?.phase == ReorderPhase.Tracking &&
                session?.keys == listOf("1", "2", "3")
        }
        composeRule.runOnUiThread { dragging = false }
    }

    @Test
    fun groupUsesItsMeasuredHeightAndCannotCrossPinnedBoundary() {
        val rows = listOf(
            DragRow(listOf(note(1, pinned = true)), "1"),
            DragRow(listOf(note(2), note(3)), "group"),
            DragRow(listOf(note(4)), "4"),
            DragRow(listOf(note(5)), "5")
        )
        var session by mutableStateOf<ReorderSession?>(
            AdjacentReorderEngine.begin(rows.map { it.key }, "group")
        )
        var pointerY by mutableFloatStateOf(0f)
        var dragging by mutableStateOf(true)
        lateinit var listState: LazyListState

        composeRule.setContent {
            val density = LocalDensity.current
            listState = rememberLazyListState()
            val currentRows = session?.keys?.mapNotNull { key -> rows.firstOrNull { it.key == key } }.orEmpty()

            AdjacentReorderEffect(
                isDragging = dragging,
                orderedKeys = currentRows.map { it.key },
                reorderSession = session,
                pointerY = pointerY,
                grabOffsetPx = with(density) { 60.dp.toPx() },
                releaseRequested = false,
                listState = listState,
                density = density,
                onSessionChange = { session = it },
                onFinishDrag = { dragging = false }
            )

            LazyColumn(state = listState) {
                item(key = "notes_reorder_viewport_anchor") { Spacer(Modifier.height(1.dp)) }
                items(currentRows, key = { it.key }) { row ->
                    Box(
                        Modifier.fillMaxWidth()
                            .height(if (row.notes.size > 1) 120.dp else 88.dp)
                            .animateItemPlacement(tween(220))
                    ) { Text(row.key) }
                }
            }
        }

        composeRule.waitUntil(2_000) { listState.layoutInfo.visibleItemsInfo.any { it.key == "group" } }
        composeRule.runOnUiThread {
            // The next row center is 1 + 88 + 120 + 44 = 253dp.
            // With the actual 120dp group height, its center crosses at 254dp.
            pointerY = with(composeRule.density) { 254.dp.toPx() }
        }
        composeRule.waitUntil(3_000) {
            session?.phase == ReorderPhase.Tracking &&
                session?.keys == listOf("1", "4", "group", "5")
        }

        composeRule.runOnUiThread {
            // Reverse over the previous unpinned row.
            pointerY = with(composeRule.density) { 132.dp.toPx() }
        }
        composeRule.waitUntil(3_000) {
            session?.phase == ReorderPhase.Tracking &&
                session?.keys == listOf("1", "group", "4", "5")
        }

        composeRule.runOnUiThread {
            // The next upward crossing would enter the pinned partition and
            // must be rejected even though the group has a valid drag geometry.
            pointerY = with(composeRule.density) { 44.dp.toPx() }
        }
        SystemClock.sleep(100)
        composeRule.runOnUiThread {
            assertEquals(listOf("1", "group", "4", "5"), session?.keys)
            assertEquals(ReorderPhase.Tracking, session?.phase)
            dragging = false
        }
    }
}
