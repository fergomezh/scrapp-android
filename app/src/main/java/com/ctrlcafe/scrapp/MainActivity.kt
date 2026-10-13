package com.ctrlcafe.scrapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ctrlcafe.scrapp.ui.theme.ScrappTheme
import com.ctrlcafe.scrapp.vista.navegacion.ScrappNavContainer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScrappTheme {
                ScrappNavContainer()
            }
        }
    }
}
