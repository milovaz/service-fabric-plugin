package com.microsoft.jenkins.servicefabric;

import java.io.IOException;
import java.io.Serializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import com.microsoft.jenkins.azurecommons.telemetry.AppInsightsUtils;
import com.microsoft.jenkins.servicefabric.util.Constants;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;

public class ServiceFabricUpdateArtifactsStep extends Step implements Serializable {

	@Override
	public StepExecution start(StepContext arg0) throws Exception {
		// TODO Auto-generated method stub
		return null;
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
		
	}
}
