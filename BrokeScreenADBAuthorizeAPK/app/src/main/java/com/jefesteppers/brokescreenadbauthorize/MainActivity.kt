package com.jefesteppers.brokescreenadbauthorize

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jefesteppers.brokescreenadbauthorize.ui.theme.BrokeScreenADBAuthorizeTheme // UPDATED THEME IMPORT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

// Define a tag for logging messages from MainActivity
private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Enable edge-to-edge display for modern UI
        setContent {
            BrokeScreenADBAuthorizeTheme { // UPDATED THEME NAME
                // A Scaffold provides a basic Material Design visual structure for the screen.
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreenContent(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreenContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // State to hold the device's IP address
    var ipAddress by remember { mutableStateOf("Loading IP...") }
    // State to hold the received password data (e.g., from web server)
    val receivedPasswordData by remember { mutableStateOf("No password data received yet.") }

    // Coroutine scope for launching network operations (like getting IP)
    val coroutineScope = rememberCoroutineScope()

    // LaunchedEffect to get IP address when the composable enters the composition
    // and to set up a listener for received password data (if we implement it later).
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            ipAddress = getIpAddress()
        }
        // TODO: Implement a mechanism to update receivedPasswordData from MyAdbEnablerService
        // This would likely involve a BroadcastReceiver or a shared ViewModel/Flow.
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Device IP Address:",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = ipAddress,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Button to open Accessibility Settings
        Button(
            onClick = { openAccessibilitySettings(context) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "Enable Accessibility Service",
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Button to open Developer Options (for manual ADB enabling if automation fails)
        Button(
            onClick = { openDeveloperOptions(context) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text(
                text = "Go to Developer Options (Manual)",
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Display area for received password data
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Received Password Info:",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = receivedPasswordData,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "(For manual entry on lock screen)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Important: This app needs to be manually enabled in Accessibility Settings. It cannot take control of your device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

// Helper function to get the device's current Wi-Fi IP address.
// Duplicated here for MainActivity's UI display, same as in service.
fun getIpAddress(): String {
    try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        for (intf in interfaces) {
            val addrs = Collections.list(intf.inetAddresses)
            for (addr in addrs) {
                if (!addr.isLoopbackAddress && addr is InetAddress && !addr.isLinkLocalAddress) {
                    // Check for IPv4 address
                    val sAddr = addr.hostAddress
                    val isIPv4 = sAddr.indexOf(':') < 0
                    if (isIPv4) {
                        return sAddr
                    }
                }
            }
        }
    } catch (ex: Exception) {
        Log.e(TAG, "Error getting IP address: ${ex.message}")
    }
    return "N/A"
}

// Function to open Android Accessibility Settings
fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Required if calling from non-Activity context
    context.startActivity(intent)
}

// Function to open Android Developer Options
fun openDeveloperOptions(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Required if calling from non-Activity context
    context.startActivity(intent)
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    BrokeScreenADBAuthorizeTheme { // UPDATED THEME NAME
        MainScreenContent()
    }
}
