package org.jenkinsci.plugins.serviceFabric;

import org.kohsuke.stapler.DataBoundConstructor;

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

    @DataBoundConstructor
    public ServiceFabricPublisher(String name,
                                  String clusterType,
                                  String clusterPublicIP,
                                  String applicationName,
                                  String applicationType,
                                  String manifestPath,
                                  String clientKey,
                                  String clientCert) {
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
     * Resolve to the updated instance during deserialization.
     */
    private Object readResolve() {
        return new com.microsoft.jenkins.servicefabric.ServiceFabricPublisher(
                clusterType,
                clusterPublicIP,
                applicationName,
                applicationType,
                manifestPath,
                clientKey,
                clientCert);
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
}
