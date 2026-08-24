package com.linkfetch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.linkfetch.app.ui.navigation.AppNavHost
import com.linkfetch.app.ui.theme.LinkFetchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LinkFetchTheme {
                val container = (application as LinkFetchApp).container
                AppNavHost(container)
            }
        }
    }
}

