package com.saad.capvid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.saad.capvid.editor.EditorScreen
import com.saad.capvid.editor.EditorViewModel
import com.saad.capvid.home.HomeScreen
import com.saad.capvid.importflow.CaptionSetupScreen
import com.saad.capvid.model.CaptionLanguage
import com.saad.capvid.model.Project
import com.saad.capvid.project.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: EditorViewModel
    private lateinit var store: ProjectStore
    private var currentScreen: Screen = Screen.HOME
    private var setupScreen: CaptionSetupScreen? = null
    private var setupJob: Job? = null
    private var recordFile: File? = null

    private enum class Screen { HOME, SETUP, EDITOR }

    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importSelectedVideo(uri)
    }

    private val recordVideo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val file = recordFile
        if (result.resultCode == RESULT_OK && file != null && file.isFile && file.length() > 0L) {
            importRecordedVideo(file)
        } else {
            file?.delete()
            Toast.makeText(this, "Recording cancelled", Toast.LENGTH_SHORT).show()
        }
        recordFile = null
    }

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions[Manifest.permission.CAMERA] == true && permissions[Manifest.permission.RECORD_AUDIO] == true
        if (granted) launchRecorder() else Toast.makeText(this, "Camera and microphone permissions are required to record", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[EditorViewModel::class.java]
        store = ProjectStore(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (currentScreen) {
                    Screen.HOME -> finish()
                    Screen.SETUP, Screen.EDITOR -> showHome()
                }
            }
        })
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.transcription.collect { state ->
                        setupScreen?.setProgress(state.phase, state.progress)
                        setupScreen?.setBusy(state.running)
                    }
                }
            }
        }
        showHome()
    }

    private fun showHome() {
        setupJob?.cancel()
        setupScreen = null
        currentScreen = Screen.HOME
        setContentView(HomeScreen(this, object : HomeScreen.Callbacks {
            override fun onCaptionVideo() { pickVideo.launch("video/*") }
            override fun onRecordVideo() { requestRecordingPermissions() }
            override fun onOpenProject(project: Project) {
                if (File(project.videoPath).isFile) showEditor(project) else Toast.makeText(this@MainActivity, "The source video is no longer available", Toast.LENGTH_LONG).show()
            }
        }))
    }

    private fun importSelectedVideo(uri: Uri) {
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { store.copyVideo(uri, displayName(uri)) }
                showSetup(file)
            } catch (error: Throwable) {
                Toast.makeText(this@MainActivity, error.message ?: "Unable to import video", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun importRecordedVideo(file: File) {
        lifecycleScope.launch {
            try {
                val copied = withContext(Dispatchers.IO) { store.importVideoFile(file) }
                file.delete()
                showSetup(copied)
            } catch (error: Throwable) {
                Toast.makeText(this@MainActivity, error.message ?: "Unable to import recording", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showSetup(video: File) {
        val setup = CaptionSetupScreen(this, video, object : CaptionSetupScreen.Callbacks {
            override fun onProceed(language: CaptionLanguage, addCaptions: Boolean) {
                val project = Project.create(video.absolutePath, video.name, readDuration(video), language).copy(addCaptions = addCaptions)
                viewModel.open(project)
                if (!addCaptions) {
                    showEditor(project)
                    return
                }
                setup.setBusy(true)
                setupJob?.cancel()
                setupJob = lifecycleScope.launch {
                    viewModel.startTranscription(language)
                    viewModel.transcription.filter { !it.running }.collect { state ->
                        if (state.error != null) {
                            setup.setBusy(false)
                            Toast.makeText(this@MainActivity, state.error, Toast.LENGTH_LONG).show()
                            setupJob?.cancel()
                        } else if (state.phase == "Transcription complete") {
                            showEditor(viewModel.project.value ?: project)
                            setupJob?.cancel()
                        }
                    }
                }
            }

            override fun onCancel() { showHome() }
        })
        setupScreen = setup
        currentScreen = Screen.SETUP
        setContentView(setup)
    }

    private fun showEditor(project: Project) {
        setupJob?.cancel()
        setupScreen = null
        viewModel.open(project)
        currentScreen = Screen.EDITOR
        val editor = EditorScreen(this, viewModel, object : EditorScreen.Callbacks {
            override fun onExitEditor() {
                viewModel.save()
                showHome()
            }
        })
        setContentView(editor)
        editor.bind(this)
    }

    private fun requestRecordingPermissions() {
        val required = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val missing = required.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) launchRecorder() else cameraPermission.launch(missing.toTypedArray())
    }

    private fun launchRecorder() {
        val file = File(cacheDir, "record-${System.currentTimeMillis()}.mp4")
        recordFile = file
        val uri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
            putExtra(MediaStore.EXTRA_DURATION_LIMIT, 600)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        recordVideo.launch(intent)
    }

    private fun displayName(uri: Uri): String? = contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

    private fun readDuration(file: File): Long = runCatching {
        MediaMetadataRetriever().let { retriever ->
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            duration
        }
    }.getOrDefault(0L)
}
