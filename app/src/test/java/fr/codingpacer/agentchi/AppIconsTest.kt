package fr.codingpacer.agentchi

import fr.codingpacer.agentchi.pet.MascotSprite
import fr.codingpacer.agentchi.widget.AppIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Icônes d'appli assorties à la mascotte : une par espèce et palette, générées depuis [MascotSprite].
 * Pour les régénérer : UPDATE_APP_ICONS=1 ./gradlew :app:testDebugUnitTest --tests '*AppIconsTest*'
 */
class AppIconsTest {
    private val res = File("src/main/res")
    private val manifest = File("src/main/AndroidManifest.xml")
    private val update = System.getenv("UPDATE_APP_ICONS") != null

    @Test
    fun `icones a jour`() {
        AppIcons.mascots.forEach { (species, palette) ->
            val name = AppIcons.resourceName(species, palette)
            check(File(res, "drawable/${name}_foreground.xml"), foreground(species, palette))
            check(File(res, "mipmap-anydpi/$name.xml"), adaptive(name))
        }
        val text = manifest.readText()
        val start = text.indexOf(BEGIN)
        val end = text.indexOf(END)
        assertTrue("marqueurs $BEGIN / $END absents du manifeste", start >= 0 && end > start)
        val updated = text.substring(0, start) + BEGIN + "\n" + aliases() + "        " + text.substring(end)
        if (update) manifest.writeText(updated)
        assertEquals("Manifeste pas à jour : $HOW", updated, manifest.readText())
    }

    @Test
    fun `une icone par espece et palette`() {
        assertEquals(MascotSprite.Species.entries.size * MascotSprite.Palette.entries.size, AppIcons.mascots.size)
        assertEquals(AppIcons.mascots.size + 1, AppIcons.aliases.size)
        assertTrue(AppIcons.aliases.all { it.startsWith(".launcher.") })
    }

    private fun check(file: File, content: String) {
        if (update) file.apply { parentFile.mkdirs() }.writeText(content)
        assertEquals("${file.path} pas à jour : $HOW", content, file.takeIf { it.exists() }?.readText())
    }

    /** Premier plan vectoriel 108 x 108 : la mascotte, une case = 5 dp, centrée dans la zone sûre. */
    private fun foreground(species: MascotSprite.Species, palette: MascotSprite.Palette): String {
        val look = MascotSprite.Look(species, palette)
        val cell = 5f
        val x0 = (108 - MascotSprite.WIDTH * cell) / 2
        val y0 = 24f
        val paths = MascotSprite.pixels(look, MascotSprite.Face.OPEN).groupBy { it.argb }.map { (argb, pixels) ->
            val data = pixels.joinToString("") { p ->
                val x = x0 + p.x * cell
                val y = y0 + (p.y + p.dy) * cell
                val h = p.h * cell
                if (p.round) {
                    val r = cell * 0.46f
                    val cy = y + h / 2
                    "M${n(x + cell / 2 - r)},${n(cy)}a${n(r)},${n(r)} 0 1,0 ${n(2 * r)},0a${n(r)},${n(r)} 0 1,0 ${n(-2 * r)},0z"
                } else {
                    "M${n(x)},${n(y)}h${n(cell)}v${n(h)}h${n(-cell)}z"
                }
            }
            """    <path android:fillColor="#${"%06X".format(argb and 0xFFFFFF)}" android:pathData="$data" />"""
        }
        return """<?xml version="1.0" encoding="utf-8"?>
<!-- Généré par AppIconsTest depuis MascotSprite : ne pas modifier à la main. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
${paths.joinToString("\n")}
</vector>
"""
    }

    private fun adaptive(name: String) = """<?xml version="1.0" encoding="utf-8"?>
<!-- Généré par AppIconsTest : ne pas modifier à la main. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/brand_ink" />
    <foreground android:drawable="@drawable/${name}_foreground" />
</adaptive-icon>
"""

    /** Une entrée du lanceur par icône ; seule l'icône classique est active à l'installation. */
    private fun aliases(): String = AppIcons.aliases.joinToString("") { alias ->
        val icon = if (alias == AppIcons.CLASSIC) "ic_launcher" else AppIcons.resourceName(alias)
        """        <activity-alias
            android:name="$alias"
            android:enabled="${alias == AppIcons.CLASSIC}"
            android:exported="true"
            android:icon="@mipmap/$icon"
            android:label="@string/app_name"
            android:roundIcon="@mipmap/$icon"
            android:targetActivity=".ui.MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>
"""
    }

    private fun n(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.2f".format(java.util.Locale.ROOT, v).trimEnd('0')

    companion object {
        private const val BEGIN = "<!-- icônes:début (généré par AppIconsTest) -->"
        private const val END = "<!-- icônes:fin -->"
        private const val HOW = "UPDATE_APP_ICONS=1 ./gradlew :app:testDebugUnitTest --tests '*AppIconsTest*'"
    }
}
