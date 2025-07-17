package com.jefesteppers.brokescreenadbauthorize // Ensure this package name is correct

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.Collections

// Define a tag for logging messages from this service
private const val TAG = "MyAdbEnablerService"

// Key for SharedPreferences to store the secret key
private const val PREFS_NAME = "AdbEnablerPrefs"
private const val SECRET_KEY_PREF = "secret_key"

// Custom Intent action for broadcasting password data to MainActivity
const val ACTION_PASSWORD_RECEIVED = "com.jefesteppers.brokescreenadbauthorize.PASSWORD_RECEIVED"
const val EXTRA_PASSWORD_TYPE = "password_type"
const val EXTRA_PASSWORD_VALUE = "password_value"

// This is the core Accessibility Service class.
// It extends AccessibilityService and overrides its lifecycle methods.
class MyAdbEnablerService : AccessibilityService() {

    // Coroutine scope for launching asynchronous tasks without blocking the main thread.
    // It's tied to the service's lifecycle, so tasks are cancelled when the service stops.
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // SharedPreferences instance for storing the secret key
    private lateinit var sharedPreferences: SharedPreferences

    // Declare serverSocket as a nullable class member, initialized to null
    private var serverSocket: ServerSocket? = null

    // This method is called when the Accessibility Service is connected and ready to receive events.
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "MyAdbEnablerService connected.")

        // Initialize SharedPreferences
        sharedPreferences = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Configure the service information dynamically.
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS // This flag handles content retrieval
        info.packageNames = arrayOf("com.android.settings", "com.android.systemui", packageName)
        info.notificationTimeout = 100

        // Set the configured AccessibilityServiceInfo to the service.
        this.serviceInfo = info

        // Start the web server when the service connects.
        // This allows the external UI to communicate with the app.
        startWebServer()
    }

    // This method is called when an AccessibilityEvent is fired.
    // This is the core of the service where UI automation logic resides.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        Log.d(TAG, "Event: ${event.eventType} - ${event.packageName} - ${event.className}")

        // We are primarily interested in window state changes, as this indicates a new dialog/screen.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val rootNode = rootInActiveWindow ?: return
            Log.d(TAG, "Root node (Window State Changed): ${rootNode.className} from ${event.packageName}")

            // Check if the current package is one that might show the ADB dialog.
            if (event.packageName == "com.android.settings" || event.packageName == "com.android.systemui") {
                handleAdbDialog(rootNode)
            }
        }
    }

    // Handles logic for the "Allow USB debugging?" dialog.
    private fun handleAdbDialog(rootNode: AccessibilityNodeInfo) {
        // Look for the dialog title or key phrases within the window content.
        // The exact text might vary slightly across Android versions/OEMs.
        val dialogTitleKeywords = listOf("Allow USB debugging?", "USB debugging", "Allow debugging")

        var foundDialog = false
        for (keyword in dialogTitleKeywords) {
            if (findNodeByText(rootNode, keyword) != null) {
                foundDialog = true
                break
            }
        }

        if (foundDialog) {
            Log.d(TAG, "Detected 'Allow USB debugging?' dialog.")

            // Try to find and click the "Always allow from this computer" checkbox.
            // The text might be "Always allow from this computer" or just "Always allow".
            val checkboxKeywords = listOf("Always allow from this computer", "Always allow", "Always")
            for (keyword in checkboxKeywords) {
                val checkboxNode = findNodeByText(rootNode, keyword)
                // Fix for 'isChecked' deprecation warning: Suppress it.
                @Suppress("DEPRECATION")
                if (checkboxNode != null && checkboxNode.isCheckable && !checkboxNode.isChecked) {
                    Log.d(TAG, "Clicking 'Always allow' checkbox.")
                    checkboxNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    break // Click only one if found
                }
            }

            // Try to find and click the "Allow" or "OK" button.
            val buttonKeywords = listOf("Allow", "OK", "Accept")
            for (keyword in buttonKeywords) {
                val buttonNode = findNodeByText(rootNode, keyword)
                // Ensure the node is clickable and visible.
                // Also, make sure it's not the "Cancel" button.
                if (buttonNode != null && buttonNode.isClickable && buttonNode.isVisibleToUser &&
                    buttonNode.text?.toString()?.lowercase() != "cancel") {
                    Log.d(TAG, "Clicking '$keyword' button.")
                    buttonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    // Optionally, you might want to disable the service after successful automation
                    // if its job is done. However, for recovery, keeping it active might be useful.
                    // disableSelf()
                    return // Dialog handled, exit
                }
            }
        }
    }

    // Helper function to find a node by its text.
    private fun findNodeByText(rootNode: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull() // Return the first matching node
    }

    // This method is called when the Accessibility Service is disconnected (e.g., user disables it).
    override fun onInterrupt() {
        Log.d(TAG, "MyAdbEnablerService interrupted.")
        // Cancel all coroutines launched by this service.
        serviceScope.cancel()
        // Close the server socket when the service is interrupted
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server socket on interrupt: ${e.message}")
        }
    }

    // --- Web Server Logic (for external UI communication) ---

    private var serverJob: Job? = null
    private var serverPort = 8080 // Default port for the web server

    // Starts a simple HTTP server to listen for incoming requests from the external UI.
    private fun startWebServer() {
        serverJob?.cancel() // Cancel any existing server job
        serverJob = serviceScope.launch {
            try {
                // Initialize serverSocket here, within the try block
                serverSocket = ServerSocket(serverPort)
                val ipAddress = getIpAddress()
                Log.d(TAG, "Web server started on ${ipAddress}:${serverPort}")

                while (isActive) { // Keep server running as long as coroutine is active
                    // Use !! to assert non-nullability, as it's initialized above
                    val clientSocket = serverSocket!!.accept()
                    Log.d(TAG, "Client connected: ${clientSocket.inetAddress}")

                    launch(Dispatchers.IO) { // Handle client request in a separate coroutine
                        handleClientRequest(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Web server error: ${e.message}", e)
            } finally {
                // Ensure server socket is closed if an error occurs or job is cancelled
                try {
                    serverSocket?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error closing server socket in finally block: ${e.message}")
                }
            }
        }
    }

    // Handles an individual client HTTP request.
    private fun handleClientRequest(clientSocket: java.net.Socket) {
        val writer = OutputStreamWriter(clientSocket.getOutputStream())
        try {
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))

            // Read the request line (e.g., "POST /data HTTP/1.1")
            val requestLine = reader.readLine()
            Log.d(TAG, "Request: $requestLine")

            if (requestLine == null || !requestLine.startsWith("POST")) {
                sendHttpResponse(writer, 405, "Method Not Allowed", "Only POST requests are supported.")
                return
            }

            // Read headers to find Content-Length
            var contentLength = 0
            val headers = mutableMapOf<String, String>()
            var currentHeaderLine: String?
            while (reader.readLine().also { currentHeaderLine = it } != null && !currentHeaderLine.isNullOrEmpty()) {
                val headerLine = currentHeaderLine!! // Assert non-null and assign to a non-nullable local val

                val colonIndex = headerLine.indexOf(':')
                if (colonIndex != -1) {
                    val headerName = headerLine.substring(0, colonIndex).trim()
                    val headerValue = headerLine.substring(colonIndex + 1).trim()
                    headers[headerName] = headerValue
                }
                if (headerLine.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = headerLine.substring("Content-Length:".length).trim().toIntOrNull() ?: 0
                }
            }

            // Read the request body (JSON payload)
            val requestBody = CharArray(contentLength).let { buffer ->
                reader.read(buffer, 0, contentLength)
                String(buffer)
            }
            Log.d(TAG, "Received JSON: $requestBody")

            // Parse JSON payload
            val jsonPayload = try {
                JSONObject(requestBody)
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing JSON: ${e.message}")
                sendHttpResponse(writer, 400, "Bad Request", "Invalid JSON format.")
                return
            }

            // FIX: Replace split with substringAfter and substringBefore for path parsing
            val path = requestLine.substringAfter(" ", "").substringBefore(" HTTP")

            when (path) {
                "/set-secret" -> {
                    val newSecret = jsonPayload.optString("secretKey")
                    if (newSecret.isNotBlank()) {
                        sharedPreferences.edit().putString(SECRET_KEY_PREF, newSecret).apply()
                        Log.d(TAG, "Secret key set successfully.")
                        sendHttpResponse(writer, 200, "OK", "Secret key set.")
                    } else {
                        Log.w(TAG, "Attempted to set empty secret key.")
                        sendHttpResponse(writer, 400, "Bad Request", "Secret key cannot be empty.")
                    }
                }
                "/data" -> {
                    // --- SECURITY: Validate Shared Secret Key ---
                    val storedSecret = sharedPreferences.getString(SECRET_KEY_PREF, null)
                    val receivedSecret = jsonPayload.optString("secretKey")

                    if (storedSecret == null || storedSecret.isBlank()) {
                        Log.w(TAG, "Secret key not set. Rejecting request.")
                        sendHttpResponse(writer, 403, "Forbidden", "Secret key not configured on device.")
                        return
                    }

                    if (receivedSecret != storedSecret) {
                        Log.w(TAG, "Unauthorized access attempt! Received key: '$receivedSecret', Expected: '$storedSecret'")
                        sendHttpResponse(writer, 401, "Unauthorized", "Invalid secret key.")
                        return
                    }
                    // --- END SECURITY VALIDATION ---

                    // Handle password/PIN data
                    val passwordType = jsonPayload.optString("passwordType")
                    val password = jsonPayload.optString("password")
                    Log.d(TAG, "Received password: Type=$passwordType, Password=$password")

                    // Call the new function to send data to MainActivity
                    sendPasswordDataToMainActivity(passwordType, password)

                    sendHttpResponse(writer, 200, "OK", "Password data received.")
                }
                "/command" -> {
                    // --- SECURITY: Validate Shared Secret Key ---
                    val storedSecret = sharedPreferences.getString(SECRET_KEY_PREF, null)
                    val receivedSecret = jsonPayload.optString("secretKey")

                    if (storedSecret == null || storedSecret.isBlank()) {
                        Log.w(TAG, "Secret key not set. Rejecting request.")
                        sendHttpResponse(writer, 403, "Forbidden", "Secret key not configured on device.")
                        return
                    }

                    if (receivedSecret != storedSecret) {
                        Log.w(TAG, "Unauthorized access attempt! Received key: '$receivedSecret', Expected: '$storedSecret'")
                        sendHttpResponse(writer, 401, "Unauthorized", "Invalid secret key.")
                        return
                    }
                    // --- END SECURITY VALIDATION ---

                    // Handle specific commands
                    val action = jsonPayload.optString("action")
                    Log.d(TAG, "Received command action: $action")

                    when (action) {
                        "triggerAdbDialogTap" -> {
                            Log.d(TAG, "Executing triggerAdbDialogTap command.")
                            // The onAccessibilityEvent will naturally handle this if the dialog appears.
                            // We don't need to do anything explicit here other than acknowledge.
                            sendHttpResponse(writer, 200, "OK", "ADB dialog tap command acknowledged.")
                        }
                        "openAccessibilitySettings" -> {
                            Log.d(TAG, "Executing openAccessibilitySettings command.")
                            openAccessibilitySettings(this@MyAdbEnablerService) // Use this@MyAdbEnablerService for context
                            sendHttpResponse(writer, 200, "OK", "Opened Accessibility Settings.")
                        }
                        else -> {
                            sendHttpResponse(writer, 400, "Bad Request", "Unknown command action.")
                        }
                    }
                }
                else -> {
                    sendHttpResponse(writer, 404, "Not Found", "Endpoint not found.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client request: ${e.message}", e)
            sendHttpResponse(writer, 500, "Internal Server Error", "Server error: ${e.message}")
        } finally {
            try { clientSocket.close() } catch (e: Exception) { /* ignore */ }
        }
    }

    // Helper to send HTTP responses
    private fun sendHttpResponse(writer: OutputStreamWriter, statusCode: Int, statusText: String, body: String) {
        val response = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" + // Allow CORS for web UI
                "\r\n" +
                body
        writer.write(response)
        writer.flush()
    }

    // Helper to get the device's current Wi-Fi IP address.
    fun getIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is InetAddress && !addr.isLinkLocalAddress) {
                        val sAddr = addr.hostAddress
                        // Check if sAddr is not null before proceeding with operations
                        if (sAddr != null) {
                            val isIPv4 = sAddr.indexOf(':') < 0
                            if (isIPv4) {
                                return sAddr
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "Error getting IP address: ${ex.message}")
        }
        return "N/A"
    }

    // Function to open Android Accessibility Settings (moved from MainActivity for service to use)
    private fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // Sends password data to MainActivity via LocalBroadcastManager
    private fun sendPasswordDataToMainActivity(passwordType: String, password: String) {
        val intent = Intent(ACTION_PASSWORD_RECEIVED).apply {
            putExtra(EXTRA_PASSWORD_TYPE, passwordType)
            putExtra(EXTRA_PASSWORD_VALUE, password)
        }
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        Log.d(TAG, "Broadcasted password data to MainActivity: Type=$passwordType, Value=$password")
    }
}
