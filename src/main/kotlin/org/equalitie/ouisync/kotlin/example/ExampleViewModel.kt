package org.equalitie.ouisync.kotlin.example

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.nio.charset.StandardCharsets
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.equalitie.ouisync.service.Service
import org.equalitie.ouisync.service.initLog
import org.equalitie.ouisync.session.EntryType
import org.equalitie.ouisync.session.OuisyncException
import org.equalitie.ouisync.session.Repository
import org.equalitie.ouisync.session.Session
import org.equalitie.ouisync.session.ShareToken
import org.equalitie.ouisync.session.close
import org.equalitie.ouisync.session.create
import org.equalitie.ouisync.session.subscribe

private const val TAG = "ouisync.example"

private const val DEFAULT_REPOSITORY_NAME = "index"
private const val ICONS_REPOSITORY_NAME = "icons"
private const val DEFAULT_REPOSITORY_TOKEN_FALLBACK =
    "https://ouisync.net/r#AwEgLOP2aHS9R4inhIyRIEAcZgyDSz-auVOltFxEnytAHkYgOW10G5WjovhC_MxE9gBuLGjoTseV0ZhbKj72EubYvio?name=Apps"

private const val ICONS_REPOSITORY_TOKEN_FALLBACK =
    "https://ouisync.net/r#AwEgljZ2Zac95VB-D1ckOXInf6e42IKpIO4tTfYRg9g7sLQgwODn4oA9KxlY77Ab17iG9XnpFg-hfwMQC9TpQXeZfHY?name=icons"

private val DEFAULT_REPOSITORY_TOKEN: String by lazy {
    val v = BuildConfig.OUISYNC_INDEX_REPO_TOKEN.trim()
    if (v.isNotBlank()) v else DEFAULT_REPOSITORY_TOKEN_FALLBACK
}

private val ICONS_REPOSITORY_TOKEN: String by lazy {
    val v = BuildConfig.OUISYNC_ICONS_REPO_TOKEN.trim()
    if (v.isNotBlank()) v else ICONS_REPOSITORY_TOKEN_FALLBACK
}

