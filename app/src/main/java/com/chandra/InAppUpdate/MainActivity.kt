
package com.chandra.InAppUpdate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chandra.InAppUpdate.ui.theme.InAppUpdateTheme

class MainActivity : ComponentActivity() {

    private lateinit var updater: AppUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Initialize updater
        updater = AppUpdater(this)

        // Check for new app version
        updater.checkUpdate()

        setContent {
            InAppUpdateTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->

                    UpdateScreen(
                        modifier = Modifier.padding(innerPadding),
                        onCheckUpdate = {
                            updater.checkUpdate(manual = true)
                        }
                    )
                }
            }
        }
    }

    // Called when user returns from Android installer settings
    override fun onResume() {
        super.onResume()

        if (::updater.isInitialized) {
            updater.installApk()
        }
    }
}

@Composable
fun UpdateScreen(
    modifier: Modifier = Modifier,
    onCheckUpdate: () -> Unit
) {
    var checking by remember {
        mutableStateOf(false)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "In-App Update System",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "App Version: ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.headlineSmall
        )

        if (BuildConfig.LOCAL_UPDATE_ENABLED) {
            Text(
                text = "Local Wi-Fi APK Update",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = {
                    if (!checking) {
                        checking = true
                        onCheckUpdate()
                    }
                },
                enabled = !checking,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Text(if (checking) "Checking..." else "Check for Updates")
            }

            // Re-enable the button after a short cooldown (prevents double clicks)
            LaunchedEffect(checking) {
                if (checking) {
                    kotlinx.coroutines.delay(2000)
                    checking = false
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    InAppUpdateTheme {
        UpdateScreen(
            onCheckUpdate = {}
        )
    }
}
