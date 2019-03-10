package ca.utoronto.ece496.spring;

import ca.utoronto.ece496.utils.GeneralUtil;
import ca.utoronto.ece496.utils.SootUtil;
import javafx.util.Pair;
import soot.*;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.*;
import soot.jimple.infoflow.data.SootMethodAndClass;
import soot.jimple.infoflow.entryPointCreators.BaseEntryPointCreator;
import soot.jimple.infoflow.util.SootMethodRepresentationParser;
import soot.jimple.toolkits.scalar.NopEliminator;

import java.util.*;

/**
 * This entry point creator will accept a collection of method names that are
 * marked with @RequestMapping or similar annotation, and randomly call these
 * methods in different sequences, with tainted params passed in.
 * <p>
 * The main difference between this class and {@link soot.jimple.infoflow.entryPointCreators.DefaultEntryPointCreator}
 * is that this class create an default taint source and pass it method calls
 * and it also collects return value from method calls and pass them to a default
 * sink point
 * <p>
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
         * the default value is toString() method of object
         */
        public String defaultTaintSource = "<java.lang.Object: java.lang.String toString()>";
        /**
         * Full soot-style method signature specifying a method that a String
         * as param and return void
         */
        public String defaultSinkPoint = "<java.io.PrintStream: void println(java.lang.String)>";

        /**
         * Restricted only for testing purpose
         * defaultTaintSource and defaultSinkPoint are supposed to be overridden by external info
         */
        public AnalysisConfig() {
        }

        public AnalysisConfig(String defaultTaintSource, String defaultSinkPoint) {
            this.defaultTaintSource = defaultTaintSource;
            this.defaultSinkPoint = defaultSinkPoint;
        }
    }

    private List<String> methodsToCall;

    private AnalysisConfig config = new AnalysisConfig();

    /**
     * Following names are reserved and should not appear in the user's program
     * By starting those names with "_", they shall have min possibility to also
     * appear in input program
     */
    private static String dummyClassName = "_dummy";
    private static String dummySourceName = "_dummy_source";
    private static String dummySinkName = "_dummy_sink";

    private static String getDefaultSourceSignature() {
        return "<" + dummyClassName + ": " + "java.lang.String " + dummySourceName + "()>";
    }

    private static String getDefaultSinkSignature() {
        return "<" + dummyClassName + ": " + "void " + dummySinkName + "(java.lang.String)>";
    }

    public SpringAppEntryPointCreator(List<String> methodsToCall) {
        super();
        this.methodsToCall = methodsToCall;
    }

    private Pair<SootClass, SootMethod> sourcePair;
    private Pair<SootClass, SootMethod> sinkPair;

    public SpringAppEntryPointCreator(List<String> methodsToCall, AnalysisConfig config) {
        this(methodsToCall);
        this.config = config;
        sourcePair = parseSootMethodString(config.defaultTaintSource);
        sinkPair = parseSootMethodString(config.defaultSinkPoint);
    }

    private SootClass createDummySourceSink(String className, String sourceName, String sinkName) {
        RefType stringType = RefType.v("java.lang.String");

        return SootUtil.createClass(className, Arrays.asList(
                SootUtil.createEmptyMethod(new SootMethod(
                        sourceName, Collections.emptyList(), stringType,
                        Modifier.PUBLIC | Modifier.STATIC
                )),
                SootUtil.createEmptyMethod(new SootMethod(
                        sinkName, Arrays.asList(stringType), VoidType.v(),
                        Modifier.PUBLIC | Modifier.STATIC
                ))
        ));
    }

    private Pair<SootClass, SootMethod> parseSootMethodString(String methodString) {
        SootMethodAndClass sootMethodAndClass = SootMethodRepresentationParser.v().parseSootMethodString(methodString);
        String className = sootMethodAndClass.getClassName();

        SootClass sootClass;

        if (!Scene.v().containsClass(className)) {
            Scene.v().addBasicClass(className);
            Scene.v().loadNecessaryClasses();
            sootClass = Scene.v().loadClassAndSupport(className);
        } else {
            sootClass = Scene.v().getSootClass(className);
        }


        SootMethod sootMethod = findMethod(sootClass, sootMethodAndClass.getSubSignature());
        assert sootMethod != null;

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
     * if (counter == 0) { <br>
     * ret = new TargetObject1().method1(defaultTaintSource, new ParamClass(), null, ...);<br>
     * // sink accepts at least one string param <br>
     * sink(ret);<br>
     * }<br>
     * if (counter == 1) <br>
     * ... method calls to other target methods
     * }
     *
     * @return main method(SootMethod instance)
     */
    @Override
    protected SootMethod createDummyMainInternal() {
        SootClass dummyClass = createDummySourceSink(dummyClassName, dummySourceName, dummySinkName);
        SootMethod sourceMethod = dummyClass.getMethodByName(dummySourceName);
        SootMethod sinkMethod = dummyClass.getMethodByName(dummySinkName);

        Map<String, Set<String>> classMap =
                SootMethodRepresentationParser.v().parseClassNames(methodsToCall, false);

        Body body = mainMethod.getActiveBody();
        LocalGenerator generator = new LocalGenerator(body);


        // String defaultSource;
        Local defaultTaintSource = generator.generateLocal(RefType.v("java.lang.String"));

        if (!sourceMethod.getReturnType().equals(RefType.v("java.lang.String"))) {
            final String errMsg = "default taint source method does not have return type of java.lang.String. " +
                    "instead it has type of " + sourceMethod.getReturnType() + " (" + config.defaultTaintSource;
            logger.error(errMsg);
            throw new RuntimeException(errMsg);
        }

        // String defaultSource = dummyClass.dummySource();
        InvokeExpr invokeExpr = buildInvokeExpr(sourceMethod, null, generator);
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

    private InvokeExpr buildInvokeExpr(SootMethod methodToCall, Local classLocal, LocalGenerator gen) {
        return buildInvokeExpr(methodToCall, classLocal, gen, Collections.emptySet());
    }

    /**
     * We have buildMethodCall in BaseEntryPointCreator which returns a statement,
     * however, in our scenario, we would specifically assign the return value of the
     * method to a local var
     * <p>
     * This method returns an InvokeExpr. The caller freely operate on its return value
     *
     * @param methodToCall  SootMethod to call
     * @param classLocal    local variable on which the method is invoked on (null for static method)
     * @param gen           Local variable generator
     * @param parentClasses // N/A inherent from parent class
     * @return InvokeExpr
     */
    private InvokeExpr buildInvokeExpr(
            SootMethod methodToCall, Local classLocal, LocalGenerator gen, Set<SootClass> parentClasses
    ) {
        final InvokeExpr invokeExpr;
        List<Value> args = new LinkedList<>();

        if (methodToCall.getParameterCount() > 0) {
            for (Type tp : methodToCall.getParameterTypes()) {
                Set<SootClass> constructionStack = new HashSet<>();

                if (!GeneralUtil.<BaseEntryPointCreator, Boolean>accessField(
                        super.getClass(), "allowSelfReferences", this
                )) {
                    constructionStack.add(methodToCall.getDeclaringClass());
                }

                args.add(GeneralUtil.invokeMethod(
                        super.getClass(), "getValueForType",
                        Arrays.asList(Body.class, LocalGenerator.class, Type.class, Set.class, Set.class),
                        Arrays.asList(body, gen, tp, constructionStack, parentClasses),
                        this));
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
        return SootMethodRepresentationParser.v().parseClassNames(methodsToCall, false).keySet();
    }

    @Override
    public Collection<SootMethod> getAdditionalMethods() {
        return Collections.emptyList();
    }

    @Override
    public Collection<SootField> getAdditionalFields() {
        return Collections.emptyList();
    }
}