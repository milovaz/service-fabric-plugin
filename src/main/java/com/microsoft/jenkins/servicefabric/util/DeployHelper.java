/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See LICENSE in the project root for
 * license information.
 */

package com.microsoft.jenkins.servicefabric.util;

import org.apache.commons.lang.StringUtils;

import java.util.Map;

public final class DeployHelper {
    public static <T> T getProperty(Object properties, String path, Class<T> type) {
        return getProperty(properties, "", path, type);
    }

    private static <T> T getProperty(Object properties, String visited, String remain, Class<T> type) {
        if (StringUtils.isBlank(remain)) {
            return type.cast(properties);
        }
        if (properties == null || !(properties instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(visited + " is not a map: " + properties);
        }
        Map<?, ?> map = (Map<?, ?>) properties;
        String[] parts = remain.split("\\.", 2);
        if (parts.length == 2) {
            remain = parts[1];
        } else {
            remain = "";
        }
        visited = StringUtils.isEmpty(visited) ? parts[0] : (visited + "." + parts[0]);
        return getProperty(map.get(parts[0]), visited, remain, type);
    }

    private DeployHelper() {
        // hide constructor
    }
}
