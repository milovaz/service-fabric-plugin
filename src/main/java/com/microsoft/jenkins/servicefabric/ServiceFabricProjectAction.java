/*

 * Copyright (c) Microsoft Corporation. All rights reserved.

 * Licensed under the MIT License. See LICENSE in the project root for

 * license information.

 */
package com.microsoft.jenkins.servicefabric;

import hudson.model.AbstractProject;
import hudson.model.Action;

public class ServiceFabricProjectAction implements Action {

    private AbstractProject<?, ?> project;

    @Override
    public String getIconFileName() {
        return "/plugin/serviceFabric/img/project_icon.png";
    }

    @Override
    public String getDisplayName() {
        return "Service Fabric Deploy Status";
    }

    @Override
    public String getUrlName() {
        return "serviceFabricPA";
    }

    public AbstractProject<?, ?> getProject() {
        return this.project;
    }

    public String getProjectName() {
        return this.project.getName();
    }

    public String getProjectMessages() {
        // List<String> projectMessages = new ArrayList<String>();
        // List<? extends AbstractBuild<?, ?>> builds = project.getBuilds();
        // String projectMessage="";
        // final Class<ServiceFabricBuildAction> buildClass = ServiceFabricBuildAction.class;

        // for (AbstractBuild<?, ?> currentBuild : builds) {
        //     projectMessage = "Build #"+currentBuild.getAction(buildClass).getBuildNumber()
        //             +": "+currentBuild.getAction(buildClass).getCommand();
        //     projectMessages.add(projectMessage);
        // }
        // return projectMessages;
        return "";
    }

    ServiceFabricProjectAction(final AbstractProject<?, ?> project) {
        this.project = project;
    }
}
