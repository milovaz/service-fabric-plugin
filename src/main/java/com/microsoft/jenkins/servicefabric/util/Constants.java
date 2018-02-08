package com.microsoft.jenkins.servicefabric.util;

public final class Constants {
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

    private Constants() {
        // hide constructor
    }
}
