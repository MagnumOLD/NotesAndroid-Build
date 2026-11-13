package com.magnumold.notes

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Owns transient navigation and view state for the app's top-level screens.
 * It deliberately contains no persistence or UI logic.
 */
@Stable
internal class AppNavigationState {
    var activeCollectionId by mutableStateOf<Long?>(null)
        private set
    var selectedId by mutableStateOf<Long?>(null)
        private set
    var autoFocusNewNote by mutableStateOf(false)
        private set
    var search by mutableStateOf("")
        private set
    var searchVisible by mutableStateOf(false)
        private set
    var settingsVisible by mutableStateOf(false)
        private set
    var vaultScreen by mutableStateOf<String?>(null)
        private set
    fun selectCollection(id: Long?) {
        activeCollectionId = id
        selectedId = null
    }

    fun openRecord(id: Long) {
        autoFocusNewNote = false
        selectedId = id
    }

    fun openNewRecord(id: Long) {
        autoFocusNewNote = true
        selectedId = id
    }

    fun closeEditor() {
        selectedId = null
        autoFocusNewNote = false
    }

    fun updateSearch(query: String) {
        search = query
    }

    fun toggleSearch() {
        searchVisible = !searchVisible
        if (!searchVisible) search = ""
    }

    fun openSettings() {
        settingsVisible = true
    }

    fun closeSettings() {
        settingsVisible = false
    }

    fun openVault(screen: String) {
        require(screen == "archive" || screen == "trash")
        vaultScreen = screen
    }

    fun closeVault() {
        vaultScreen = null
    }
}
