/*

 * Copyright (c) Microsoft Corporation. All rights reserved.

 * Licensed under the MIT License. See LICENSE in the project root for

 * license information.

 */
package org.jenkinsci.plugins.serviceFabric.ServiceFabricCommands;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class SFCommandBuilder {
    private static final Logger logger = Logger.getLogger(SFCommandBuilder.class.getName());

    // declare the command templates
    private String sfConnect = "sfctl cluster select --endpoint http://{clusterIP}:19080";
    private String sfSecureConnect = "sfctl cluster select --endpoint https://{clusterIP}:19080 --key {clientKey} --cert {clientCert} --no-verify";
    private String sfCopy = "sfctl application upload --path {appName} --show-progress";
    private String sfRegisterType = "sfctl application provision --application-type-build-path {appName}";
    private String sfApplicationCreate = "sfctl application create --app-name {appName} --app-type {appType} --app-version {appVersion}";
    private String sfApplicationUpgrade = "sfctl application upgrade --app-id {appName} --app-version {appVersion} --parameters [] --mode Monitored";
    private String sfApplicationRemove = "sfctl application delete --application-id {appId}";
    private String sfApplicationUnregister = "sfctl application unprovision --application-type-name {appType} --application-type-version {appVersion}";

    private String appName;
    private String appType;
    private String clusterIP;
    private String manifestPath;
    private String projectName;
    private String clientKey;
    private String clientCert;

    // constructor for the class
    public SFCommandBuilder(String name, String type, String cIP, String mPath, String cKey, String cCert,
            String pName) {

        // set the values
        this.appName = name;
        this.appType = type;
        this.clusterIP = cIP;
        this.manifestPath = mPath;
        this.projectName = pName;
        this.clientKey = cKey;
        this.clientCert = cCert;
    }

    // build and return the output command
    public String buildCommands() {

        String appId = getAppIdFromName(appName);
        String targetVersion = checkTargetApplicationManifestVersion(projectName, manifestPath);

        // start building the command. note that since Jenkins resets its
        // location after each command,
        // we put everything into one big command in order to avoid having to
        // move locations over and over
        String outputCommand = "";

        // start by connecting to the cluster -- see if this is a secure cluster
        // or not
        if (isSecureCluster() == true) {
            // implies this is a secure cluster
            outputCommand += sfSecureConnect.replace("{clusterIP}", clusterIP).replace("{clientKey}", clientKey)
                    .replace("{clientCert}", clientCert);
        } else {
            // non-secure cluster
            outputCommand += sfConnect.replace("{clusterIP}", clusterIP);
        }

        outputCommand += " && " + createCheckCleanCommand(appId, appType, targetVersion);

        // make the command: move into the application package folder
        // Getting application path from the appilcation-manifest path input in
        // Jenkins portal
        String tmpString = manifestPath.substring(0, manifestPath.lastIndexOf('/', manifestPath.length() - 1));
        String applicationPath = tmpString.substring(0, tmpString.lastIndexOf('/', tmpString.length() - 1));
        outputCommand += "&& cd " + applicationPath;

        // add on the different commands: copy -> register -> create
        outputCommand += " && " + (sfCopy.replace("{appName}", appName.replace("fabric:/", "")));
        outputCommand += " && " + (sfRegisterType.replace("{appName}", appName.replace("fabric:/", "")));

        outputCommand += " && " + createUpgradeOrInstallCommand(appId, appName, appType, targetVersion);

        logger.info("Command to be run:" + outputCommand);
        return outputCommand;
    }

    private String getAppIdFromName(String appName) {
        return appName.substring(appName.indexOf(":/") + 2);
    }

    private boolean isSecureCluster() {
        if (!this.clientKey.isEmpty() && !this.clientCert.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    private String createCheckCleanCommand(String appId, String appType, String appVersion) {
        String checkUninstall = "if [ `sfctl application info --application-id {appId} | wc -l` != 0 ]; then if [ `sfctl application info --application-id {appId} | grep {appVersion} | wc -l` == 1 ]; then "
                + sfApplicationRemove + " && " + sfApplicationUnregister + "; fi; fi";
        return checkUninstall.replace("{appId}", appId).replace("{appType}", appType).replace("{appVersion}",
                appVersion);
    }

    private String createUpgradeOrInstallCommand(String appId, String appName, String appType, String appVersion) {
        String upgradeOrInstallCommand = "if [ `sfctl application info --application-id {appId} | wc -l` != 0 ]; then if [ `sfctl application info --application-id {appId} | grep {appVersion} | wc -l` == 0 ]; then "
                + sfApplicationUpgrade + "; fi; else " + sfApplicationCreate + "; fi";
        return upgradeOrInstallCommand.replace("{appId}", appId).replace("{appType}", appType)
                .replace("{appVersion}", appVersion).replace("{appName}", appName);
    }

    private String checkTargetApplicationManifestVersion(String pName, String filePath) {

        String targetVersion = null,
                newFilePath = System.getenv("JENKINS_HOME") + "/workspace/" + pName + "/" + filePath;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        Document applicationManifest = null;
        try {
            builder = factory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            logger.log(Level.SEVERE, "ParserConfigurationException:" + e);
            throw new RuntimeException(e.getMessage());
        }
        try {
            applicationManifest = builder.parse(new File(newFilePath));
        } catch (SAXException e) {
            logger.log(Level.SEVERE, "SAXException:" + e);
            throw new RuntimeException(e.getMessage());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "IOException:" + e);
            throw new RuntimeException(e.getMessage());
        }
        targetVersion = applicationManifest.getDocumentElement().getAttribute("ApplicationTypeVersion");
        return targetVersion;

    }

}
