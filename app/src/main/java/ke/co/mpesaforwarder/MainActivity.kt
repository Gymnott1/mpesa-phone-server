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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                if (serverUrl.isEmpty()) {
                    Toast.makeText(context, "Please enter server URL first", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isLoading = true
                testServerConnection(context, serverUrl) { success, message ->
                    isLoading = false
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
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

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium
                )
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

fun testServerConnection(context: Context, url: String, callback: (Boolean, String) -> Unit) {
    Thread {
        try {
            val testData = mapOf(
                "test" to "true",
                "message" to "Test connection from M-Pesa Forwarder"
            )
            NetworkHelper.sendToServer(url, testData)
            callback(true, "✓ Test sent successfully! Check your server logs.")
        } catch (e: Exception) {
            callback(false, "✗ Connection failed: ${e.message}")
        }
    }.start()
}