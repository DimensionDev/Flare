package dev.dimension.flareui.buildlogic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AndroidVectorConversionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `converts current color and transformed primitives`() {
        val svg =
            temporaryFolder.newFile("transformed.svg").apply {
                writeText(
                    """
                    <svg xmlns="http://www.w3.org/2000/svg"
                        width="24" height="24" viewBox="0 0 24 24">
                      <g transform="translate(2 3)">
                        <rect width="10" height="8" fill="currentColor"/>
                      </g>
                    </svg>
                    """.trimIndent(),
                )
            }

        val vector = convertSvgToAndroidVector(svg)

        assertTrue(vector.contains("""android:viewportWidth="24""""))
        assertTrue(vector.contains("""android:viewportHeight="24""""))
        assertTrue(vector.contains("""android:fillColor="#000000""""))
        assertTrue(vector.contains("android:pathData="))
        assertFalse(vector.contains("currentColor"))
    }

    @Test
    fun `converts linear gradients`() {
        val svg =
            temporaryFolder.newFile("gradient.svg").apply {
                writeText(
                    """
                    <svg xmlns="http://www.w3.org/2000/svg"
                        width="24" height="24" viewBox="0 0 24 24">
                      <defs>
                        <linearGradient id="gradient" x1="0" y1="0" x2="24" y2="24"
                            gradientUnits="userSpaceOnUse">
                          <stop stop-color="#02EBD2"/>
                          <stop offset="1" stop-color="#00BBA9"/>
                        </linearGradient>
                      </defs>
                      <path fill="url(#gradient)" d="M0 0H24V24H0Z"/>
                    </svg>
                    """.trimIndent(),
                )
            }

        val vector = convertSvgToAndroidVector(svg)

        assertTrue(vector, vector.contains("<gradient"))
        assertTrue(vector, vector.contains("""android:color="#FF02EBD2""""))
        assertTrue(vector, vector.contains("""android:color="#FF00BBA9""""))
    }

    @Test
    fun `reports unsupported SVG content`() {
        val svg =
            temporaryFolder.newFile("text.svg").apply {
                writeText(
                    """
                    <svg xmlns="http://www.w3.org/2000/svg"
                        width="24" height="24" viewBox="0 0 24 24">
                      <text x="0" y="12">Flare</text>
                    </svg>
                    """.trimIndent(),
                )
            }

        val failure =
            assertThrows(IllegalStateException::class.java) {
                convertSvgToAndroidVector(svg)
            }

        assertTrue(failure.message.orEmpty().contains("<text> is not supported"))
    }
}
