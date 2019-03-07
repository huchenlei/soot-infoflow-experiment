package soot.jimple.infoflow.spring;

import javafx.util.Pair;
import soot.*;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.*;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.infoflow.entryPointCreators.BaseEntryPointCreator;
import soot.jimple.infoflow.util.SootMethodRepresentationParser;
import soot.jimple.toolkits.scalar.NopEliminator;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * This entry point creator will accept a collection of method names that are
 * marked with @RequestMapping or similar annotation, and randomly call these
 * methods in different sequences, with tainted params passed in.
 * <p>
 * The main difference between this class and the default
 * Created by Charlie on 04. 03 2019
 */
public class SpringAppEntryPointCreator extends BaseEntryPointCreator {
    /**
     * Config object to control behaviour of the entry point creator
     */
    public static class AnalysisConfig {
        /**
         * Full soot-style method signature specifying a method that take
         * no input param and return a tainted String
         * <p>
         * the default value is reading from standard input
         */
        public String defaultTaintSource = "<java.util.Scanner: java.lang.String nextLine()>";
        public String defaultSinkPoint = "<java.io.PrintStream: void println(java.lang.String)>";

        public AnalysisConfig() {
        }
    }

    private List<String> methodsToCall;

    private AnalysisConfig config = new AnalysisConfig();

    public SpringAppEntryPointCreator(List<String> methodsToCall) {
        super();
        this.methodsToCall = methodsToCall;
    }

    public SpringAppEntryPointCreator(List<String> methodsToCall, AnalysisConfig config) {
        this(methodsToCall);
        this.config = config;
    }

    private Pair<SootClass, SootMethod> parseSootMethodString(String methodString) {
        SootMethodAndClass sootMethodAndClass = SootMethodRepresentationParser.v().parseSootMethodString(methodString);

        SootClass sootClass;
        try {
            sootClass = Scene.v().getSootClass(sootMethodAndClass.getClassName());
        } catch (RuntimeException re) {
            // class not loaded
            sootClass = Scene.v().loadClassAndSupport(sootMethodAndClass.getClassName());
        }

        SootMethod sootMethod = findMethod(sootClass, sootMethodAndClass.getSubSignature());
        return new Pair<>(
                sootClass,
                sootMethod
        );
    }

    /**
     * the main method created will looks like following
     * <p>
     * Scanner scanner = null;
     * String defaultTaintSource = scanner.nextLine();
     * <p>
     * // methods invocations
     * // String params will be passed in with the tainted value
     * // other params will be automatically generated by the LocalGenerator
     * <p>
     * int counter;
     * String ret;
     * while (true) {
     *   if (counter == 0) {
     *     ret = new TargetObject1().method1(defaultTaintSource, new ParamClass(), null, ...);
     *     // sink accepts at least one string param
     *     sink(ret);
     *   }
     *   if (counter == 1)
     * ... method calls to other target methods
     * }
     *
     * @return main method(SootMethod instance)
     */
    @Override
    protected SootMethod createDummyMainInternal() {
        Map<String, Set<String>> classMap =
                SootMethodRepresentationParser.v().parseClassNames(methodsToCall, false);

        Body body = mainMethod.getActiveBody();
        LocalGenerator generator = new LocalGenerator(body);

        Pair<SootClass, SootMethod> sourcePair = parseSootMethodString(config.defaultTaintSource);
        SootClass sourceClass = sourcePair.getKey();
        SootMethod sourceMethod = sourcePair.getValue();

        Pair<SootClass, SootMethod> sinkPair = parseSootMethodString(config.defaultSinkPoint);
        SootClass sinkClass = sinkPair.getKey();
        SootMethod sinkMethod = sinkPair.getValue();

        // String defaultSource;
        Local defaultTaintSource = generator.generateLocal(RefType.v("java.lang.String"));

        if (!sourceMethod.getReturnType().equals(RefType.v("java.lang.String"))) {
            final String errMsg = "default taint source method does not have return type of java.lang.String. " +
                    "instead it has type of " + sourceMethod.getReturnType() + " (" + config.defaultTaintSource;
            logger.error(errMsg);
            throw new RuntimeException(errMsg);
        }

        // Scanner rx = null;
        Local scannerLocal = generator.generateLocal(RefType.v(sourceClass.getName()));
        body.getUnits().add(Jimple.v().newAssignStmt(
                scannerLocal,
                NullConstant.v()
        ));

        // String defaultSource = rx.nextLine();
        InvokeExpr invokeExpr = buildInvokeExpr(sourceMethod, scannerLocal, generator);
        Stmt stmt = Jimple.v().newAssignStmt(defaultTaintSource, invokeExpr);
        body.getUnits().add(stmt);

        HashMap<String, Local> localVarsForClasses = new HashMap<>();

        // create instance of each target class
        // so that we could invoke target methods on them
        for (String className : classMap.keySet()) {
            SootClass createdClass = Scene.v().forceResolve(className, SootClass.BODIES);
            createdClass.setApplicationClass();

            Local localVal = generateClassConstructor(createdClass, body);
            if (localVal == null) {
                logger.warn("Cannot generate constructor for class: {}", createdClass);
                continue;
            }
            localVarsForClasses.put(className, localVal);
        }

        // add entry point calls
        // the following are same as DefaultEntryPointCreator
        // where methods are simulated to be called in a random order
        int conditionCounter = 0;
        final Jimple jimple = Jimple.v();
        NopStmt startStmt = jimple.newNopStmt();
        NopStmt endStmt = jimple.newNopStmt();
        Value intCounter = generator.generateLocal(IntType.v());
        body.getUnits().add(startStmt);
        for (Map.Entry<String, Set<String>> entry : classMap.entrySet()) {
            Local classLocal = localVarsForClasses.get(entry.getKey());
            for (String method : entry.getValue()) {

                Pair<SootClass, SootMethod> methodPair = parseSootMethodString(method);
                SootMethod currentMethod = methodPair.getValue();

                if (currentMethod == null) {
                    logger.warn("Entry point not found: {}", method);
                    continue;
                }

                EqExpr cond = jimple.newEqExpr(intCounter, IntConstant.v(conditionCounter));
                conditionCounter++;
                NopStmt thenStmt = jimple.newNopStmt();
                IfStmt ifStmt = jimple.newIfStmt(cond, thenStmt);
                body.getUnits().add(ifStmt);
                buildMethodCall(currentMethod, body, classLocal, generator);
                body.getUnits().add(thenStmt);
            }
        }
        body.getUnits().add(endStmt);
        GotoStmt gotoStart = jimple.newGotoStmt(startStmt);
        body.getUnits().add(gotoStart);

        body.getUnits().add(Jimple.v().newReturnVoidStmt());
        NopEliminator.v().transform(body);
        eliminateSelfLoops(body);
        return mainMethod;
    }

