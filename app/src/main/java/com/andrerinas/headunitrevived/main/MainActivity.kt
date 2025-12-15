package com.andrerinas.headunitrevived.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import com.andrerinas.headunitrevived.App
import com.andrerinas.headunitrevived.R
import com.andrerinas.headunitrevived.aap.AapProjectionActivity
import com.andrerinas.headunitrevived.utils.AppLog
import com.andrerinas.headunitrevived.utils.toInetAddress
import com.andrerinas.headunitrevived.view.BitmapProjectionView
import java.net.Inet4Address
import kotlin.math.max

class MainActivity : FragmentActivity() {

    private var lastBackPressTime: Long = 0
    var keyListener: KeyListener? = null
    private val viewModel: MainViewModel by viewModels()

    private lateinit var video_button: Button
    private lateinit var usb: Button
    private lateinit var settings: Button
    private lateinit var wifi: Button
    private lateinit var ipView: TextView
    private lateinit var backButton: Button
    private lateinit var mainButtonsContainer: ConstraintLayout
    private lateinit var mainContentFrame: FrameLayout
    private lateinit var headerContainer: LinearLayout // Added headerContainer declaration
    private lateinit var exitButton: Button // Added exitButton declaration

    private var networkCallback: ConnectivityManager.NetworkCallback? = null // Made nullable

