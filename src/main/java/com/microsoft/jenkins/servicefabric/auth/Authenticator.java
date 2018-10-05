package com.microsoft.jenkins.servicefabric.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.Run;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import jnr.ffi.annotations.In;

public class Authenticator {

	private StandardUsernamePasswordCredentials usernamePasswordCredential;
	private FileCredentials fileCredentials;

	public Authenticator() {

	}

	public Authenticator(String credentialsId, Run<?, ?> run, String type, String matchType) {
		if(type.equalsIgnoreCase("usernamePassword")) {
			this.usernamePasswordCredential = CredentialsProvider.findCredentialById(credentialsId, StandardUsernamePasswordCredentials.class, run, Collections.<DomainRequirement>emptyList());
		} else if(type.equalsIgnoreCase("file")) {
			if(matchType.equalsIgnoreCase("equals")) {
				this.fileCredentials = CredentialsProvider.findCredentialById(credentialsId, FileCredentials.class, run, Collections.<DomainRequirement>emptyList());
			} else {
				this.fileCredentials = findFileCredentials(credentialsId, matchType);
			}
		}
	}

	private FileCredentials findFileCredentials(String credentialsId, String matchType) {
		List<FileCredentials> credentialsList = CredentialsProvider.lookupCredentials(FileCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, Collections.<DomainRequirement> emptyList());
		if(credentialsList != null && !credentialsList.isEmpty()) {
			for(FileCredentials fc : credentialsList) {
				if(matchType.equalsIgnoreCase("lookup-regex") && fc.getId().matches(credentialsId)) {
					return fc;
				} else if(matchType.equalsIgnoreCase("lookup-contains") && fc.getId().contains(matchType)) {
					return fc;
				}
			}
		}
		
		return null;
	}
	
	public String getPlainPassword() {
		return this.usernamePasswordCredential != null && this.usernamePasswordCredential.getPassword() != null ? this.usernamePasswordCredential.getPassword().getPlainText() : null;
	}

	public String getUsername() {
		return this.usernamePasswordCredential != null ? this.usernamePasswordCredential.getUsername() : null;
	}
	
	public FileCredentials getFile() {
		return this.fileCredentials;
	}
	
	public boolean fileExists() {
		return this.fileCredentials != null;
	}
	
	public List<String> getLines() throws IOException {
		InputStreamReader in = null;
		BufferedReader reader = null;
		if(this.fileExists()) {
			try {
				in = new InputStreamReader(this.fileCredentials.getContent(), "UTF-8");
				reader = new BufferedReader(in);
				String envLine = "";
				List<String> envList = new ArrayList<>();
				while ((envLine = reader.readLine()) != null) {    
				    envList.add(envLine);
				}
				
				return envList;
			} finally {
				if(reader != null) {
					reader.close();
				}
				
				if(in != null) {
					in.close();
				}
			}
		}
		
		return null;
	}
}
