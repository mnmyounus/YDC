package com.mnmyounus.ydc

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mnmyounus.ydc.core.device.HostSessionManager
import com.mnmyounus.ydc.core.device.PermissionManager
import com.mnmyounus.ydc.core.device.UninstallManager
import com.mnmyounus.ydc.core.filetransfer.FileTransferManager
import com.mnmyounus.ydc.core.network.DeviceDiscovery
import com.mnmyounus.ydc.core.network.DiscoveredDevice
import com.mnmyounus.ydc.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val discovery by lazy { DeviceDiscovery(this) }
    private var hostSession: HostSessionManager? = null
    private var scanJob: Job? = null
    private val foundDevices = mutableListOf<DiscoveredDevice>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshStatus() }

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { sendPickedFile(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRequestPermissions.setOnClickListener {
            permissionLauncher.launch(PermissionManager.requiredRuntimePermissions())
        }
        binding.btnAccessibilitySettings.setOnClickListener {
            PermissionManager.openAccessibilitySettings(this)
        }
        binding.btnDeviceAdmin.setOnClickListener {
            PermissionManager.requestDeviceAdmin(this)
        }
        binding.btnUninstall.setOnClickListener {
            UninstallManager.requestSelfUninstall(this)
        }
        binding.btnToggleHostMode.setOnClickListener { toggleHostMode() }
        binding.btnScan.setOnClickListener { startScan() }
        binding.btnPickAndSend.setOnClickListener { filePicker.launch(arrayOf("*/*")) }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        hostSession?.stop()
        scanJob?.cancel()
    }

    private fun refreshStatus() {
        val runtimeOk = PermissionManager.hasAllRuntimePermissions(this)
        val accessibilityOk = PermissionManager.isAccessibilityServiceEnabled(this)
        val deviceAdminOk = PermissionManager.isDeviceAdminActive(this)

        binding.statusText.text = buildString {
            appendLine("Runtime permissions: ${if (runtimeOk) "granted" else "not granted"}")
            appendLine("Accessibility service: ${if (accessibilityOk) "enabled" else "disabled"}")
            append("Device admin: ${if (deviceAdminOk) "active" else "inactive"}")
        }
    }

    private fun toggleHostMode() {
        val current = hostSession
        if (current == null) {
            val name = binding.deviceNameInput.text?.toString()?.ifBlank { "YDC Device" } ?: "YDC Device"
            val session = HostSessionManager(this, name)
            session.start()
            hostSession = session
            binding.btnToggleHostMode.text = "Stop Host Mode"
            binding.hostModeStatus.text = "Broadcasting as \"$name\""
        } else {
            current.stop()
            hostSession = null
            binding.btnToggleHostMode.text = "Start Host Mode"
            binding.hostModeStatus.text = "Stopped"
        }
    }

    private fun startScan() {
        scanJob?.cancel()
        foundDevices.clear()
        binding.discoveredDevices.text = "Scanning…"
        scanJob = lifecycleScope.launch {
            discovery.discover().collect { device ->
                if (foundDevices.none { it.name == device.name }) {
                    foundDevices += device
                    binding.discoveredDevices.text =
                        foundDevices.joinToString("\n") { "${it.name} — ${it.host}:${it.port}" }
                }
            }
        }
    }

    private fun sendPickedFile(uri: Uri) {
        val target = foundDevices.firstOrNull()
        if (target == null) {
            binding.transferStatus.text = "Scan and find a device first."
            return
        }
        binding.transferStatus.text = "Sending to ${target.name}…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val tempFile = copyUriToTempFile(uri)
                    FileTransferManager().sendFile(
                        targetHost = target.host,
                        targetPort = target.port,
                        file = tempFile,
                        transferId = tempFile.name,
                    )
                }
            }
            binding.transferStatus.text = if (result.isSuccess) {
                "Sent to ${target.name}."
            } else {
                "Send failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    private fun copyUriToTempFile(uri: Uri): File {
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "shared_file"
        val temp = File(cacheDir, name)
        contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        }
        return temp
    }
}
