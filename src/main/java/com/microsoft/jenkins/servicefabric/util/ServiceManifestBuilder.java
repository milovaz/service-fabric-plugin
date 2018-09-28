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
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.microsoft.jenkins.servicefabric.auth.Authenticator;

public class ServiceManifestBuilder {
	private static final Logger LOGGER = Logger.getLogger(ServiceManifestBuilder.class.getName());

	private Document serviceManifest;
	private String filePath;

	public ServiceManifestBuilder(String filePath) {
		try {
			this.filePath = filePath;
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();;
			serviceManifest = builder.parse(new InputSource(new StringReader(new String(Files.readAllBytes(Paths.get(filePath)), "UTF-8"))));
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
		String targetVersion = serviceManifest.getDocumentElement().getAttribute("Version");
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

		serviceManifest.getDocumentElement().setAttribute("Version", newVersion);
		NodeList nodeList = serviceManifest.getDocumentElement().getElementsByTagName("CodePackage");
		for (int i = 0; i < nodeList.getLength(); i++) {
			((Element) nodeList.item(i)).setAttribute("Version", newVersion);
		}
	}

	public void updateContainerImageVersion(String buildNumber) {
		XPathFactory xpf = XPathFactory.newInstance();
        XPath xpath = xpf.newXPath();
        String imageURI;
		try {
			Node imageNameNode = (Node) xpath.evaluate("/ServiceManifest/CodePackage/EntryPoint/ContainerHost/ImageName", serviceManifest, XPathConstants.NODE);
			imageURI = imageNameNode.getTextContent();

			String[] imageURIVersion = imageURI.split(":");

			if (imageURIVersion.length > 1 && (buildNumber == null || buildNumber.isEmpty())) {
				buildNumber = String.valueOf(Integer.parseInt(imageURIVersion[1]) + 1);
			}

			imageNameNode.setTextContent(imageURIVersion[0] + ":" + buildNumber);
		} catch (XPathExpressionException e) {
			LOGGER.log(Level.SEVERE, "Malformed ServiceManifet:" + e);
			throw new RuntimeException(e.getMessage());
		}

	}

	public void insertEnvironmentVariables(Authenticator authenticator) {
        try {
        	XPathFactory xpf = XPathFactory.newInstance();
            XPath xpath = xpf.newXPath();
			Node codePackageElement = (Node) xpath.evaluate("/ServiceManifest/CodePackage", serviceManifest, XPathConstants.NODE);
			Element environmentVariablesElement = serviceManifest.createElement("EnvironmentVariables");
			List<String> envList = authenticator.getLines();
			
			for(String envLine : envList) {
				String [] envLineParts = envLine.split("=");
				environmentVariablesElement.appendChild(createEnvironmentVariableElement(envLineParts[0].trim()));
			}
			
			codePackageElement.appendChild(environmentVariablesElement);
		} catch (XPathExpressionException e) {
			LOGGER.log(Level.SEVERE, "Malformed ServiceManifet:" + e);
			throw new RuntimeException(e.getMessage());
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Can't find secret file" + e);
			throw new RuntimeException(e.getMessage());
		}
	}
	
	public String joinArray(String [] arr, String separator) {
		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < arr.length; i++) {
			buffer.append((i > 0 ? separator : "") + arr[i]);
		}

		return buffer.toString();
	}

	public void saveToFile() {
		try {
			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(serviceManifest);
			StreamResult result = new StreamResult(new File(filePath));
			transformer.transform(source, result);
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		}
	}

	public String getContent() {
		try {
			return toString(serviceManifest);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private Element createEnvironmentVariableElement(String name) {
		Element environmentVariableElement = serviceManifest.createElement("EnvironmentVariable");
		environmentVariableElement.setAttribute("Name", name);
		environmentVariableElement.setAttribute("Value", "[" + name + "]");
		
		return environmentVariableElement;
	}
	
	private String toString(Document newDoc) throws Exception {
	    DOMSource domSource = new DOMSource(newDoc);
	    Transformer transformer = TransformerFactory.newInstance().newTransformer();
	    StringWriter sw = new StringWriter();
	    StreamResult sr = new StreamResult(sw);
	    transformer.transform(domSource, sr);
	    return sw.toString();
	}
}
