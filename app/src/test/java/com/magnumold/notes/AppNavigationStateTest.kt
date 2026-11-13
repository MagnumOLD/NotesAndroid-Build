package com.magnumold.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationStateTest {
    @Test
    fun collectionAndEditorTransitionsResetOnlyTheExpectedState() {
        val state = AppNavigationState()
        state.openNewRecord(42L)
        assertEquals(42L, state.selectedId)
        assertTrue(state.autoFocusNewNote)

        state.openRecord(7L)
        assertEquals(7L, state.selectedId)
        assertFalse(state.autoFocusNewNote)

        state.selectCollection(15L)
        assertEquals(15L, state.activeCollectionId)
        assertNull(state.selectedId)

        state.openNewRecord(99L)
        state.closeEditor()
        assertNull(state.selectedId)
        assertFalse(state.autoFocusNewNote)
        assertEquals(15L, state.activeCollectionId)
    }

    @Test
    fun hidingSearchClearsQueryButShowingSearchDoesNot() {
        val state = AppNavigationState()
        state.updateSearch("meeting")
        state.toggleSearch()

        assertTrue(state.searchVisible)
        assertEquals("meeting", state.search)

        state.toggleSearch()
        assertFalse(state.searchVisible)
        assertEquals("", state.search)
    }

    @Test
    fun settingsAndVaultTransitionsRemainIndependentFromEditorSelection() {
        val state = AppNavigationState()
        state.openRecord(5L)
        state.openSettings()
        state.openVault("archive")

        assertTrue(state.settingsVisible)
        assertEquals("archive", state.vaultScreen)
        assertEquals(5L, state.selectedId)

        state.closeVault()
        assertNull(state.vaultScreen)
        assertTrue(state.settingsVisible)

        state.closeSettings()
        assertFalse(state.settingsVisible)
        assertEquals(5L, state.selectedId)
    }
}
