package `in`.senthilnathan_dev.sentrack.feature.finance

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.senthilnathan_dev.sentrack.core.data.finance.TaxDocumentEntity
import `in`.senthilnathan_dev.sentrack.core.security.vault.FileVault
import `in`.senthilnathan_dev.sentrack.core.ui.IconAction
import `in`.senthilnathan_dev.sentrack.core.ui.Message
import `in`.senthilnathan_dev.sentrack.core.ui.MessageKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shows a stored document inside SenTrack. PDFs are rendered page by page; images can be pinch-zoomed. A readable
 * copy of a PDF exists only in the app's private cache while this screen is open and is deleted when it closes
 * (and whenever the vault locks). Screenshots are blocked app-wide.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DocumentViewerScreen(
    doc: TaxDocumentEntity,
    service: TaxDocumentService,
    files: FileVault,
    onExport: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack) },
                actions = { IconAction(Icons.Filled.Download, "Save a readable copy", onExport) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                doc.mimeType == "application/pdf" -> PdfViewer(doc, service)
                doc.mimeType.startsWith("image/") -> ImageViewer(doc, files)
                else -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Message(
                        "SenTrack cannot preview this kind of file. It is safely stored; use the download icon to save a readable copy.",
                        MessageKind.INFO,
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfViewer(doc: TaxDocumentEntity, service: TaxDocumentService) {
    var file by remember(doc.id) { mutableStateOf<File?>(null) }
    var failed by remember(doc.id) { mutableStateOf(false) }
    val holder = remember(doc.id) { arrayOfNulls<File>(1) }

    LaunchedEffect(doc.id) {
        try {
            file = service.decryptToCache(doc).also { holder[0] = it }
        } catch (e: Exception) {
            failed = true
        }
    }
    DisposableEffect(doc.id) { onDispose { holder[0]?.delete() } }

    val current = file
    when {
        failed -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Message("This document could not be opened.", MessageKind.ERROR)
        }
        current == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Decrypting…") }
        else -> PdfPages(current)
    }
}

@Composable
private fun PdfPages(file: File) {
    val renderer = remember(file) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull()
    }
    DisposableEffect(renderer) { onDispose { runCatching { renderer?.close() } } }
    if (renderer == null) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Message("This PDF could not be displayed (it may be password-protected). Use the download icon to save a copy.", MessageKind.ERROR)
        }
        return
    }
    val mutex = remember(renderer) { Mutex() }   // PdfRenderer handles one page at a time
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items((0 until renderer.pageCount).toList()) { index ->
            var bitmap by remember(index) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(index) {
                bitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        mutex.withLock {
                            renderer.openPage(index).use { page ->
                                val height = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                                Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(AndroidColor.WHITE)   // PDFs have a transparent page background
                                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }.asImageBitmap()
                            }
                        }
                    }.getOrNull()
                }
            }
            val shown = bitmap
            if (shown == null) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.7f).padding(8.dp), contentAlignment = Alignment.Center) { Text("Page ${index + 1}…") }
            } else {
                Image(
                    bitmap = shown,
                    contentDescription = "Page ${index + 1}",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
    }
}

@Composable
private fun ImageViewer(doc: TaxDocumentEntity, files: FileVault) {
    var bitmap by remember(doc.id) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(doc.id) { mutableStateOf(false) }
    LaunchedEffect(doc.id) {
        val decoded = withContext(Dispatchers.IO) { runCatching { decodeSampled(files, doc.vaultFileId) }.getOrNull() }
        if (decoded == null) failed = true else bitmap = decoded.asImageBitmap()
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale > 1f) offset + pan else Offset.Zero
    }
    val shown = bitmap
    when {
        failed -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Message("This image could not be opened.", MessageKind.ERROR)
        }
        shown == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Decrypting…", color = MaterialTheme.colorScheme.onSurface) }
        else -> Image(
            bitmap = shown,
            contentDescription = doc.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .transformable(transform)
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        )
    }
}

/** Decodes an image from the vault, shrinking very large photos so they do not exhaust memory. */
private fun decodeSampled(files: FileVault, id: String, maxEdge: Int = 2400): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    files.open(id).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > maxEdge || bounds.outHeight / sample > maxEdge) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return files.open(id).use { BitmapFactory.decodeStream(it, null, options) }
}
