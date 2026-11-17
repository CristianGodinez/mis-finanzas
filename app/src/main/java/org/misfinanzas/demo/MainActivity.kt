package org.misfinanzas.demo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.misfinanzas.demo.ui.FinanceApp
import org.misfinanzas.demo.ui.FinanceTheme

class MainActivity : ComponentActivity() {
    private var openAddMovementTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == ACTION_OPEN_ADD_MOVEMENT) openAddMovementTrigger++
        enableEdgeToEdge()
        setContent {
            FinanceTheme {
                FinanceApp(openAddMovementTrigger = openAddMovementTrigger)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_ADD_MOVEMENT) openAddMovementTrigger++
    }

    companion object {
        const val ACTION_OPEN_ADD_MOVEMENT = "org.misfinanzas.demo.OPEN_ADD_MOVEMENT"
    }
}

