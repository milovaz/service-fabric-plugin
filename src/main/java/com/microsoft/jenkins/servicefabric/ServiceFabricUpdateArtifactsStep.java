package com.microsoft.jenkins.servicefabric;

import java.io.IOException;
import java.io.Serializable;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.ImmutableSet;
import com.microsoft.jenkins.azurecommons.telemetry.AppInsightsUtils;
import com.microsoft.jenkins.servicefabric.auth.Authenticator;
import com.microsoft.jenkins.servicefabric.util.ApplicationManifestBuilder;
import com.microsoft.jenkins.servicefabric.util.Constants;
import com.microsoft.jenkins.servicefabric.util.ServiceManifestBuilder;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

public class ServiceFabricUpdateArtifactsStep extends Step implements Serializable {

	private static final long serialVersionUID = 1L;

	private String applicationManifestPath;
	private String serviceManifestPath;
	private String serviceTargetVersion;
	private String applicationTargetVersion;
	private String credentials;


	@DataBoundConstructor
	public ServiceFabricUpdateArtifactsStep() {
	}

	@Override
	public StepExecution start(StepContext context) throws Exception {
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

	public void runStep(@Nullable StepContext context,
            @Nonnull Run<?, ?> run,
            @Nonnull FilePath workspace,
            @Nonnull Launcher launcher,
            @Nonnull TaskListener listener) throws InterruptedException, IOException {
		try {
			doRunStep(context, run, workspace, launcher, listener);
		} catch (InterruptedException | IOException | RuntimeException e) {
			throw e;
		}
	}

	private void doRunStep(@Nullable StepContext context,
            @Nonnull Run<?, ?> run,
            @Nonnull FilePath workspace,
            @Nonnull Launcher launcher,
            @Nonnull TaskListener listener) throws InterruptedException, IOException {

		ServiceManifestBuilder serviceManifestBuilder = new ServiceManifestBuilder(workspace.child(serviceManifestPath).getRemote());
		serviceManifestBuilder.updateContainerImageVersion(serviceTargetVersion);
		serviceManifestBuilder.updateApplicationTypeVersion(Constants.PATCH_VERSION, serviceTargetVersion);
		serviceManifestBuilder.saveToFile();

		ApplicationManifestBuilder appManifestBuilder = new ApplicationManifestBuilder(workspace.child(applicationManifestPath).getRemote());
		appManifestBuilder.updateApplicationTypeVersion(Constants.PATCH_VERSION, applicationTargetVersion);
		appManifestBuilder.updateServiceManifestVersion(Constants.PATCH_VERSION, applicationTargetVersion);
		appManifestBuilder.insertContainerRegistryCredentials(new Authenticator(credentials, run));
		appManifestBuilder.saveToFile();		
		
	}
	
	public String getApplicationManifestPath() {
		return applicationManifestPath;
	}

	@DataBoundSetter
	public void setApplicationManifestPath(String applicationManifestPath) {
		this.applicationManifestPath = applicationManifestPath;
	}

	public String getServiceManifestPath() {
		return serviceManifestPath;
	}

	@DataBoundSetter
	public void setServiceManifestPath(String serviceManifestPath) {
		this.serviceManifestPath = serviceManifestPath;
	}

	public String getServiceTargetVersion() {
		return serviceTargetVersion;
	}

	@DataBoundSetter
	public void setServiceTargetVersion(String serviceTargetVersion) {
		this.serviceTargetVersion = serviceTargetVersion;
	}

	public String getApplicationTargetVersion() {
		return applicationTargetVersion;
	}

	@DataBoundSetter
	public void setApplicationTargetVersion(String applicationTargetVersion) {
		this.applicationTargetVersion = applicationTargetVersion;
	}

	public String getCredentials() {
		return credentials;
	}

	@DataBoundSetter
	public void setCredentials(String credentials) {
		this.credentials = credentials;
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
            return "azureServiceFabricUpdateArtifacts";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Update configuration artifacts from a Service Fabric Project";
        }
	}
}
