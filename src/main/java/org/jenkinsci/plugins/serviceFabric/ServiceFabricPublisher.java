/*

 * Copyright (c) Microsoft Corporation. All rights reserved.

 * Licensed under the MIT License. See LICENSE in the project root for

 * license information.

 */
package org.jenkinsci.plugins.serviceFabric;

import hudson.Launcher;
import hudson.Extension;
import hudson.model.Action;
import hudson.tasks.*;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;

import org.jenkinsci.plugins.serviceFabric.ServiceFabricCommands.SFCommandBuilder;

/**
 * Sample {@link Publisher}.
 * When the user configures the project and enables this publisher,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link ServiceFabricPublisher} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 * When a build is performed and is complete, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked.
 *
 * @author Kohsuke Kawaguchi
 */
public class ServiceFabricPublisher extends Recorder {

    private final String name;
    private final String clusterType;
    private final String clusterPublicIP;
    private final String applicationName;
    private final String applicationType;
    private final String manifestPath;
    private final String clientKey;
    private final String clientCert;
    

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public ServiceFabricPublisher(String name, String clusterType, String clusterPublicIP, String applicationName,
            String applicationType, String manifestPath, String clientKey, String clientCert) {

        this.name = name;
        this.clusterType = clusterType;
        this.clusterPublicIP = clusterPublicIP; 
        this.applicationName = applicationName;
        this.applicationType = applicationType;
        this.manifestPath = manifestPath;
        this.clientKey = clientKey;
        this.clientCert = clientCert;
    }

    /**
     * We'll use this from the <tt>config.jelly</tt>.
     */
    public String getName() {
        return name;
    }
    
    public String getClusterType()
{        return clusterType;
    }
    
    public String getClusterPublicIP(){
        return clusterPublicIP;
    }
    
    public String getApplicationName(){
        return applicationName;
    }
    
    public String getApplicationType(){
        return applicationType;
    }

    public String getManifestPath(){
        return manifestPath;
    }

    public String getClientKey(){
        return clientKey;
    }

    public String getClientCert(){
        return clientCert;
    }
    
    // public String getDeployType(){
    //     return deployType;
    // }
    
    // public String getAppUpgradeCheck(){
    //     return appUpgradeCheck;
    // }
    
    // public String isDeployType(String deployTypeName){
        
    //     if(deployTypeName != null && !deployTypeName.isEmpty()){
    //         return this.deployType.equalsIgnoreCase(deployTypeName) ? "true" : "";
    //     }
        
    //     else{
    //         return "";
    //     }
    // }
    
    // public String isAppUpgradeCheck(String upgradeParams){
        
    //     if(upgradeParams != null && !upgradeParams.isEmpty()){
    //         if (this.appUpgradeCheck != null){
    //             return this.appUpgradeCheck.equalsIgnoreCase(upgradeParams) ? "true" : "";
    //         }
    //         else if(upgradeParams.equalsIgnoreCase("default"))
    //             return "true";
    //     }
        
    //     return "";
    // }

    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener){


        // use the parameters to construct the commands
        SFCommandBuilder commandBuilder = new SFCommandBuilder(applicationName, applicationType, clusterPublicIP, manifestPath, clientKey, clientCert, build.getProject().getName());
        String commandString = commandBuilder.buildCommands(); 

        Shell command = new Shell(commandString);
        
        try{
            boolean status = command.perform(build, launcher, listener);
            
            if (status == false){
           	    return false;
            }
        }catch(InterruptedException e){
            return false;
        }

        return true;
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

        /**
         * Performs on-the-fly validation of the form field 'name'.
         *
         * @param value This parameter receives the value that the user has typed.
         * @return Indicates the outcome of the validation. This is sent to the browser.
         * Note that returning {@link FormValidation#error(String)} does not
         * prevent the form from being saved. It just means that a message
         * will be displayed to the user.
         */
        
        // Check to make sure that the application name begins with "fabric:/"
        public FormValidation doCheckApplicationName(@QueryParameter String value) 
                throws IOException, ServletException {
            if(value.startsWith("fabric:/"))
                return FormValidation.ok();
            else
                return FormValidation.error("Application name must begin with \"fabric:/\"");
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

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            // To persist global configuration information,
            // set that to properties and call save().
            save();
            return super.configure(req, formData);
        }

    }
}

