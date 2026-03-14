package com.example.shadesync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.shadesync.feature.shadematch.ui.ShadeSyncApp
import com.example.shadesync.ui.theme.ShadeSyncTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShadeSyncTheme {
                ShadeSyncApp()
            }
        }
    }
}