class ExampleViewModel(
    private val configDir: String,
    private val storeDir: String,
    cacheDir: String,
) : ViewModel() {
    private var service: Service? = null
    private var session: Session? = null
    private var indexMonitorStarted = false

    private val cacheDirFile = File(cacheDir)

    var sessionError by mutableStateOf<String?>(null)
        private set

    var repositories by mutableStateOf<Map<String, Repository>>(mapOf())
        private set

    var indexState by mutableStateOf<IndexState>(IndexState.Loading)
        private set

    var appDownloads by mutableStateOf<Map<String, AppDownloadState>>(emptyMap())
        private set

    var peers by mutableStateOf<List<PeerInfo>>(emptyList())
        private set

    var iconCacheVersion by mutableStateOf(0)
        private set

    @Volatile
    private var requestedIconFiles: Set<String> = emptySet()
    private var iconsCacheJob: Job? = null

    private val appMonitorJobs = mutableMapOf<String, Job>()
    private var peerMonitorJob: Job? = null

    private val json = Json { ignoreUnknownKeys = true }
    private val defaultPeers = listOf("quic/51.79.21.142:20209", "tcp/51.79.21.142:20209")

    init {
        cacheDirFile.mkdirs()
        initLog()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                service = Service.start(configDir)
            } catch (e: OuisyncException.ServiceAlreadyRunning) {
                Log.d(TAG, "Service already running")
            } catch (e: Exception) {
                Log.e(TAG, "Service.start failed", e)
                sessionError = e.toString()
            }

            if (service != null) {
                try {
                    session = Session.create(configDir)
                    session?.setStoreDir(storeDir)
                } catch (e: Exception) {
                    Log.e(TAG, "Session.create failed", e)
                    sessionError = e.toString()
                } catch (e: java.lang.Error) {
                    Log.e(TAG, "Session.create failed", e)
                    sessionError = e.toString()
                }
            }

            session?.let {
                it.bindNetwork(listOf("quic/0.0.0.0:0", "quic/[::]:0"))
                it.setPortForwardingEnabled(true)
                it.setLocalDiscoveryEnabled(true)
                runCatching { it.addUserProvidedPeers(defaultPeers) }
            }

            ensureDefaultRepository()
            ensureIconsRepository()
            startPeerMonitor()
        }
    }


    fun cachedIconFile(app: IndexApp): File? {
        val raw = app.icon.trim()
        if (raw.isBlank()) return null
        val name = sanitizeIconFileName(raw)
        if (name.isBlank()) return null

        val f = File(cacheDirFile, name)
        return f.takeIf { it.exists() && it.isFile && it.length() > 0L }
    }

    private fun bumpIconCacheVersion() {
        viewModelScope.launch(Dispatchers.Main) { iconCacheVersion++ }
    }


    suspend fun createRepository(name: String, token: String) {
        val session = this.session ?: return

        if (repositories.containsKey(name)) {
            Log.e(TAG, "repository named \"$name\" already exists")
            return
        }

        val shareToken: ShareToken? =
            if (token.isNotEmpty()) session.validateShareToken(token) else null

        val repo = session.createRepository(name, token = shareToken)

        repo.setSyncEnabled(true)
        repo.setDhtEnabled(true)
        repo.setPexEnabled(true)

        repositories = repositories + (name to repo)
    }

    suspend fun deleteRepository(name: String) {
        val repo = repositories[name] ?: return
        repositories -= name
        repo.delete()
    }

    private suspend fun openRepositories() {
        val session = this.session ?: return
        val opened = mutableMapOf<String, Repository>()

        for (repo in session.listRepositories().values) {
            try {
                opened[repo.getShortName()] = repo
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read repository name", e)
                opened[repo.toString()] = repo
            }
        }

        repositories = repositories + opened
    }

    private suspend fun ensureIconsRepository() {
        openRepositories()

        repositories[ICONS_REPOSITORY_NAME]?.let { repo ->
            repo.setSyncEnabled(true)
            repo.setDhtEnabled(true)
            repo.setPexEnabled(true)
            return
        }

        repositories.values.firstOrNull {
            try { it.getShortName() == ICONS_REPOSITORY_NAME } catch (_: Exception) { false }
        }?.let { repo ->
            repositories = repositories + (ICONS_REPOSITORY_NAME to repo)

            repo.setSyncEnabled(true)
            repo.setDhtEnabled(true)
            repo.setPexEnabled(true)
            return
        }

        val session = this.session ?: return

        try {
            val token = session.validateShareToken(ICONS_REPOSITORY_TOKEN)
            val repo = session.createRepository(ICONS_REPOSITORY_NAME, token = token)

            repo.setSyncEnabled(true)
            repo.setDhtEnabled(true)
            repo.setPexEnabled(true)

            repositories = repositories + (ICONS_REPOSITORY_NAME to repo)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to ensure icons repository", e)
        }
    }

    private suspend fun ensureDefaultRepository() {
        openRepositories()

        repositories[DEFAULT_REPOSITORY_NAME]?.let {
            it.setSyncEnabled(true)
            it.setDhtEnabled(true)
            it.setPexEnabled(true)
            startIndexMonitor(it)
            return
        }

        repositories.values.firstOrNull {
            try { it.getShortName() == DEFAULT_REPOSITORY_NAME } catch (_: Exception) { false }
        }?.let {
            repositories = repositories + (DEFAULT_REPOSITORY_NAME to it)

            it.setSyncEnabled(true)
            it.setDhtEnabled(true)
            it.setPexEnabled(true)

            startIndexMonitor(it)
            return
        }

        val session = this.session ?: return

        try {
            val token = session.validateShareToken(DEFAULT_REPOSITORY_TOKEN)
            val repo = session.createRepository(DEFAULT_REPOSITORY_NAME, token = token)

            repo.setSyncEnabled(true)
            repo.setDhtEnabled(true)
            repo.setPexEnabled(true)

            repositories = repositories + (DEFAULT_REPOSITORY_NAME to repo)
            startIndexMonitor(repo)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to ensure default repository", e)
        }
    }


    private fun startIndexMonitor(repo: Repository) {
        if (indexMonitorStarted) return
        indexMonitorStarted = true

        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                @OptIn(ExperimentalCoroutinesApi::class)
                val events: ReceiveChannel<Unit> =
                    produce {
                        try {
                            repo.subscribe().collect { send(Unit) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Index monitor subscription ended", e)
                        }
                    }

                try {
                    while (isActive) {
                        try {
                            val file = repo.openFile("/index.json")
                            val length = file.getLength()

                            while (isActive) {
                                val progress = file.getProgress()

                                if (progress < length) {
                                    val progressFraction =
                                        if (length > 0) (progress.toFloat() / length.toFloat()) else 1f
                                    indexState = IndexState.Syncing(progressFraction.coerceIn(0f, 1f))

                                    if (!waitForEvent(events, 1000)) {
                                        break
                                    }
                                    continue
                                }

                                val content = file.read(0, length).toString(StandardCharsets.UTF_8)

                                indexState =
                                    IndexState.Loaded(
                                        json.decodeFromString(IndexFile.serializer(), content),
                                    )

                                refreshAppDownloadsFor(indexState)
                                refreshIconCacheFor(indexState)

                                break
                            }

                            if (!waitForEvent(events, 1000)) {
                                break
                            }
                        } catch (e: SerializationException) {
                            indexState = IndexState.Error(e)
                            if (!waitForEvent(events, 1000)) break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to load index.json (will retry)", e)
                            if (indexState !is IndexState.Loaded) indexState = IndexState.Loading

                            if (!waitForEvent(events, 1000)) break
                        }
                    }
                } finally {
                    events.cancel()
                }

                delay(300)
            }
        }
    }


    private fun refreshIconCacheFor(index: IndexState) {
        val loaded = index as? IndexState.Loaded ?: return

        val iconsFromIndex =
            loaded.index.categories
                .flatMap { it.apps }
                .map { sanitizeIconFileName(it.icon) }
                .filter { it.isNotBlank() }
                .toSet()

        if (iconsFromIndex.isEmpty()) return

        requestedIconFiles = requestedIconFiles + iconsFromIndex

        if (iconsCacheJob?.isActive == true) return

        iconsCacheJob =
            viewModelScope.launch(Dispatchers.IO) {
                ensureIconsRepository()

                val repo =
                    repositories[ICONS_REPOSITORY_NAME]
                        ?: repositories.values.firstOrNull {
                            try { it.getShortName() == ICONS_REPOSITORY_NAME } catch (_: Exception) { false }
                        }
                        ?: return@launch

                repo.setSyncEnabled(true)
                repo.setDhtEnabled(true)
                repo.setPexEnabled(true)

                cacheRequestedIcons(repo)
            }
    }

    private fun sanitizeIconFileName(raw: String): String =
        runCatching { File(raw).name.trim() }.getOrDefault("").trim()

    private fun iconCacheFile(iconFileName: String): File =
        File(cacheDirFile, sanitizeIconFileName(iconFileName))

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun cacheRequestedIcons(repo: Repository) =
        coroutineScope {
            while (isActive) {
                val events: ReceiveChannel<Unit> =
                    produce {
                        try {
                            repo.subscribe().collect { send(Unit) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Icons subscription ended", e)
                        }
                    }

                try {
                    while (isActive) {
                        val wanted =
                            requestedIconFiles.map(::sanitizeIconFileName)
                                .filter { it.isNotBlank() }
                                .toSet()

                        val pending =
                            wanted.filter { name ->
                                val out = iconCacheFile(name)
                                !(out.exists() && out.isFile && out.length() > 0L)
                            }.toSet()

                        if (pending.isEmpty()) return@coroutineScope

                        var didWork = false
                        for (iconName in pending) {
                            if (cacheOneIconIfReady(repo, iconName, events)) didWork = true
                        }

                        if (!didWork) {
                            if (!waitForEvent(events, 2000)) break
                        }
                    }
                } finally {
                    events.cancel()
                }

                delay(300)
            }
        }

    private suspend fun cacheOneIconIfReady(
        repo: Repository,
        iconNameRaw: String,
        events: ReceiveChannel<*>,
    ): Boolean {
        val iconName = sanitizeIconFileName(iconNameRaw)
        if (iconName.isBlank()) return false

        val outFile = iconCacheFile(iconName)

        if (outFile.exists() && outFile.isFile && outFile.length() > 0L) return true

        if (outFile.exists() && outFile.isFile && outFile.length() == 0L) {
            runCatching { outFile.delete() }
        }

        val existsInRepo =
            runCatching {
                repo.readDirectory("").any {
                    it.entryType == EntryType.FILE && it.name == iconName
                }
            }.getOrDefault(false)

        if (!existsInRepo) return false

        return cacheRepoFileToCache(repo, "/$iconName", outFile, events)
    }

    private suspend fun cacheRepoFileToCache(
        repo: Repository,
        repoPath: String,
        outFile: File,
        events: ReceiveChannel<*>,
    ): Boolean {
        if (outFile.exists() && outFile.isFile && outFile.length() > 0L) return true

        if (outFile.exists() && outFile.isFile && outFile.length() == 0L) {
            runCatching { outFile.delete() }
        }

        val tmp = File(outFile.parentFile, ".${outFile.name}.part")
        runCatching { tmp.delete() }

        return try {
            val f = repo.openFile(repoPath)
            val length = f.getLength()

            tmp.outputStream().use { stream ->
                var offset = 0L
                val bufferSize = 64 * 1024

                while (offset < length) {
                    val progress = f.getProgress()
                    val available = progress - offset

                    if (available <= 0) {
                        if (!waitForEvent(events, 1000)) return false
                        continue
                    }

                    val bytesToRead =
                        min(
                            min(bufferSize.toLong(), length - offset),
                            available,
                        )

                    val chunk = f.read(offset, bytesToRead)

                    if (chunk.isEmpty()) {
                        if (!waitForEvent(events, 1000)) return false
                        continue
                    }

                    stream.write(chunk)
                    offset += chunk.size.toLong()
                }

                stream.flush()
            }

            if (outFile.exists()) runCatching { outFile.delete() }

            val ok =
                tmp.renameTo(outFile) ||
                        runCatching {
                            tmp.copyTo(outFile, overwrite = true)
                            true
                        }.getOrDefault(false)

            runCatching { tmp.delete() }

            if (ok && outFile.exists() && outFile.length() > 0L) {
                bumpIconCacheVersion()
                true
            } else {
                runCatching { outFile.delete() }
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache icon $repoPath", e)
            runCatching { tmp.delete() }
            false
        }
    }


    override fun onCleared() {
        peerMonitorJob?.cancel()
        iconsCacheJob?.cancel()

        val repos = repositories.values
        repositories = mapOf()

        viewModelScope.launch {
            for (repo in repos) repo.close()
            session?.close()
            session = null
        }
    }

    private fun startPeerMonitor() {
        if (peerMonitorJob != null) return

        peerMonitorJob =
            viewModelScope.launch(Dispatchers.IO) {
                while (isActive) {
                    updatePeers()
                    delay(1000)
                }
            }
    }

    private suspend fun updatePeers() {
        val session = this.session ?: return
        val fetchedPeers = runCatching { session.getPeers() }.getOrElse { return }

        peers =
            fetchedPeers
                .map { PeerInfo.fromPeer(it) }
                .sortedBy { statePriority(it.state) }
    }

    private fun statePriority(state: String): Int =
        when (state.lowercase()) {
            "active" -> 0
            "handshaking" -> 1
            "known" -> 2
            "connecting" -> 3
            else -> 4
        }


    fun downloadStateFor(app: IndexApp): AppDownloadState =
        appDownloads[appKey(app)] ?: AppDownloadState.NotDownloaded

    fun startDownload(app: IndexApp) {
        val key = appKey(app)
        if (appDownloads[key] is AppDownloadState.Downloading) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                appDownloads = appDownloads + (key to AppDownloadState.Downloading())

                val repoName = appRepositoryName(app)
                val repo =
                    repositories[repoName]
                        ?: run {
                            createRepository(repoName, app.token)
                            repositories[repoName]
                        }
                        ?: throw IllegalStateException("Repository not found after creation")

                repo.setSyncEnabled(true)
                repo.setDhtEnabled(true)
                repo.setPexEnabled(true)

                appDownloads =
                    appDownloads + (key to AppDownloadState.Downloading(repo.getShortName(), progress = 0f))

                monitorApk(key, repo, app.version)
            } catch (e: Exception) {
                appDownloads = appDownloads + (key to AppDownloadState.Error(e.message ?: "Failed to download"))
            }
        }
    }

    fun installApp(context: Context, app: IndexApp) {
        val key = appKey(app)
        val readyState = appDownloads[key] as? AppDownloadState.Ready ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheFile = File(readyState.cachePath)
                if (!cacheFile.exists()) throw IllegalStateException("Cached APK missing")

                Log.i(TAG, "Starting APK install for ${app.name} v${app.version} from ${cacheFile.absolutePath}")

                val apkUri =
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        cacheFile,
                    )

                val installIntent =
                    Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                withContext(Dispatchers.Main) { context.startActivity(installIntent) }
            } catch (e: Exception) {
                appDownloads = appDownloads + (key to AppDownloadState.Error(e.message ?: "Failed to install"))
            }
        }
    }

    fun reinstallApp(app: IndexApp) {
        val key = appKey(app)

        viewModelScope.launch(Dispatchers.IO) {
            val repositoryName =
                (appDownloads[key] as? AppDownloadState.Ready)?.repositoryName ?: appRepositoryName(app)

            appMonitorJobs.remove(key)?.cancelAndJoin()
            removeRepositoryByName(repositoryName)
            deleteCachedApk(repositoryName)

            appDownloads = appDownloads - key
            startDownload(app)
        }
    }

    fun isCachedVersionMismatch(app: IndexApp): Boolean {
        val key = appKey(app)
        val readyState = appDownloads[key] as? AppDownloadState.Ready ?: return false

        val cacheFile = File(readyState.cachePath)
        if (!cacheFile.exists()) return false

        val cachedVersion = extractVersionFromName(cacheFile.nameWithoutExtension) ?: return false
        val expectedVersion = normalizeVersion(app.version)
        if (expectedVersion.isBlank()) return false

        return cachedVersion != expectedVersion
    }

    private fun extractVersionFromName(name: String): String? {
        val match =
            Regex("v?(\\d+(?:[._-]\\d+)*)", RegexOption.IGNORE_CASE)
                .findAll(name)
                .lastOrNull()

        return match?.groupValues?.getOrNull(1)?.let(::normalizeVersion)
    }

    private fun normalizeVersion(version: String): String {
        val cleaned =
            version.trim().trimStart('v', 'V')
                .replace('_', '.')
                .replace('-', '.')

        val numericPrefix = Regex("^([0-9]+(?:\\.[0-9]+)*)").find(cleaned)?.groupValues?.getOrNull(1)
        return numericPrefix ?: cleaned
    }

    private fun appKey(app: IndexApp): String = app.name

    private fun appRepositoryBaseName(app: IndexApp): String =
        "app-${app.name.lowercase()}".replace(Regex("[^a-z0-9-]+"), "-")

    private fun appRepositoryName(app: IndexApp): String {
        val baseName = appRepositoryBaseName(app)
        return repositories.keys.firstOrNull { existing ->
            existing == baseName || existing.startsWith("$baseName-")
        } ?: baseName
    }

    private suspend fun removeRepositoryByName(repoName: String) {
        val repo = repositories[repoName] ?: return
        try {
            repositories -= repoName
            repo.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete repository $repoName", e)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun awaitApk(key: String, repo: Repository, expectedVersion: String?) =
        coroutineScope {
            while (isActive) {
                val events: ReceiveChannel<Unit> =
                    produce {
                        try {
                            repo.subscribe().collect { send(Unit) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "App monitor subscription ended for $key", e)
                        }
                    }

                try {
                    while (isActive) {
                        try {
                            val syncProgress = repo.getSyncProgress()
                            val progressFraction =
                                if (syncProgress.total > 0) {
                                    (syncProgress.value.toFloat() / syncProgress.total).coerceIn(0f, 1f)
                                } else 0f

                            val rootEntries = repo.readDirectory("")
                            val apkEntry =
                                rootEntries.firstOrNull {
                                    it.entryType == EntryType.FILE && it.name.endsWith(".apk")
                                }

                            if (apkEntry != null && syncProgress.total > 0 && syncProgress.value >= syncProgress.total) {
                                val path = "/${apkEntry.name}"
                                cacheApkFor(key, repo, path, expectedVersion)
                                return@coroutineScope
                            }

                            appDownloads =
                                appDownloads + (key to AppDownloadState.Downloading(repo.getShortName(), progressFraction))

                            if (!waitForEvent(events)) {
                                break
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            appDownloads =
                                appDownloads + (key to AppDownloadState.Error(e.message ?: "Failed to sync"))
                            return@coroutineScope
                        }
                    }
                } finally {
                    events.cancel()
                }

                delay(300)
            }
        }

    private fun cacheFileForRepo(
        repoName: String,
        apkFileName: String? = null,
        expectedVersion: String? = null,
    ): File {
        val normalizedVersion = normalizeVersion(expectedVersion ?: "").takeIf { it.isNotBlank() }
        val originalName = apkFileName?.let { File(it).nameWithoutExtension } ?: repoName
        val baseName =
            if (normalizedVersion != null && !originalName.contains(normalizedVersion)) "$originalName-v$normalizedVersion"
            else originalName

        val extension = apkFileName?.let { File(it).extension }.takeUnless { it.isNullOrBlank() } ?: "apk"
        return File(cacheDirFile, "$repoName-$baseName.$extension")
    }

    private fun findCachedApk(app: IndexApp): File? {
        val baseName = appRepositoryBaseName(app)
        return cacheDirFile.listFiles()?.firstOrNull { file ->
            file.isFile && (file.name == "$baseName.apk" || file.name.startsWith("$baseName-"))
        }
    }

    private fun deleteCachedApk(repoName: String) {
        cacheDirFile
            .listFiles()
            ?.filter { file -> file.name == "$repoName.apk" || file.name.startsWith("$repoName-") }
            ?.forEach { file -> runCatching { file.delete() } }
    }

    private suspend fun cacheApkFor(
        key: String,
        repo: Repository,
        apkPath: String,
        expectedVersion: String?,
    ) {
        try {
            val cacheFile = cacheFileForRepo(repo.getShortName(), apkPath, expectedVersion)
            val file = repo.openFile(apkPath)
            val length = file.getLength()

            appDownloads = appDownloads + (key to AppDownloadState.Caching(repo.getShortName(), 0f))

            cacheFile.outputStream().use { stream ->
                var offset = 0L
                val bufferSize = 1024 * 1024

                while (offset < length) {
                    val bytesToRead = min(bufferSize.toLong(), length - offset)
                    val chunk = file.read(offset, bytesToRead)
                    if (chunk.isEmpty()) throw IllegalStateException("Failed to read APK bytes from repository")

                    stream.write(chunk)
                    offset += chunk.size.toLong()

                    val progress =
                        if (length > 0) (offset.toFloat() / length.toFloat()).coerceIn(0f, 1f) else 1f

                    appDownloads = appDownloads + (key to AppDownloadState.Caching(repo.getShortName(), progress))
                }

                stream.flush()
            }

            appDownloads = appDownloads + (key to AppDownloadState.Ready(repo.getShortName(), cacheFile.absolutePath))
        } catch (e: Exception) {
            appDownloads = appDownloads + (key to AppDownloadState.Error(e.message ?: "Failed to cache APK"))
        }
    }

    private fun monitorApk(key: String, repo: Repository, expectedVersion: String?) {
        val existingJob = appMonitorJobs[key]
        if (existingJob?.isActive == true) return
        val job = viewModelScope.launch(Dispatchers.IO) { awaitApk(key, repo, expectedVersion) }
        appMonitorJobs[key] = job
    }

    private suspend fun waitForEvent(events: ReceiveChannel<*>, timeoutMs: Long? = null): Boolean =
        try {
            if (timeoutMs != null) {
                withTimeoutOrNull(timeoutMs) { events.receive() }
            } else {
                events.receive()
            }
            true
        } catch (_: ClosedReceiveChannelException) {
            false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }

    fun cancelDownload(app: IndexApp) {
        val key = appKey(app)
        viewModelScope.launch(Dispatchers.IO) {
            appMonitorJobs.remove(key)?.cancelAndJoin()
            removeRepository(app)
            deleteCachedApk(appRepositoryName(app))
            appDownloads = appDownloads - key
        }
    }

    fun deleteAppRepository(app: IndexApp) {
        val key = appKey(app)
        viewModelScope.launch(Dispatchers.IO) {
            appMonitorJobs.remove(key)?.cancelAndJoin()
            removeRepository(app)
            deleteCachedApk(appRepositoryName(app))
            appDownloads = appDownloads + (key to AppDownloadState.NotDownloaded)
        }
    }

    private suspend fun removeRepository(app: IndexApp) =
        removeRepositoryByName(appRepositoryName(app))

    private fun refreshAppDownloadsFor(index: IndexState) {
        val loadedIndex = index as? IndexState.Loaded ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val updates = mutableMapOf<String, AppDownloadState>()

            loadedIndex.index.categories.forEach { category ->
                category.apps.forEach { app ->
                    val key = appKey(app)

                    if (appMonitorJobs[key]?.isActive == true) return@forEach

                    val repoName = appRepositoryName(app)
                    val repo = repositories[repoName]

                    if (repo == null) {
                        val cachedApk = findCachedApk(app)
                        updates[key] =
                            if (cachedApk?.exists() == true) AppDownloadState.Ready(repoName, cachedApk.absolutePath)
                            else AppDownloadState.NotDownloaded
                        return@forEach
                    }

                    try {
                        repo.setSyncEnabled(true)
                        repo.setDhtEnabled(true)
                        repo.setPexEnabled(true)

                        val repoShortName = repo.getShortName()

                        val syncProgress = repo.getSyncProgress()
                        val progressFraction =
                            if (syncProgress.total > 0) {
                                (syncProgress.value.toFloat() / syncProgress.total).coerceIn(0f, 1f)
                            } else 0f

                        val apkEntry =
                            repo.readDirectory("").firstOrNull {
                                it.entryType == EntryType.FILE && it.name.endsWith(".apk")
                            }

                        if (apkEntry != null && syncProgress.total > 0 && syncProgress.value >= syncProgress.total) {
                            val cacheFile = findCachedApk(app)
                            if (cacheFile?.exists() == true) {
                                updates[key] = AppDownloadState.Ready(repoShortName, cacheFile.absolutePath)
                            } else {
                                updates[key] = AppDownloadState.Caching(repoShortName, 0f)
                                monitorApk(key, repo, app.version)
                            }
                        } else {
                            updates[key] = AppDownloadState.Downloading(repoShortName, progressFraction)
                            monitorApk(key, repo, app.version)
                        }
                    } catch (e: Exception) {
                        updates[key] = AppDownloadState.Error(e.message ?: "Failed to check repository state")
                    }
                }
            }

            if (updates.isNotEmpty()) appDownloads = appDownloads + updates
        }
    }
}

sealed class IndexState {
    data object Loading : IndexState()
    data class Syncing(val progress: Float) : IndexState()
    data class Loaded(val index: IndexFile) : IndexState()
    data class Error(val exception: Exception) : IndexState()
}

@Serializable
data class IndexFile(
    @SerialName("list") val categories: List<IndexCategory> = emptyList(),
)

@Serializable
data class IndexCategory(
    @SerialName("category") val name: String = "",
    val apps: List<IndexApp> = emptyList(),
)

@Serializable
data class IndexApp(
    val name: String = "",
    val version: String = "",
    val icon: String = "",
    val token: String = "",
    @SerialName("dscb") val description: String = "",
)

sealed class AppDownloadState {
    data object NotDownloaded : AppDownloadState()
    data class Downloading(val repositoryName: String? = null, val progress: Float? = null) : AppDownloadState()
    data class Caching(val repositoryName: String, val progress: Float? = null) : AppDownloadState()
    data class Ready(val repositoryName: String, val cachePath: String) : AppDownloadState()
    data class Error(val message: String) : AppDownloadState()
}

data class PeerInfo(
    val address: String,
    val state: String,
) {
    companion object {
        fun fromPeer(peer: Any): PeerInfo {
            val address = peer.extractString("getAddr", "getAddress", "addr", "address") ?: peer.toString()
            val state = formatPeerStateLabel(peer.extractChild("getState", "state"))
            return PeerInfo(address, state)
        }
    }
}

private fun Any?.extractChild(vararg getters: String): Any? {
    for (getter in getters) {
        val value = runCatching { this?.javaClass?.getMethod(getter)?.invoke(this) }.getOrNull()
        if (value != null) return value
    }
    return null
}

private fun Any?.extractString(vararg getters: String): String? {
    for (getter in getters) {
        val value = runCatching { this?.javaClass?.getMethod(getter)?.invoke(this) }.getOrNull()
        if (value is String && value.isNotBlank()) return value

        val propertyValue = runCatching { this.findProperty(getter) }.getOrNull()
        if (propertyValue is String && propertyValue.isNotBlank()) return propertyValue
    }
    return findProperty(getters.firstOrNull() ?: "") as? String
}

private fun Any?.findProperty(name: String): Any? {
    if (name.isBlank()) return null
    val match = collectFields().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
    return runCatching {
        match.isAccessible = true
        match.get(this)
    }.getOrNull()
}

private fun Any?.collectFields(): List<java.lang.reflect.Field> {
    val rootClass = this?.javaClass ?: return emptyList()
    return generateSequence(rootClass) { it.superclass }
        .flatMap { it.declaredFields.asSequence() }
        .toList()
}

private fun formatPeerStateLabel(state: Any?): String {
    val rawName = (state as? String) ?: state?.javaClass?.simpleName ?: return "Unknown"
    return when (rawName.lowercase()) {
        "connecting" -> "Connecting"
        "handshaking" -> "Handshaking"
        "active" -> "Active"
        "known" -> "Known"
        else -> rawName
    }
}
