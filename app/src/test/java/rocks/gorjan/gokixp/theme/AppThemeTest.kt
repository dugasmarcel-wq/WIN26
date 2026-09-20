package rocks.gorjan.gokixp.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeTest {

    @Test
    fun `retained internal renderers round-trip through stored names`() {
        for (theme in AppTheme.all()) {
            assertEquals(theme, AppTheme.fromString(theme.toString()))
        }
    }

    @Test
    fun `unknown or retired theme names fall back to XP`() {
        assertEquals(AppTheme.WindowsXP, AppTheme.fromString(null))
        assertEquals(AppTheme.WindowsXP, AppTheme.fromString(""))
        assertEquals(AppTheme.WindowsXP, AppTheme.fromString("retired-theme"))
    }

    @Test
    fun `only XP Classic renderer and Vista remain internally selectable`() {
        assertEquals(
            listOf(AppTheme.WindowsXP, AppTheme.WindowsClassic, AppTheme.WindowsVista),
            AppTheme.all()
        )
    }
}
