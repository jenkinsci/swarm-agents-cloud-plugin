package io.jenkins.plugins.swarmcloud.rest;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.RootAction;
import hudson.slaves.Cloud;
import io.jenkins.plugins.swarmcloud.SwarmAgent;
import io.jenkins.plugins.swarmcloud.SwarmAgentTemplate;
import io.jenkins.plugins.swarmcloud.SwarmCloud;
import io.jenkins.plugins.swarmcloud.monitoring.ClusterMonitor;
import io.jenkins.plugins.swarmcloud.monitoring.ClusterStatus;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.DELETE;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.POST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * REST API for managing Docker Swarm agents.
 */
@Extension
public class SwarmRestApi implements RootAction {

    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "Swarm REST API";
    }

    @Override
    public String getUrlName() {
        return "swarm-api";
    }

    /**
     * GET /swarm-api/clouds
     */
    @GET
    public void doClouds(StaplerRequest req, StaplerResponse rsp) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        JSONArray clouds = new JSONArray();
        for (SwarmCloud cloud : getSwarmClouds()) {
            JSONObject c = new JSONObject();
            c.put("name", cloud.name);
            c.put("dockerHost", cloud.getDockerHost());
            c.put("maxConcurrentAgents", cloud.getMaxConcurrentAgents());
            c.put("currentAgents", cloud.countCurrentAgents());
            c.put("templateCount", cloud.getTemplates().size());
            clouds.add(c);
        }

        writeJsonResponse(rsp, 200, clouds.toString());
    }

    /**
     * GET /swarm-api/cloud?name={name}
     */
    @GET
    public void doCloud(StaplerRequest req, StaplerResponse rsp,
                        @QueryParameter String name) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (name == null || name.isBlank()) {
            writeJsonError(rsp, 400, "Cloud name is required");
            return;
        }

        SwarmCloud cloud = findCloud(name);
        if (cloud == null) {
            writeJsonError(rsp, 404, "Cloud not found: " + name);
            return;
        }

        JSONObject result = new JSONObject();
        result.put("name", cloud.name);
        result.put("dockerHost", cloud.getDockerHost());
        result.put("jenkinsUrl", cloud.getJenkinsUrl());
        result.put("swarmNetwork", cloud.getSwarmNetwork());
        result.put("maxConcurrentAgents", cloud.getMaxConcurrentAgents());
        result.put("currentAgents", cloud.countCurrentAgents());
        result.put("canProvision", cloud.canProvision());

        JSONArray templates = new JSONArray();
        for (SwarmAgentTemplate template : cloud.getTemplates()) {
            templates.add(templateToJson(template));
        }
        result.put("templates", templates);

        writeJsonResponse(rsp, 200, result.toString());
    }

    /**
     * GET /swarm-api/templates
     */
    @GET
    public void doTemplates(StaplerRequest req, StaplerResponse rsp,
                            @QueryParameter String cloud) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        JSONArray templates = new JSONArray();

        if (cloud != null && !cloud.isBlank()) {
            SwarmCloud swarmCloud = findCloud(cloud);
            if (swarmCloud == null) {
                writeJsonError(rsp, 404, "Cloud not found: " + cloud);
                return;
            }
            for (SwarmAgentTemplate template : swarmCloud.getTemplates()) {
                JSONObject t = templateToJson(template);
                t.put("cloudName", swarmCloud.name);
                templates.add(t);
            }
        } else {
            for (SwarmCloud swarmCloud : getSwarmClouds()) {
                for (SwarmAgentTemplate template : swarmCloud.getTemplates()) {
                    JSONObject t = templateToJson(template);
                    t.put("cloudName", swarmCloud.name);
                    templates.add(t);
                }
            }
        }

        writeJsonResponse(rsp, 200, templates.toString());
    }

    /**
     * GET /swarm-api/agents
     */
    @GET
    public void doAgents(StaplerRequest req, StaplerResponse rsp,
                         @QueryParameter String cloud) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        JSONArray agents = new JSONArray();

        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            for (var node : jenkins.getNodes()) {
                if (node instanceof SwarmAgent) {
                    SwarmAgent agent = (SwarmAgent) node;

                    if (cloud != null && !cloud.isBlank() && !cloud.equals(agent.getCloudName())) {
                        continue;
                    }

                    JSONObject a = new JSONObject();
                    a.put("name", agent.getNodeName());
                    a.put("cloudName", agent.getCloudName());
                    a.put("serviceId", agent.getServiceId());
                    a.put("templateName", agent.getTemplateName());
                    a.put("remoteFS", agent.getRemoteFS());
                    a.put("numExecutors", agent.getNumExecutors());
                    a.put("labelString", agent.getLabelString());

                    var computer = agent.toComputer();
                    if (computer != null) {
                        a.put("online", computer.isOnline());
                        a.put("idle", computer.isIdle());
                        a.put("connecting", computer.isConnecting());
                    }

                    agents.add(a);
                }
            }
        }

        writeJsonResponse(rsp, 200, agents.toString());
    }

    /**
     * GET /swarm-api/metrics
     */
    @GET
    public void doMetrics(StaplerRequest req, StaplerResponse rsp,
                          @QueryParameter String cloud) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (cloud != null && !cloud.isBlank()) {
            ClusterStatus status = ClusterMonitor.getStatus(cloud);
            writeJsonResponse(rsp, 200, statusToJson(status).toString());
            return;
        }

        JSONObject result = new JSONObject();
        JSONArray clouds = new JSONArray();

        for (var entry : ClusterMonitor.getAllStatuses().entrySet()) {
            clouds.add(statusToJson(entry.getValue()));
        }

        result.put("clouds", clouds);
        result.put("lastUpdate", ClusterMonitor.getLastUpdate());

        writeJsonResponse(rsp, 200, result.toString());
    }

    /**
     * POST /swarm-api/provision
     */
    @POST
    public void doProvision(StaplerRequest req, StaplerResponse rsp,
                            @QueryParameter String cloud,
                            @QueryParameter String template) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (cloud == null || cloud.isBlank() || template == null || template.isBlank()) {
            writeJsonError(rsp, 400, "Cloud name and template name are required");
            return;
        }

        SwarmCloud swarmCloud = findCloud(cloud);
        if (swarmCloud == null) {
            writeJsonError(rsp, 404, "Cloud not found: " + cloud);
            return;
        }

        SwarmAgentTemplate tmpl = null;
        for (SwarmAgentTemplate t : swarmCloud.getTemplates()) {
            if (t.getName().equals(template)) {
                tmpl = t;
                break;
            }
        }

        if (tmpl == null) {
            writeJsonError(rsp, 404, "Template not found: " + template);
            return;
        }

        if (!swarmCloud.canProvision()) {
            writeJsonError(rsp, 400, "Cannot provision: max agents reached");
            return;
        }

        if (tmpl.getAvailableCapacity() <= 0) {
            writeJsonError(rsp, 400, "Cannot provision: template max instances reached");
            return;
        }

        try {
            String agentName = tmpl.generateAgentName();
            String serviceId = swarmCloud.getDockerClient().createService(
                    agentName,
                    tmpl,
                    swarmCloud.getEffectiveJenkinsUrl(),
                    swarmCloud.getSwarmNetwork()
            );

            SwarmAgent agent = new SwarmAgent(agentName, tmpl, swarmCloud.name, serviceId);
            Jenkins.get().addNode(agent);

            JSONObject result = new JSONObject();
            result.put("agentName", agentName);
            result.put("serviceId", serviceId);
            result.put("status", "provisioning");

            writeJsonResponse(rsp, 200, result.toString());
        } catch (Exception e) {
            writeJsonError(rsp, 500, "Failed to provision: " + e.getMessage());
        }
    }

    // Helper methods

    private void writeJsonResponse(StaplerResponse rsp, int status, String json) throws IOException {
        rsp.setStatus(status);
        rsp.setContentType(JSON_CONTENT_TYPE);
        try (PrintWriter writer = rsp.getWriter()) {
            writer.write(json);
        }
    }

    private void writeJsonError(StaplerResponse rsp, int status, String message) throws IOException {
        JSONObject error = new JSONObject();
        error.put("error", message);
        writeJsonResponse(rsp, status, error.toString());
    }

    @NonNull
    private List<SwarmCloud> getSwarmClouds() {
        List<SwarmCloud> clouds = new ArrayList<>();
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins != null) {
            for (Cloud cloud : jenkins.clouds) {
                if (cloud instanceof SwarmCloud) {
                    clouds.add((SwarmCloud) cloud);
                }
            }
        }
        return clouds;
    }

    private SwarmCloud findCloud(String name) {
        for (SwarmCloud cloud : getSwarmClouds()) {
            if (cloud.name.equals(name)) {
                return cloud;
            }
        }
        return null;
    }

    private JSONObject templateToJson(SwarmAgentTemplate template) {
        JSONObject t = new JSONObject();
        t.put("name", template.getName());
        t.put("image", template.getImage());
        t.put("labelString", template.getLabelString());
        t.put("remoteFs", template.getRemoteFs());
        t.put("numExecutors", template.getNumExecutors());
        t.put("maxInstances", template.getMaxInstances());
        t.put("currentInstances", template.getCurrentInstances());
        t.put("availableCapacity", template.getAvailableCapacity());
        t.put("cpuLimit", template.getCpuLimit());
        t.put("memoryLimit", template.getMemoryLimit());
        return t;
    }

    private JSONObject statusToJson(ClusterStatus status) {
        JSONObject json = new JSONObject();
        json.put("cloudName", status.getCloudName());
        json.put("healthy", status.isHealthy());
        json.put("errorMessage", status.getErrorMessage());
        json.put("totalNodes", status.getTotalNodes());
        json.put("readyNodes", status.getReadyNodes());
        json.put("activeServices", status.getActiveServices());
        json.put("runningTasks", status.getRunningTasks());
        json.put("pendingTasks", status.getPendingTasks());
        json.put("failedTasks", status.getFailedTasks());
        json.put("maxAgents", status.getMaxAgents());
        json.put("currentAgents", status.getCurrentAgents());
        json.put("utilizationPercent", status.getUtilizationPercent());
        json.put("lastUpdate", status.getLastUpdate());
        return json;
    }
}
