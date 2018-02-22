/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.jenkins.servicefabric;

import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.resources.GenericResource;
import com.microsoft.azure.management.resources.fluentcore.arm.ResourceUtils;
import com.microsoft.jenkins.servicefabric.util.Constants;
import com.microsoft.jenkins.servicefabric.util.DeployHelper;

/**
 * Manages Service Fabric cluster with Azure generic resource API.
 * <p>
 * TODO: Use Service Fabric specific API when it's supported in Azure SDK
 */
public class ServiceFabricCluster {
    private final Azure azure;
    private final String resourceGroup;
    private final String name;

    private Object properties;
    private boolean loaded;

    public ServiceFabricCluster(Azure azure, String resourceGroup, String name) {
        this.azure = azure;
        this.resourceGroup = resourceGroup;
        this.name = name;
    }

    public void load(boolean force) {
        if (!force && loaded) {
            return;
        }

        String id = ResourceUtils.constructResourceId(
                azure.subscriptionId(),
                resourceGroup,
                Constants.SF_PROVIDER,
                Constants.SF_CLUSTER_TYPE,
                name,
                "");
        GenericResource resource = azure.genericResources().getById(id);
        if (resource != null) {
            this.properties = resource.properties();
        } else {
            throw new IllegalArgumentException(
                    String.format("Cannot find Service Fabric cluster %s in resource group %s",
                            name, resourceGroup));
        }
        loaded = true;
    }

    public String getResourceGroup() {
        return resourceGroup;
    }

    public String getName() {
        return name;
    }

    public String getManagementEndpoint() {
        load(false);

        return DeployHelper.getProperty(properties, "managementEndpoint", String.class);
    }
}
