package ke.co.mpesaforwarder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.util.Log
import ke.co.mpesaforwarder.ui.theme.MPesaForwarderTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions denied - app won't work", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check and request permissions
        if (!checkSmsPermissions()) {
            requestSmsPermissions()
        }

        setContent {
            MPesaForwarderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    private fun checkSmsPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_SMS
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestSmsPermissions() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS
            )
        )
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("MPesaForwarder", Context.MODE_PRIVATE)

    var serverUrl by remember {
        mutableStateOf(prefs.getString("server_url", "") ?: "")
    }
    var statusMessage by remember {
        mutableStateOf(getPermissionStatus(context))
    }
    var isLoading by remember { mutableStateOf(false) }
    var debugLogs by remember { mutableStateOf(getDebugInfo(context)) }
    var errorMessages by remember { mutableStateOf(listOf<String>()) }
    var warningMessages by remember { mutableStateOf(listOf<String>()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title
        Text(
            text = "M-Pesa SMS Forwarder",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Server URL Input
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL") },
            placeholder = { Text("https://yourserver.com/mpesa-webhook") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Save Button
        Button(
            onClick = {
                if (serverUrl.isNotEmpty()) {
                    prefs.edit().putString("server_url", serverUrl).apply()
                    Toast.makeText(context, "Server URL saved!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Please enter a URL", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Server URL")
        }

        // Test Connection Button
        Button(
            onClick = {
                errorMessages = emptyList()
                warningMessages = emptyList()
                
                if (serverUrl.isEmpty()) {
                    errorMessages = listOf("❌ Server URL is required")
                    return@Button
                }
                
                if (!isValidUrl(serverUrl)) {
                    errorMessages = listOf("❌ Invalid URL format")
                    return@Button
                }

                isLoading = true
                testServerConnection(context, serverUrl) { success, message ->
                    isLoading = false
                    if (success) {
                        errorMessages = emptyList()
                    } else {
                        errorMessages = listOf("❌ Connection failed: $message")
                    }
                    debugLogs = getDebugInfo(context)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isLoading) "Testing..." else "Test Connection")
        }

        // Error Messages
        if (errorMessages.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFEBEE)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "❌ Errors",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.Bold
                    )
                    errorMessages.forEach { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD32F2F),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Warning Messages
        if (warningMessages.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3E0)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚠️ Warnings",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFFF8F00),
                        fontWeight = FontWeight.Bold
                    )
                    warningMessages.forEach { warning ->
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFF8F00),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📊 Status",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Debug Information Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🔧 Debug Info",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = debugLogs,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                
                Button(
                    onClick = {
                        debugLogs = getDebugInfo(context)
                        statusMessage = getPermissionStatus(context)
                        warningMessages = getWarnings(context)
                        errorMessages = getErrors(context)
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("🔄 Refresh Debug Info")
                }
            }
        }

        // Instructions Card
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "How it works",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = """
                        1. Grant SMS permissions
                        2. Enter your server URL
                        3. Keep this phone plugged in
                        4. App runs in background
                        5. When M-Pesa SMS arrives, it's automatically sent to your server
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight.times(1.5f)
                )
            }
        }
    }
}

fun getPermissionStatus(context: Context): String {
    val hasReceiveSms = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECEIVE_SMS
    ) == PackageManager.PERMISSION_GRANTED

    val hasReadSms = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    return when {
        hasReceiveSms && hasReadSms -> "✓ SMS Permissions Granted - App is ready!"
        else -> "✗ SMS Permissions Required - Please grant permissions"
    }
}

fun isValidUrl(url: String): Boolean {
    return try {
        val urlPattern = "^https?://.*".toRegex()
        urlPattern.matches(url)
    } catch (e: Exception) {
        false
    }
}

fun getDebugInfo(context: Context): String {
    val prefs = context.getSharedPreferences("MPesaForwarder", Context.MODE_PRIVATE)
    val serverUrl = prefs.getString("server_url", "Not set") ?: "Not set"
    
    return buildString {
        appendLine("📱 App Version: 1.0")
        appendLine("🌐 Server URL: $serverUrl")
        appendLine("📞 SMS Permission: ${if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) "✓ Granted" else "❌ Denied"}")
        appendLine("📖 Read SMS Permission: ${if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) "✓ Granted" else "❌ Denied"}")
        appendLine("🌍 Internet Permission: ✓ Granted")
        appendLine("🔋 Battery Optimization: ${if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) "Check manually" else "N/A"}")
        appendLine("📊 Android Version: ${android.os.Build.VERSION.RELEASE}")
        appendLine("🏗️ SDK Version: ${android.os.Build.VERSION.SDK_INT}")
        appendLine("⏰ Last Updated: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
    }
}

fun getWarnings(context: Context): List<String> {
    val warnings = mutableListOf<String>()
    val prefs = context.getSharedPreferences("MPesaForwarder", Context.MODE_PRIVATE)
    val serverUrl = prefs.getString("server_url", "") ?: ""
    
    if (serverUrl.isEmpty()) {
        warnings.add("⚠️ Server URL not configured")
    }
    
    if (serverUrl.startsWith("http://")) {
        warnings.add("⚠️ Using HTTP instead of HTTPS (insecure)")
    }
    
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            warnings.add("⚠️ Battery optimization enabled - may affect background SMS processing")
        }
    }
    
    return warnings
}

fun getErrors(context: Context): List<String> {
    val errors = mutableListOf<String>()
    
    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        errors.add("❌ SMS receive permission required")
    }
    
    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        errors.add("❌ SMS read permission required")
    }
    
    return errors
}

fun testServerConnection(context: Context, url: String, callback: (Boolean, String) -> Unit) {
    Thread {
        try {
            Log.d("MPesaForwarder", "Testing connection to: $url")
            val testData = mapOf(
                "test" to "true",
                "message" to "Test connection from M-Pesa Forwarder",
                "timestamp" to System.currentTimeMillis().toString(),
                "device" to android.os.Build.MODEL
            )
            NetworkHelper.sendToServer(url, testData)
            Log.d("MPesaForwarder", "Connection test successful")
            callback(true, "✓ Test sent successfully! Check your server logs.")
        } catch (e: java.net.UnknownHostException) {
            Log.e("MPesaForwarder", "Unknown host: ${e.message}")
            callback(false, "Unknown host - check URL")
        } catch (e: java.net.ConnectException) {
            Log.e("MPesaForwarder", "Connection refused: ${e.message}")
            callback(false, "Connection refused - server may be down")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e("MPesaForwarder", "Timeout: ${e.message}")
            callback(false, "Connection timeout - check internet")
        } catch (e: javax.net.ssl.SSLException) {
            Log.e("MPesaForwarder", "SSL error: ${e.message}")
            callback(false, "SSL certificate error")
        } catch (e: Exception) {
            Log.e("MPesaForwarder", "Connection failed: ${e.message}")
            callback(false, e.message ?: "Unknown error")
        }
    }.start()
}