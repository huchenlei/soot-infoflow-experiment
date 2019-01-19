import org.junit.Assert
import org.junit.Test
import soot.*
import soot.jimple.JasminClass
import soot.jimple.Jimple
import soot.jimple.StringConstant
import soot.options.Options
import soot.util.JasminOutputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.lang.reflect.Modifier

/**
 *
 * Created by Charlie on 18. 01 2019
 */
class SootExperiment {
    /**
     * try to construct a hello-world class with Soot-jimple
     */
    @Test
    fun testCreateClass() {
        val v = Scene.v()

        v.loadClassAndSupport("java.lang.Object")
        v.loadClassAndSupport("java.lang.System")

        val clazz = SootClass("helloWorld", Modifier.PUBLIC)
        clazz.superclass = v.getSootClass("java.lang.Object")

        v.addClass(clazz)

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

        val printMethod = v.getMethod("<java.io.PrintStream: void println(java.lang.String)>")

        val sysout = v.getField("<java.lang.System: java.io.PrintStream out>")
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

        val outputJimple = true

        if (outputJimple) {
            val fileName = SourceLocator.v().getFileNameFor(clazz, Options.output_format_class)
            val streamOut = JasminOutputStream(FileOutputStream(fileName))
            val writerOut = PrintWriter(OutputStreamWriter(streamOut))

            val jasminClass = JasminClass(clazz)
            jasminClass.print(writerOut)
            writerOut.flush()
            streamOut.close()
        } else {
            val sClass = clazz
            val fileName = SourceLocator.v().getFileNameFor(sClass, Options.output_format_jimple)
            val streamOut = FileOutputStream(fileName)
            val writerOut = PrintWriter(OutputStreamWriter(streamOut))
            Printer.v().printTo(sClass, writerOut)
            writerOut.flush()
            streamOut.close()
        }
    }

    @Test
    fun testAddAttrToClass() {

    }
}