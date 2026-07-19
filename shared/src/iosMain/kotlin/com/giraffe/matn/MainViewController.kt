package com.giraffe.matn

import androidx.compose.ui.window.ComposeUIViewController
import com.giraffe.matn.data.db.DatabaseDriverFactory
import com.giraffe.matn.di.initMatnKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    initMatnKoin(DatabaseDriverFactory())
    return ComposeUIViewController { App() }
}