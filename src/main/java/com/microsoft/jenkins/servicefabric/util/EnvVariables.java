package com.microsoft.jenkins.servicefabric.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.List;
import org.jenkinsci.plugins.durabletask.BourneShellScript;
import org.jenkinsci.plugins.durabletask.Controller;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import com.microsoft.jenkins.servicefabric.auth.Authenticator;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Shell;

public class EnvVariables {
	
	public String generateEncryptedEnvVariables(Authenticator authenticator, StepContext context, Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
		List<String> envList = authenticator.getLines();
		File tempDir = Files.createTempDirectory("crp").toFile();
		String tempFilePath = tempDir.getPath();
		String fileContentPath = tempFilePath + "/content.enc";
		StringBuffer commandBuff = null;
		StringBuffer paramsBuff = null;
		BufferedReader br = null;
		InputStreamReader in = null;
		
		try {
			commandBuff = new StringBuffer();
			commandBuff.append(keyGenCommand(tempFilePath) + " && ");
			
			for(String envLine : envList) {
				String [] envLineParts = envLine.split("=");
				commandBuff.append("encvar=$(echo " + envLineParts[1].trim() + " | openssl enc -aes-256-cbc -a -kfile " + tempFilePath + "/keyfile) && "
								   + "echo " + envLineParts[0].trim() + "=${encvar} >> " + tempFilePath + "/content.enc;");			
			}
			
			runCommand(commandBuff.toString(), context, run, workspace, launcher, listener);
			
			in = new InputStreamReader(new FileInputStream(fileContentPath), "UTF-8");
			br = new BufferedReader(in);
			
			String sCurrentLine;
			paramsBuff = new StringBuffer();
	
			while ((sCurrentLine = br.readLine()) != null) {
				if(!sCurrentLine.isEmpty()) {
	    			String [] envLineParts = sCurrentLine.split("=");
	    			if(paramsBuff.length() > 0) {
	    				paramsBuff.append(",");
	    			}
	    			paramsBuff.append("\"" + envLineParts[0].trim() + "\":\"" + envLineParts[1].trim().replaceAll("\"", "") + "\"");	
		    	}
			}
			
			paramsBuff.append(",\"PARAPHRASE\":\"" + getGeneratedKey(tempFilePath + "/keyfile.enc.b64", true) + "\"");
			
		} finally {
			deleteFile(tempDir);
			
			if(br != null) {
				br.close();
			}
			
			if(in != null) {
				in.close();
			}
		}
		
		return "'{" + paramsBuff.toString() + "}'";
	}
	
	private String keyGenCommand(String tempFilePath) {
		String keyPath = "";
		if(EnvVars.masterEnvVars.get("JENKINS_HOME") == null) {
			keyPath = EnvVars.masterEnvVars.get("HOME") != null ? EnvVars.masterEnvVars.get("HOME") : "/home/milovaz";
		}
		
		keyPath += "/key.pub";
		
		String commandString = "openssl rand 32 -out " + tempFilePath + "/keyfile -base64 && "
										 + "openssl rsautl -encrypt -pubin -inkey " + keyPath + " -in " + tempFilePath + "/keyfile -out " + tempFilePath + "/keyfile.enc && "	
									     + "openssl enc -A -base64 -in " + tempFilePath + "/keyfile.enc -out " + tempFilePath + "/keyfile.enc.b64";	
		
		return commandString;
	}
	
	private String getGeneratedKey(String filePath, boolean base64) throws IOException {
		 byte[] bytes = Files.readAllBytes(new File(filePath).toPath());
         return new String(bytes,"UTF-8").trim();
	}
	
	private void runCommand(String commandString, StepContext context, Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
		if (run instanceof AbstractBuild) {
            Shell command = new Shell(commandString);

            boolean result = command.perform((AbstractBuild) run, launcher, listener);
            if (!result) {
                throw new AbortException("Shell script execution failed");
            }
        } else if (context != null) {
        	BourneShellScript shellScript = new BourneShellScript(commandString);
            Controller controller = shellScript.launch(context.get(EnvVars.class), workspace, launcher, listener);

            long startTime = System.currentTimeMillis();
            AbortException exp = null;
            try {
                final long minInterval = 250;
                final long maxInterval = 5000;
                final long timeoutMinutes = 5;
                final long timeout = timeoutMinutes * 60 * 1000;
                while (true) {
                    long waitInterval;
                    if (controller.writeLog(workspace, listener.getLogger())) {
                        // got output, maybe we will get more
                        waitInterval = minInterval;
                    } else {
                        waitInterval = maxInterval;
                    }

                    Integer exitCode = controller.exitStatus(workspace, launcher);
                    if (exitCode == null) {
                        if (System.currentTimeMillis() - startTime > timeout) {
                            controller.stop(workspace, launcher);
                            exp = new AbortException("Script execution timeout after " + timeoutMinutes + " minutes");
                            break;
                        }
                        Thread.sleep(waitInterval);
                    } else {
                        controller.writeLog(workspace, listener.getLogger());
                        if (exitCode != 0) {
                            exp = new AbortException("script returned exit code " + exitCode);
                        }
                        break;
                    }
                }
            } finally {
                controller.cleanup(workspace);
            }
            if (exp != null) {
                throw exp;
            }
        }
	}

	private void deleteFile(File file) throws IOException {
		if(file != null) {
			File[] fileList = file.listFiles();
			if(fileList != null && fileList.length > 0) {
				for(File f : fileList) {
					if(file.isDirectory()) {
						deleteFile(f);
					} else {
						if(!file.delete()) {
							throw new IOException("Can't exclude temporary cripto file");
						}
					}
				}
			}
			if(!file.delete()) {
				throw new IOException("Can't exclude temporary cripto file");
			}
		}
	}
}
