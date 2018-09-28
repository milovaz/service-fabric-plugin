/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */
package org.jenkinsci.plugins.serviceFabric;

import org.kohsuke.stapler.DataBoundConstructor;

import com.microsoft.jenkins.servicefabric.ServiceFabricPublishStep;
import com.microsoft.jenkins.servicefabric.util.Constants;

/**
 * Moved to {@link com.microsoft.jenkins.servicefabric.ServiceFabricPublisher}.
 * Leaving it here for legacy version migration.
 *
 * @deprecated see {@link com.microsoft.jenkins.servicefabric.ServiceFabricPublisher}
 */
@Deprecated
public class ServiceFabricPublisher {
    private final String name;
    private final String clusterType;
    private final String clusterPublicIP;
    private final String applicationName;
    private final String applicationType;
    private final String manifestPath;
    private final String clientKey;
    private final String clientCert;
    private final String environmentType;

    @DataBoundConstructor
    public ServiceFabricPublisher(String name,
                                  String clusterType,
                                  String clusterPublicIP,
                                  String applicationName,
                                  String applicationType,
                                  String manifestPath,
                                  String clientKey,
                                  String clientCert,
                                  String environmentType) {
        this.name = name;
        this.clusterType = clusterType;
        this.clusterPublicIP = clusterPublicIP;
        this.applicationName = applicationName;
        this.applicationType = applicationType;
        this.manifestPath = manifestPath;
        this.clientKey = clientKey;
        this.clientCert = clientCert;
        this.environmentType = environmentType;
    }

    /**
     * Resolve to the updated instance during deserialization.
     */
    private Object readResolve() {
        ServiceFabricPublishStep step = new ServiceFabricPublishStep();
        step.setConfigureType(Constants.CONFIGURE_TYPE_FILL);
        step.setManagementHost(clusterPublicIP);
        step.setApplicationName(applicationName);
        step.setApplicationType(applicationType);
        step.setManifestPath(manifestPath);
        step.setClientKey(clientKey);
        step.setClientCert(clientCert);
        step.setEnvironmentType(environmentType);
        com.microsoft.jenkins.servicefabric.ServiceFabricPublisher sf =
                new com.microsoft.jenkins.servicefabric.ServiceFabricPublisher(step);
        return sf;
    }

    public String getName() {
        return name;
    }

    public String getClusterType() {
        return clusterType;
    }

    public String getClusterPublicIP() {
        return clusterPublicIP;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getApplicationType() {
        return applicationType;
    }

    public String getManifestPath() {
        return manifestPath;
    }

    public String getClientKey() {
        return clientKey;
    }

    public String getClientCert() {
        return clientCert;
    }

	public String getEnvironmentType() {
		return environmentType;
	}

}
