package io.jenkins.plugins.swarmcloud.pipeline;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Launcher;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Slave;
import hudson.model.Slave.SlaveDescriptor;
import hudson.remoting.VirtualChannel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DumbSlave;
import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;
import jenkins.model.Jenkins;
import io.jenkins.plugins.swarmcloud.SwarmCloud;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the body of the scripted {@code swarmAgent { ... }} step executes ON the
 * provisioned Swarm agent — establishing a workspace / {@code FilePath} context — rather than
 * inheriting the enclosing context (issue #18).
 *
 * <p>Real Docker Swarm provisioning is unavailable in unit tests, so
 * {@link SwarmAgentStep#nodeProvisioner} is overridden to hand back an already-online stand-in
 * agent. This isolates the behaviour under test: whether the step pushes the node context into
 * the body.</p>
 */
@WithJenkins
class SwarmAgentStepBodyTest {

    @AfterEach
    void restoreProvisioner() {
        SwarmAgentStep.nodeProvisioner = SwarmAgentStep.DEFAULT_NODE_PROVISIONER;
        CaptureLauncherStep.capturedChannel = null;
    }

    @Test
    void bodyRunsOnProvisionedAgentWithWorkspaceContext(JenkinsRule j) throws Exception {
        // A real, online stand-in for the Swarm agent that would otherwise be provisioned.
        DumbSlave standIn = j.createOnlineSlave();
        hudson.FilePath standInRoot = standIn.getRootPath();
        final String expectedWorkspace = standInRoot != null
                ? standInRoot.child("workspace").getRemote() : "";
        SwarmAgentStep.nodeProvisioner =
                (cloud, template, agentName, step, logger) -> (Node) standIn;

        SwarmCloud cloud = new SwarmCloud("swarm");
        SwarmAgentTemplate template = new SwarmAgentTemplate("t");
        template.setLabelString("t");
        cloud.setTemplates(List.of(template));
        j.jenkins.clouds.add(cloud);

        // Scripted pipeline with NO enclosing node (equivalent to `agent none`): pwd() requires a
        // FilePath context, which only exists if swarmAgent established the agent's workspace.
        WorkflowJob job = j.createProject(WorkflowJob.class, "body-on-agent");
        job.setDefinition(new CpsFlowDefinition(
                "swarmAgent(cloud: 'swarm', template: 't') {\n"
                        + "    echo \"RAN_ON=${env.NODE_NAME}\"\n"
                        + "    echo \"WS=${pwd()}\"\n"
                        + "}\n",
                true));

        WorkflowRun run = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        String log = j.getLog(run);
        // pwd() resolves the FilePath context: it must be the stand-in agent's workspace, proving
        // the body ran on the provisioned node rather than the (absent) enclosing context. Without
        // the fix, pwd() fails with "Required context class hudson.FilePath is missing".
        assertTrue(log.contains("WS=" + expectedWorkspace),
                "body should run in the provisioned agent's workspace '" + expectedWorkspace
                        + "', log was:\n" + log);
    }

    /**
     * The body context must stay serializable: with the default MAX_SURVIVABILITY durability the CPS
     * engine persists the program whenever the pipeline suspends inside the body, serializing every
     * context variable. {@code hudson.Launcher$RemoteLauncher} is neither {@code Serializable} nor
     * covered by a {@code Pickle}, so pushing a live launcher into the context broke every build
     * containing a suspending step ({@code sh}, {@code bat}, {@code sleep}) — issue #37.
     */
    @Test
    void bodyContextSurvivesProgramPersistence(JenkinsRule j) throws Exception {
        // Unlike a plain DumbSlave, the real SwarmAgent replaces itself with a name-based proxy when
        // serialized, so the stand-in must do the same for this test to isolate the launcher.
        Slave standIn = createSerializableStandIn(j);
        SwarmAgentStep.nodeProvisioner =
                (cloud, template, agentName, step, logger) -> (Node) standIn;

        SwarmCloud cloud = new SwarmCloud("swarm");
        SwarmAgentTemplate template = new SwarmAgentTemplate("t");
        template.setLabelString("t");
        cloud.setTemplates(List.of(template));
        j.jenkins.clouds.add(cloud);

        WorkflowJob job = j.createProject(WorkflowJob.class, "body-persists");
        // sleep suspends the CPS thread, which triggers CpsThreadGroup.saveProgram().
        job.setDefinition(new CpsFlowDefinition(
                "swarmAgent(cloud: 'swarm', template: 't') {\n"
                        + "    sleep 1\n"
                        + "    echo 'DONE'\n"
                        + "}\n",
                true));

        WorkflowRun run = j.assertBuildStatusSuccess(job.scheduleBuild2(0));
        String log = j.getLog(run);
        assertFalse(log.contains("NotSerializableException"),
                "persisting the program must not fail on a context variable, log was:\n" + log);
    }

    /**
     * The {@link Launcher} the body resolves must still be the provisioned agent's, even when the
     * step is nested inside an enclosing {@code node} — otherwise {@code sh} / {@code bat} spawn
     * their process on the outer node while using the Swarm agent's workspace path (issue #29).
     * Since the fix for issue #37 the launcher is inferred from the {@code Node} in the body context
     * instead of being pushed into it, so this covers that inference.
     */
    @Test
    void bodyLauncherTargetsProvisionedAgentInsideOuterNode(JenkinsRule j) throws Exception {
        DumbSlave outer = j.createOnlineSlave(Label.get("outer"));
        DumbSlave standIn = j.createOnlineSlave();
        SwarmAgentStep.nodeProvisioner =
                (cloud, template, agentName, step, logger) -> (Node) standIn;

        SwarmCloud cloud = new SwarmCloud("swarm");
        SwarmAgentTemplate template = new SwarmAgentTemplate("t");
        template.setLabelString("t");
        cloud.setTemplates(List.of(template));
        j.jenkins.clouds.add(cloud);

        WorkflowJob job = j.createProject(WorkflowJob.class, "launcher-on-agent");
        job.setDefinition(new CpsFlowDefinition(
                "node('outer') {\n"
                        + "    swarmAgent(cloud: 'swarm', template: 't') {\n"
                        + "        captureLauncher()\n"
                        + "    }\n"
                        + "}\n",
                true));

        j.assertBuildStatusSuccess(job.scheduleBuild2(0));

        VirtualChannel captured = CaptureLauncherStep.capturedChannel;
        assertNotNull(captured, "the body should have resolved a Launcher");
        assertSame(standIn.toComputer().getChannel(), captured,
                "the body's launcher must run on the provisioned Swarm agent, not on "
                        + outer.getNodeName());
    }

    /**
     * Creates an online stand-in agent that, like {@link io.jenkins.plugins.swarmcloud.SwarmAgent},
     * serializes to a name-based proxy. A plain {@link DumbSlave} is not serializable at all (its
     * {@code launcher} field holds a test-only command launcher), which would mask what this test is
     * about: whether the step leaves a non-serializable object in the body context.
     */
    private static Slave createSerializableStandIn(JenkinsRule j) throws Exception {
        Slave standIn = new StandInAgent(
                "stand-in",
                j.createTmpDir().getAbsolutePath(),
                j.createComputerLauncher(null));
        j.jenkins.addNode(standIn);
        j.waitOnline(standIn);
        return standIn;
    }

    /** Agent that serializes to a name-based proxy, mirroring {@code SwarmAgent}. */
    public static class StandInAgent extends Slave {

        private static final long serialVersionUID = 1L;

        StandInAgent(String name, String remoteFS, ComputerLauncher launcher) throws Exception {
            super(name, remoteFS, launcher);
        }

        private Object writeReplace() {
            return new Proxy(getNodeName());
        }

        @TestExtension
        public static final class DescriptorImpl extends SlaveDescriptor {

            @NonNull
            @Override
            public String getDisplayName() {
                return "Stand-in agent";
            }

            @Override
            public boolean isInstantiable() {
                return false;
            }
        }

        private static final class Proxy implements Serializable {

            private static final long serialVersionUID = 1L;

            private final String nodeName;

            Proxy(String nodeName) {
                this.nodeName = nodeName;
            }

            private Object readResolve() {
                return Jenkins.get().getNode(nodeName);
            }
        }
    }

    /** Test-only step recording which channel the body's {@link Launcher} is bound to. */
    public static final class CaptureLauncherStep extends Step {

        static volatile VirtualChannel capturedChannel;

        @DataBoundConstructor
        public CaptureLauncherStep() {
        }

        @Override
        public StepExecution start(StepContext context) {
            return new Execution(context);
        }

        private static final class Execution extends SynchronousStepExecution<Void> {

            private static final long serialVersionUID = 1L;

            Execution(StepContext context) {
                super(context);
            }

            @Override
            protected Void run() throws Exception {
                capturedChannel = getContext().get(Launcher.class).getChannel();
                return null;
            }
        }

        @TestExtension
        public static final class DescriptorImpl extends StepDescriptor {

            @Override
            public String getFunctionName() {
                return "captureLauncher";
            }

            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Set.of(Launcher.class);
            }
        }
    }
}