    interface KeyListener {
        fun onKeyEvent(event: KeyEvent?): Boolean
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme)
        setContentView(R.layout.activity_main)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AppLog.d("MainActivity: handleOnBackPressed - backStackEntryCount: ${supportFragmentManager.backStackEntryCount}")
                if (supportFragmentManager.backStackEntryCount > 0) {
                    AppLog.d("MainActivity: handleOnBackPressed - popping back stack")
                    supportFragmentManager.popBackStack()
                } else if (System.currentTimeMillis() - lastBackPressTime < 2000) {
                    AppLog.d("MainActivity: handleOnBackPressed - finishing activity")
                    finish()
                } else {
                    AppLog.d("MainActivity: handleOnBackPressed - showing exit toast")
                    lastBackPressTime = System.currentTimeMillis()
                    Toast.makeText(this@MainActivity, R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show()
                }
            }
        })

        video_button = findViewById(R.id.video_button)
        usb = findViewById(R.id.usb_button)
        settings = findViewById(R.id.settings_button)
        wifi = findViewById(R.id.wifi_button)
        ipView = findViewById(R.id.ip_address)
        backButton = findViewById(R.id.back_button)
        mainButtonsContainer = findViewById(R.id.main_buttons_container)
        mainContentFrame = findViewById(R.id.main_content)
        headerContainer = findViewById(R.id.header_container) // Initialized headerContainer
        exitButton = findViewById(R.id.exit_button) // Initialized exitButton

        exitButton.setOnClickListener {
            finishAffinity() // Close the app
        }

        backButton.setOnClickListener {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            }
        }

        // Initialize networkCallback conditionally
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    updateIpAddressView()
                }

                override fun onLost(network: Network) {
                    updateIpAddressView()
                }
            }
        }

        video_button.setOnClickListener {
            if (App.provide(this).transport.isAlive) {
                val aapIntent = Intent(this@MainActivity, AapProjectionActivity::class.java)
                aapIntent.putExtra(AapProjectionActivity.EXTRA_FOCUS, true)
                startActivity(aapIntent)
            } else {
                Toast.makeText(this, getString(R.string.no_android_auto_device_connected), Toast.LENGTH_LONG).show()
            }
        }

        usb.setOnClickListener {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.main_content, UsbListFragment())
                .addToBackStack(null) // Added to back stack
                .commit()
        }

        settings.setOnClickListener {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.main_content, SettingsFragment())
                .addToBackStack(null) // Added to back stack
                .commit()
        }

        wifi.setOnClickListener {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.main_content, NetworkListFragment())
                .addToBackStack(null) // Added to back stack
                .commit()
        }

        viewModel.register()

        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION
            ), permissionRequestCode
        )

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.main_content, HomeFragment())
                .commit()
        }
        supportFragmentManager.addOnBackStackChangedListener {
            AppLog.d("MainActivity: onBackStackChanged - backStackEntryCount: ${supportFragmentManager.backStackEntryCount}")
            updateBackButtonVisibility()
        }
        updateBackButtonVisibility() // Initial check

        val testBitmapButton = findViewById<Button>(R.id.test_bitmap_button)
        testBitmapButton.setOnClickListener {

            setContentView(R.layout.activity_headunit)
            AppLog.i("MainActivity Test Bitmap button clicked.")

            // Remove any existing views from mainContentFrame
            mainContentFrame.removeAllViews()

            // Make mainContentFrame visible and hide other UI elements
            mainContentFrame.visibility = View.VISIBLE
            mainButtonsContainer.visibility = View.GONE
            headerContainer.visibility = View.GONE
            exitButton.visibility = View.GONE
            testBitmapButton.visibility = View.GONE
            backButton.visibility = View.VISIBLE // Show back button to return

            val displayMetrics = resources.displayMetrics
            val actualScreenWidth = displayMetrics.widthPixels
            val actualScreenHeight = displayMetrics.heightPixels

            // Negotiated resolution (for test image, it's 1920x1080)
            val negotiatedWidth = 1920
            val negotiatedHeight = 1080

            // Calculate crop margins (these are the margins the phone adds)
            val cropTopBottom = (negotiatedHeight - actualScreenHeight) / 2 // 180
            val cropLeftRight = (negotiatedWidth - actualScreenWidth) / 2 // 36

            // Source rectangle (part of the video stream we want to show - blue area)
            val srcRect = RectF(
                cropLeftRight.toFloat(),
                cropTopBottom.toFloat(),
                (negotiatedWidth - cropLeftRight).toFloat(),
                (negotiatedHeight - cropTopBottom).toFloat()
            )

            // Destination rectangle (TextureView's bounds)
            val dstRect = RectF(0f, 0f, actualScreenWidth.toFloat(), actualScreenHeight.toFloat())

            AppLog.i("MainActivity: Test srcRect: $srcRect")
            AppLog.i("MainActivity: Test dstRect: $dstRect")

            val bitmapView = BitmapProjectionView(this)
            bitmapView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            val container = findViewById<android.widget.FrameLayout>(R.id.container)
            container.addView(bitmapView)

            // Load the test bitmap
            val options = BitmapFactory.Options().apply {
                inScaled = false // Disable density scaling
            }
            val testBitmap = BitmapFactory.decodeResource(resources, R.drawable.screentest, options)
            if (testBitmap != null) {
                bitmapView.setBitmap(testBitmap)
                AppLog.i("MainActivity: Loaded test bitmap ${testBitmap.width}x${testBitmap.height}")
            } else {
                AppLog.e("MainActivity", "Failed to load screentest.png")
            }

            // Apply transformation to show only the blue area
            //val matrix = Matrix()
            //matrix.setRectToRect(srcRect, srcRect, Matrix.ScaleToFit.FILL) // Use FILL to map srcRect to dstRect
            //bitmapView.setTransform(matrix)


            val ratioSurface: Float = actualScreenWidth.toFloat() / actualScreenHeight
            val ratioPreview: Float = negotiatedWidth.toFloat() / negotiatedHeight

            val scaleX: Float
            val scaleY: Float
/*
            if (ratioSurface > ratioPreview) {
                scaleX = actualScreenHeight.toFloat() / negotiatedHeight
                scaleY = 1f
            } else {
                scaleX = 1f
                scaleY = actualScreenWidth.toFloat() / negotiatedWidth
            }

            scaleX = (negotiatedHeight +36) / actualScreenHeight.toFloat()
            scaleY = (negotiatedWidth + 115) / actualScreenWidth.toFloat()

            AppLog.i("Scale: $scaleX, $scaleY")

            val matrix = Matrix()



            val scaledWidth: Float = actualScreenWidth * scaleX
            val scaledHeight: Float = actualScreenHeight * scaleY

            val dx: Float = (actualScreenWidth - scaledWidth) / 2
            val dy: Float = (actualScreenHeight - scaledHeight) / 2
*/

/*
            val matrix = Matrix()

            // KEINE Skalierung
            matrix.setScale(1f, 1f)

            // Buffer so verschieben, dass das Zentrum sichtbar ist
            matrix.postTranslate(-36f, -180f)
            bitmapView.setTransform(matrix)
*/
            //AppLog.i("Transpoints: $dx, $dy")
            //bitmapView.translationX = -40f
            //bitmapView.translationY = -125f

            //matrix.setScale(2.5f, 3.5f)
            //bitmapView.setTransform(matrix)


            //AppLog.i("MainActivity: Test Bitmap View loaded with transformation.")

            // Handle back press for bitmap view
            onBackPressedDispatcher.addCallback(this@MainActivity, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    setContentView(R.layout.activity_main)
                    AppLog.d("MainActivity: handleOnBackPressed - removing bitmap view")
                    mainContentFrame.removeAllViews()
                    mainContentFrame.visibility = View.GONE
                    mainButtonsContainer.visibility = View.VISIBLE
                    headerContainer.visibility = View.VISIBLE
                    exitButton.visibility = View.VISIBLE
                    testBitmapButton.visibility = View.VISIBLE
                    backButton.visibility = View.GONE // Hide back button again
                    this.remove() // Remove this callback
                }
            })
        }
    }

    private fun updateBackButtonVisibility() {
        val isFragmentOnStack = supportFragmentManager.backStackEntryCount > 0
        backButton.visibility = if (isFragmentOnStack) View.VISIBLE else View.GONE
        ipView.visibility = if (isFragmentOnStack) View.GONE else View.VISIBLE // IP visible only on main screen
        mainButtonsContainer.visibility = if (isFragmentOnStack) View.GONE else View.VISIBLE
        mainContentFrame.visibility = if (isFragmentOnStack) View.VISIBLE else View.GONE
        exitButton.visibility = if (isFragmentOnStack) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            networkCallback?.let {
                connectivityManager.registerNetworkCallback(request, it)
            }
        }
        updateIpAddressView() // Call this regardless of API level
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let {
                connectivityManager.unregisterNetworkCallback(it)
            }
        }
    }

    private fun updateIpAddressView() {
        var ipAddress: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // API 23+ (for getActiveNetwork)
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork // API 23
            if (activeNetwork != null) {
                val linkProperties = connectivityManager.getLinkProperties(activeNetwork) // API 21
                ipAddress = linkProperties?.linkAddresses?.find { it.address is Inet4Address }?.address?.hostAddress
            }
        } else { // API 19, 20, 21, 22
            val wifiManager = App.provide(this).wifiManager
            @Suppress("DEPRECATION")
            val currentIp = wifiManager.connectionInfo.ipAddress
            if (currentIp != 0) {
                ipAddress = currentIp.toInetAddress().hostAddress
            }
        }

        runOnUiThread {
            ipView.text = ipAddress ?: ""
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        AppLog.i("onKeyDown: %d", keyCode)
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Let the system handle the back button, which will trigger onBackPressedDispatcher
            return super.onKeyDown(keyCode, event)
        }
        return keyListener?.onKeyEvent(event) ?: super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        AppLog.i("onKeyUp: %d", keyCode)
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Let the system handle the back button, which will trigger onBackPressedDispatcher
            return super.onKeyUp(keyCode, event)
        }
        return keyListener?.onKeyEvent(event) ?: super.onKeyUp(keyCode, event)
    }

    companion object {
        private const val permissionRequestCode = 97
    }
}