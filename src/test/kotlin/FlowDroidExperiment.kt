import ca.utoronto.ece496.spring.SpringAppEntryPointCreator
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import soot.jimple.infoflow.Infoflow
import soot.jimple.infoflow.InfoflowConfiguration
import soot.jimple.infoflow.entryPointCreators.DefaultEntryPointCreator

/**
 *
 * Created by Charlie on 09. 03 2019
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlowDroidExperiment {
    private val projectRoot = "spring_sample_apps/build/libs/exp-spring-boot-0.1.0/BOOT-INF"
    val appPath = "$projectRoot/classes"
    val libPath = "$projectRoot/lib"
    val entryPoints = listOf(
            "<ca.utoronto.ece496.samples.HelloWorldController: java.lang.String userPage(java.lang.String)>",
            "<ca.utoronto.ece496.samples.HelloWorldController: java.lang.String hello()>"
    )

    @Test
    fun testFlowDroidRun() {
        val infoflow = Infoflow()

        val sinks = listOf(
                SpringAppEntryPointCreator.getDefaultSinkSignature()
//                ,
//                "<ca.utoronto.ece496.samples.Mock: void sink(java.lang.String)>"
        )
        val sources = listOf(
                SpringAppEntryPointCreator.getDefaultSourceSignature()
//                ,
//                "<ca.utoronto.ece496.samples.Mock: java.lang.String source()>"
        )

        val entryPointCreator = DefaultEntryPointCreator(entryPoints)
        val se = SpringAppEntryPointCreator(
                entryPoints,
                SpringAppEntryPointCreator.AnalysisConfig(
                        "<ca.utoronto.ece496.samples.Mock: java.lang.String source()>",
                        "<ca.utoronto.ece496.samples.Mock: void sink(java.lang.String)>"
                )
        )

        val config = infoflow.config
        config.flowSensitiveAliasing = true
        val pathConfiguration = config.pathConfiguration
        pathConfiguration.pathReconstructionMode = InfoflowConfiguration.PathReconstructionMode.Precise

        infoflow.computeInfoflow(
                libPath,
                appPath,
                se,
                sources,
                sinks
        )

        infoflow.results.printResults()
    }
}