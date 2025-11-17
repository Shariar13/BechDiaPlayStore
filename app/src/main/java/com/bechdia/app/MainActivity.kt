package com.bechdia.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var offlineLayout: RelativeLayout
    private lateinit var offlineText: TextView

    // File Upload Support
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoPath: String? = null

    // Constants
    private val WEBSITE_URL = "https://www.bechdia.com"
    private val ALLOWED_DOMAINS = arrayOf("bechdia.com", "www.bechdia.com")

    // Activity Result Launchers
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleFileChooserResult(result.resultCode, result.data)
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            openFileChooser()
        } else {
            Toast.makeText(this, "Camera permission required for photo upload", Toast.LENGTH_SHORT).show()
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(com.bechdia.app.R.layout.activity_main)

        // Initialize UI components
        initializeViews()

        // Setup all features
        setupWebView()
        setupPullToRefresh()
        setupBackNavigation()

        // Restore WebView state if available
        savedInstanceState?.let {
            webView.restoreState(it)
        } ?: run {
            // Load website or show offline screen
            loadWebsite()
        }
    }

    // -------------------------------
    //      Initialize UI Components
    // -------------------------------
    private fun initializeViews() {
        webView = findViewById(com.bechdia.app.R.id.webView)
        swipeRefresh = findViewById(com.bechdia.app.R.id.swipeRefresh)
        progressBar = findViewById(com.bechdia.app.R.id.progressBar)
        offlineLayout = findViewById(com.bechdia.app.R.id.offlineLayout)
        offlineText = findViewById(com.bechdia.app.R.id.offlineText)

        // Setup retry button click
        findViewById<TextView>(com.bechdia.app.R.id.retryButton)?.setOnClickListener {
            loadWebsite()
        }
    }

    // -------------------------------
    //      WebView Configuration
    // -------------------------------
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            // JavaScript
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false

            // Storage
            domStorageEnabled = true
            databaseEnabled = true

            // Caching for better performance
            cacheMode = WebSettings.LOAD_DEFAULT

            // Media & Content
            loadsImagesAutomatically = true
            mediaPlaybackRequiresUserGesture = false

            // Viewport
            useWideViewPort = true
            loadWithOverviewMode = true

            // Zoom (disabled for better mobile experience)
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)

            // Security
            allowFileAccess = false
            allowContentAccess = true
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false

            // Mixed content (allow HTTPS with HTTP resources if needed)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }

            // Text & Font
            textZoom = 100
            minimumFontSize = 8

            // Enable safe browsing
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        // Setup WebView Chrome Client
        webView.webChromeClient = object : WebChromeClient() {

            // Progress bar updates
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
                swipeRefresh.isRefreshing = newProgress < 100
            }

            // File upload support (Android 5.0+)
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                } else {
                    openFileChooser()
                }

                return true
            }

            // Handle JavaScript alerts
            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("BechDia")
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .create()
                    .show()
                return true
            }

            // Handle JavaScript confirms
            override fun onJsConfirm(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("BechDia")
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setNegativeButton("Cancel") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .create()
                    .show()
                return true
            }

            // Update page title
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                title?.let { setTitle(it) }
            }
        }

        // Setup WebView Client
        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false

                return when {
                    // External links
                    url.startsWith("tel:") || url.startsWith("mailto:") ||
                            url.startsWith("sms:") || url.startsWith("geo:") ||
                            url.startsWith("maps:") || url.startsWith("whatsapp:") -> {
                        handleExternalLink(url)
                        true
                    }

                    // Check if domain is allowed
                    isAllowedDomain(url) -> {
                        if (isOnline()) {
                            view?.loadUrl(url)
                        } else {
                            showOfflineScreen()
                        }
                        true
                    }

                    // External domains
                    else -> {
                        openInBrowser(url)
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                offlineLayout.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)

                if (request?.isForMainFrame == true) {
                    if (!isOnline()) {
                        showOfflineScreen()
                    } else {
                        showErrorScreen("Error loading page. Please try again.")
                    }
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)

                if (request?.isForMainFrame == true) {
                    val statusCode = errorResponse?.statusCode
                    when (statusCode) {
                        404 -> showErrorScreen("Page not found (404)")
                        500 -> showErrorScreen("Server error (500)")
                        503 -> showErrorScreen("Service unavailable (503)")
                    }
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: android.net.http.SslError?
            ) {
                handler?.cancel()
                showErrorScreen("SSL Certificate Error. Connection is not secure.")
            }
        }

        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = false

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            handleDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    // -------------------------------
    //      Pull-to-Refresh Setup
    // -------------------------------
    private fun setupPullToRefresh() {
        swipeRefresh.setColorSchemeColors(
            Color.parseColor("#EF4444"),
            Color.parseColor("#EC4899")
        )

        swipeRefresh.setOnRefreshListener {
            if (isOnline()) {
                webView.reload()
            } else {
                swipeRefresh.isRefreshing = false
                showOfflineScreen()
                Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -------------------------------
    //      Back Navigation
    // -------------------------------
    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this) {
            when {
                webView.canGoBack() -> webView.goBack()
                else -> {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Exit BechDia?")
                        .setMessage("Do you want to exit the app?")
                        .setPositiveButton("Yes") { _, _ -> finish() }
                        .setNegativeButton("No", null)
                        .show()
                }
            }
        }
    }

    // -------------------------------
    //      File Upload Handling
    // -------------------------------
    private fun openFileChooser() {
        try {
            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (takePictureIntent.resolveActivity(packageManager) != null) {
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    null
                }

                photoFile?.also {
                    cameraPhotoPath = "file:${it.absolutePath}"
                    val photoURI = FileProvider.getUriForFile(
                        this,
                        "${applicationContext.packageName}.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                }
            }

            val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            }

            val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
                putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
                putExtra(Intent.EXTRA_TITLE, "Choose File")
                takePictureIntent?.let {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(it))
                }
            }

            fileChooserLauncher.launch(chooserIntent)

        } catch (e: Exception) {
            Toast.makeText(this, "Error opening file chooser", Toast.LENGTH_SHORT).show()
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun handleFileChooserResult(resultCode: Int, data: Intent?) {
        if (fileUploadCallback == null) return

        val results = if (resultCode == RESULT_OK) {
            when {
                data?.dataString != null -> arrayOf(Uri.parse(data.dataString))
                cameraPhotoPath != null -> arrayOf(Uri.parse(cameraPhotoPath))
                else -> null
            }
        } else {
            null
        }

        fileUploadCallback?.onReceiveValue(results)
        fileUploadCallback = null
        cameraPhotoPath = null
    }

    // -------------------------------
    //      Download Handling
    // -------------------------------
    private fun handleDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String
    ) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                addRequestHeader("User-Agent", userAgent)
                setDescription("Downloading file from BechDia")
                setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType))
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, contentDisposition, mimeType)
                )
            }

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(this, "Downloading file...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------------
    //      External Link Handling
    // -------------------------------
    private fun handleExternalLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to handle this action", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open external link", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------------
    //      Network & Domain Check
    // -------------------------------
    private fun isOnline(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun isAllowedDomain(url: String): Boolean {
        return try {
            val host = Uri.parse(url).host?.lowercase() ?: return false
            ALLOWED_DOMAINS.any { host == it || host.endsWith(".$it") }
        } catch (e: Exception) {
            false
        }
    }

    // -------------------------------
    //      Screen States
    // -------------------------------
    private fun loadWebsite() {
        if (isOnline()) {
            offlineLayout.visibility = View.GONE
            webView.visibility = View.VISIBLE
            webView.loadUrl(WEBSITE_URL)
        } else {
            showOfflineScreen()
        }
    }

    private fun showOfflineScreen() {
        webView.visibility = View.GONE
        offlineLayout.visibility = View.VISIBLE
        offlineText.text = "No Internet Connection"
        swipeRefresh.isRefreshing = false
    }

    private fun showErrorScreen(message: String) {
        webView.visibility = View.GONE
        offlineLayout.visibility = View.VISIBLE
        offlineText.text = message
        swipeRefresh.isRefreshing = false
    }

    // -------------------------------
    //      Lifecycle Management
    // -------------------------------
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
        webView.pauseTimers()
    }

    override fun onDestroy() {
        swipeRefresh.removeAllViews()
        webView.clearHistory()
        webView.clearCache(true)
        webView.loadUrl("about:blank")
        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }
}