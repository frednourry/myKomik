package fr.nourry.mynewkomik.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import fr.nourry.mynewkomik.R
import timber.log.Timber

class PermissionFragment: Fragment() {
    companion object {
        var PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
    private lateinit var textView:TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate $savedInstanceState")
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Timber.d("onCreateView")
        return inflater.inflate(R.layout.fragment_permission, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Timber.d("onViewCreated")

        super.onViewCreated(view, savedInstanceState)

        textView = view.findViewById(R.id.textViewNoPermission)

        if (savedInstanceState == null) {
            // Check permissions
            if (Build.VERSION.SDK_INT < 30) {
                checkPermissionBefore30()
            } else {
                // Build.VERSION.SDK_INT >= 30
                checkPermission30AndAfter()
            }

        } else {
            // No need to check permissions
            startAppAfterPermissionsCheck()
        }
    }


    // Check reading permissions if build version < 30
    private fun checkPermissionBefore30() {
        Timber.d("checkPermissionBefore30")
        if (!checkBefore30(activity as Context, PERMISSIONS)) {
            requestBefore30()
        } else {
            // User granted file permission, Access your file
            startAppAfterPermissionsCheck()
        }
    }

    private val permissionBefore30RequestLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all {
                it.value == true
            }
            Timber.v("permissionBefore30RequestLauncher=>RequestMultiplePermissions granted = $granted")
            if (granted) {
                startAppAfterPermissionsCheck()
            } else {
                startNotEnoughPermission()
            }
        }

    private fun checkBefore30(context: Context, permissions: Array<String>): Boolean = permissions.all {
        Timber.d("checkBefore30")
        ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBefore30() {
        Timber.d("requestBefore30")
        permissionBefore30RequestLauncher.launch(PERMISSIONS)
    }
    // End permissions before 30

    // Check reading permissions if build version >= 30
    @RequiresApi(api = Build.VERSION_CODES.R)
    var permissionAfter30RequestLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Timber.d("permissionAfter30RequestLauncher.StartActivityForResult -> result=$result")
        if (result.resultCode == Activity.RESULT_OK) {
            startAppAfterPermissionsCheck()
        } else {
            if (!Environment.isExternalStorageManager()) {
                startNotEnoughPermission()
            } else {
                startAppAfterPermissionsCheck()
//                checkPermissionBefore30() // No need
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private fun checkPermission30AndAfter() {
        Timber.d("checkPermission30AndAfter")
        if (!Environment.isExternalStorageManager()) {
            // Still no permissions (ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION or ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            try {
                Timber.d("checkPermission30AndAfter 11")
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(String.format("package:%s", requireActivity().applicationContext.packageName))
                permissionAfter30RequestLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                permissionAfter30RequestLauncher.launch(intent)
            }
        } else {
            startAppAfterPermissionsCheck()
//            checkPermissionBefore30()
        }
    }
    // End permissions after 30


    // Permissions are OK, so change to BrowserFragment
    private fun startAppAfterPermissionsCheck() {
        Timber.d("startAppAfterPermissionsCheck")

        val action = PermissionFragmentDirections.actionPermissionFragmentToBrowserFragment()
        findNavController().navigate(action)
    }

    // Permissions are not OK, so print a message
    private fun startNotEnoughPermission() {
        Timber.d("startNotEnoughPermission")
        textView.visibility = View.VISIBLE
    }

}