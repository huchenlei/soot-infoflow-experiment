/**
 * This is a demo program to use FlowDroid Core as library
 *
 * Created by Charlie on 04. 10 2018
 */

import soot.jimple.infoflow.Infoflow
import soot.jimple.infoflow.InfoflowConfiguration

fun runAnalysis() {
    val infoflow = Infoflow()
//    Experiment with webgoat
//    Require specifically designed EntryPointCreator

//    infoflow.computeInfoflow(
//            "/Users/Charlie/Desktop/ClassNotes/ECE496/ECE496_Playground/webgoat/webgoat-server-8.0.0.M21.jar",
//            "",
//            SequentialEntryPointCreator(listOf("<org.owasp.webgoat.util.Exec: void main(java.lang.String[])>")),
//            listOf("<org.owasp.webgoat.session.WebSession: java.lang.Object get(java.lang.String)>"), // sources
//            listOf("<org.owasp.webgoat.session.WebSession: void setMessage(java.lang.String)>")
//    )

    val config = infoflow.config
    config.implicitFlowMode = InfoflowConfiguration.ImplicitFlowMode.NoImplicitFlows

//    Experiment with server/client app from ECE419
    infoflow.computeInfoflow(
            "/Users/Charlie/repos/ECE419/ecs.jar",
            "",
            "<app_kvECS.ECSClient: void main(java.lang.String[])>",
            listOf(
                    "<java.io.BufferedReader: java.lang.String readLine()>"
            ),
            listOf(
                    "<ecs.ECS: ecs.IECSNode addNode(java.lang.String,int)>",
                    "<ecs.ECS: boolean removeNodes(java.util.Collection)>",
                    "<app_kvECS.ECSClient: void handleCommand(java.lang.String)>",
                    "<app_kvECS.ECSClient: void printError(java.lang.String)>"
            )
    )

    val results = infoflow.results
    results.printResults()
    for (dataFlowResult in results.resultSet) {

    }
}

fun main(args: Array<String>) {

}