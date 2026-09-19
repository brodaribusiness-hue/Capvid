package com.saad.capvid

import android.app.Dialog
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.saad.capvid.caption.StyleCatalog
import com.saad.capvid.editor.EditorScreen
import com.saad.capvid.editor.EditorViewModel
import com.saad.capvid.model.CaptionWord
import com.saad.capvid.model.Project
import com.saad.capvid.model.VideoTransform
import com.saad.capvid.scale.ScaleDialog
import com.saad.capvid.style.StylePanelDialog
import com.saad.capvid.trim.TrimDialog
import com.saad.capvid.export.AssSubtitleBuilder
import com.saad.capvid.export.VideoExporter
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.io.File

@RunWith(AndroidJUnit4::class)
class CapvidInstrumentedTest {
    @Test
    fun homeScreenLaunchesWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("CAPVID")).check(matches(isDisplayed()))
            onView(withText("＋  Create")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun editorScreenLaunchesWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val project = Project.create(File(activity.cacheDir, "missing.mp4").absolutePath).copy(
                    words = listOf(CaptionWord(text = "Preview", startMs = 0, endMs = 500))
                )
                val vm = ViewModelProvider(activity)[EditorViewModel::class.java]
                vm.open(project)
                val editor = EditorScreen(activity, vm, object : EditorScreen.Callbacks { override fun onExitEditor() = Unit })
                activity.setContentView(editor)
                editor.bind(activity)
            }
            onView(withText("Editor")).check(matches(isDisplayed()))
            onView(withText("Style")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun stylePanelOpensAndAppliesTemplate() {
        var applied = ""
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                StylePanelDialog(activity, StyleCatalog.defaultStyle()) { applied = it.id }.show()
            }
            onView(withText("Neon")).perform(click())
            onView(withText("Apply")).perform(click())
        }
        assertEquals("neon", applied)
    }

    @Test
    fun trimAndScaleDialogsRespondToControls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var trim: TrimDialog
            scenario.onActivity { activity ->
                trim = TrimDialog(activity, 10_000L, VideoTransform(), {})
                trim.show()
            }
            onView(withText("Trim video")).check(matches(isDisplayed()))
            scenario.onActivity { activity ->
                trim.dismiss()
                ScaleDialog(activity, VideoTransform(), {}).show()
            }
            onView(withText("Scale & crop")).check(matches(isDisplayed()))
            onView(withText("Apply scale")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun exportPipelineRunsOnBundledShortSample() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = File(context.cacheDir, "instrumented-sample.mp4")
        context.assets.open("sample.mp4").use { source -> input.outputStream().use { destination -> source.copyTo(destination) } }
        val output = File(context.cacheDir, "instrumented-export.mp4")
        val project = Project.create(input.absolutePath, durationMs = 2_000L).copy(
            style = StyleCatalog.find("flip"),
            words = listOf(CaptionWord(text = "Offline", startMs = 0, endMs = 800))
        )
        val ass = AssSubtitleBuilder.build(project, 640, 360)
        val command = VideoExporter.buildCommand(
            input, output, File("/data/local/tmp/captions.ass"), File("/data/local/tmp/fonts"), project,
            VideoExporter.Metadata(640, 360, 2_000L, 30f)
        )
        assertTrue(ass.contains("\\frx90"))
        assertTrue(command.contains("subtitles="))
        assertTrue(command.contains("-c:a copy"))
        runBlocking { VideoExporter(context).export(project, output) }
        assertTrue(output.isFile)
        assertTrue(output.length() > 0L)
        input.delete()
        output.delete()
    }
}
