/*

 * Copyright (c) Microsoft Corporation. All rights reserved.

 * Licensed under the MIT License. See LICENSE in the project root for

 * license information.

 */
package com.microsoft.jenkins.servicefabric;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.GenericResource;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.util.AzureBaseCredentials;
import com.microsoft.jenkins.servicefabric.command.SFCommandBuilder;
import com.microsoft.jenkins.servicefabric.util.AzureHelper;
import com.microsoft.jenkins.servicefabric.util.Constants;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.tasks.Shell;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * When the user configures the project and enables this publisher,
 * {@link DescriptorImpl#newInstance(org.kohsuke.stapler.StaplerRequest)} is invoked
 * and a new {@link ServiceFabricPublisher} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields
 * to remember the configuration.
 * When a build is performed and is complete, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked.
 *
 * @author Kohsuke Kawaguchi
 */
public class ServiceFabricPublisher extends Recorder {

    private String configureType;
    private String azureCredentialsId;
    private String resourceGroupName;
    private String serviceFabricName;
    private String clusterPublicIP;
    private String applicationName;
    private String applicationType;
    private String manifestPath;
    private String clientKey;
    private String clientCert;

    @DataBoundConstructor
    public ServiceFabricPublisher() {
    }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException {
        String cfgType = getConfigureType();

        if (Constants.CONFIGURE_TYPE_SELECT.equals(cfgType)) {
            ServiceFabricCluster cluster = new ServiceFabricCluster(
                    AzureHelper.buildClient(azureCredentialsId),
                    resourceGroupName,
                    serviceFabricName);
            String managementEndpoint = cluster.getManagementEndpoint();
            try {
                URL url = new URL(managementEndpoint);
                if ("https".equalsIgnoreCase(url.getProtocol())) {
                    if (StringUtils.isBlank(clientKey) || StringUtils.isBlank(clientCert)) {
                        throw new IllegalStateException("Certificate and Key are not specified for "
                                + "secured Service Fabric management endpoint.");
                    }
                }
                clusterPublicIP = url.getHost();
            } catch (MalformedURLException e) {
                throw new AbortException("Cannot determine Service Fabric management endpoint. " + e.getMessage());
            }
        }

        SFCommandBuilder commandBuilder = new SFCommandBuilder(
                build.getWorkspace(),
                applicationName,
                applicationType,
                clusterPublicIP,
                manifestPath,
                clientKey,
                clientCert);
        String commandString = commandBuilder.buildCommands();

        Shell command = new Shell(commandString);

        try {
            return command.perform(build, launcher, listener);
        } catch (InterruptedException e) {
            return false;
        }
    }

    public String getConfigureType() {
        if (StringUtils.isBlank(configureType)) {
            if (StringUtils.isBlank(clusterPublicIP)) {
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

    public String getResourceGroupName() {
        return resourceGroupName;
    }

    @DataBoundSetter
    public void setResourceGroupName(String resourceGroupName) {
        this.resourceGroupName = resourceGroupName;
    }

    public String getServiceFabricName() {
        return serviceFabricName;
    }

    @DataBoundSetter
    public void setServiceFabricName(String serviceFabricName) {
        this.serviceFabricName = serviceFabricName;
    }

    public String getClusterPublicIP() {
        return clusterPublicIP;
    }

    @DataBoundSetter
    public void setClusterPublicIP(String clusterPublicIP) {
        this.clusterPublicIP = clusterPublicIP;
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

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        return new ServiceFabricProjectAction(project);
    }

    /**
     * Descriptor for {@link ServiceFabricPublisher}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     * See <tt>src/main/resources/org/jenkinsci/plugins/serviceFabric/ServiceFabricPublisher/*.jelly</tt>
     * for the actual HTML fragment for the configuration screen.
     */
    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
            super();
            load();
        }

        public ListBoxModel doFillAzureCredentialsIdItems(@AncestorInPath Item owner) {
            StandardListBoxModel model = new StandardListBoxModel();
            model.includeEmptyValue();
            model.includeAs(ACL.SYSTEM, owner, AzureBaseCredentials.class);
            return model;
        }

        public ListBoxModel doFillResourceGroupNameItems(@QueryParameter String azureCredentialsId) {
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

        public ListBoxModel doFillServiceFabricNameItems(@QueryParameter String azureCredentialsId,
                                                         @QueryParameter String resourceGroupName) {
            ListBoxModel model = new ListBoxModel();
            model.add("");

            if (StringUtils.isBlank(azureCredentialsId) || StringUtils.isBlank(resourceGroupName)) {
                return model;
            }

            try {
                Azure azure = AzureHelper.buildClient(azureCredentialsId);
                // TODO: Use ServiceFabric related API when the ServiceFabric Java SDK is GA
                PagedList<GenericResource> resources =
                        azure.genericResources().listByResourceGroup(resourceGroupName);
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

        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Deploy Service Fabric Project";
        }
    }
}

