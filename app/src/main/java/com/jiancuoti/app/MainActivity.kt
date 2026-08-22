package com.jiancuoti.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * 简错题 · Android 壳
 * WebView 承载单文件网页应用（assets/index.html），
 * 处理文件选择与相机拍摄（连拍模式）。
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraShotUri: Uri? = null

    companion object {
        private const val REQ_FILE = 1001
        private const val REQ_CAMERA_PERM = 1002
    }

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

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.startsWith("http:") || url.startsWith("https:")) {
                    // 外链用系统浏览器打开
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    return true
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback

                val pickIntent = params.createIntent()

                // 相机拍摄意图（连拍 / 拍照上传）
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
                    Toast.makeText(this@MainActivity, "无法打开选择器：${e.message}", Toast.LENGTH_SHORT).show()
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    return false
                }
                return true
            }
        }

        webView.loadUrl("file:///android_asset/index.html")

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA_PERM)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FILE) return
        val callback = filePathCallback ?: return
        filePathCallback = null

        if (resultCode != RESULT_OK) {
            callback.onReceiveValue(null)
            return
        }

        val results = ArrayList<Uri>()
        val clip = data?.clipData
        when {
            clip != null -> for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let(results::add)
            }
            data?.data != null -> results.add(data.data!!)
            cameraShotUri != null -> results.add(cameraShotUri!!)
        }
        callback.onReceiveValue(results.toTypedArray())
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA_PERM &&
            grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "未授予相机权限，连拍功能将不可用", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
