package com.auralis.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.auralis.music.ai.AIEqualizerService
import javax.inject.Inject

class AIEqualizerViewModel @Inject constructor(
    val aiService: AIEqualizerService
) : ViewModel()
