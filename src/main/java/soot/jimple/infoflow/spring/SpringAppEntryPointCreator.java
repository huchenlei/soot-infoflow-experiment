package soot.jimple.infoflow.spring;

import soot.SootField;
import soot.SootMethod;
import soot.jimple.infoflow.entryPointCreators.BaseEntryPointCreator;

import java.util.Collection;

/**
 * class which creates a dummy main method with the entry points according to the Spring app lifecycles
 *
 * Created by Charlie on 10. 01 2019
 */
public class SpringAppEntryPointCreator extends BaseEntryPointCreator {

    @Override
    protected SootMethod createDummyMainInternal() {
        return null;
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
