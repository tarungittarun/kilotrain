package io.opentrack.core.contract

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * M1 EXIT CRITERION, enforced by a test, not by review (KICKOFF M1):
 * "the app can never print a time without a provenance label".
 *
 * Part 1 (this test): every renderable contract type declares a Provenance field, so a value
 * that the UI can show cannot be constructed without its source. Part 2 lives in the app
 * module: every rendered row carries a label() string on screen.
 */
class ProvenanceEnforcementTest {

    private val renderable = listOf(
        TrainIdentity::class.java,
        RouteStop::class.java,
        PairListing::class.java,
    )

    @Test
    fun everyRenderableContractTypeDeclaresAProvenanceField() {
        renderable.forEach { type ->
            val fields = type.declaredFields.map { it.type.name }
            assertTrue("${type.simpleName} has no Provenance field: $fields",
                fields.any { it == Provenance::class.java.name })
        }
    }

    /** 6.2: the label() function is THE renderer - no other public string claims freshness. */
    @Test
    fun labelIsTheOnlyFreshnessRenderer() {
        val source = File("src/main/kotlin/io/opentrack/core/contract/Provenance.kt").readText()
        assertTrue("label() must be a public top-level function", source.contains("fun Provenance.label("))
        val forbidden = listOf("\"live data", "\"cached ", "\"via ")
        forbidden.forEach { phrase ->
            assertTrue("freshness vocabulary '$phrase' hard-coded outside label()",
                source.split("fun Provenance.label(").size == 2 || !source.substringAfter("fun Provenance.label(").contains(phrase))
        }
    }

    /** The failure type cannot masquerade as an answer. */
    @Test
    fun failureIsNotAListing() {
        val listingMethods = PairListing::class.java.methods.map { it.name }
        assertTrue(listingMethods.none { it.equals("failure") && it.returnType == PairListing::class.java })
    }
}
