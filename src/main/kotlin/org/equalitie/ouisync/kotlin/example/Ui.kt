package org.equalitie.ouisync.kotlin.example

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Rect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.caverock.androidsvg.SVG
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.HorizontalDivider

private val PADDING = 8.dp
private val bottomBarColor = Color(0xFF2E2E2E)

private val TILE_WIDTH = 148.dp
private val TILE_ICON = 76.dp
private val TILE_RADIUS = 18.dp

@Composable
fun ExampleApp(viewModel: ExampleViewModel) {
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var settingsDestination by remember { mutableStateOf(SettingsDestination.Root) }
    var selectedApp by remember { mutableStateOf<IndexApp?>(null) }

    ApplySystemBars(navBarColor = bottomBarColor)

    val showAppDetails = selectedTab == AppTab.Home && selectedApp != null
    val showPeersTopBar = selectedTab == AppTab.Settings && settingsDestination == SettingsDestination.Peers

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            when {
                showAppDetails -> AppDetailsTopBar(app = selectedApp!!, onBack = { selectedApp = null })
                showPeersTopBar -> SettingsTopBar(onBack = { settingsDestination = SettingsDestination.Root })
            }
        },
        bottomBar = {
            ExampleBottomBar(
                selectedTab = selectedTab,
                onSelectTab = {
                    selectedTab = it
                    selectedApp = null
                    if (it == AppTab.Settings) settingsDestination = SettingsDestination.Root
                },
            )
        },
    ) { padding ->
        when (selectedTab) {
            AppTab.Home -> {
                if (selectedApp == null) {
                    AppCatalogScreen(
                        viewModel = viewModel,
                        onOpenApp = { selectedApp = it },
                        modifier = Modifier.padding(padding).fillMaxSize(),
                    )
                } else {
                    AppDetailsScreen(
                        app = selectedApp!!,
                        viewModel = viewModel,
                        modifier = Modifier.padding(padding).fillMaxSize(),
                    )
                }
            }

            AppTab.Settings -> {
                SettingsScreen(
                    destination = settingsDestination,
                    peers = viewModel.peers,
                    onOpenPeers = { settingsDestination = SettingsDestination.Peers },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

private enum class AppTab { Home, Settings }
private enum class SettingsDestination { Root, Peers }

@Composable
private fun ExampleBottomBar(selectedTab: AppTab, onSelectTab: (AppTab) -> Unit) {
    NavigationBar(
        containerColor = bottomBarColor,
        tonalElevation = 0.dp,
        modifier = Modifier.height(62.dp),
    ) {
        val itemColors =
            NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedIconColor = Color.White,
            )

        NavigationBarItem(
            selected = selectedTab == AppTab.Home,
            onClick = { onSelectTab(AppTab.Home) },
            colors = itemColors,
            alwaysShowLabel = false,
            icon = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = "Home",
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
        )

        NavigationBarItem(
            selected = selectedTab == AppTab.Settings,
            onClick = { onSelectTab(AppTab.Settings) },
            colors = itemColors,
            alwaysShowLabel = false,
            icon = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
        )
    }
}

@Composable
fun AppCatalogScreen(
    viewModel: ExampleViewModel,
    onOpenApp: (IndexApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessionError = viewModel.sessionError

    if (sessionError == null) {
        IndexList(
            state = viewModel.indexState,
            viewModel = viewModel,
            onOpenApp = onOpenApp,
            modifier = modifier,
        )
    } else {
        ErrorBox(sessionError, modifier)
    }
}

@Composable
fun IndexList(
    state: IndexState,
    viewModel: ExampleViewModel,
    onOpenApp: (IndexApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = PADDING * 2, vertical = PADDING * 2),
        verticalArrangement = spacedBy(PADDING * 2),
        modifier = modifier.fillMaxSize(),
    ) {
        when (state) {
            IndexState.Loading -> {
                item {
                    StatusCard(
                        title = "Syncing catalog",
                        description = "Searching for peers… Waiting for index.json to sync once peers connect.",
                    )
                }
            }

            is IndexState.Syncing -> {
                item {
                    StatusCard(title = "Syncing catalog") {
                        Column(verticalArrangement = spacedBy(PADDING / 2)) {
                            Text("Downloading the latest app list…")
                            FlatLinearProgress(progress = state.progress)

                            if (state.progress == 0f) {
                                Text("Searching for peers…", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            is IndexState.Error -> {
                item {
                    StatusCard(
                        title = "Failed to load catalog",
                        description = state.exception.message ?: "Unable to read index.json",
                    )
                }
            }

            is IndexState.Loaded -> {
                if (state.index.categories.isEmpty()) {
                    item { StatusCard(title = "Catalog is empty", description = "No apps were found.") }
                } else {
                    items(state.index.categories, key = { it.name }) { category ->
                        CategorySection(
                            category = category,
                            viewModel = viewModel,
                            onOpenApp = onOpenApp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySection(
    category: IndexCategory,
    viewModel: ExampleViewModel,
    onOpenApp: (IndexApp) -> Unit,
) {
    Column(verticalArrangement = spacedBy(PADDING)) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = spacedBy(PADDING),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                category.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${category.apps.size} app${if (category.apps.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box {
            LazyRow(
                horizontalArrangement = spacedBy(PADDING),
                contentPadding = PaddingValues(end = PADDING * 2),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(category.apps, key = { it.name }) { app ->
                    val downloadState = viewModel.downloadStateFor(app)
                    val needsUpdate = viewModel.isCachedVersionMismatch(app)

                    StoreAppTile(
                        app = app,
                        viewModel = viewModel,
                        downloadState = downloadState,
                        needsUpdate = needsUpdate,
                        onClick = { onOpenApp(app) },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(26.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                        ),
                    ),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    }
}

@Composable
private fun StoreAppTile(
    app: IndexApp,
    viewModel: ExampleViewModel,
    downloadState: AppDownloadState,
    needsUpdate: Boolean,
    onClick: () -> Unit,
) {
    val progress =
        when (downloadState) {
            is AppDownloadState.Downloading -> downloadState.progress
            is AppDownloadState.Caching -> downloadState.progress
            else -> null
        }

    val badge =
        when (downloadState) {
            AppDownloadState.NotDownloaded -> "Details"
            is AppDownloadState.Downloading -> "Downloading… ${(progress?.times(100f) ?: 0f).toInt()}%"
            is AppDownloadState.Caching -> "Caching… ${(progress?.times(100f) ?: 0f).toInt()}%"
            is AppDownloadState.Ready -> "Ready"
            is AppDownloadState.Error -> "Error"
        }

    val accent =
        when {
            needsUpdate -> MaterialTheme.colorScheme.tertiary
            downloadState is AppDownloadState.Error -> MaterialTheme.colorScheme.error
            downloadState is AppDownloadState.Ready -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        shape = RoundedCornerShape(TILE_RADIUS),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .width(TILE_WIDTH)
            .clickable(onClick = onClick),
    ) {
        Column(
            verticalArrangement = spacedBy(PADDING),
            modifier = Modifier.padding(PADDING * 1.5f),
        ) {
            AppLogo(
                viewModel = viewModel,
                app = app,
                size = TILE_ICON,
                placeholderCorner = 16.dp,
            )

            Text(
                app.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = spacedBy(PADDING / 2),
            ) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (needsUpdate) {
                    Text(
                        "Update",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            if (progress != null) {
                FlatLinearProgress(progress = min(1f, progress))
            }
        }
    }
}

@Composable
private fun AppLogo(
    viewModel: ExampleViewModel,
    app: IndexApp,
    size: Dp,
    placeholderCorner: Dp,
) {
    @Suppress("UNUSED_VARIABLE")
    val iconCacheVersionTrigger = viewModel.iconCacheVersion


    val file = viewModel.cachedIconFile(app)
    if (file == null) {
        AppIconPlaceholder(name = app.name, size = size, corner = placeholderCorner)
        return
    }

    val density = LocalDensity.current
    val targetPx = with(density) { size.roundToPx() }.coerceAtLeast(24)

    val bmp: Bitmap? by produceState<Bitmap?>(initialValue = null, file.absolutePath, targetPx) {
        value = withContext(Dispatchers.IO) { decodeLogoBitmap(file, targetPx) }
    }

    if (bmp == null) {
        AppIconPlaceholder(name = app.name, size = size, corner = placeholderCorner)
        return
    }

    Image(
        bitmap = bmp!!.asImageBitmap(),
        contentDescription = "${app.name} logo",
        modifier = Modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}

private fun decodeLogoBitmap(file: File, targetPx: Int): Bitmap? {
    val ext = file.extension.lowercase()
    return when (ext) {
        "svg" -> decodeSvgToBitmap(file, targetPx)
        "png", "jpg", "jpeg", "webp" -> decodeRasterToBitmap(file, targetPx)
        else -> decodeRasterToBitmap(file, targetPx)
    }
}

private fun decodeSvgToBitmap(file: File, targetPx: Int): Bitmap? {
    return runCatching {
        file.inputStream().use { input ->
            val svg = SVG.getFromInputStream(input)
            val picture = svg.renderToPicture()

            val pw = picture.width.coerceAtLeast(1)
            val ph = picture.height.coerceAtLeast(1)

            val bmp = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bmp)

            val scale = min(targetPx.toFloat() / pw.toFloat(), targetPx.toFloat() / ph.toFloat())
            val dw = (pw * scale).toInt().coerceAtLeast(1)
            val dh = (ph * scale).toInt().coerceAtLeast(1)
            val left = ((targetPx - dw) / 2f).toInt()
            val top = ((targetPx - dh) / 2f).toInt()

            canvas.drawPicture(picture, Rect(left, top, left + dw, top + dh))
            bmp
        }
    }.getOrNull()
}

private fun decodeRasterToBitmap(file: File, targetPx: Int): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        val srcW = bounds.outWidth.coerceAtLeast(1)
        val srcH = bounds.outHeight.coerceAtLeast(1)

        val sample = computeInSampleSize(srcW, srcH, targetPx, targetPx)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@runCatching null

        val maxDim = maxOf(decoded.width, decoded.height).coerceAtLeast(1)
        if (maxDim <= targetPx) return@runCatching decoded

        val scale = targetPx.toFloat() / maxDim.toFloat()
        val nw = (decoded.width * scale).toInt().coerceAtLeast(1)
        val nh = (decoded.height * scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(decoded, nw, nh, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }.getOrNull()
}

private fun computeInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
    var inSampleSize = 1
    var halfW = srcW / 2
    var halfH = srcH / 2

    while (halfW / inSampleSize >= reqW && halfH / inSampleSize >= reqH) {
        inSampleSize *= 2
    }
    return inSampleSize.coerceAtLeast(1)
}

@Composable
private fun AppIconPlaceholder(
    name: String,
    size: Dp,
    corner: Dp,
) {
    val initials = remember(name) {
        name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "A" }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            initials,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailsTopBar(app: IndexApp, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
    )
}

@Composable
private fun AppDetailsScreen(
    app: IndexApp,
    viewModel: ExampleViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val downloadState = viewModel.downloadStateFor(app)
    val needsUpdate = viewModel.isCachedVersionMismatch(app)

    val progress =
        when (downloadState) {
            is AppDownloadState.Downloading -> downloadState.progress
            is AppDownloadState.Caching -> downloadState.progress
            else -> null
        }

    val statusText =
        when (downloadState) {
            AppDownloadState.NotDownloaded -> "Not downloaded"
            is AppDownloadState.Downloading ->
                progress?.let { "Downloading repository… ${(it * 100).toInt()}%" } ?: "Downloading repository…"
            is AppDownloadState.Caching ->
                progress?.let { "Copying APK to cache… ${(it * 100).toInt()}%" } ?: "Copying APK to cache…"
            is AppDownloadState.Ready -> "APK cached from ${downloadState.repositoryName}"
            is AppDownloadState.Error -> downloadState.message
        }

    val primaryLabel =
        when (downloadState) {
            AppDownloadState.NotDownloaded -> "Download"
            is AppDownloadState.Downloading -> "Download"
            is AppDownloadState.Caching -> "Download"
            is AppDownloadState.Ready -> "Install"
            is AppDownloadState.Error -> "Retry"
        }

    val primaryEnabled =
        downloadState !is AppDownloadState.Downloading && downloadState !is AppDownloadState.Caching

    val onPrimaryClick: () -> Unit =
        when (downloadState) {
            is AppDownloadState.Ready -> ({ viewModel.installApp(context, app) })
            is AppDownloadState.Downloading -> ({})
            is AppDownloadState.Caching -> ({})
            else -> ({ viewModel.startDownload(app) })
        }

    val secondary: Pair<String, () -> Unit>? =
        when (downloadState) {
            is AppDownloadState.Downloading -> "Cancel download" to { viewModel.cancelDownload(app) }
            is AppDownloadState.Caching -> "Delete" to { viewModel.deleteAppRepository(app) }
            is AppDownloadState.Ready -> "Delete" to { viewModel.deleteAppRepository(app) }
            else -> null
        }

    Column(
        verticalArrangement = spacedBy(PADDING * 2),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PADDING * 2, vertical = PADDING * 2),
    ) {
        Row(horizontalArrangement = spacedBy(PADDING * 2), verticalAlignment = Alignment.CenterVertically) {
            AppLogo(
                viewModel = viewModel,
                app = app,
                size = 92.dp,
                placeholderCorner = 22.dp,
            )

            Column(verticalArrangement = spacedBy(PADDING / 2), modifier = Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = spacedBy(PADDING), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "v${app.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (app.token.isNotEmpty()) {
                        Text(
                            "Token: present",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = spacedBy(PADDING),
                modifier = Modifier.fillMaxWidth().padding(PADDING * 2),
            ) {
                Text("Status", fontWeight = FontWeight.SemiBold)
                Text(statusText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (progress != null) {
                    FlatLinearProgress(progress = min(1f, progress))
                }

                if (downloadState is AppDownloadState.Error) {
                    Text(
                        downloadState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        Column(verticalArrangement = spacedBy(PADDING), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onPrimaryClick,
                enabled = primaryEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(primaryLabel) }

            secondary?.let { (label, onClick) ->
                OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }

            if (needsUpdate) {
                Button(
                    onClick = { viewModel.reinstallApp(app) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Update") }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = spacedBy(PADDING),
                modifier = Modifier.fillMaxWidth().padding(PADDING * 2),
            ) {
                Text("Description", fontWeight = FontWeight.SemiBold)
                Text(app.description.ifBlank { "No description provided." }, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(PADDING * 2))
    }
}

@Composable
private fun SettingsScreen(
    destination: SettingsDestination,
    peers: List<PeerInfo>,
    onOpenPeers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        SettingsDestination.Root -> SettingsHome(onOpenPeers, modifier)
        SettingsDestination.Peers -> PeerListScreen(peers = peers, modifier = modifier)
    }
}

@Composable
private fun SettingsHome(onOpenPeers: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = spacedBy(PADDING * 2),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = PADDING * 2, vertical = PADDING * 2),
    ) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenPeers),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                horizontalArrangement = spacedBy(PADDING * 2),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(PADDING * 2),
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Column(verticalArrangement = spacedBy(PADDING / 2), modifier = Modifier.weight(1f)) {
                    Text("Peers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "View connected peers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            "Version 0.0.2",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = { Text("Peers") },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
    )
}

@Composable
private fun PeerListScreen(peers: List<PeerInfo>, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = spacedBy(PADDING * 2),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = PADDING * 2, vertical = PADDING * 2),
    ) {
        if (peers.isEmpty()) {
            Text(
                "Searching for peers…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }

        LazyColumn(verticalArrangement = spacedBy(PADDING * 1.5f)) {
            items(peers) { peer -> PeerCard(peer) }
        }
    }
}

@Composable
private fun PeerCard(peer: PeerInfo) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = spacedBy(PADDING), modifier = Modifier.fillMaxWidth().padding(PADDING * 2)) {
            Column(verticalArrangement = spacedBy(PADDING / 4)) {
                Text(peer.address, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(peer.state, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ErrorBox(error: String, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize().padding(PADDING * 2),
    ) {
        Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun FlatLinearProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = indicatorColor.copy(alpha = 0.22f),
) {
    val p = progress.coerceIn(0f, 1f)

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val y = size.height / 2f
        val stroke = size.height

        drawLine(
            color = trackColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = stroke,
            cap = StrokeCap.Butt,
        )

        drawLine(
            color = indicatorColor,
            start = Offset(0f, y),
            end = Offset(size.width * p, y),
            strokeWidth = stroke,
            cap = StrokeCap.Butt,
        )
    }
}

@Composable
private fun StatusCard(
    title: String,
    description: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = spacedBy(PADDING),
            modifier = Modifier.fillMaxWidth().padding(PADDING * 2),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            content?.invoke()
        }
    }
}

@Composable
private fun ApplySystemBars(navBarColor: Color) {
    val context = LocalContext.current
    val view = LocalView.current

    SideEffect {
        val activity = context as? Activity ?: return@SideEffect
        val window = activity.window

        window.navigationBarColor = navBarColor.toArgb()
        window.statusBarColor = navBarColor.toArgb()

        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightNavigationBars = false
        controller.isAppearanceLightStatusBars = false
    }
}
