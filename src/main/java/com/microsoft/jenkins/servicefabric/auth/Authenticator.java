package com.microsoft.jenkins.servicefabric.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jenkinsci.plugins.plaincredentials.FileCredentials;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.model.Run;

public class Authenticator {

	private StandardUsernamePasswordCredentials usernamePasswordCredential;
	private FileCredentials fileCredentials;

	public Authenticator() {

	}

	public Authenticator(String credentialsId, Run<?, ?> run, String type) {
		if(type.equalsIgnoreCase("usernamePassword")) {
			this.usernamePasswordCredential = CredentialsProvider.findCredentialById(credentialsId, StandardUsernamePasswordCredentials.class, run, Collections.<DomainRequirement>emptyList());
		} else if(type.equalsIgnoreCase("file")) {
			this.fileCredentials = CredentialsProvider.findCredentialById(credentialsId, FileCredentials.class, run, Collections.<DomainRequirement>emptyList());
		}
	}

	public String getPlainPassword() {
		return this.usernamePasswordCredential != null && this.usernamePasswordCredential.getPassword() != null ? this.usernamePasswordCredential.getPassword().getPlainText() : null;
	}

	public String getUsername() {
		return this.usernamePasswordCredential != null ? this.usernamePasswordCredential.getUsername() : null;
	}
	
	public List<String> getLines() throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(this.fileCredentials.getContent()));
		String envLine = "";
		List<String> envList = new ArrayList<>();
		while ((envLine = reader.readLine()) != null) {    
		    envList.add(envLine);
		}
		
		return envList; 
	}
}
