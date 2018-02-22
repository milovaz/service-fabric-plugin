/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.jenkins.servicefabric.util;

public final class Constants {
    public static final String PLUGIN_NAME = "AzureJenkinsServiceFabric";

    /**
     * Select the Service Fabric management endpoint.
     */
    public static final String CONFIGURE_TYPE_SELECT = "select";
    /**
     * Fill the Service Fabric management endpoint directly.
     */
    public static final String CONFIGURE_TYPE_FILL = "fill";

    public static final String SF_PROVIDER = "Microsoft.ServiceFabric";
    public static final String SF_CLUSTER_TYPE = "clusters";

    // AI Constants
    public static final String AI_SERVICE_FABRIC = "ServiceFabric";
    public static final String AI_RUN = "Run";

    private Constants() {
        // hide constructor
    }
}
