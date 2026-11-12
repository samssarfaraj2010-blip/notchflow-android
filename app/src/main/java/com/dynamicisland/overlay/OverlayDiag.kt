package com.dynamicisland.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tiny on-device status board shown on the Home screen so overlay problems can be seen without a computer. Holds no personal data. */
object OverlayDiag {
    private val _lines = MutableStateFlow<Map<String, String>>(emptyMap())
    val lines: StateFlow<Map<String, String>> = _lines.asStateFlow()

    fun put(key: String, value: String) { _lines.value = _lines.value + (key to value) }
    fun clear() { _lines.value = emptyMap() }
}
