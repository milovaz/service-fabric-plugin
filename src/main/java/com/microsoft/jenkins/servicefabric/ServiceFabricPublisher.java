/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */
package com.microsoft.jenkins.servicefabric;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * When the user configures the project and enables this publisher,
 * {@link DescriptorImpl#newInstance(org.kohsuke.stapler.StaplerRequest)} is invoked
 * and a new {@link ServiceFabricPublisher} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields
 * to remember the configuration.
 */
public class ServiceFabricPublisher extends Recorder implements SimpleBuildStep {
    private ServiceFabricPublishStep publishStep;

    @DataBoundConstructor
    public ServiceFabricPublisher(ServiceFabricPublishStep publishStep) {
        this.publishStep = publishStep;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run,
                        @Nonnull FilePath workspace,
                        @Nonnull Launcher launcher,
                        @Nonnull TaskListener listener) throws InterruptedException, IOException {
        publishStep.runStep(null, run, workspace, launcher, listener);
    }

    public ServiceFabricPublishStep getPublishStep() {
        return publishStep;
    }

    public void setPublishStep(ServiceFabricPublishStep publishStep) {
        this.publishStep = publishStep;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
            super();
            load();
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
