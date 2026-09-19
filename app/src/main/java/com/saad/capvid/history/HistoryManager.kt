package com.saad.capvid.history

import com.saad.capvid.model.CaptionStyle
import com.saad.capvid.model.CaptionWord
import com.saad.capvid.model.Project
import com.saad.capvid.model.VideoTransform

interface ProjectCommand {
    val description: String
    fun execute(project: Project): Project
    fun undo(project: Project): Project
}

/** A reversible snapshot command is used at the ViewModel boundary. Each UI
 * gesture still enters the command stack, while the model remains immutable. */
open class SnapshotCommand(
    open override val description: String,
    private val before: Project,
    private val after: Project
) : ProjectCommand {
    override fun execute(project: Project): Project = after
    override fun undo(project: Project): Project = before
}

class UpdateWordTextCommand(
    before: Project,
    after: Project,
    override val description: String = "Edit caption text"
) : SnapshotCommand(description, before, after)

class UpdateWordTimingCommand(
    before: Project,
    after: Project,
    override val description: String = "Adjust caption timing"
) : SnapshotCommand(description, before, after)

class ApplyStyleCommand(
    before: Project,
    after: Project,
    override val description: String = "Apply caption style"
) : SnapshotCommand(description, before, after)

class TransformCommand(
    before: Project,
    after: Project,
    override val description: String = "Edit trim or scale"
) : SnapshotCommand(description, before, after)

class SplitClipCommand(
    before: Project,
    after: Project,
    override val description: String = "Split video clip"
) : SnapshotCommand(description, before, after)

class DeleteClipCommand(
    before: Project,
    after: Project,
    override val description: String = "Delete video clip"
) : SnapshotCommand(description, before, after)

class AddCaptionCommand(
    before: Project,
    after: Project,
    override val description: String = "Add caption"
) : SnapshotCommand(description, before, after)

class DeleteCaptionCommand(
    before: Project,
    after: Project,
    override val description: String = "Delete caption"
) : SnapshotCommand(description, before, after)

class HighlightCommand(
    before: Project,
    after: Project,
    override val description: String = "Highlight caption word"
) : SnapshotCommand(description, before, after)

class HistoryManager(initial: Project) {
    private var currentProject: Project = initial
    private val undoStack = ArrayDeque<ProjectCommand>()
    private val redoStack = ArrayDeque<ProjectCommand>()

    val current: Project get() = currentProject
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun apply(command: ProjectCommand): Project {
        currentProject = command.execute(currentProject)
        undoStack.addLast(command)
        redoStack.clear()
        return currentProject
    }

    fun applySnapshot(after: Project, description: String): Project = apply(
        SnapshotCommand(description, currentProject, after)
    )

    fun undo(): Project? {
        if (undoStack.isEmpty()) return null
        val command = undoStack.removeLast()
        currentProject = command.undo(currentProject)
        redoStack.addLast(command)
        return currentProject
    }

    fun redo(): Project? {
        if (redoStack.isEmpty()) return null
        val command = redoStack.removeLast()
        currentProject = command.execute(currentProject)
        undoStack.addLast(command)
        return currentProject
    }

    fun clear(project: Project = currentProject) {
        currentProject = project
        undoStack.clear()
        redoStack.clear()
    }
}

/** Small factories keep editor code readable and make the command semantics
 * explicit at call sites. */
object Commands {
    fun text(before: Project, after: Project) = UpdateWordTextCommand(before, after)
    fun timing(before: Project, after: Project) = UpdateWordTimingCommand(before, after)
    fun style(before: Project, after: Project) = ApplyStyleCommand(before, after)
    fun transform(before: Project, after: Project) = TransformCommand(before, after)
    fun splitClip(before: Project, after: Project) = SplitClipCommand(before, after)
    fun deleteClip(before: Project, after: Project) = DeleteClipCommand(before, after)
    fun addCaption(before: Project, after: Project) = AddCaptionCommand(before, after)
    fun deleteCaption(before: Project, after: Project) = DeleteCaptionCommand(before, after)
    fun highlight(before: Project, after: Project) = HighlightCommand(before, after)
}
