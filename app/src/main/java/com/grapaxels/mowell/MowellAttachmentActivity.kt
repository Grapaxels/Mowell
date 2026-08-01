package com.grapaxels.mowell

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.grapaxels.mowell.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/** Small, dependency-free in-app viewer for Mowell attachments and contacts. */
class MowellAttachmentActivity : ComponentActivity() {
    private lateinit var content: LinearLayout
    private var pdf: PdfRenderer? = null
    private var pdfFile: ParcelFileDescriptor? = null
    private var pageIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(247, 245, 240)) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(12, 18, 18, 12) }
        header.addView(Button(this).apply { text = "‹"; setOnClickListener { finish() } }, LinearLayout.LayoutParams(72, 64))
        header.addView(TextView(this).apply {
            text = intent.getStringExtra(EXTRA_NAME) ?: "Mowell viewer"
            textSize = 19f; setTextColor(Color.rgb(21, 19, 26)); setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(header)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(18, 12, 18, 24) }
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        if (intent.getBooleanExtra(EXTRA_CONTACT, false)) showContact()
        else downloadAndShow()
    }

    private fun showContact() {
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "Contact" }
        val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
        content.addView(TextView(this).apply { text = name.take(1).uppercase(); textSize = 48f; gravity = Gravity.CENTER; setTextColor(Color.rgb(115, 87, 246)) }, LinearLayout.LayoutParams(-1, 150))
        content.addView(TextView(this).apply { text = name; textSize = 26f; gravity = Gravity.CENTER; setTextColor(Color.rgb(21, 19, 26)) })
        content.addView(TextView(this).apply { text = phone.ifBlank { "No phone number shared" }; textSize = 18f; gravity = Gravity.CENTER; setPadding(0, 18, 0, 18); setTextColor(Color.DKGRAY) })
        content.addView(Button(this).apply {
            text = "Copy phone number"; isEnabled = phone.isNotBlank()
            setOnClickListener {
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("Phone number", phone))
                text = "Copied"
            }
        })
    }

    private fun downloadAndShow() {
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        if (id.isBlank()) return showError("This attachment is unavailable")
        val progress = ProgressBar(this)
        content.addView(progress)
        lifecycleScope.launch {
            val result = AuthRepository(this@MowellAttachmentActivity).downloadAttachment(id)
            content.removeAllViews()
            result.onSuccess { (serverMime, bytes) ->
                val mime = intent.getStringExtra(EXTRA_MIME).orEmpty().ifBlank { serverMime.substringBefore(';') }
                val name = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "attachment" }
                val file = withContext(Dispatchers.IO) {
                    File(cacheDir, "mowell_viewer").apply { mkdirs() }.resolve("${id}_${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}").apply { writeBytes(bytes) }
                }
                showFile(file, mime, name)
            }.onFailure { showError(it.message ?: "Could not open this attachment") }
        }
    }

    private fun showFile(file: File, mime: String, name: String) {
        when {
            mime.startsWith("image/") -> content.addView(ImageView(this).apply {
                setImageBitmap(BitmapFactory.decodeFile(file.absolutePath)); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
            }, LinearLayout.LayoutParams(-1, -1))
            mime.startsWith("video/") || mime.startsWith("audio/") -> content.addView(VideoView(this).apply {
                val controller = MediaController(this@MowellAttachmentActivity); setMediaController(controller); controller.setAnchorView(this)
                setVideoURI(Uri.fromFile(file)); setOnPreparedListener { if (mime.startsWith("audio/")) it.start() }; requestFocus()
            }, LinearLayout.LayoutParams(-1, -1))
            mime == "application/pdf" || name.endsWith(".pdf", true) -> showPdf(file)
            mime.startsWith("text/") || name.endsWith(".json", true) || name.endsWith(".xml", true) || name.endsWith(".csv", true) -> showText(file.readText().take(1_000_000))
            name.endsWith(".docx", true) || name.endsWith(".xlsx", true) || name.endsWith(".pptx", true) -> showText(extractOfficeText(file, name))
            else -> showError("This file is saved securely in Mowell, but this file type has no built-in preview yet.\n\n$name\n$mime")
        }
    }

    private fun showPdf(file: File) {
        pdfFile = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pdf = PdfRenderer(pdfFile!!)
        val image = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
        val controls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val previous = Button(this).apply { text = "Previous"; setOnClickListener { renderPdf(image, pageIndex - 1) } }
        val next = Button(this).apply { text = "Next"; setOnClickListener { renderPdf(image, pageIndex + 1) } }
        controls.addView(previous); controls.addView(next)
        content.addView(image, LinearLayout.LayoutParams(-1, 0, 1f)); content.addView(controls)
        renderPdf(image, 0)
    }

    private fun renderPdf(image: ImageView, requested: Int) {
        val renderer = pdf ?: return
        pageIndex = requested.coerceIn(0, renderer.pageCount - 1)
        renderer.openPage(pageIndex).use { page ->
            val width = resources.displayMetrics.widthPixels.coerceAtLeast(720)
            val height = (width.toFloat() / page.width * page.height).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            image.setImageBitmap(bitmap)
        }
    }

    private fun showText(value: String) {
        content.gravity = Gravity.TOP
        content.addView(ScrollView(this).apply { addView(TextView(this@MowellAttachmentActivity).apply {
            text = value.ifBlank { "No readable text was found in this document." }; textSize = 16f; setTextColor(Color.rgb(21, 19, 26)); setTextIsSelectable(true); setPadding(12, 12, 12, 36)
        }) }, LinearLayout.LayoutParams(-1, -1))
    }

    private fun extractOfficeText(file: File, name: String): String = runCatching {
        ZipFile(file).use { zip ->
            val entries = when {
                name.endsWith(".docx", true) -> listOfNotNull(zip.getEntry("word/document.xml"))
                name.endsWith(".xlsx", true) -> listOfNotNull(zip.getEntry("xl/sharedStrings.xml"))
                else -> zip.entries().toList().filter { it.name.matches(Regex("ppt/slides/slide\\d+\\.xml")) }.sortedBy { it.name }
            }
            entries.joinToString("\n\n") { entry ->
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
                    .replace(Regex("</(w:p|a:p|si)>"), "\n")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            }.trim()
        }
    }.getOrElse { "This document could not be previewed.\n\n${it.message.orEmpty()}" }

    private fun showError(message: String) {
        content.removeAllViews(); content.addView(TextView(this).apply { text = message; textSize = 17f; gravity = Gravity.CENTER; setTextColor(Color.DKGRAY) }, LinearLayout.LayoutParams(-1, -1))
    }

    override fun onDestroy() { pdf?.close(); pdfFile?.close(); super.onDestroy() }

    companion object {
        const val EXTRA_ID = "attachment_id"
        const val EXTRA_MIME = "attachment_mime"
        const val EXTRA_NAME = "attachment_name"
        const val EXTRA_CONTACT = "contact"
        const val EXTRA_PHONE = "phone"
    }
}
