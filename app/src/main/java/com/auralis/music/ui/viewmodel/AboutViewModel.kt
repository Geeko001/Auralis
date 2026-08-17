package com.auralis.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.auralis.music.data.SessionManager
import javax.inject.Inject

class AboutViewModel @Inject constructor(
    private val sessionManager: SessionManager
) : ViewModel() {
    // Developer mode related logic removed
}
