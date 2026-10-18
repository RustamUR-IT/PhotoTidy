package app.phototidy

import android.Manifest
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import app.phototidy.data.Settings
import app.phototidy.ui.AppRoot
import app.phototidy.ui.MainViewModel
import app.phototidy.ui.PermState
import app.phototidy.ui.PhotoTidyTheme
import app.phototidy.work.ScanWorker
import kotlinx.coroutines.CompletableDeferred

class PhotoTidyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ScanWorker.createChannels(this)
        ScanWorker.setNightly(this, Settings(this).nightlyEnabled)
    }
}

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val perms = mutableStateOf(PermState(hasAccess = false, partialOnly = false, canManageMedia = false))

    private var pendingConsent: CompletableDeferred<Boolean>? = null

    private val consentLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            pendingConsent?.complete(result.resultCode == RESULT_OK)
            pendingConsent = null
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updatePerms()
        }

    /** Shows Android's "Allow Photo Tidy to modify/trash these photos?" dialog. */
    private suspend fun askConsent(pi: PendingIntent): Boolean {
        val d = CompletableDeferred<Boolean>()
        pendingConsent = d
        consentLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        return d.await()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updatePerms()
        if (!perms.value.hasAccess) requestAccess()

        setContent {
            PhotoTidyTheme {
                AppRoot(
                    vm = vm,
                    perms = perms.value,
                    consent = ::askConsent,
                    onRequestAccess = ::requestAccess,
                    onEnableManageMedia = ::openManageMedia,
                    onNightlyEnabled = ::requestNotifications,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePerms()
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun updatePerms() {
        val full = if (Build.VERSION.SDK_INT >= 33) granted(Manifest.permission.READ_MEDIA_IMAGES)
        else granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        val partial = Build.VERSION.SDK_INT >= 34 && !full &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        val manage = Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(this)
        perms.value = PermState(hasAccess = full || partial, partialOnly = partial, canManageMedia = manage)
    }

    private fun requestAccess() {
        val list = mutableListOf<String>()
        when {
            Build.VERSION.SDK_INT >= 34 -> {
                list += Manifest.permission.READ_MEDIA_IMAGES
                list += Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            }
            Build.VERSION.SDK_INT >= 33 -> list += Manifest.permission.READ_MEDIA_IMAGES
            else -> list += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        permissionLauncher.launch(list.toTypedArray())
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    private fun openManageMedia() {
        val intent = if (Build.VERSION.SDK_INT >= 31) {
            Intent(AndroidSettings.ACTION_REQUEST_MANAGE_MEDIA, Uri.parse("package:$packageName"))
        } else {
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }
}
