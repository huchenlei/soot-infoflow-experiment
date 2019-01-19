package soot.jimple.infoflow.spring;

import soot.jimple.infoflow.InfoflowConfiguration;

/**
 * Created by Charlie on 18. 01 2019
 */
public class SpringAppInfoflowConfiguration extends InfoflowConfiguration {
    /**
     * Enumeration containing the different ways in which Soot can be used
     *
     * @author Steven Arzt
     *
     */
    public enum SootIntegrationMode {
        /**
         * With this option, FlowDroid initializes and configures its own Soot instance.
         * This option is the default and the best choice in most cases.
         */
        CreateNewInstace,

        /**
         * With this option, FlowDroid uses the existing Soot instance, but generates
         * its own callgraph. Note that it is the responsibility of the caller to make
         * sure that pre-existing Soot instances are configured correctly for the use
         * with FlowDroid.
         */
        UseExistingInstance,

        UseExistingCallGraph;

        /**
         * Gets whether this integration mode requires FlowDroid to build its own
         * callgraph
         *
         * @return True if FlowDroid must create its own callgraph, otherwise false
         */
        boolean needsToBuildCallgraph() {
            return this == SootIntegrationMode.CreateNewInstace || this == SootIntegrationMode.UseExistingInstance;
        }
    }

    private SootIntegrationMode sootIntegrationMode;

    public SootIntegrationMode getSootIntegrationMode() {
        return sootIntegrationMode;
    }

    public void setSootIntegrationMode(SootIntegrationMode sootIntegrationMode) {
        this.sootIntegrationMode = sootIntegrationMode;
    }
}
