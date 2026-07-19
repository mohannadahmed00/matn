package com.giraffe.matn

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.giraffe.matn.presentation.theme.MatnTheme
import com.giraffe.matn.presentation.navigation.MatnNavHost

/**
 * App root composable (Phase 1). Hosts the navigation graph inside [MatnTheme], which forces
 * right-to-left layout and the light Material 3 scheme. The `@Composable fun App()` signature
 * is preserved so `MainActivity` (Android) and `MainViewController` (iOS) keep their thin
 * entry points unchanged.
 */
@Composable
@Preview
fun App() {
    MatnTheme {
        MatnNavHost()
    }
}