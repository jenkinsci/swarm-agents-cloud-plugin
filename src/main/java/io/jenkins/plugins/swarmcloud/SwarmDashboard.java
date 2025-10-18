package io.jenkins.plugins.swarmcloud;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.ManagementLink;
import hudson.model.RootAction;
import hudson.security.Permission;
import hudson.slaves.Cloud;
import io.jenkins.plugins.swarmcloud.monitoring.ClusterMonitor;
import io.jenkins.plugins.swarmcloud.monitoring.ClusterStatus;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.verb.GET;
import org.kohsuke.stapler.verb.POST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dashboard for monitoring Docker Swarm agents.
 * Accessible from Jenkins Management page.
 */
@Extension
public class SwarmDashboard extends ManagementLink implements RootAction {

    @Override
    public String getIconFileName() {
        return "symbol-cloud-outline plugin-ionicons-api";
    }

    @Override
    public String getDisplayName() {
        return "Swarm Agents Dashboard";
    }

    @Override
    public String getUrlName() {
        return "swarm-dashboard";
    }

    @Override
    public String getDescription() {
        return "Monitor Docker Swarm agents, view cluster status, and manage services";
    }

    @NonNull
    @Override
    public Category getCategory() {
        return Category.STATUS;
    }

    @NonNull
    @Override
    public Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }

    /**
     * Gets all Swarm clouds.
     */
    @NonNull
    public List<SwarmCloud> getSwarmClouds() {
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

    /**
     * Gets the status for a specific cloud.
     */
    @NonNull
    public ClusterStatus getClusterStatus(@NonNull String cloudName) {
        return ClusterMonitor.getStatus(cloudName);
    }

    /**
     * Gets all cluster statuses.
     */
    @NonNull
    public Map<String, ClusterStatus> getAllStatuses() {
        return ClusterMonitor.getAllStatuses();
    }

    /**
     * Gets the last update timestamp.
     */
    public long getLastUpdate() {
        return ClusterMonitor.getLastUpdate();
    }

    /**
     * API endpoint to get cluster status as JSON.
     */
    @GET
    public void doApi(StaplerRequest req, StaplerResponse rsp,
                      @QueryParameter String cloud) throws IOException {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        JSONObject response = new JSONObject();

        if (cloud != null && !cloud.isBlank()) {
            ClusterStatus status = getClusterStatus(cloud);
            response.put("status", statusToJson(status));
        } else {
            JSONArray clouds = new JSONArray();
            for (Map.Entry<String, ClusterStatus> entry : getAllStatuses().entrySet()) {
                clouds.add(statusToJson(entry.getValue()));
            }
            response.put("clouds", clouds);
        }

        response.put("lastUpdate", getLastUpdate());
        writeJsonResponse(rsp, 200, response.toString());
    }

    private void writeJsonResponse(StaplerResponse rsp, int status, String json) throws IOException {
        rsp.setStatus(status);
        rsp.setContentType("application/json;charset=UTF-8");
        try (PrintWriter writer = rsp.getWriter()) {
            writer.write(json);
        }
    }

    /**
     * API endpoint to refresh metrics for a cloud.
     */
    @POST
    public HttpResponse doRefresh(@QueryParameter String cloud) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (cloud != null && !cloud.isBlank()) {
            ClusterMonitor.refreshNow(cloud);
            return HttpResponses.ok();
        }

        return HttpResponses.error(400, "Cloud name is required");
    }

    /**
     * API endpoint to remove a service.
     */
    @POST
    public HttpResponse doRemoveService(@QueryParameter String cloud, @QueryParameter String serviceId) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);

        if (cloud == null || cloud.isBlank() || serviceId == null || serviceId.isBlank()) {
            return HttpResponses.error(400, "Cloud name and service ID are required");
        }

        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return HttpResponses.error(500, "Jenkins is not available");
        }

        for (Cloud c : jenkins.clouds) {
            if (c instanceof SwarmCloud && c.name.equals(cloud)) {
                SwarmCloud swarmCloud = (SwarmCloud) c;
                try {
                    swarmCloud.getDockerClient().removeService(serviceId);
                    ClusterMonitor.refreshNow(cloud);
                    return HttpResponses.ok();
                } catch (Exception e) {
                    return HttpResponses.error(500, "Failed to remove service: " + e.getMessage());
                }
            }
        }

        return HttpResponses.error(404, "Cloud not found: " + cloud);
    }

    /**
     * Converts ClusterStatus to JSON.
     */
    private JSONObject statusToJson(ClusterStatus status) {
        JSONObject json = new JSONObject();
        json.put("cloudName", status.getCloudName());
        json.put("healthy", status.isHealthy());
        json.put("errorMessage", status.getErrorMessage());
        json.put("swarmVersion", status.getSwarmVersion());
        json.put("totalNodes", status.getTotalNodes());
        json.put("readyNodes", status.getReadyNodes());
        json.put("managerNodes", status.getManagerNodes());
        json.put("totalMemory", status.getTotalMemory());
        json.put("formattedMemory", status.getFormattedMemory());
        json.put("totalCpu", status.getTotalCpu());
        json.put("activeServices", status.getActiveServices());
        json.put("runningTasks", status.getRunningTasks());
        json.put("pendingTasks", status.getPendingTasks());
        json.put("failedTasks", status.getFailedTasks());
        json.put("maxAgents", status.getMaxAgents());
        json.put("currentAgents", status.getCurrentAgents());
        json.put("availableCapacity", status.getAvailableCapacity());
        json.put("utilizationPercent", status.getUtilizationPercent());
        json.put("statusClass", status.getStatusClass());
        json.put("lastUpdate", status.getLastUpdate());

        JSONArray nodes = new JSONArray();
        for (var node : status.getNodes()) {
            JSONObject n = new JSONObject();
            n.put("id", node.getId());
            n.put("hostname", node.getHostname());
            n.put("state", node.getState());
            n.put("role", node.getRole());
            n.put("memoryBytes", node.getMemoryBytes());
            n.put("formattedMemory", node.getFormattedMemory());
            n.put("cpuCores", node.getCpuCores());
            n.put("stateClass", node.getStateClass());
            nodes.add(n);
        }
        json.put("nodes", nodes);

        JSONArray services = new JSONArray();
        for (var svc : status.getServices()) {
            JSONObject s = new JSONObject();
            s.put("id", svc.getId());
            s.put("shortId", svc.getShortId());
            s.put("name", svc.getName());
            s.put("state", svc.getState());
            s.put("templateName", svc.getTemplateName());
            s.put("uptime", svc.getUptime());
            s.put("error", svc.getError());
            s.put("stateClass", svc.getStateClass());
            services.add(s);
        }
        json.put("services", services);

        return json;
    }
}
