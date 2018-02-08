/*

 * Copyright (c) Microsoft Corporation. All rights reserved.

 * Licensed under the MIT License. See LICENSE in the project root for

 * license information.

 */
package org.jenkinsci.plugins.serviceFabric.ServiceFabricCommands;

import hudson.FilePath;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SFCommandBuilder {
    private static final Logger LOGGER = Logger.getLogger(SFCommandBuilder.class.getName());

    // declare the command templates
    private static final String SF_CONNECT =
            "sfctl cluster select --endpoint http://{clusterIP}:19080";
    private static final String SF_SECURE_CONNECT =
            "sfctl cluster select --endpoint https://{clusterIP}:19080 "
                    + "--key {clientKey} --cert {clientCert} --no-verify";
    private static final String SF_COPY =
            "sfctl application upload --path {appName} --show-progress";
    private static final String SF_REGISTER_TYPE =
            "sfctl application provision --application-type-build-path {appName}";
    private static final String SF_APPLICATION_CREATE =
            "sfctl application create --app-name {appName} --app-type {appType} --app-version {appVersion}";
    private static final String SF_APPLICATION_UPGRADE =
            "sfctl application upgrade --app-id {appName} --app-version {appVersion} --parameters [] --mode Monitored";
    private static final String SF_APPLICATION_REMOVE =
            "sfctl application delete --application-id {appId}";
    private static final String SF_APPLICATION_UNREGISTER =
            "sfctl application unprovision --application-type-name {appType} --application-type-version {appVersion}";

    private FilePath workspace;
    private String appName;
    private String appType;
    private String clusterIP;
    private String manifestPath;
    private String clientKey;
    private String clientCert;

    public SFCommandBuilder(FilePath workspace,
                            String applicationName,
                            String applicationType,
                            String clusterIP,
                            String manifestPath,
                            String clientKey,
                            String clientCert) {
        this.workspace = workspace;
        this.appName = applicationName;
        this.appType = applicationType;
        this.clusterIP = clusterIP;
        this.manifestPath = manifestPath;
        this.clientKey = clientKey;
        this.clientCert = clientCert;
    }

    /**
     * Build and return the output command.
     */
    public String buildCommands() {

        String appId = getAppIdFromName(appName);
        String targetVersion = checkTargetApplicationManifestVersion(workspace, manifestPath);

        // start building the command. note that since Jenkins resets its
        // location after each command,
        // we put everything into one big command in order to avoid having to
        // move locations over and over
        String outputCommand = "";

        // start by connecting to the cluster -- see if this is a secure cluster
        // or not
        if (isSecureCluster()) {
            // implies this is a secure cluster
            outputCommand += SF_SECURE_CONNECT.replace("{clusterIP}", clusterIP).replace("{clientKey}", clientKey)
                    .replace("{clientCert}", clientCert);
        } else {
            // non-secure cluster
            outputCommand += SF_CONNECT.replace("{clusterIP}", clusterIP);
        }

        outputCommand += " && " + createCheckCleanCommand(appId, appType, targetVersion);

        // make the command: move into the application package folder
        // Getting application path from the appilcation-manifest path input in
        // Jenkins portal
        String tmpString = manifestPath.substring(0, manifestPath.lastIndexOf('/', manifestPath.length() - 1));
        String applicationPath = tmpString.substring(0, tmpString.lastIndexOf('/', tmpString.length() - 1));
        outputCommand += "&& cd " + applicationPath;

        // add on the different commands: copy -> register -> create
        outputCommand += " && " + (SF_COPY.replace("{appName}", appName.replace("fabric:/", "")));
        outputCommand += " && " + (SF_REGISTER_TYPE.replace("{appName}", appName.replace("fabric:/", "")));

        outputCommand += " && " + createUpgradeOrInstallCommand(appId, appName, appType, targetVersion);

        LOGGER.info("Command to be run:" + outputCommand);
        return outputCommand;
    }

    private String getAppIdFromName(String name) {
        return name.substring(name.indexOf(":/") + 2);
    }

    private boolean isSecureCluster() {
        return !this.clientKey.isEmpty() && !this.clientCert.isEmpty();
    }

    private String createCheckCleanCommand(String appId, String type, String appVersion) {
        String checkUninstall =
                "if [ `sfctl application info --application-id {appId} | wc -l` != 0 ]; "
                        + "then "
                        + "if [ `sfctl application info --application-id {appId} | grep {appVersion} | wc -l` == 1 ]; "
                        + "then "
                        + SF_APPLICATION_REMOVE + " && " + SF_APPLICATION_UNREGISTER + "; "
                        + "fi; "
                        + "fi";
        return checkUninstall.replace("{appId}", appId).replace("{appType}", type).replace("{appVersion}",
                appVersion);
    }

    private String createUpgradeOrInstallCommand(String appId, String name, String type, String appVersion) {
        String upgradeOrInstallCommand =
                "if [ `sfctl application info --application-id {appId} | wc -l` != 0 ]; "
                        + "then "
                        + "if [ `sfctl application info --application-id {appId} | grep {appVersion} | wc -l` == 0 ]; "
                        + "then "
                        + SF_APPLICATION_UPGRADE + "; "
                        + "fi; "
                        + "else " + SF_APPLICATION_CREATE + "; "
                        + "fi";
        return upgradeOrInstallCommand.replace("{appId}", appId).replace("{appType}", type)
                .replace("{appVersion}", appVersion).replace("{appName}", name);
    }

    private String checkTargetApplicationManifestVersion(FilePath ws, String filePath) {

        String targetVersion,
                newFilePath = workspace.child(filePath).getRemote();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        Document applicationManifest;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            LOGGER.log(Level.SEVERE, "ParserConfigurationException:" + e);
            throw new RuntimeException(e.getMessage());
        }
        try {
            applicationManifest = builder.parse(new File(newFilePath));
        } catch (SAXException e) {
            LOGGER.log(Level.SEVERE, "SAXException:" + e);
            throw new RuntimeException(e.getMessage());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "IOException:" + e);
            throw new RuntimeException(e.getMessage());
        }
        targetVersion = applicationManifest.getDocumentElement().getAttribute("ApplicationTypeVersion");
        return targetVersion;

    }

}
