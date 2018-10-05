package com.microsoft.jenkins.servicefabric.util;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.microsoft.jenkins.servicefabric.auth.Authenticator;


public class ApplicationManifestBuilder {

	private static final Logger LOGGER = Logger.getLogger(ApplicationManifestBuilder.class.getName());

	private Document applicationManifest;
	private String filePath;

	public ApplicationManifestBuilder(String filePath) {
		try {
			this.filePath = filePath;
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			applicationManifest = builder.parse(new InputSource(new StringReader(new String(Files.readAllBytes(Paths.get(filePath)), "UTF-8"))));
		} catch (ParserConfigurationException e) {
		    LOGGER.log(Level.SEVERE, "ParserConfigurationException:" + e);
		    throw new RuntimeException(e.getMessage());
		} catch (SAXException e) {
		    LOGGER.log(Level.SEVERE, "SAXException:" + e);
		    throw new RuntimeException(e.getMessage());
		} catch (IOException e) {
		    LOGGER.log(Level.SEVERE, "IOException:" + e);
		    throw new RuntimeException(e.getMessage());
		}
	}

	public void updateApplicationTypeVersion(int versionLabelType, String buildNumber) {
		String targetVersion = applicationManifest.getDocumentElement().getAttribute("ApplicationTypeVersion");
		String [] versionDigits = targetVersion.split("\\.");

		if (versionDigits.length > 0 && versionDigits.length - 1 < versionLabelType) {
			versionLabelType -= 1;
		}

		if (buildNumber != null && !buildNumber.isEmpty()) {
			versionDigits[versionLabelType] = buildNumber;
		} else {
			versionDigits[versionLabelType] = String.valueOf(Integer.parseInt(versionDigits[versionLabelType]) + 1);
		}
		String newVersion = joinArray(versionDigits, ".");

		applicationManifest.getDocumentElement().setAttribute("ApplicationTypeVersion", newVersion);
	}

	public void updateServiceManifestVersion(int versionLabelType, String buildNumber) {
		XPathFactory xpf = XPathFactory.newInstance();
        XPath xpath = xpf.newXPath();
		try {
			Node serviceManifestRef = (Node) xpath.evaluate("/ApplicationManifest/ServiceManifestImport/ServiceManifestRef", applicationManifest, XPathConstants.NODE);

			String targetVersion = ((Element) serviceManifestRef).getAttribute("ServiceManifestVersion");
			String [] versionDigits = targetVersion.split("\\.");

			if (versionDigits.length > 0 && versionDigits.length - 1 < versionLabelType) {
				versionLabelType -= 1;
			}

			versionDigits[versionLabelType] = buildNumber;

			String newVersion = joinArray(versionDigits, ".");

			((Element) serviceManifestRef).setAttribute("ServiceManifestVersion", newVersion);
		} catch (XPathExpressionException e) {
			LOGGER.log(Level.SEVERE, "Malformed ApplicationManifet:" + e);
			throw new RuntimeException(e.getMessage());
		}
	}

	public void insertContainerRegistryCredentials(Authenticator authenticator, String isPasswordEncrypted) {
		XPathFactory xpf = XPathFactory.newInstance();
        XPath xpath = xpf.newXPath();
		try {
			Node containerHostPolicies = (Node) xpath.evaluate("/ApplicationManifest/ServiceManifestImport/Policies/ContainerHostPolicies", applicationManifest, XPathConstants.NODE);
			
			Element repositoryCredentialsElement = applicationManifest.createElement("RepositoryCredentials");
			repositoryCredentialsElement.setAttribute("AccountName", authenticator.getUsername());
			repositoryCredentialsElement.setAttribute("Password", authenticator.getPlainPassword());
			repositoryCredentialsElement.setAttribute("PasswordEncrypted", isPasswordEncrypted);
			containerHostPolicies.appendChild(repositoryCredentialsElement);
			
			containerHostPolicies.appendChild(repositoryCredentialsElement);
		} catch (XPathExpressionException e) {
			LOGGER.log(Level.SEVERE, "Malformed ApplicationManifet:" + e);
			throw new RuntimeException(e.getMessage());
		}
	}
	
	public void insertEnvironmentVariables(Authenticator authenticator) {
		try {
			List<String> envList = authenticator.getLines();
			Element parametersElement = applicationManifest.createElement("Parameters");
			Element environmentOverridesElement = applicationManifest.createElement("EnvironmentOverrides");
			environmentOverridesElement.setAttribute("CodePackageRef", "Code");
			
			for(String envLine : envList) {
				String [] envLineParts = envLine.split("=");
				parametersElement.appendChild(createParameterElement(envLineParts[0].trim()));
				environmentOverridesElement.appendChild(createEnvironmentVariableElement(envLineParts[0].trim()));
			}
			
			parametersElement.appendChild(createParameterElement("PARAPHRASE"));
			environmentOverridesElement.appendChild(createEnvironmentVariableElement("PARAPHRASE"));
			
			applicationManifest.getDocumentElement().appendChild(parametersElement);
			
			XPathFactory xpf = XPathFactory.newInstance();
	        XPath xpath = xpf.newXPath();
	        Node serviceManifestImportElement = (Node) xpath.evaluate("/ApplicationManifest/ServiceManifestImport", applicationManifest, XPathConstants.NODE);
	        serviceManifestImportElement.appendChild(environmentOverridesElement);
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Can't find secret file" + e);
			throw new RuntimeException(e.getMessage());
		} catch (XPathExpressionException e) {
			LOGGER.log(Level.SEVERE, "Malformed ApplicationManifet:" + e);
			throw new RuntimeException(e.getMessage());
		} 
	}
	
	public String getContent() {
		try {
			return toString(applicationManifest);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	public void saveToFile() {
		try {
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(applicationManifest);
			StreamResult result = new StreamResult(new File(filePath));
			transformer.transform(source, result);
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		}
	}

	public String joinArray(String [] arr, String separator) {
		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < arr.length; i++) {
			buffer.append((i > 0 ? separator : "") + arr[i]);
		}

		return buffer.toString();
	}

	private Element createEnvironmentVariableElement(String name) {
		Element environmentVariableElement = applicationManifest.createElement("EnvironmentVariable");
		environmentVariableElement.setAttribute("Name", name);
		environmentVariableElement.setAttribute("Value", "[" + name + "]");
		
		return environmentVariableElement;
	}
	
	private Element createParameterElement(String name) {
		Element parameterElement = applicationManifest.createElement("Parameter");
		parameterElement.setAttribute("Name", name);
		parameterElement.setAttribute("DefaultValue", ""	);
		
		return parameterElement;
	}
	
	private String toString(Document newDoc) throws Exception{
	    DOMSource domSource = new DOMSource(newDoc);
	    Transformer transformer = TransformerFactory.newInstance().newTransformer();
	    StringWriter sw = new StringWriter();
	    StreamResult sr = new StreamResult(sw);
	    transformer.transform(domSource, sr);
	    return sw.toString();
	}
}
