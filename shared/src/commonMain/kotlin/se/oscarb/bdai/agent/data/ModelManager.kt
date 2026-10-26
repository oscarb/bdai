package se.oscarb.bdai.agent.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import se.oscarb.bdai.agent.domain.LlmEngine
import se.oscarb.bdai.agent.domain.ModelState

/**
 * Owns the model's lifecycle: download it once, then load it into the [LlmEngine]. Work runs in
 * [applicationScope] so a download keeps going when the chat sheet is closed.
 */
class ModelManager(
    private val engine: LlmEngine,
    private val downloader: ModelDownloader,
    private val paths: ModelPaths,
    private val applicationScope: CoroutineScope
) {
    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private var job: Job? = null

    /**
     * Loads the model if it's already on the device. Does nothing while a download or load runs.
     */
    fun prepare() {
        if (job?.isActive == true || engine.isLoaded) {
            if (engine.isLoaded) _state.value = ModelState.Ready
            return
        }
        if (SystemFileSystem.exists(Path(paths.modelFile))) {
            load()
        } else {
            _state.value = ModelState.NotDownloaded
        }
    }

    fun download() {
        if (job?.isActive == true) return
        job = applicationScope.launch {
            try {
                downloader.download(
                    url = LocalModel.DOWNLOAD_URL,
                    targetFile = paths.modelFile,
                    expectedSizeBytes = LocalModel.SIZE_BYTES
                ).collect { progress ->
                    _state.value = ModelState.Downloading(progress.downloadedBytes, progress.totalBytes)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Model download failed: $e")
                _state.value = ModelState.DownloadFailed(e.message ?: "Model download failed")
                return@launch
            }
            loadModel()
        }
    }

    fun retryLoad() {
        if (job?.isActive == true) return
        if (SystemFileSystem.exists(Path(paths.modelFile))) {
            load()
        } else {
            download()
        }
    }

    private fun load() {
        job = applicationScope.launch { loadModel() }
    }

    private suspend fun loadModel() {
        _state.value = ModelState.Loading
        try {
            engine.load(modelPath = paths.modelFile, cacheDir = paths.cacheDir)
            _state.value = ModelState.Ready
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Model load failed: $e")
            _state.value = ModelState.LoadFailed(e.message ?: "Model load failed")
        }
    }
}
