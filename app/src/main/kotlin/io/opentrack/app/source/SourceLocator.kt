package io.opentrack.app.source

/**
 * Test seam (not a service locator pattern we endorse elsewhere): production builds always use
 * the real transport; unit tests install a fake so no test ever touches the network (3.5/10.1).
 */
object SourceLocator {
    @Volatile
    var erailSource: ErailTrainSource? = null
}
