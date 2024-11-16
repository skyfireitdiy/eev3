package com.example.eev3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import com.example.eev3.ui.screens.MusicPlayerApp
import com.example.eev3.ui.theme.Eev3Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 添加返回键处理
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 直接将应用移到后台
                moveTaskToBack(true)
            }
        })

        setContent {
            Eev3Theme {
                MusicPlayerApp(
                    onBackPressed = {
                        // 触发返回键处理
                        onBackPressedDispatcher.onBackPressed()
                    }
                )
            }
        }
    }
}