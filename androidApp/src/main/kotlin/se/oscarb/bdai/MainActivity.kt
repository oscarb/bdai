package se.oscarb.bdai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import se.oscarb.bdai.voice.AndroidPermissionBridge

class MainActivity : ComponentActivity() {

    private var pendingPermissionCallback: ((Boolean) -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingPermissionCallback?.invoke(granted)
        pendingPermissionCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        AndroidPermissionBridge.requestPermissionHandler = { permission, onResult ->
            pendingPermissionCallback = onResult
            permissionLauncher.launch(permission)
        }

        setContent {
            App()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            AndroidPermissionBridge.requestPermissionHandler = null
        }
    }
}
