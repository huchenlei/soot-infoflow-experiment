import ca.utoronto.ece496.spring.SpringAppEntryPointCreator
import org.junit.Assert
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import soot.*
import soot.jimple.Jimple
import soot.jimple.StringConstant
import soot.jimple.infoflow.entryPointCreators.DefaultEntryPointCreator
import soot.options.Options
import soot.tagkit.GenericAttribute
import soot.util.JasminOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.lang.reflect.Modifier

/**
 *
 * Created by Charlie on 18. 01 2019
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SootExperiment {
    private val className = "helloworld"
    private val scene = Scene.v()

    /**
     * try to construct a hello-world class with Soot-jimple
     */
    @Test
    fun test01CreateClass() {
        scene.loadClassAndSupport("java.lang.Object")
        scene.loadClassAndSupport("java.lang.System")

        val clazz = SootClass(className, Modifier.PUBLIC)
        clazz.superclass = scene.getSootClass("java.lang.Object")

        scene.addClass(clazz)

        val main = SootMethod("main",
                listOf(ArrayType.v(RefType.v("java.lang.String"), 1)),
                VoidType.v(),
                Modifier.PUBLIC or Modifier.STATIC
        )

        clazz.addMethod(main)

        val body = Jimple.v().newBody(main)
        main.activeBody = body


        val arg = Jimple.v().newLocal("l0", ArrayType.v(RefType.v("java.lang.String"), 1))
        body.locals.add(arg)

        body.units.add(
                Jimple.v().newIdentityStmt(
                        arg, Jimple.v().newParameterRef(ArrayType.v(RefType.v("java.lang.String"), 1), 0)
                )
        )

        val printMethod = scene.getMethod("<java.io.PrintStream: void println(java.lang.String)>")

        val sysout = scene.getField("<java.lang.System: java.io.PrintStream out>")
        Assert.assertTrue(sysout.isStatic)

        val r1 = Jimple.v().newLocal("l1", RefType.v("java.io.PrintStream"))
        body.locals.add(r1)

        val fieldRef = Jimple.v().newStaticFieldRef(sysout.makeRef())
        body.units.add(Jimple.v().newAssignStmt(r1, fieldRef))

        body.units.add(Jimple.v().newInvokeStmt(
                Jimple.v().newVirtualInvokeExpr(
                        r1,
                        printMethod.makeRef(),
                        StringConstant.v("Hello World!")
                )
        ))

        body.units.add(Jimple.v().newReturnVoidStmt())

        clazz.outputToClassFile()
    }

    /**
     * Adding attr is useless in our scenario
     * Ignoring the later soot tut from github
     */
    @Test
    fun test02AddAttrToClass() {
        val sClass = scene.getSootClass(className)
        val method = sClass.getMethodByName("main")

        // create and add the class attribute, with data ``foo''
        val classAttr = GenericAttribute("ca.mcgill.sable.MyClassAttr", "foo".toByteArray())
        sClass.addTag(classAttr)

        // Create and add the method attribute with no data
        val mAttr = GenericAttribute(
                "ca.mcgill.sable.MyMethodAttr",
                "".toByteArray())
        method.addTag(mAttr)
    }

    /**
     * Try to load a jar to soot's scene
     *
     * Note: when spring app is packed as a jar file, the root dir within that jar
     * is not the application's classpath, instead, the application classpath is placed
     * at ./BOOT-INF/classes
     */
    @Test
    fun test03LoadJar() {
        val classPath = "spring_sample_apps/build/libs/exp-spring-boot-0.1.0/BOOT-INF/classes"
        val targetClassName = "ca.utoronto.ece496.samples.HelloWorldController"

        Options.v().set_no_bodies_for_excluded(true)
        Options.v().set_allow_phantom_refs(true)
        Options.v().set_src_prec(Options.src_prec_java)

        Options.v().set_soot_classpath(classPath)
        Scene.v().addBasicClass(targetClassName, SootClass.BODIES)
        Scene.v().loadNecessaryClasses()

        val c = Scene.v().forceResolve(targetClassName, SootClass.BODIES)

        Assert.assertNotNull(c)
        Assert.assertFalse(c.isPhantomClass)
        Assert.assertFalse(c.isPhantom)
    }


    /**
     * Test the entry point creator
     */
    @Test
    fun test04EntryPointCreator() {
        scene.loadClassAndSupport("java.lang.Object")
        scene.loadClassAndSupport("java.lang.System")
        // needs to initialize soot first

        val entryPoints = listOf(
                "<ca.utoronto.ece496.samples.HelloWorldController: java.lang.String userPage(java.lang.String)>",
                "<ca.utoronto.ece496.samples.HelloWorldController: java.lang.String hello()>"
        )

        val springEntryPointCreator = SpringAppEntryPointCreator(
                entryPoints,
                SpringAppEntryPointCreator.AnalysisConfig()
        )

        val defaultEntryPointCreator = DefaultEntryPointCreator(entryPoints)

        val creator = springEntryPointCreator

        val rootDir = "spring_sample_apps/build/libs/exp-spring-boot-0.1.0/BOOT-INF/"
        initializeSoot(
                rootDir + "classes",
                rootDir + "lib",
                creator.requiredClasses
        )

        val main = creator.createDummyMain()
        main.declaringClass.outputToClassFile()
    }
}

fun SootClass.outputToClassFile() {
    val sClass = this
    val fileName = SourceLocator.v().getFileNameFor(sClass, Options.output_format_class)
    val streamOut = JasminOutputStream(FileOutputStream(fileName))
    val writerOut = PrintWriter(OutputStreamWriter(streamOut))

    val jasminClass = soot.jimple.JasminClass(sClass)
    jasminClass.print(writerOut)

    writerOut.flush()
    streamOut.close()
}

fun SootClass.outputToJimple() {
    val sClass = this
    val fileName = SourceLocator.v().getFileNameFor(sClass, Options.output_format_jimple)
    val streamOut = FileOutputStream(fileName)
    val writerOut = PrintWriter(OutputStreamWriter(streamOut))
    Printer.v().printTo(sClass, writerOut)
    writerOut.flush()
    streamOut.close()
}

fun initializeSoot(appPath: String, libPath: String, classes: Collection<String>) {
    soot.G.reset()

    Options.v().set_no_bodies_for_excluded(true)
    Options.v().set_allow_phantom_refs(true)

    val classPath =
            when {
                appPath.isEmpty() -> libPath
                libPath.isEmpty() -> appPath
                else -> appPath + File.pathSeparator + libPath
            }

    Options.v().set_soot_classpath(classPath)

    for (clazz in classes) {
        Scene.v().addBasicClass(clazz, SootClass.BODIES)
    }

    Scene.v().loadNecessaryClasses()

    for (clazz in classes) {
        Assert.assertFalse(Scene.v().forceResolve(clazz, SootClass.BODIES).isPhantom)
    }
}
