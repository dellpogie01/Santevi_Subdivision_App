
package com.example

import android.os.Bundle
import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.example.notifications.SanteviMessagingService
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    private var notificationRoute by mutableStateOf<String?>(null)
    private var notificationDocumentId by mutableStateOf<String?>(null)
    private var notificationSequence by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureNotificationIntent(intent)
        SanteviMessagingService.ensureNotificationChannel(this)

        val firebaseApp = FirebaseApp.initializeApp(this)
        if (firebaseApp == null) {
            Log.e(
                "FirebaseInit",
                "Firebase initialization failed. Check google-services.json and Google Services Gradle plugin."
            )
        } else {
            Log.d("FirebaseInit", "Firebase initialized: ${firebaseApp.name}")
        }

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        notificationRoute = notificationRoute,
                        notificationDocumentId = notificationDocumentId,
                        notificationSequence = notificationSequence,
                        onNotificationRouteConsumed = {
                            notificationRoute = null
                            notificationDocumentId = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureNotificationIntent(intent)
    }

    private fun captureNotificationIntent(intent: Intent?) {
        notificationRoute = intent?.getStringExtra(SanteviMessagingService.EXTRA_ROUTE)
        notificationDocumentId = intent?.getStringExtra(SanteviMessagingService.EXTRA_DOCUMENT_ID)
        if (notificationRoute != null) notificationSequence += 1
    }
}
