/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fish.payara.micro;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 *
 * @author daniel
 */
public class ConfigOptions implements java.io.Serializable {

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public int getSslPort() {
        return sslPort;
    }

    public void setSslPort(int sslPort) {
        this.sslPort = sslPort;
    }

    public int getMaxHttpThreads() {
        return maxHttpThreads;
    }

    public void setMaxHttpThreads(int maxHttpThreads) {
        this.maxHttpThreads = maxHttpThreads;
    }

    public int getMinHttpThreads() {
        return minHttpThreads;
    }

    public void setMinHttpThreads(int minHttpThreads) {
        this.minHttpThreads = minHttpThreads;
    }

    public String getHzClusterName() {
        return hzClusterName;
    }

    public void setHzClusterName(String hzClusterName) {
        this.hzClusterName = hzClusterName;
    }

    public String getHzMulticastGroup() {
        return hzMulticastGroup;
    }

    public void setHzMulticastGroup(String hzMulticastGroup) {
        this.hzMulticastGroup = hzMulticastGroup;
    }

    public String getHzClusterPassword() {
        return hzClusterPassword;
    }

    public void setHzClusterPassword(String hzClusterPassword) {
        this.hzClusterPassword = hzClusterPassword;
    }

    public int getHzPort() {
        return hzPort;
    }

    public void setHzPort(int hzPort) {
        this.hzPort = hzPort;
    }

    public int getHzStartPort() {
        return hzStartPort;
    }

    public void setHzStartPort(int hzStartPort) {
        this.hzStartPort = hzStartPort;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public File getDeploymentRoot() {
        return deploymentRoot;
    }

    public void setDeploymentRoot(File deploymentRoot) {
        this.deploymentRoot = deploymentRoot;
    }

    public File getRootDir() {
        return rootDir;
    }

    public void setRootDir(File rootDir) {
        this.rootDir = rootDir;
    }

    public List<File> getDeployments() {
        return deployments;
    }

    public void setDeployments(List<File> deployments) {
        this.deployments = deployments;
    }
    
    public void addDeployment(File file) {
        this.deployments.add(file);
    }
    
    public File getAlternateDomainXML() {
        return alternateDomainXML;
    }

    public void setAlternateDomainXML(File alternateDomainXML) {
        this.alternateDomainXML = alternateDomainXML;
    }

    public boolean isNoCluster() {
        return noCluster;
    }

    public void setNoCluster(boolean noCluster) {
        this.noCluster = noCluster;
    }

    public boolean isLiteMember() {
        return liteMember;
    }

    public void setLiteMember(boolean liteMember) {
        this.liteMember = liteMember;
    }

    public URI getAlternateHZConfigFile() {
        return alternateHZConfigFile;
    }

    public void setAlternateHZConfigFile(URI alternateHZConfigFile) {
        this.alternateHZConfigFile = alternateHZConfigFile;
    }

    public boolean isAutoBindHttp() {
        return autoBindHttp;
    }

    public void setAutoBindHttp(boolean autoBindHttp) {
        this.autoBindHttp = autoBindHttp;
    }

    public boolean isAutoBindSsl() {
        return autoBindSsl;
    }

    public void setAutoBindSsl(boolean autoBindSsl) {
        this.autoBindSsl = autoBindSsl;
    }

    public int getAutoBindRange() {
        return autoBindRange;
    }

    public void setAutoBindRange(int autoBindRange) {
        this.autoBindRange = autoBindRange;
    }

    public boolean isEnableHealthCheck() {
        return enableHealthCheck;
    }

    public void setEnableHealthCheck(boolean enableHealthCheck) {
        this.enableHealthCheck = enableHealthCheck;
    }

    public List<String> getGAVs() {
        return GAVs;
    }

    public void setGAVs(List<String> GAVs) {
        this.GAVs = GAVs;
    }
    
    public void addGAVs(String GAV) {
        this.GAVs.add(GAV);
    }

    public List<URL> getRepositoryURLs() {
        return repositoryURLs;
    }

    public void setRepositoryURLs(List<URL> repositoryURLs) {
        this.repositoryURLs = repositoryURLs;
    }
    
    public void addRepositoryURL(URL url) {
        this.repositoryURLs.add(url);
    }
    
    public File getUberJar() {
        return uberJar;
    }

    public void setUberJar(File uberJar) {
        this.uberJar = uberJar;
    }

    public Properties getUserSystemProperties() {
        return userSystemProperties;
    }

    public void setUserSystemProperties(Properties userSystemProperties) {
        this.userSystemProperties = userSystemProperties;
    }

    public boolean isDisablePhoneHome() {
        return disablePhoneHome;
    }

    public void setDisablePhoneHome(boolean disablePhoneHome) {
        this.disablePhoneHome = disablePhoneHome;
    }

    public boolean isGenerateLogo() {
        return generateLogo;
    }

    public void setGenerateLogo(boolean generateLogo) {
        this.generateLogo = generateLogo;
    }

    public String getAccessLogFormat() {
        return accessLogFormat;
    }

    public void setAccessLogFormat(String accessLogFormat) {
        this.accessLogFormat = accessLogFormat;
    }

    public File getAccessLogFile() {
        return accessLogFile;
    }

    public void setAccessLogFile(File accessLogFile) {
        this.accessLogFile = accessLogFile;
    }

    public boolean getLogPropertiesFile() {
        return logPropertiesFile;
    }

    public void setLogPropertiesFile(File fileName) {
        System.setProperty("java.util.logging.config.file", fileName.getAbsolutePath());
        this.logPropertiesFile = true;
        this.userLogPropertiesFile = fileName.getAbsolutePath();
    }
    
    public String getUserLogPropertiesFile() {
        return userLogPropertiesFile;
    }

    public void setUserLogPropertiesFile(String userLogPropertiesFile) {
        this.userLogPropertiesFile = userLogPropertiesFile;
    }
    
    public void setBooleanLogPropertiesFile(boolean logPropertiesFile) {
        this.logPropertiesFile = logPropertiesFile;
    }
    
    
    private int httpPort = Integer.MIN_VALUE; //
    private int sslPort = Integer.MIN_VALUE; //
    private int maxHttpThreads = Integer.MIN_VALUE; //
    private int minHttpThreads = Integer.MIN_VALUE; //
    private String hzClusterName; //
    private String hzMulticastGroup; //
    private String hzClusterPassword; //
    private int hzPort = Integer.MIN_VALUE;
    private int hzStartPort = Integer.MIN_VALUE;
    private String instanceName = UUID.randomUUID().toString(); //
    private File deploymentRoot; //
    private File rootDir; //
    private List<File> deployments; //
    private File alternateDomainXML; //
    private boolean noCluster = false;//
    private boolean liteMember = false;//
    private URI alternateHZConfigFile; //
    private boolean autoBindHttp = false;//
    private boolean autoBindSsl = false;//
    private int autoBindRange = 5;//
    private boolean enableHealthCheck = false;//
    private List<String> GAVs; //
    private List<URL> repositoryURLs;//
    private File uberJar;//
    private Properties userSystemProperties;//
    private boolean disablePhoneHome = false;//
    private boolean generateLogo = false;//
    private String accessLogFormat = "%client.name% %auth-user-name% %datetime% %request% %status% %response.length%";
    private File accessLogFile;
    private boolean logPropertiesFile;
    private String userLogPropertiesFile = "";

}