    protected InvokeExpr buildInvokeExpr(SootMethod methodToCall, Local classLocal, LocalGenerator gen) {
        return buildInvokeExpr(methodToCall, classLocal, gen, Collections.emptySet());
    }

    /**
     * We have buildMethodCall in BaseEntryPointCreator which returns a statement,
     * however, in our scenario, we would specifically assign the return value of the
     * method to a local var
     * <p>
     * This method returns an InvokeExpr. The caller freely operate on its return value
     *
     * @param methodToCall SootMethod to call
     * @param classLocal local variable on which the method is invoked on (null for static method)
     * @param gen Local variable generator
     * @param parentClasses // N/A inherent from parent class
     * @return InvokeExpr
     */
    protected InvokeExpr buildInvokeExpr(
            SootMethod methodToCall, Local classLocal, LocalGenerator gen, Set<SootClass> parentClasses
    ) {
        final InvokeExpr invokeExpr;
        List<Value> args = new LinkedList<>();

        if (methodToCall.getParameterCount() > 0) {
            for (Type tp : methodToCall.getParameterTypes()) {
                Set<SootClass> constructionStack = new HashSet<>();
                Field field;
                // Hack
                // Use reflection to bypass private field access issue
                try {
                    field = super.getClass().getDeclaredField("allowSelfReferences");
                    assert field != null;
                    field.setAccessible(true);
                    assert field.isAccessible();
                    if (!(Boolean) field.get(this))
                        constructionStack.add(methodToCall.getDeclaringClass());

                    Method method = super.getClass().getDeclaredMethod("getValueForType", Body.class, LocalGenerator.class, Type.class, Set.class, Set.class);
                    assert method != null;
                    method.setAccessible(true);
                    args.add((Value) method.invoke(this, body, gen, tp, constructionStack, parentClasses));

                } catch (IllegalAccessException
                        | NoSuchFieldException
                        | NoSuchMethodException
                        | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }

            if (methodToCall.isStatic())
                invokeExpr = Jimple.v().newStaticInvokeExpr(methodToCall.makeRef(), args);
            else {
                assert classLocal != null : "Class local method was null for non-static method call";
                if (methodToCall.isConstructor())
                    invokeExpr = Jimple.v().newSpecialInvokeExpr(classLocal, methodToCall.makeRef(), args);
                else
                    invokeExpr = Jimple.v().newVirtualInvokeExpr(classLocal, methodToCall.makeRef(), args);
            }
        } else {
            if (methodToCall.isStatic()) {
                invokeExpr = Jimple.v().newStaticInvokeExpr(methodToCall.makeRef());
            } else {
                assert classLocal != null : "Class local method was null for non-static method call";
                if (methodToCall.isConstructor())
                    invokeExpr = Jimple.v().newSpecialInvokeExpr(classLocal, methodToCall.makeRef());
                else
                    invokeExpr = Jimple.v().newVirtualInvokeExpr(classLocal, methodToCall.makeRef());
            }
        }


        return invokeExpr;
    }

    @Override
    public Collection<String> getRequiredClasses() {
        return null;
    }

    @Override
    public Collection<SootMethod> getAdditionalMethods() {
        return null;
    }

    @Override
    public Collection<SootField> getAdditionalFields() {
        return null;
    }


}