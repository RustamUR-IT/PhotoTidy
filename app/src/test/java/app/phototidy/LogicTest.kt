package app.phototidy

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Runs the logic checks (photo junk, file sorting, app importance, home folders, widgets)
 * on every GitHub build. If any check fails, the build fails and no broken APK is published.
 */
class LogicTest {
    @Test fun photos() { app.phototidy.checks.core.main(); assertEquals(0, app.phototidy.checks.core.failures) }
    @Test fun files() { app.phototidy.checks.file.main(); assertEquals(0, app.phototidy.checks.file.fails) }
    @Test fun apps() { app.phototidy.checks.apps.main(); assertEquals(0, app.phototidy.checks.apps.fails) }
    @Test fun homeFolders() { app.phototidy.checks.home.main(); assertEquals(0, app.phototidy.checks.home.fails) }
    @Test fun widgets() { app.phototidy.checks.widget.main(); assertEquals(0, app.phototidy.checks.widget.fails) }
}
