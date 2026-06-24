package io.jenkins.plugins.swarmcloud.pipeline;

import hudson.model.Node;
import hudson.slaves.DumbSlave;
import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;
import io.jenkins.plugins.swarmcloud.SwarmCloud;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.List;

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
}
