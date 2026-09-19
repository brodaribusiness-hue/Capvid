package com.saad.capvid.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.saad.capvid.export.GallerySaver
import com.saad.capvid.export.VideoExporter
import com.saad.capvid.history.Commands
import com.saad.capvid.history.HistoryManager
import com.saad.capvid.history.ProjectCommand
import com.saad.capvid.model.CaptionLanguage
import com.saad.capvid.model.CaptionWord
import com.saad.capvid.model.Project
import com.saad.capvid.model.VideoTransform
import com.saad.capvid.model.CaptionStyle
import com.saad.capvid.whisper.TranscriptionEngine
import com.saad.capvid.project.ProjectStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    data class TranscriptionState(val running: Boolean = false, val phase: String = "", val progress: Int = 0, val error: String? = null)
    data class ExportState(val running: Boolean = false, val progress: Int = 0, val uri: android.net.Uri? = null, val error: String? = null)

    private val store = ProjectStore(application)
    private var history: HistoryManager? = null
    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()
    private val _transcription = MutableStateFlow(TranscriptionState())
    val transcription: StateFlow<TranscriptionState> = _transcription.asStateFlow()
    private val _export = MutableStateFlow(ExportState())
    val export: StateFlow<ExportState> = _export.asStateFlow()

    fun open(project: Project) {
        history = HistoryManager(project)
        _project.value = project
    }

    fun save() {
        _project.value?.let { saved ->
            val result = store.save(saved)
            _project.value = result
            history?.clear(result)
        }
    }

    fun startTranscription(language: CaptionLanguage) {
        val base = _project.value ?: return
        if (_transcription.value.running) return
        _transcription.value = TranscriptionState(running = true, phase = "Preparing offline transcription", progress = 0)
        viewModelScope.launch {
            try {
                val words = TranscriptionEngine(getApplication()).transcribe(File(base.videoPath), language) { state ->
                    _transcription.value = TranscriptionState(true, state.phase, state.percent)
                }
                commit(base.copy(words = words, language = language, addCaptions = true), Commands::text)
                _transcription.value = TranscriptionState(false, "Transcription complete", 100)
            } catch (error: Throwable) {
                _transcription.value = TranscriptionState(false, "Transcription failed", 0, error.message ?: "Unknown transcription error")
            }
        }
    }

    fun updateWordText(wordId: String, text: String) {
        val base = _project.value ?: return
        val changed = base.copy(words = base.words.map { if (it.id == wordId) it.copy(text = text) else it })
        commit(base, changed, Commands::text)
    }

    fun updateWordTiming(wordId: String, startMs: Long, endMs: Long) {
        val base = _project.value ?: return
        val changed = base.copy(words = base.words.map {
            if (it.id == wordId) it.copy(startMs = startMs.coerceAtLeast(0L), endMs = maxOf(startMs + 40L, endMs)) else it
        }.sortedBy { it.startMs })
        commit(base, changed, Commands::timing)
    }

    fun setStyle(style: CaptionStyle) {
        val base = _project.value ?: return
        commit(base, base.copy(style = style), Commands::style)
    }

    fun setTransform(transform: VideoTransform) {
        val base = _project.value ?: return
        commit(base, base.copy(transform = transform), Commands::transform)
    }

    fun splitAtPlayhead(playheadMs: Long) {
        val base = _project.value ?: return
        val split = base.transform.splitAt(playheadMs, base.durationMs)
        if (split != base.transform) commit(base, base.copy(transform = split), Commands::splitClip)
    }

    fun deleteClip(index: Int) {
        val base = _project.value ?: return
        val changed = base.transform.deleteSegment(index, base.durationMs)
        if (changed != base.transform) commit(base, base.copy(transform = changed), Commands::deleteClip)
    }

    fun addManualCaption(text: String, startMs: Long, endMs: Long) {
        val base = _project.value ?: return
        val manual = com.saad.capvid.caption.WordGrouping.wordsFromText(text, startMs, endMs)
        if (manual.isEmpty()) return
        commit(base, base.copy(words = (base.words + manual).sortedBy { it.startMs }), Commands::addCaption)
    }

    fun deleteWord(wordId: String) {
        val base = _project.value ?: return
        commit(base, base.copy(words = base.words.filterNot { it.id == wordId }), Commands::deleteCaption)
    }

    fun setWordHighlighted(wordId: String, highlighted: Boolean) {
        val base = _project.value ?: return
        val changed = base.copy(words = base.words.map { if (it.id == wordId) it.copy(highlighted = highlighted) else it })
        commit(base, changed, Commands::highlight)
    }

    fun undo() {
        history?.undo()?.let { _project.value = it }
    }

    fun redo() {
        history?.redo()?.let { _project.value = it }
    }

    fun canUndo(): Boolean = history?.canUndo == true
    fun canRedo(): Boolean = history?.canRedo == true

    fun exportToGallery() {
        val project = _project.value ?: return
        if (_export.value.running) return
        _export.value = ExportState(running = true, progress = 0)
        viewModelScope.launch {
            val temp = File(getApplication<Application>().cacheDir, "export-${project.id}.mp4")
            try {
                VideoExporter(getApplication()).export(project, temp) { progress ->
                    _export.value = ExportState(true, progress)
                }
                val uri = GallerySaver.saveVideo(getApplication(), temp)
                _export.value = ExportState(false, 100, uri)
            } catch (error: Throwable) {
                _export.value = ExportState(false, 0, error = error.message ?: "Export failed")
            } finally {
                temp.delete()
            }
        }
    }

    private fun commit(before: Project, after: Project, factory: (Project, Project) -> ProjectCommand) {
        val manager = history ?: return
        _project.value = manager.apply(factory(before, after))
    }

    private fun commit(after: Project, factory: (Project, Project) -> ProjectCommand) {
        val before = _project.value ?: return
        commit(before, after, factory)
    }
}
