/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.jenkins.servicefabric.util;

import com.microsoft.azure.management.Azure;
import com.microsoft.azure.util.AzureBaseCredentials;
import com.microsoft.azure.util.AzureCredentialUtil;
import com.microsoft.jenkins.azurecommons.core.AzureClientFactory;
import com.microsoft.jenkins.azurecommons.core.credentials.TokenCredentialData;
import com.microsoft.jenkins.servicefabric.AzureServiceFabricPlugin;
import hudson.model.Item;

public final class AzureHelper {
    public static TokenCredentialData getToken(Item owner, String credentialsId) {
        AzureBaseCredentials credentials = AzureCredentialUtil.getCredential(owner, credentialsId);
        if (credentials == null) {
            throw new IllegalStateException(
                    String.format("Can't find credential in scope %s with id: %s", owner, credentialsId));
        }
        return TokenCredentialData.deserialize(credentials.serializeToTokenData());
    }

    public static Azure buildClient(Item owner, String credentialsId) {
        TokenCredentialData token = getToken(owner, credentialsId);
        return buildClient(token);
    }

    public static Azure buildClient(TokenCredentialData token) {
        return AzureClientFactory.getClient(token, new AzureClientFactory.Configurer() {
            @Override
            public Azure.Configurable configure(Azure.Configurable configurable) {
                return configurable
                        .withInterceptor(new AzureServiceFabricPlugin.AzureTelemetryInterceptor())
                        .withUserAgent(AzureClientFactory.getUserAgent(Constants.PLUGIN_NAME,
                                AzureHelper.class.getPackage().getImplementationVersion()));
            }
        });
    }

    private AzureHelper() {
        // hide constructor
    }
}
