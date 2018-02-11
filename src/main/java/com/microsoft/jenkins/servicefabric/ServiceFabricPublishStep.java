/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.jenkins.servicefabric;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.google.common.collect.ImmutableSet;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.GenericResource;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.util.AzureBaseCredentials;
import com.microsoft.jenkins.azurecommons.telemetry.AppInsightsUtils;
import com.microsoft.jenkins.servicefabric.command.SFCommandBuilder;
import com.microsoft.jenkins.servicefabric.util.AzureHelper;
import com.microsoft.jenkins.servicefabric.util.Constants;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.Shell;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.durabletask.BourneShellScript;
import org.jenkinsci.plugins.durabletask.Controller;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

/**
 * The Step for the Service Fabric publish.
 */
public class ServiceFabricPublishStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private String configureType;
    private String azureCredentialsId;
    private String resourceGroup;
    private String serviceFabric;
    private String managementHost;
    private String applicationName;
    private String applicationType;
    private String manifestPath;
    private String clientKey;
    private String clientCert;

    @DataBoundConstructor
    public ServiceFabricPublishStep() {
    }

    /**
     * This method will be invoked by the Pipeline, when the function defined in
     * {@link DescriptorImpl#getFunctionName()} is called.
     *
     * @param context the context of the step.
     * @return execution block of the step.
     */
    @Override
    public StepExecution start(StepContext context) {
        return new SynchronousNonBlockingStepExecution<Void>(context) {
            @SuppressWarnings("ConstantConditions")
            @Override
            protected Void run() throws Exception {
                StepContext context = getContext();
                FilePath workspace = context.get(FilePath.class);
                workspace.mkdirs();

                runStep(context,
                        context.get(Run.class),
                        workspace,
                        context.get(Launcher.class),
                        context.get(TaskListener.class));
                return null;
            }
        };
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }


    public void runStep(@Nullable StepContext context,
                        @Nonnull Run<?, ?> run,
                        @Nonnull FilePath workspace,
                        @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws InterruptedException, IOException {
        String buildId = AppInsightsUtils.hash(run.getUrl());
        AzureServiceFabricPlugin.sendEvent("StartDeploy",
                Constants.AI_RUN, buildId);

        try {
            doRunStep(context, run, workspace, launcher, listener);
            AzureServiceFabricPlugin.sendEvent("Deployed", Constants.AI_RUN, buildId);
        } catch (InterruptedException | IOException | RuntimeException e) {
            AzureServiceFabricPlugin.sendEvent("DeployFailed",
                    Constants.AI_RUN, buildId,
                    "Message", e.getMessage());
            throw e;
        }
    }

    private void doRunStep(@Nullable StepContext context,
                           @Nonnull Run<?, ?> run,
                           @Nonnull FilePath workspace,
                           @Nonnull Launcher launcher,
                           @Nonnull TaskListener listener) throws InterruptedException, IOException {
        String buildId = AppInsightsUtils.hash(run.getUrl());

        String cfgType = getConfigureType();
        if (Constants.CONFIGURE_TYPE_SELECT.equals(cfgType)) {
            Azure azure = AzureHelper.buildClient(azureCredentialsId);
            AzureServiceFabricPlugin.sendEvent("DeployAzure",
                    Constants.AI_RUN, buildId,
                    "Subscription", AppInsightsUtils.hash(azure.subscriptionId()),
                    "ResourceGroup", AppInsightsUtils.hash(resourceGroup),
                    "Cluster", AppInsightsUtils.hash(serviceFabric));

            ServiceFabricCluster cluster = new ServiceFabricCluster(azure, resourceGroup, serviceFabric);
            String managementEndpoint = cluster.getManagementEndpoint();
            try {
                URL url = new URL(managementEndpoint);
                if ("https".equalsIgnoreCase(url.getProtocol())) {
                    if (StringUtils.isBlank(clientKey) || StringUtils.isBlank(clientCert)) {
                        throw new IllegalStateException("Certificate and Key are not specified for "
                                + "secured Service Fabric management endpoint.");
                    }
                }
                managementHost = url.getHost();
            } catch (MalformedURLException e) {
                throw new AbortException("Cannot determine Service Fabric management endpoint. " + e.getMessage());
            }
        } else {
            AzureServiceFabricPlugin.sendEvent("DeployServiceFabric",
                    Constants.AI_RUN, buildId,
                    "Endpoint", managementHost);
        }

        SFCommandBuilder commandBuilder = new SFCommandBuilder(
                workspace,
                applicationName,
                applicationType,
                managementHost,
                manifestPath,
                clientKey,
                clientCert);
        String commandString = commandBuilder.buildCommands();

        if (run instanceof AbstractBuild) {
            // build configured with GUI goes here
            Shell command = new Shell(commandString);

            boolean result = command.perform((AbstractBuild) run, launcher, listener);
            if (!result) {
                throw new AbortException("Shell script execution failed");
            }
        } else if (context != null) {
            // pipeline build goes here
            BourneShellScript shellScript = new BourneShellScript(commandString);
            Controller controller = shellScript.launch(context.get(EnvVars.class), workspace, launcher, listener);

            long startTime = System.currentTimeMillis();
            // logic borrowed from org.jenkins-ci.plugins.workflow/workflow-durable-task-step
            // org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep.Execution#check()
            AbortException exp = null;
            try {
                final long minInterval = 250;
                final long maxInterval = 5000;
                final long timeoutMinutes = 5;
                final long timeout = timeoutMinutes * 60 * 1000;
                while (true) {
                    long waitInterval;
                    if (controller.writeLog(workspace, listener.getLogger())) {
                        // got output, maybe we will get more
                        waitInterval = minInterval;
                    } else {
                        waitInterval = maxInterval;
                    }

                    Integer exitCode = controller.exitStatus(workspace, launcher);
                    if (exitCode == null) {
                        if (System.currentTimeMillis() - startTime > timeout) {
                            controller.stop(workspace, launcher);
                            exp = new AbortException("Script execution timeout after " + timeoutMinutes + " minutes");
                            break;
                        }
                        Thread.sleep(waitInterval);
                    } else {
                        controller.writeLog(workspace, listener.getLogger());
                        if (exitCode != 0) {
                            exp = new AbortException("script returned exit code " + exitCode);
                        }
                        break;
                    }
                }
            } finally {
                controller.cleanup(workspace);
            }
            if (exp != null) {
                throw exp;
            }
        } else {
            // should not reach here, throw in case upstream logic changed in future.
            throw new IllegalStateException("Unsupported run type " + run.getClass().getName());
        }
    }

    public String getConfigureType() {
        if (StringUtils.isBlank(configureType)) {
            if (StringUtils.isBlank(managementHost)) {
                return Constants.CONFIGURE_TYPE_SELECT;
            } else {
                return Constants.CONFIGURE_TYPE_FILL;
            }
        }
        return configureType;
    }

    @DataBoundSetter
    public void setConfigureType(String configureType) {
        this.configureType = configureType;
    }

    public String getAzureCredentialsId() {
        return azureCredentialsId;
    }

    @DataBoundSetter
    public void setAzureCredentialsId(String azureCredentialsId) {
        this.azureCredentialsId = azureCredentialsId;
    }

    public String getResourceGroup() {
        return resourceGroup;
    }

    @DataBoundSetter
    public void setResourceGroup(String resourceGroup) {
        this.resourceGroup = resourceGroup;
    }

    public String getServiceFabric() {
        return serviceFabric;
    }

    @DataBoundSetter
    public void setServiceFabric(String serviceFabric) {
        this.serviceFabric = serviceFabric;
    }

    public String getManagementHost() {
        return managementHost;
    }

    @DataBoundSetter
    public void setManagementHost(String managementHost) {
        this.managementHost = managementHost;
    }

    public String getApplicationName() {
        return applicationName;
    }

    @DataBoundSetter
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationType() {
        return applicationType;
    }

    @DataBoundSetter
    public void setApplicationType(String applicationType) {
        this.applicationType = applicationType;
    }

    public String getManifestPath() {
        return manifestPath;
    }

    @DataBoundSetter
    public void setManifestPath(String manifestPath) {
        this.manifestPath = manifestPath;
    }

    public String getClientKey() {
        return clientKey;
    }

    @DataBoundSetter
    public void setClientKey(String clientKey) {
        this.clientKey = clientKey;
    }

    public String getClientCert() {
        return clientCert;
    }

    @DataBoundSetter
    public void setClientCert(String clientCert) {
        this.clientCert = clientCert;
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends StepDescriptor {
        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, FilePath.class, Launcher.class, TaskListener.class);
        }

        @Override
        public String getFunctionName() {
            return "azureServiceFabricPublish";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Deploy Service Fabric Project";
        }

        public ListBoxModel doFillAzureCredentialsIdItems(@AncestorInPath Item owner) {
            StandardListBoxModel model = new StandardListBoxModel();
            model.includeEmptyValue();
            model.includeAs(ACL.SYSTEM, owner, AzureBaseCredentials.class);
            return model;
        }

        public ListBoxModel doFillResourceGroupItems(@QueryParameter String azureCredentialsId) {
            ListBoxModel model = new ListBoxModel();
            model.add("");

            if (StringUtils.isBlank(azureCredentialsId)) {
                return model;
            }

            try {
                Azure azure = AzureHelper.buildClient(azureCredentialsId);
                for (ResourceGroup resourceGroup : azure.resourceGroups().list()) {
                    model.add(resourceGroup.name());
                }
            } catch (Exception ex) {
                model.add("Failed to load resource groups: " + ex.getMessage(), "");
            }

            return model;
        }

        public ListBoxModel doFillServiceFabricItems(@QueryParameter String azureCredentialsId,
                                                     @QueryParameter String resourceGroup) {
            ListBoxModel model = new ListBoxModel();
            model.add("");

            if (StringUtils.isBlank(azureCredentialsId) || StringUtils.isBlank(resourceGroup)) {
                return model;
            }

            try {
                Azure azure = AzureHelper.buildClient(azureCredentialsId);
                // TODO: Use ServiceFabric related API when the ServiceFabric Java SDK is GA
                PagedList<GenericResource> resources =
                        azure.genericResources().listByResourceGroup(resourceGroup);
                for (GenericResource resource : resources) {
                    if (Constants.SF_PROVIDER.equals(resource.resourceProviderNamespace())
                            && Constants.SF_CLUSTER_TYPE.equals(resource.resourceType())) {
                        model.add(resource.name());
                    }
                }
            } catch (Exception ex) {
                model.add("** Failed to load Service Fabric clusters: " + ex.getMessage(), "");
            }

            return model;
        }

        /**
         * Check to make sure that the application name begins with "fabric:/".
         */
        public FormValidation doCheckApplicationName(@QueryParameter String value) {
            if (value.startsWith("fabric:/")) {
                return FormValidation.ok();
            } else {
                return FormValidation.error("Application name must begin with \"fabric:/\"");
            }
        }
    }
}
