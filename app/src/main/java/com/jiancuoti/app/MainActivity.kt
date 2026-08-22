package com.jiancuoti.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraShotUri: Uri? = null

    companion object {
        private const val REQ_FILE = 1001
        private const val REQ_PERMS = 1002
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
        }

        webView.addJavascriptInterface(AlbumBridge(), "AlbumBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http:") || url.startsWith("https:")) {
                    if (!url.contains("jsdelivr.net") && !url.contains("katex")) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        return true
                    }
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val cam = Manifest.permission.CAMERA
                val wantCam = request.resources.any { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }
                if (wantCam && checkSelfPermission(cam) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(cam), REQ_PERMS)
                    pendingPermRequest = request
                    return
                }
                request.grant(request.resources)
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback

                val pickIntent = params.createIntent()
                val shotFile = File(File(cacheDir, "shots").apply { mkdirs() },
                    "shot_${System.currentTimeMillis()}.jpg")
                cameraShotUri = FileProvider.getUriForFile(
                    this@MainActivity, "$packageName.fileprovider", shotFile)
                val takeIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, cameraShotUri)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
                val chooser = Intent(Intent.ACTION_CHOOSER).apply {
                    putExtra(Intent.EXTRA_INTENT, pickIntent)
                    putExtra(Intent.EXTRA_TITLE, "选择图片或拍摄")
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf<Intent>(takeIntent))
                }
                try {
                    startActivityForResult(chooser, REQ_FILE)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "无法打开选择器", Toast.LENGTH_SHORT).show()
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    return false
                }
                return true
            }
        }

        webView.loadUrl("file:///android_asset/index.html")
        requestNeededPermissions()
    }

    private var pendingPermRequest: PermissionRequest? = null

    private fun requestNeededPermissions() {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        perms.add(
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE
        )
        val missing = perms.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            val granted = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            pendingPermRequest?.let { req ->
                if (granted) req.grant(req.resources) else req.deny()
                pendingPermRequest = null
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FILE) return
        val callback = filePathCallback ?: return
        filePathCallback = null
        if (resultCode != RESULT_OK) { callback.onReceiveValue(null); return }
        val results = ArrayList<Uri>()
        val clip = data?.clipData
        when {
            clip != null -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(results::add)
            data?.data != null -> results.add(data.data!!)
            cameraShotUri != null -> results.add(cameraShotUri!!)
        }
        callback.onReceiveValue(results.toTypedArray())
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() { webView.destroy(); super.onDestroy() }

    inner class AlbumBridge {

        @JavascriptInterface
        fun getImages(limit: Int): String {
            val arr = JSONArray()
            try {
                val proj = arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.DATE_ADDED
                )
                val order = "${MediaStore.Images.Media.DATE_TAKEN} DESC, ${MediaStore.Images.Media.DATE_ADDED} DESC"
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    proj, null, null, order
                )?.use { cur ->
                    val idCol = cur.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    var n = 0
                    while (cur.moveToNext() && n < limit) {
                        val id = cur.getLong(idCol)
                        val uri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                        val thumb = try {
                            if (Build.VERSION.SDK_INT >= 29) {
                                val bmp = contentResolver.loadThumbnail(
                                    uri, android.util.Size(160, 160), null)
                                bmpToJpeg(bmp, 60)
                            } else {
                                @Suppress("DEPRECATION")
                                val bmp = MediaStore.Images.Thumbnails.getThumbnail(
                                    contentResolver, id,
                                    MediaStore.Images.Thumbnails.MINI_KIND, null)
                                if (bmp != null) bmpToJpeg(bmp, 60) else ""
                            }
                        } catch (e: Exception) { "" }
                        if (thumb.isEmpty()) continue
                        arr.put(JSONObject().put("id", id).put("thumb", thumb))
                        n++
                    }
                }
            } catch (e: Exception) {
                return "[]"
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun getImage(id: Long): String {
            return try {
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                contentResolver.openInputStream(uri)?.use { ins ->
                    val bmp = BitmapFactory.decodeStream(ins) ?: return ""
                    val scaled = if (bmp.width > 2200) {
                        val h = (bmp.height * 2200f / bmp.width).toInt()
                        Bitmap.createScaledBitmap(bmp, 2200, h, true)
                    } else bmp
                    bmpToJpeg(scaled, 88)
                } ?: ""
            } catch (e: Exception) { "" }
        }

        private fun bmpToJpeg(bmp: Bitmap, quality: Int): String {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            return "data:image/jpeg;base64," +
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    }
}
