/*

 DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.

 Copyright (c) 2016 Payara Foundation and/or its affiliates.
 All rights reserved.

 The contents of this file are subject to the terms of the Common Development
 and Distribution License("CDDL") (collectively, the "License").  You
 may not use this file except in compliance with the License.  You can
 obtain a copy of the License at
 https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 or packager/legal/LICENSE.txt.  See the License for the specific
 language governing permissions and limitations under the License.

 When distributing the software, include this License Header Notice in each
 file and include the License file at packager/legal/LICENSE.txt.
 */
package fish.payara.micro;

import static com.sun.enterprise.glassfish.bootstrap.StaticGlassFishRuntime.copy;
import fish.payara.nucleus.hazelcast.HazelcastCore;
import fish.payara.nucleus.hazelcast.MulticastConfiguration;
import fish.payara.nucleus.phonehome.PhoneHomeCore;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import fish.payara.nucleus.healthcheck.HealthCheckService;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.net.JarURLConnection;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.glassfish.embeddable.BootstrapProperties;
import org.glassfish.embeddable.Deployer;
import org.glassfish.embeddable.GlassFish;
import org.glassfish.embeddable.GlassFish.Status;
import org.glassfish.embeddable.GlassFishException;
import org.glassfish.embeddable.GlassFishProperties;
import org.glassfish.embeddable.GlassFishRuntime;
import com.sun.appserv.server.util.Version;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * Main class for Bootstrapping Payara Micro Edition This class is used from
 * applications to create a full JavaEE runtime environment and deploy war
 * files.
 *
 * This class is used to configure and bootstrap a Payara Micro Runtime.
 *
 * @author steve
 */
public class PayaraMicro {

    private static final Logger logger = Logger.getLogger("PayaraMicro");
    private static PayaraMicro instance;
    private String instanceName = UUID.randomUUID().toString();
    private GlassFish gf;
    private PayaraMicroRuntime runtime;
    private boolean logToFile = false;
    private boolean enableAccessLog = false;
    private boolean enableAccessLogFormat = false;
    private boolean logPropertiesFile = false;
    private String bootImage = "boot.txt";
    private String applicationDomainXml;
    private Map<String, URL> deploymentURLsMap;
    private final String defaultMavenRepository = "https://repo.maven.apache.org/maven2/";
    private final short defaultHttpPort = 8080;
    private final short defaultHttpsPort = 8181;
    private String loggingPropertiesFileName = "logging.properties";
    private String loggingToFilePropertiesFileName = "loggingToFile.properties";
    private String domainFileName = "domain.xml";
    private String userLogFile = "payara-server%u.log";
    private String userAccessLogDirectory = "";
    private String userPropertiesFileName = "";
    
    private static ConfigOptions cfgOpts = new ConfigOptions();
    private static ArgumentParser argparser;

    /**
     * Runs a Payara Micro server used via java -jar payara-micro.jar
     *
     * @param args Command line arguments for PayaraMicro Usage: --noCluster
     * Disables clustering<br/>
     * --port sets the http port<br/>
     * --sslPort sets the https port number<br/>
     * --mcAddress sets the cluster multicast group<br/>
     * --mcPort sets the cluster multicast port<br/>
     * --startPort sets the cluster start port number<br/>
     * --name sets the instance name<br/>
     * --rootDir Sets the root configuration directory and saves the
     * configuration across restarts<br/>
     * --deploymentDir if set to a valid directory all war files in this
     * directory will be deployed<br/>
     * --deploy specifies a war file to deploy<br/>
     * --domainConfig overrides the complete server configuration with an
     * alternative domain.xml file<br/>
     * --minHttpThreads the minimum number of threads in the HTTP thread
     * pool<br/>
     * --maxHttpThreads the maximum number of threads in the HTTP thread
     * pool<br/>
     * --lite Sets this Payara Micro to not store Cluster Data<br/>
     * --enableHealthCheck enables/disables Health Check Service<br/>
     * --disablePhomeHome disables Phone Home Service<br/>
     * --logToFile outputs all the Log entries to a user defined file<br/>
     * --accessLog Sets user defined directory path for the access log<br/>
     * --accessLogFormat Sets user defined log format for the access log<br/>
     * --logProperties Allows user to set their own logging properties file <br/>
     * --help Shows this message and exits\n
     * @throws BootstrapException If there is a problem booting the server
     */
    
    // removed static from main function -__-
    public static void main(String args[]) throws BootstrapException {
        PayaraMicro main = getInstance();
        argparser = new ArgumentParser(args,cfgOpts);
        
        if (main.getUberJar() != null) {
            main.packageUberJar();
        } else {
            main.bootStrap();
        }
    }

    /**
     * Obtains the static singleton instance of the Payara Micro Server. If it
     * does not exist it will be create.
     *
     * @return The singleton instance
     */
    public static PayaraMicro getInstance() {
        return getInstance(true);
    }

    /**
     * Bootstraps the PayaraMicroRuntime with all defaults and no additional
     * configuration. Functionally equivalent to
     * PayaraMicro.getInstance().bootstrap();
     *
     * @return
     */
    public static PayaraMicroRuntime bootstrap() throws BootstrapException {
        return getInstance().bootStrap();
    }

    /**
     *
     * @param create If false the instance won't be created if it has not been
     * initialised
     * @return null if no instance exists and create is false. Otherwise returns
     * the singleton instance
     */
    public static PayaraMicro getInstance(boolean create) {
        if (instance == null && create) {
            instance = new PayaraMicro();
        }
        return instance;
    }

    /**
     * Gets the cluster group
     *
     * @return The Multicast Group that will beused for the Hazelcast clustering
     */
    public String getClusterMulticastGroup() {
        return cfgOpts.getHzMulticastGroup();
    }

    /**
     * Sets the cluster group used for Payara Micro clustering used for cluster
     * communications and discovery. Each Payara Micro cluster should have
     * different values for the MulticastGroup
     *
     * @param hzMulticastGroup String representation of the multicast group
     * @return
     */
    public PayaraMicro setClusterMulticastGroup(String hzMulticastGroup) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        this.cfgOpts.setHzMulticastGroup(hzMulticastGroup);
        return this;
    }

    /**
     * Sets the path to the logo file printed at boot. This can be on the
     * classpath of the server or an absolute URL
     *
     * @param filePath
     * @return
     */
    public PayaraMicro setLogoFile(String filePath) {
        bootImage = filePath;
        return this;
    }

    /**
     * Set whether the logo should be generated on boot
     *
     * @param generate
     * @return
     */
    public PayaraMicro setPrintLogo(boolean generate) {
        cfgOpts.setGenerateLogo(generate);
        return this;
    }

    /**
     * Set user defined file for the Log entries
     *
     * @param fileName
     * @return
     */
    public PayaraMicro setUserLogFile(String fileName) {
        File file = new File(fileName);
        if (file.isDirectory()) {
            if (!file.exists() || !file.canWrite()) {
                logger.log(Level.SEVERE, "{0} is not a valid directory for storing logs as it must exist and be writable", file.getAbsolutePath());                            
                throw new IllegalArgumentException();
            }
            this.userLogFile = file.getAbsolutePath() + File.separator + userLogFile;
        } else {
            userLogFile = fileName;
        }
        logToFile = true;
        return this;
    }
    
    /**
     * Set user defined properties file for logging
     *
     * @param fileName
     * @return
     */
    public PayaraMicro setLogPropertiesFile(File fileName) {
        System.setProperty("java.util.logging.config.file", fileName.getAbsolutePath());
        logPropertiesFile = true;
        cfgOpts.setUserLogPropertiesFile(fileName.getAbsolutePath());
        userPropertiesFileName = fileName.getName();
        return this;
    }

    /**
     * Set user defined file directory for the access log
     *
     * @param filePath
     */
    public void setAccessLogDir(String filePath) {
        this.userAccessLogDirectory = filePath;
        enableAccessLog = true;
    }

    /**
     * Set user defined formatting for the access log
     *
     * @param format
     */
    public void setAccessLogFormat(String format) {
        this.cfgOpts.setAccessLogFormat(format);
        this.enableAccessLogFormat = true;
    }

    /**
     * Gets the cluster multicast port used for cluster communications
     *
     * @return The configured cluster port
     */
    public int getClusterPort() {
        return cfgOpts.getHzPort();
    }

    /**
     * Sets the multicast group used for Payara Micro clustering used for
     * cluster communication and discovery. Each Payara Micro cluster should
     * have different values for the cluster port
     *
     * @param hzPort The port number
     * @return
     */
    public PayaraMicro setClusterPort(int hzPort) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        cfgOpts.setHzPort(hzPort);
        return this;
    }

    /**
     * Gets the instance listen port number used by clustering. This number will
     * be incremented automatically if the port is unavailable due to another
     * instance running on the same host,
     *
     * @return The start port number
     */
    public int getClusterStartPort() {
        return cfgOpts.getHzStartPort();
    }

    /**
     * Sets the start port number for the Payara Micro to listen on for cluster
     * communications.
     *
     * @param hzStartPort Start port number
     * @return
     */
    public PayaraMicro setClusterStartPort(int hzStartPort) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        this.cfgOpts.setHzStartPort(hzStartPort);
        return this;
    }

    /**
     * The configured port Payara Micro will use for HTTP requests.
     *
     * @return The HTTP port
     */
    public int getHttpPort() {
        return cfgOpts.getHttpPort();
    }

    /**
     * Sets the port used for HTTP requests
     *
     * @param httpPort The port number
     * @return
     */
    public PayaraMicro setHttpPort(int httpPort) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        this.cfgOpts.setHttpPort(httpPort);
        return this;
    }

    /**
     * The configured port for HTTPS requests
     *
     * @return The HTTPS port
     */
    public int getSslPort() {
        return cfgOpts.getSslPort();
    }

    /**
     * The UberJar to create
     *
     * @return
     */
    public File getUberJar() {
        return cfgOpts.getUberJar();
    }

    /**
     * Sets the configured port for HTTPS requests. If this is not set HTTPS is
     * disabled
     *
     * @param sslPort The HTTPS port
     * @return
     */
    public PayaraMicro setSslPort(int sslPort) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        this.cfgOpts.setSslPort(sslPort);
        return this;
    }

    /**
     * Gets the logical name for this PayaraMicro Server within the server
     * cluster
     *
     * @return The configured instance name
     */
    public String getInstanceName() {
        return cfgOpts.getInstanceName();
    }

    /**
     * Sets the logical instance name for this PayaraMicro server within the
     * server cluster If this is not set a UUID is generated
     *
     * @param instanceName The logical server name
     * @return
     */
    public PayaraMicro setInstanceName(String instanceName) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        this.cfgOpts.setInstanceName(instanceName);
        return this;
    }

    /**
     * A directory which will be scanned for archives to deploy
     *
     * @return
     */
    public File getDeploymentDir() {
        return cfgOpts.getDeploymentRoot();
    }

    /**
     * Sets a directory to scan for archives to deploy on boot. This directory
     * is not monitored while running for changes. Therefore archives in this
     * directory will NOT be redeployed during runtime.
     *
     * @param deploymentRoot File path to the directory
     * @return
     */
    public PayaraMicro setDeploymentDir(File deploymentRoot) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        this.cfgOpts.setDeploymentRoot(deploymentRoot);
        return this;
    }

    /**
     * The path to an alternative domain.xml for PayaraMicro to use at boot
     *
     * @return The path to the domain.xml
     */
    public File getAlternateDomainXML() {
        return cfgOpts.getAlternateDomainXML();
    }

    /**
     * Sets an application specific domain.xml file that is embedded on the
     * classpath of your application.
     *
     * @param domainXml This is a resource string for your domain.xml
     * @return
     */
    public PayaraMicro setApplicationDomainXML(String domainXml) {
        applicationDomainXml = domainXml;
        return this;
    }

    /**
     * Sets the path to a domain.xml file PayaraMicro should use to boot. If
     * this is not set PayaraMicro will use an appropriate domain.xml from
     * within its jar file
     *
     * @param alternateDomainXML
     * @return
     */
    public PayaraMicro setAlternateDomainXML(File alternateDomainXML) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        this.cfgOpts.setAlternateDomainXML(alternateDomainXML);
        return this;
    }

    /**
     * Adds an archive to the list of archives to be deployed at boot. These
     * archives are not monitored for changes during running so are not
     * redeployed without restarting the server
     *
     * @param pathToWar File path to the deployment archive
     * @return
     */
    public PayaraMicro addDeployment(String pathToWar) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        File file = new File(pathToWar);
        return addDeploymentFile(file);
    }

    /**
     * Adds an archive to the list of archives to be deployed at boot. These
     * archives are not monitored for changes during running so are not
     * redeployed without restarting the server
     *
     * @param file File path to the deployment archive
     * @return
     */
    public PayaraMicro addDeploymentFile(File file) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        if (cfgOpts.getDeployments() == null) {
            cfgOpts.setDeployments(new LinkedList<File>());
        }
        cfgOpts.addDeployment(file);
        return this;
    }

    /**
     * Adds a Maven GAV coordinate to the list of archives to be deployed at boot.
     *
     * @param GAV GAV coordinate
     * @return
     */
    public PayaraMicro addDeployFromGAV(String GAV) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        if (cfgOpts.getGAVs() == null) {
            cfgOpts.setGAVs(new LinkedList<String>());
        }
        cfgOpts.addGAVs(GAV);
        if (cfgOpts.getGAVs() != null) {
            try {
                // Convert the provided GAV Strings into target URLs
                getGAVURLs();
            } catch (GlassFishException ex) {
                Logger.getLogger(PayaraMicro.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return this;
    }

    /**
     * Adds a Maven repository to the list of repositories to search for artifacts in 
     *
     * @param URLs URL to Maven repository
     * @return
     */
    public PayaraMicro addRepoUrl(String... URLs){
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        for (String url : URLs){
            try {
                if (!url.endsWith("/")) {
                    cfgOpts.addRepositoryURL(new URL(url + "/"));
                } else {
                    cfgOpts.addRepositoryURL(new URL(url));
                }
            } catch (MalformedURLException ex) {
                logger.log(Level.SEVERE, "{0} is not a valid URL and will be ignored", url);
            }
        }
        return this;
    }

    /**
     * Indicated whether clustering is enabled
     *
     * @return
     */
    public boolean isNoCluster() {
        return cfgOpts.isNoCluster();
    }

    /**
     * Enables or disables clustering before bootstrap
     *
     * @param noCluster set to true to disable clustering
     * @return
     */
    public PayaraMicro setNoCluster(boolean noCluster) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        this.cfgOpts.setNoCluster(noCluster);
        return this;
    }

    /**
     * Indicates whether this is a lite cluster member which means it stores no
     * cluster data although it participates fully in the cluster.
     *
     * @return
     */
    public boolean isLite() {
        return cfgOpts.isLiteMember();
    }

    /**
     * Sets the lite status of this cluster member. If true the Payara Micro is
     * a lite cluster member which means it stores no cluster data.
     *
     * @param liteMember set to true to set as a lite cluster member with no
     * data storage
     * @return
     */
    public PayaraMicro setLite(boolean liteMember) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        this.cfgOpts.setLiteMember(liteMember);
        return this;
    }

    /**
     * The maximum threads in the HTTP(S) threadpool processing HTTP(S)
     * requests. Setting this will determine how many concurrent HTTP requests
     * can be processed. The default value is 200. This value is shared by both
     * HTTP and HTTP(S) requests.
     *
     * @return
     */
    public int getMaxHttpThreads() {
        return cfgOpts.getMaxHttpThreads();
    }

    /**
     * The maximum threads in the HTTP(S) threadpool processing HTTP(S)
     * requests. Setting this will determine how many concurrent HTTP requests
     * can be processed. The default value is 200
     *
     * @param maxHttpThreads Maximum threads in the HTTP(S) threadpool
     * @return
     */
    public PayaraMicro setMaxHttpThreads(int maxHttpThreads) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        this.cfgOpts.setMaxHttpThreads(maxHttpThreads);
        return this;
    }

    /**
     * The minimum number of threads in the HTTP(S) threadpool Default value is
     * 10
     *
     * @return The minimum threads to be created in the threadpool
     */
    public int getMinHttpThreads() {
        return cfgOpts.getMinHttpThreads();
    }

    /**
     * The minimum number of threads in the HTTP(S) threadpool Default value is
     * 10
     *
     * @param minHttpThreads
     * @return
     */
    public PayaraMicro setMinHttpThreads(int minHttpThreads) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        this.cfgOpts.setMinHttpThreads(minHttpThreads);
        return this;
    }

    /**
     * The File path to a directory that PayaraMicro should use for storing its
     * configuration files
     *
     * @return
     */
    public File getRootDir() {
        return cfgOpts.getRootDir();
    }

    /**
     * Sets the File path to a directory PayaraMicro should use to install its
     * configuration files. If this is set the PayaraMicro configuration files
     * will be stored in the directory and persist across server restarts. If
     * this is not set the configuration files are created in a temporary
     * location and not persisted across server restarts.
     *
     * @param rootDir Path to a valid directory
     * @return Returns the PayaraMicro instance
     */
    public PayaraMicro setRootDir(File rootDir) {
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, setting attributes has no effect");
        }
        this.cfgOpts.setRootDir(rootDir);
        return this;
    }

    /**
     * Indicates whether autobinding of the HTTP port is enabled
     *
     * @return
     */
    public boolean getHttpAutoBind() {
        return cfgOpts.isAutoBindHttp();
    }

    /**
     * Enables or disables autobinding of the HTTP port
     *
     * @param httpAutoBind The true or false value to enable or disable HTTP
     * autobinding
     * @return
     */
    public PayaraMicro setHttpAutoBind(boolean httpAutoBind) {
        this.cfgOpts.setAutoBindHttp(httpAutoBind);
        return this;
    }

    /**
     * Indicates whether autobinding of the HTTPS port is enabled
     *
     * @return
     */
    public boolean getSslAutoBind() {
        return cfgOpts.isAutoBindSsl();
    }

    /**
     * Enables or disables autobinding of the HTTPS port
     *
     * @param sslAutoBind The true or false value to enable or disable HTTPS
     * autobinding
     * @return
     */
    public PayaraMicro setSslAutoBind(boolean sslAutoBind) {
        this.cfgOpts.setAutoBindSsl(sslAutoBind);
        return this;
    }

    /**
     * Gets the maximum number of ports to check if free for autobinding
     * purposes
     *
     * @return The number of ports to check if free
     */
    public int getAutoBindRange() {
        return cfgOpts.getAutoBindRange();
    }

    /**
     * Sets the maximum number of ports to check if free for autobinding
     * purposes
     *
     * @param autoBindRange The maximum number of ports to increment the port
     * value by
     * @return
     */
    public PayaraMicro setAutoBindRange(int autoBindRange) {
        this.cfgOpts.setAutoBindRange(autoBindRange);
        return this;
    }

    /**
     * Gets the name of the Hazelcast cluster group.
     * Clusters with different names do not interact
     * @return The current Cluster Name
     */
    public String getHzClusterName() {
        return cfgOpts.getHzClusterName();
    }

    /**
     * Sets the name of the Hazelcast cluster group
     * @param hzClusterName The name of the hazelcast cluster
     * @return
     */
    public PayaraMicro setHzClusterName(String hzClusterName) {
        this.cfgOpts.setHzClusterName(hzClusterName);
        return this;
    }

    /**
     * Gets the password of the Hazelcast cluster group
     * @return
     */
    public String getHzClusterPassword() {
        return cfgOpts.getHzClusterPassword();
    }

    /**
     * Sets the Hazelcast cluster group password.
     * For two clusters to work together then the group name and password must be the same
     * @param hzClusterPassword The password to set
     * @return
     */
    public PayaraMicro setHzClusterPassword(String hzClusterPassword) {
        this.cfgOpts.setHzClusterPassword(hzClusterPassword);
        return this;
    }

    /**
     * Boots the Payara Micro Server. All parameters are checked at this point
     *
     * @return An instance of PayaraMicroRuntime that can be used to access the
     * running server
     * @throws BootstrapException
     */
    public PayaraMicroRuntime bootStrap() throws BootstrapException {

        long start = System.currentTimeMillis();
        //if (runtime != null) {
        if (isRunning()) {
            throw new IllegalStateException("Payara Micro is already running, calling bootstrap now is meaningless");
        }

        // check hazelcast cluster overrides
        MulticastConfiguration mc = new MulticastConfiguration();
        mc.setMemberName(instanceName);
        if (cfgOpts.getHzPort() > Integer.MIN_VALUE) {
            mc.setMulticastPort(cfgOpts.getHzPort());
        }

        if (cfgOpts.getHzStartPort() > Integer.MIN_VALUE) {
            mc.setStartPort(cfgOpts.getHzStartPort());
        }

        if (cfgOpts.getHzMulticastGroup() != null) {
            mc.setMulticastGroup(cfgOpts.getHzMulticastGroup());
        }

        if (cfgOpts.getAlternateHZConfigFile() != null) {
            mc.setAlternateConfiguration(cfgOpts.getAlternateHZConfigFile());
        }
        mc.setLite(cfgOpts.isLiteMember());

        if (cfgOpts.getHzClusterName() != null) {
            mc.setClusterGroupName(cfgOpts.getHzClusterName());
        }

        if (cfgOpts.getHzClusterPassword() != null) {
            mc.setClusterGroupPassword(cfgOpts.getHzClusterPassword());
        }

        HazelcastCore.setMulticastOverride(mc);

        setSystemProperties();
        BootstrapProperties bprops = new BootstrapProperties();
        GlassFishRuntime gfruntime;
        PortBinder portBinder = new PortBinder();

        if (cfgOpts.isDisablePhoneHome() == true) {
            PhoneHomeCore.setOverrideEnabled(false);
        }

        try {
            gfruntime = GlassFishRuntime.bootstrap(bprops, Thread.currentThread().getContextClassLoader());
            GlassFishProperties gfproperties = new GlassFishProperties();

            if (cfgOpts.getHttpPort() != Integer.MIN_VALUE) {
                if (cfgOpts.isAutoBindHttp() == true) {
                    // Log warnings if overriding other options
                    logPortPrecedenceWarnings(false);

                    // Search for an available port from the specified port
                    try {
                        gfproperties.setPort("http-listener",
                                portBinder.findAvailablePort(cfgOpts.getHttpPort(),
                                        cfgOpts.getAutoBindRange()));
                    } catch (BindException ex) {
                        logger.log(Level.SEVERE, "No available port found in range: "
                                + cfgOpts.getHttpPort() + " - "
                                + (cfgOpts.getHttpPort() + cfgOpts.getAutoBindRange()), ex);

                        throw new GlassFishException("Could not bind HTTP port");
                    }
                } else {
                    // Log warnings if overriding other options
                    logPortPrecedenceWarnings(false);

                    // Set the port as normal
                    gfproperties.setPort("http-listener", cfgOpts.getHttpPort());
                }
            } else if (cfgOpts.isAutoBindHttp() == true) {
                // Log warnings if overriding other options
                logPortPrecedenceWarnings(false);

                // Search for an available port from the default HTTP port
                try {
                    gfproperties.setPort("http-listener",
                            portBinder.findAvailablePort(defaultHttpPort,
                                    cfgOpts.getAutoBindRange()));
                } catch (BindException ex) {
                    logger.log(Level.SEVERE, "No available port found in range: "
                            + defaultHttpPort + " - "
                            + (defaultHttpPort + cfgOpts.getAutoBindRange()), ex);

                    throw new GlassFishException("Could not bind HTTP port");
                }
            }

            if (cfgOpts.getSslPort() != Integer.MIN_VALUE) {
                if (cfgOpts.isAutoBindSsl() == true) {
                    // Log warnings if overriding other options
                    logPortPrecedenceWarnings(true);

                    // Search for an available port from the specified port
                    try {
                        gfproperties.setPort("https-listener",
                                portBinder.findAvailablePort(cfgOpts.getSslPort(), cfgOpts.getAutoBindRange()));
                    } catch (BindException ex) {
                        logger.log(Level.SEVERE, "No available port found in range: "
                                + cfgOpts.getSslPort() + " - " + (cfgOpts.getSslPort() + cfgOpts.getAutoBindRange()), ex);

                        throw new GlassFishException("Could not bind SSL port");
                    }
                } else {
                    // Log warnings if overriding other options
                    logPortPrecedenceWarnings(true);

                    // Set the port as normal
                    gfproperties.setPort("https-listener", cfgOpts.getSslPort());
                }
            } else if (cfgOpts.isAutoBindSsl() == true) {
                // Log warnings if overriding other options
                logPortPrecedenceWarnings(true);

                // Search for an available port from the default HTTPS port
                try {
                    gfproperties.setPort("https-listener",
                            portBinder.findAvailablePort(defaultHttpsPort, cfgOpts.getAutoBindRange()));
                } catch (BindException ex) {
                    logger.log(Level.SEVERE, "No available port found in range: "
                            + defaultHttpsPort + " - " + (defaultHttpsPort + cfgOpts.getAutoBindRange()), ex);

                    throw new GlassFishException("Could not bind SSL port");
                }
            } 

            if (cfgOpts.getAlternateDomainXML() != null) {
                gfproperties.setConfigFileReadOnly(false);
                gfproperties.setConfigFileURI("file:///" + cfgOpts.getAlternateDomainXML().getAbsolutePath().replace('\\', '/'));
            } else if (applicationDomainXml != null) {
                gfproperties.setConfigFileURI(Thread.currentThread().getContextClassLoader().getResource(applicationDomainXml).toExternalForm());
            } else if (cfgOpts.isNoCluster()) {
                gfproperties.setConfigFileURI(Thread.currentThread().getContextClassLoader().getResource("microdomain-nocluster.xml").toExternalForm());

            } else {
                gfproperties.setConfigFileURI(Thread.currentThread().getContextClassLoader().getResource("microdomain.xml").toExternalForm());
            }

            if (cfgOpts.getRootDir() != null) {
                gfproperties.setInstanceRoot(cfgOpts.getRootDir().getAbsolutePath());
                File configFile = new File(cfgOpts.getRootDir().getAbsolutePath() + File.separator + "config" + File.separator + "domain.xml");
                if (!configFile.exists()) {
                    installFiles(gfproperties);
                } else {
                    if (cfgOpts.getAlternateDomainXML() == null) {
                        String absolutePath = cfgOpts.getRootDir().getAbsolutePath();
                        absolutePath = absolutePath.replace('\\', '/');
                        gfproperties.setConfigFileURI("file:///" + absolutePath + "/config/domain.xml");
                        gfproperties.setConfigFileReadOnly(false);
                }
                }

            }

            if (this.cfgOpts.getMaxHttpThreads() != Integer.MIN_VALUE) {
                gfproperties.setProperty("embedded-glassfish-config.server.thread-pools.thread-pool.http-thread-pool.max-thread-pool-size", Integer.toString(cfgOpts.getMaxHttpThreads()));
            }

            if (this.cfgOpts.getMinHttpThreads() != Integer.MIN_VALUE) {
                gfproperties.setProperty("embedded-glassfish-config.server.thread-pools.thread-pool.http-thread-pool.min-thread-pool-size", Integer.toString(cfgOpts.getMinHttpThreads()));
            }
            
            if (enableAccessLog) {
                gfproperties.setProperty("embedded-glassfish-config.server.http-service.access-logging-enabled", "true");
                gfproperties.setProperty("embedded-glassfish-config.server.http-service.virtual-server.server.access-logging-enabled", "true");
                gfproperties.setProperty("embedded-glassfish-config.server.http-service.virtual-server.server.access-log", userAccessLogDirectory);
                if (enableAccessLogFormat) {
                    gfproperties.setProperty("embedded-glassfish-config.server.http-service.access-log.format", cfgOpts.getAccessLogFormat());
                }
            }
            gf = gfruntime.newGlassFish(gfproperties);

            // reset logger.
            // reset the Log Manager 
            String instanceRootStr = System.getProperty("com.sun.aas.instanceRoot");
            File configDir = new File(instanceRootStr, "config");
            File loggingToFileProperties = new File(configDir.getAbsolutePath(), loggingToFilePropertiesFileName);
            Path domainFile = Paths.get(configDir.getAbsolutePath() + File.separator + domainFileName);

            if (enableAccessLog) {
                Charset charset = StandardCharsets.UTF_8;
                try {
                    String content = new String(Files.readAllBytes(domainFile), charset);
                    content = content.replaceAll("access-logging-enabled=\"false\"", "access-logging-enabled=\"true\"");
                    content = content.replace("access-log=\"\"", "access-log=\"" + userAccessLogDirectory + "\"");
                    if (enableAccessLogFormat) {
                        content = content.replace("access-log format=\"%client.name% %auth-user-name% %datetime% %request% %status% %response.length%\"",
                                "access-log format=\"" + cfgOpts.getAccessLogFormat() + "\"");
                    }
                    Files.write(domainFile, content.getBytes(charset));
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
            if (logToFile) {
                loggingPropertiesFileName = loggingToFilePropertiesFileName;
                Properties props = new Properties();
                String propsFilename = loggingToFileProperties.getAbsolutePath();
                FileInputStream configStream;
                try {
                    configStream = new FileInputStream(propsFilename);
                    props.load(configStream);
                    configStream.close();
                    props.setProperty("java.util.logging.FileHandler.pattern", userLogFile);
                    FileOutputStream output = new FileOutputStream(propsFilename);
                    props.store(output, "Payara Micro Logging Properties File");
                    output.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }

            File loggingProperties = new File(configDir.getAbsolutePath(), loggingPropertiesFileName);
            if (loggingProperties.exists() && loggingProperties.canRead() && loggingProperties.isFile()) {
                if (System.getProperty("java.util.logging.config.file") == null) {
                    System.setProperty("java.util.logging.config.file", loggingProperties.getAbsolutePath());
                }
                try {
                    LogManager.getLogManager().readConfiguration();
                } catch (IOException | SecurityException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
            configureSecurity();
            gf.start();
            //this.runtime = new PayaraMicroRuntime(instanceName, gf);
            this.runtime = new PayaraMicroRuntime(instanceName, gf, gfruntime);
            deployAll();

            if (cfgOpts.isGenerateLogo()) {
                generateLogo();
            }

            if (cfgOpts.isEnableHealthCheck()) {
                HealthCheckService healthCheckService = gf.getService(HealthCheckService.class);
                healthCheckService.setEnabled(cfgOpts.isEnableHealthCheck());
            }

            long end = System.currentTimeMillis();
            logger.info(Version.getFullVersion() + " ready in " + (end - start) + " (ms)");

            return runtime;
        } catch (GlassFishException ex) {
            throw new BootstrapException(ex.getMessage(), ex);
        }
    }

    /**
     * Get a handle on the running Payara instance to manipulate the server once
     * running
     *
     * @return
     * @throws IllegalStateException
     */
    public PayaraMicroRuntime getRuntime() throws IllegalStateException {
        if (!isRunning()) {
            throw new IllegalStateException("Payara Micro is not running");
        }
        return runtime;
    }

    /**
     * Stops and then shutsdown the Payara Micro Server
     *
     * @throws BootstrapException
     */
    public void shutdown() throws BootstrapException {
        if (!isRunning()) {
            throw new IllegalStateException("Payara Micro is not running");
        }
        runtime.shutdown();
        runtime = null;
    }

    private PayaraMicro() {
        try {
            cfgOpts.setRepositoryURLs(new LinkedList<URL>());
            cfgOpts.addRepositoryURL(new URL(defaultMavenRepository));
            setArgumentsFromSystemProperties();
            addShutdownHook();
        } catch (MalformedURLException ex) {
            logger.log(Level.SEVERE, "{0} is not a valid default URL", defaultMavenRepository);
        }
    }

//    private void scanArgs(String[] args) {
//        for (int i = 0; i < args.length; i++) {
//            String arg = args[i];
//            if (null != arg) {
//                switch (arg) {
//                    case "--port": {
//                        String httpPortS = args[i + 1];
//                        try {
//                            httpPort = Integer.parseInt(httpPortS);
//                            if (httpPort < 1 || httpPort > 65535) {
//                                throw new NumberFormatException("Not a valid tcp port");
//                            }
//                        } catch (NumberFormatException nfe) {
//                            logger.log(Level.SEVERE, "{0} is not a valid http port number", httpPortS);
//                            throw new IllegalArgumentException();
//                        }
//                        i++;
//                        break;
//                    }
//                    case "--sslPort": {
//                        String httpPortS = args[i + 1];
//                        try {
//                            sslPort = Integer.parseInt(httpPortS);
//                            if (sslPort < 1 || sslPort > 65535) {
//                                throw new NumberFormatException("Not a valid tcp port");
//                            }
//                        } catch (NumberFormatException nfe) {
//                            logger.log(Level.SEVERE, "{0} is not a valid ssl port number and will be ignored", httpPortS);
//                            throw new IllegalArgumentException();
//                        }
//                        i++;
//                        break;
//                    }
//                    case "--version": {
//                        String deployments = System.getProperty("user.dir");
//                        System.err.println("deployments " + deployments);
//                        try {
//                            Properties props = new Properties();
//                            InputStream input = PayaraMicro.class.getResourceAsStream("/config/branding/glassfish-version.properties");
//                            props.load(input);
//                            StringBuilder output = new StringBuilder();
//                            if (props.getProperty("product_name").isEmpty() == false){
//                                output.append(props.getProperty("product_name")+" ");
//                         
//}
//                            if (props.getProperty("major_version").isEmpty() == false){
//                                output.append(props.getProperty("major_version")+".");
//                            }
//                            if (props.getProperty("minor_version").isEmpty() == false){
//                                output.append(props.getProperty("minor_version")+".");
//                            }
//                            if (props.getProperty("update_version").isEmpty() == false){
//                                output.append(props.getProperty("update_version")+".");
//                            }
//                            if (props.getProperty("payara_version").isEmpty() == false){
//                                output.append(props.getProperty("payara_version"));
//                            }
//                            if (props.getProperty("payara_update_version").isEmpty() == false){
//                                output.append("." + props.getProperty("payara_update_version"));
//                            }
//                            if (props.getProperty("build_id").isEmpty() == false){
//                                output.append(" Build Number " + props.getProperty("build_id"));
//                            }
//
//                            System.err.println(output.toString());
//                        } catch (FileNotFoundException ex) {
//                            Logger.getLogger(PayaraMicro.class.getName()).log(Level.SEVERE, null, ex);
//                        } catch (IOException io){
//                            Logger.getLogger(PayaraMicro.class.getName()).log(Level.SEVERE, null, io);
//                        }
//                        System.exit(1);
//                        break;
//                    }
//                    case "--maxHttpThreads": {
//                        String threads = args[i + 1];
//                        try {
//                            maxHttpThreads = Integer.parseInt(threads);
//                            if (maxHttpThreads < 2) {
//                                throw new NumberFormatException("Maximum Threads must be 2 or greater");
//                            }
//                        } catch (NumberFormatException nfe) {
//                            logger.log(Level.SEVERE, "{0} is not a valid maximum threads number and will be ignored", threads);
//                            throw new IllegalArgumentException();
//                        }
//                        i++;
//                        break;
//                    }
//                    case "--minHttpThreads": {
//                        String threads = args[i + 1];
//                        try {
//                            minHttpThreads = Integer.parseInt(threads);
//                            if (minHttpThreads < 0) {
//                                throw new NumberFormatException("Minimum Threads must be zero or greater");
//                            }
//                        } catch (NumberFormatException nfe) {
//                            logger.log(Level.SEVERE, "{0} is not a valid minimum threads number and will be ignored", threads);
//                            throw new IllegalArgumentException();
//                        }
//                        i++;
//                        break;
//                    }
//                    case "--mcAddress":
//                        hzMulticastGroup = args[i + 1];
//                        i++;
//                        break;
//                case "--clusterName" :
//                    hzClusterName = args[i+1];
//                        i++;
//                        break;
//                case "--clusterPassword" :
//                    hzClusterPassword = args[i+1];
//                        i++;
//                        break;
//                    case "--mcPort": {
//                        String httpPortS = args[i + 1];
//                        try {
//                            hzPort = Integer.parseInt(httpPortS);
//                            if (hzPort < 1 || hzPort > 65535) {
//                                throw new NumberFormatException("Not a valid tcp port");
//                            }
//                        } catch (NumberFormatException nfe) {
//                            logger.log(Level.SEVERE, "{0} is not a valid multicast port number and will be ignored", httpPortS);
//                            throw new IllegalArgumentException();
//                        }
//                        i++;
//                        break;
//                    }
//                    case "--startPort":
//                        String startPort = args[i + 1];
//                        try {
//                            hzStartPort = Integer.parseInt(startPort);
//                            if (hzStartPort < 1 || hzStartPort > 65535) {
//                                throw new NumberFormatException("Not a valid tcp port");
//                            }
//                        } catch (NumberFormatException nfe) {
//                            logger.log(Level.SEVERE, "{0} is not a valid port number and will be ignored", startPort);
//                            throw new IllegalArgumentException();
//                        }
//                        i++;
//                        break;
//                    case "--name":
//                        instanceName = args[i + 1];
//                        i++;
//                        break;
//                    case "--deploymentDir":
//                    case "--deployDir":
//                        deploymentRoot = new File(args[i + 1]);
//                        if (!deploymentRoot.exists() || !deploymentRoot.isDirectory()) {
//                            logger.log(Level.SEVERE, "{0} is not a valid deployment directory and will be ignored", args[i + 1]);
//                            throw new IllegalArgumentException();
//                        }
//                        i++;
//                        break;
//                    case "--rootDir":
//                        rootDir = new File(args[i + 1]);
//                        if (!rootDir.exists() || !rootDir.isDirectory()) {
//                            logger.log(Level.SEVERE, "{0} is not a valid root directory and will be ignored", args[i + 1]);
//                            throw new IllegalArgumentException();
//                        }
//                        i++;
//                        break;
//                    case "--deploy":
//                        File deployment = new File(args[i + 1]);
//                        if (!deployment.exists() || !deployment.canRead()) {
//                            logger.log(Level.SEVERE, "{0} is not a valid deployment path and will be ignored", deployment.getAbsolutePath());
//                        } else {
//                            if (deployments == null) {
//                                deployments = new LinkedList<>();
//                            }
//                            deployments.add(deployment);
//                        }
//                        i++;
//                        break;
//                    case "--domainConfig":
//                        alternateDomainXML = new File(args[i + 1]);
//                        if (!alternateDomainXML.exists() || !alternateDomainXML.isFile() || !alternateDomainXML.canRead() || !alternateDomainXML.getAbsolutePath().endsWith(".xml")) {
//                            logger.log(Level.SEVERE, "{0} is not a valid path to an xml file and will be ignored", alternateDomainXML.getAbsolutePath());
//                            throw new IllegalArgumentException();
//                        }
//                        i++;
//                        break;
//                    case "--noCluster":
//                        noCluster = true;
//                        break;
//                    case "--lite":
//                        liteMember = true;
//                        break;
//                    case "--hzConfigFile":
//                        File testFile = new File(args[i + 1]);
//                        if (!testFile.exists() || !testFile.isFile() || !testFile.canRead() || !testFile.getAbsolutePath().endsWith(".xml")) {
//                            logger.log(Level.SEVERE, "{0} is not a valid path to an xml file and will be ignored", testFile.getAbsolutePath());
//                            throw new IllegalArgumentException();
//                        }
//                        alternateHZConfigFile = testFile.toURI();
//                        i++;
//                        break;
//                    case "--autoBindHttp":
//                        autoBindHttp = true;
//                        break;
//                    case "--autoBindSsl":
//                        autoBindSsl = true;
//                        break;
//                    case "--autoBindRange":
//                        String autoBindRangeString = args[i + 1];
//                        try {
//                            autoBindRange = Integer.parseInt(autoBindRangeString);
//                            if (autoBindRange < 1) {
//                                throw new NumberFormatException("Not a valid auto bind range");
//                            }
//                        } catch (NumberFormatException nfe) {
//                            logger.log(Level.SEVERE,
//                                    "{0} is not a valid auto bind range number",
//                                    autoBindRangeString);
//                            throw new IllegalArgumentException();
//                        }
//                        i++;
//                        break;
//                    case "--enableHealthCheck":
//                        String enableHealthCheckString = args[i + 1];
//                        enableHealthCheck = Boolean.valueOf(enableHealthCheckString);
//                        break;
//                    case "--deployFromGAV":
//                        if (GAVs == null) {
//                            GAVs = new LinkedList<>();
//                        }
//
//                        GAVs.add(args[i + 1]);
//                        i++;
//                        break;
//                    case "--additionalRepository":
//                        try {
//                            // If there isn't a trailing /, add one
//                            if (!args[i + 1].endsWith("/")) {
//                                repositoryURLs.add(new URL(args[i + 1] + "/"));
//                            } else {
//                                repositoryURLs.add(new URL(args[i + 1]));
//                            }
//                        } catch (MalformedURLException ex) {
//                            logger.log(Level.SEVERE, "{0} is not a valid URL and will be ignored", args[i + 1]);
//                        }
//
//                        i++;
//                        break;
//                    case "--outputUberJar":
//                        uberJar = new File(args[i + 1]);
//                        i++;
//                        break;
//                    case "--systemProperties": {
//                        File propertiesFile = new File(args[i + 1]);
//                        userSystemProperties = new Properties();
//                        try (FileReader reader = new FileReader(propertiesFile)) {
//                            userSystemProperties.load(reader);
//                            Enumeration<String> names = (Enumeration<String>) userSystemProperties.propertyNames();
//                            while (names.hasMoreElements()) {
//                                String name = names.nextElement();
//                                System.setProperty(name, userSystemProperties.getProperty(name));
//                            }
//                        } catch (IOException e) {
//                            logger.log(Level.SEVERE,
//                                    "{0} is not a valid properties file",
//                                    propertiesFile.getAbsolutePath());
//                            throw new IllegalArgumentException(e);
//                        }
//                        if (!propertiesFile.isFile() && !propertiesFile.canRead()) {
//                            logger.log(Level.SEVERE,
//                                    "{0} is not a valid properties file",
//                                    propertiesFile.getAbsolutePath());
//                            throw new IllegalArgumentException();
//
//                        }
//                    }
//                    break;
//                    case "--disablePhoneHome":
//                        disablePhoneHome = true;
//                        break;
//                    case "--help":
//                        System.err.println("Usage:\n  --noCluster  Disables clustering\n"
//                                + "  --port <http-port-number> sets the http port\n"
//                                + "  --sslPort <ssl-port-number> sets the https port number\n"
//                                + "  --mcAddress <muticast-address> sets the cluster multicast group\n"
//                                + "  --mcPort <multicast-port-number> sets the cluster multicast port\n"
//                                + "  --clusterName <cluster-name> sets the Cluster Group Name\n"
//                                + "  --clusterPassword <cluster-password> sets the Cluster Group Password\n"
//                                + "  --startPort <cluster-start-port-number> sets the cluster start port number\n"
//                                + "  --name <instance-name> sets the instance name\n"
//                                + "  --rootDir <directory-path> Sets the root configuration directory and saves the configuration across restarts\n"
//                                + "  --deploymentDir <directory-path> if set to a valid directory all war files in this directory will be deployed\n"
//                                + "  --deploy <file-path> specifies a war file to deploy\n"
//                                + "  --domainConfig <file-path> overrides the complete server configuration with an alternative domain.xml file\n"
//                                + "  --minHttpThreads <threads-number> the minimum number of threads in the HTTP thread pool\n"
//                                + "  --maxHttpThreads <threads-number> the maximum number of threads in the HTTP thread pool\n"
//                                + "  --hzConfigFile <file-path> the hazelcast-configuration file to use to override the in-built hazelcast cluster configuration\n"
//                                + "  --autoBindHttp sets autobinding of the http port to a non-bound port\n"
//                                + "  --autoBindSsl sets autobinding of the https port to a non-bound port\n"
//                                + "  --autoBindRange <number-of-ports> sets the maximum number of ports to look at for port autobinding\n"
//                                + "  --lite sets the micro container to lite mode which means it clusters with other Payara Micro instances but does not store any cluster data\n"
//                                + "  --enableHealthCheck <boolean> enables/disables Health Check Service (disabled by default).\n"
//                                + "  --logo reveal the #BadAssFish\n"
//                                + "  --deployFromGAV <list-of-artefacts> specifies a comma separated groupId,artifactId,versionNumber of an artefact to deploy from a repository\n"
//                                + "  --additionalRepository <repo-url> specifies an additional repository to search for deployable artefacts in\n"
//                                + "  --outputUberJar <file-path> packages up an uber jar at the specified path based on the command line arguments and exits\n"
//                                + "  --systemProperties <file-path> Reads system properties from a file\n"
//                                + "  --disablePhoneHome Disables sending of usage tracking information\n"
//                                + "  --version Displays the version information\n"
//                                + "  --logToFile <file-path> outputs all the Log entries to a user defined file\n"
//                                + "  --logProperties <file-path> Allows user to set their own logging properties file\n"
//                                + "  --accessLog <directory-path> Sets user defined directory path for the access log\n"
//                                + "  --accessLogFormat Sets user defined log format for the access log\n"
//                                + "  --help Shows this message and exits\n");
//                        System.exit(1);
//                        break;
//                    case "--logToFile":
//                         setUserLogFile(args[i + 1]);
//                        break;
//                    case "--accessLog":
//                        File file = new File(args[i + 1]);
//                        if (!file.exists() || !file.isDirectory() || !file.canWrite()) {
//                            logger.log(Level.SEVERE, "{0} is not a valid directory for storing access logs as it must exist and be writable", file.getAbsolutePath());                            
//                            throw new IllegalArgumentException();
//                        }
//                        setAccessLogDir(file.getAbsolutePath());
//			break;
//                    case "--accessLogFormat":
//                        setAccessLogFormat(args[i + 1]);
//			break;
//                    case "--logProperties":
//                        File logPropertiesFile = new File(args[i + 1]);
//                        if (!logPropertiesFile.exists() || !logPropertiesFile.canRead() || logPropertiesFile.isDirectory() ) {
//                            logger.log(Level.SEVERE, "{0} is not a valid properties file path", logPropertiesFile.getAbsolutePath());
//                            throw new IllegalArgumentException();
//                        } else {
//                            setLogPropertiesFile(logPropertiesFile);
//                        }
//                        break;
//                    case "--logo":
//                        generateLogo = true;
//                        break;
//                }
//            }
//        }
//    }

    private void deployAll() throws GlassFishException {
        // Deploy explicit wars first.
        int deploymentCount = 0;
        Deployer deployer = gf.getDeployer();
        if (cfgOpts.getDeployments() != null) {
            for (File war : cfgOpts.getDeployments()) {
                if (war.exists() && war.canRead()) {
                    deployer.deploy(war, "--availabilityenabled=true", "--force=true");
                    deploymentCount++;
                } else {
                    logger.log(Level.WARNING, "{0} is not a valid deployment", war.getAbsolutePath());
                }
            }
        }

        // Deploy from deployment directory
        if (cfgOpts.getDeploymentRoot() != null) {
            for (File war : cfgOpts.getDeploymentRoot().listFiles()) {
                String warPath = war.getAbsolutePath();
                if (war.isFile() && war.canRead() && (warPath.endsWith(".war") || warPath.endsWith(".ear") || warPath.endsWith(".jar") || warPath.endsWith(".rar"))) {
                    deployer.deploy(war, "--availabilityenabled=true", "--force=true");
                    deploymentCount++;
                }
            }
        }

        // Deploy from URI only called if GAVs provided
        if (cfgOpts.getGAVs() != null) {
            // Convert the provided GAV Strings into target URLs
            getGAVURLs();

            if (!deploymentURLsMap.isEmpty()) {
                for (Map.Entry<String, URL> deploymentMapEntry : deploymentURLsMap.entrySet()) {
                    try {
                        // Convert the URL to a URI for use with the deploy method
                        URI artefactURI = deploymentMapEntry.getValue().toURI();

                        deployer.deploy(artefactURI, "--availabilityenabled",
                                "true", "--contextroot",
                                deploymentMapEntry.getKey(), "--force=true");

                        deploymentCount++;
                    } catch (URISyntaxException ex) {
                        logger.log(Level.WARNING, "{0} could not be converted to a URI,"
                                + " artefact will be skipped",
                                deploymentMapEntry.getValue().toString());
                    }
                }
            }
        }

        // search META-INF/deploy for deployments
        // if there is a deployment called ROOT deploy to the root context /
        URL url = this.getClass().getClassLoader().getResource("META-INF/deploy");
        if (url != null) {
            String entryName = "";
            try {
                HashSet<String> entriesToDeploy = new HashSet<>();
                JarURLConnection urlcon = (JarURLConnection) url.openConnection();
                JarFile jFile = urlcon.getJarFile();
                Enumeration<JarEntry> entries = jFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    entryName = entry.getName();
                    if (!entry.isDirectory() && !entry.getName().endsWith(".properties") && !entry.getName().endsWith(".xml") && entry.getName().startsWith("META-INF/deploy")) {
                        entriesToDeploy.add(entry.getName());
                    }
                }

                for (String entry : entriesToDeploy) {
                    File file = new File(entry);
                    String contextRoot = file.getName();
                    if (contextRoot.endsWith(".ear") || contextRoot.endsWith(".war") || contextRoot.endsWith(".jar") || contextRoot.endsWith(".rar")) {
                        contextRoot = contextRoot.substring(0, contextRoot.length() - 4);
                    }

                    if (contextRoot.equals("ROOT")) {
                        contextRoot = "/";
                    }

                    deployer.deploy(this.getClass().getClassLoader().getResourceAsStream(entry), "--availabilityenabled",
                            "true", "--contextroot",
                            contextRoot, "--name", file.getName(), "--force=true");
                    deploymentCount++;
                }
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "Could not deploy jar entry {0}",
                        entryName);
            }
        } else {
            logger.info("No META-INF/deploy directory");
        }

        logger.log(Level.INFO, "Deployed {0} archives", deploymentCount);
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(
                "GlassFish Shutdown Hook") {
            @Override
            public void run() {
                try {
                    if (gf != null) {
                        gf.stop();
                        gf.dispose();
                    }
                } catch (Exception ex) {
                }
            }
        });
    }

    private void installFiles(GlassFishProperties gfproperties) {
        // make directories
        File configDir = new File(cfgOpts.getRootDir().getAbsolutePath(), "config");
        String[] configFiles;
        PrintWriter writer;
        String sCurrentLine;
        BufferedReader bufferedReader = null;
        new File(cfgOpts.getRootDir().getAbsolutePath(), "docroot").mkdirs();
        configDir.mkdirs();
        if (logPropertiesFile) {
            File userLogPropsFile = new File(configDir.getAbsolutePath() + File.separator + userPropertiesFileName);
            try {
                if (userLogPropsFile.exists()) {
                   userLogPropsFile.delete();
                } else {
                    userLogPropsFile.createNewFile();
                }
            } catch (IOException ex) {
                 logger.log(Level.SEVERE, null, ex);
            }

            try {
                writer = new PrintWriter(userLogPropsFile.getAbsoluteFile());
                writer.print("");
                writer.close();
            } catch (FileNotFoundException ex) {
                logger.log(Level.SEVERE, null, ex);
            }

            try {
                bufferedReader = new BufferedReader(new FileReader(cfgOpts.getUserLogPropertiesFile() ));
                FileWriter fileWriter = new FileWriter(userLogPropsFile.getAbsoluteFile());
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                while ((sCurrentLine = bufferedReader.readLine()) != null) {
                    bufferedWriter.append(sCurrentLine);
                    bufferedWriter.newLine();

                }
                bufferedWriter.close();
            } catch (FileNotFoundException ex) {
                logger.log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            } finally {
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
            }
        
            configFiles = new String[]{"config/keyfile",
                "config/server.policy",
                "config/cacerts.jks",
                "config/keystore.jks",
                "config/login.conf",
                "config/logging.properties",
                "config/loggingToFile.properties",
                "config/admin-keyfile",
                "config/"+ userPropertiesFileName,
                "config/default-web.xml",
                "org/glassfish/embed/domain.xml"
            };
        } else {
            configFiles = new String[]{"config/keyfile",
                "config/server.policy",
                "config/cacerts.jks",
                "config/keystore.jks",
                "config/login.conf",
                "config/logging.properties",
                "config/loggingToFile.properties",
                "config/admin-keyfile",
                "config/default-web.xml",
                "org/glassfish/embed/domain.xml"
            };
        }

        /**
         * Copy all the config files from uber jar to the instanceConfigDir
         */
        ClassLoader cl = getClass().getClassLoader();
        for (String configFile : configFiles) {
            URL url = cl.getResource(configFile);
            if (url != null) {
                copy(url, new File(configDir.getAbsoluteFile(),
                        configFile.substring(configFile.lastIndexOf('/') + 1)), false);
            }
        }

        // copy branding file if available
        URL brandingUrl = cl.getResource("config/branding/glassfish-version.properties");
        if (brandingUrl != null) {
            copy(brandingUrl, new File(configDir.getAbsolutePath(), "branding/glassfish-version.properties"), false);
        }

        //Copy in the relevant domain.xml
        String configFileURI = gfproperties.getConfigFileURI();
        try {
            copy(URI.create(configFileURI).toURL(),
                    new File(configDir.getAbsolutePath(), "domain.xml"), true);
        } catch (MalformedURLException ex) {
            Logger.getLogger(PayaraMicro.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void setSystemProperties() {
        try {
            Properties embeddedBootProperties = new Properties();
            ClassLoader loader = getClass().getClassLoader();
            embeddedBootProperties.load(loader.getResourceAsStream("payara-boot.properties"));
            for (Object key : embeddedBootProperties.keySet()) {
                String keyStr = (String) key;
                if (System.getProperty(keyStr) == null) {
                    System.setProperty(keyStr, embeddedBootProperties.getProperty(keyStr));
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(PayaraMicro.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Determines whether the server is running i.e. bootstrap has been called
     *
     * @return true of the server is running
     */
    boolean isRunning() {
        try {
            return (gf != null && gf.getStatus() == Status.STARTED);
        } catch (GlassFishException ex) {
            return false;
        }
    }

    void generateLogo() {
        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(bootImage);) {
            byte[] buffer = new byte[1024];
            for (int length; (length = is.read(buffer)) != -1;) {

                System.err.write(buffer, 0, length);
                System.err.flush();
            }
        } catch (IOException | NullPointerException ex) {
            Logger.getLogger(PayaraMicro.class.getName()).log(Level.WARNING, "Problems displaying Boot Image", ex);
        }
    }

    /**
     * Converts the GAVs provided to a URLs, and stores them in the
     * deploymentURLsMap.
     */
    private void getGAVURLs() throws GlassFishException {
        GAVConvertor gavConvertor = new GAVConvertor();

        for (String gav : cfgOpts.getGAVs()) {
            Map.Entry<String, URL> artefactMapEntry
                    = gavConvertor.getArtefactMapEntry(gav, cfgOpts.getRepositoryURLs());

            if (deploymentURLsMap == null) {
                deploymentURLsMap = new LinkedHashMap<>();
            }

            deploymentURLsMap.put(artefactMapEntry.getKey(),
                    artefactMapEntry.getValue());
        }
    }

    /**
     * Logs warnings if ports are being overridden
     * @param HTTPS True if checking if HTTPS ports are being overridden
     */
    private void logPortPrecedenceWarnings(boolean HTTPS) {
        if (HTTPS == true) {
            if (cfgOpts.getAlternateDomainXML() != null) {
                if (cfgOpts.getSslPort() != Integer.MIN_VALUE) {
                    if (cfgOpts.isAutoBindSsl() == true) {
                        logger.log(Level.INFO, "Overriding HTTPS port value set"
                                + " in {0} and auto-binding against " + cfgOpts.getSslPort(),
                                cfgOpts.getAlternateDomainXML().getAbsolutePath());
                    } else {
                        logger.log(Level.INFO, "Overriding HTTPS port value set"
                                + " in {0} with " + cfgOpts.getSslPort(),
                                cfgOpts.getAlternateDomainXML().getAbsolutePath());
                    }
                } else if (cfgOpts.isAutoBindSsl() == true) {
                    logger.log(Level.INFO, "Overriding HTTPS port value set"
                            + " in {0} and auto-binding against "
                            + defaultHttpsPort,
                            cfgOpts.getAlternateDomainXML().getAbsolutePath());
                } else {
                    logger.log(Level.INFO, "Overriding HTTPS port value set"
                            + " in {0} with " + defaultHttpsPort,
                            cfgOpts.getAlternateDomainXML().getAbsolutePath());
                }
            }

            if (cfgOpts.getRootDir() != null) {
                File configFile = new File(cfgOpts.getRootDir().getAbsolutePath()
                        + File.separator + "config" + File.separator
                        + "domain.xml");
                if (configFile.exists()) {
                    if (cfgOpts.getSslPort() != Integer.MIN_VALUE) {
                        if (cfgOpts.isAutoBindSsl() == true) {
                            logger.log(Level.INFO, "Overriding HTTPS port value"
                                    + " set in {0} and auto-binding against "
                                    + cfgOpts.getSslPort(), configFile.getAbsolutePath());
                        } else {
                            logger.log(Level.INFO, "Overriding HTTPS port value"
                                    + " set in {0} with " + cfgOpts.getSslPort(),
                                    configFile.getAbsolutePath());
                        }
                    } else if (cfgOpts.isAutoBindSsl() == true) {
                        logger.log(Level.INFO, "Overriding HTTPS port value"
                                + " set in {0} and auto-binding against "
                                + defaultHttpsPort,
                                configFile.getAbsolutePath());
                    } else {
                        logger.log(Level.INFO, "Overriding HTTPS port value"
                                + " set in {0} with default value of "
                                + defaultHttpsPort,
                                configFile.getAbsolutePath());
                    }
                }
            }
        } else {
            if (cfgOpts.getAlternateDomainXML() != null) {
                if (cfgOpts.getHttpPort() != Integer.MIN_VALUE) {
                    if (cfgOpts.isAutoBindHttp() == true) {
                        logger.log(Level.INFO, "Overriding HTTP port value set "
                                + "in {0} and auto-binding against " + cfgOpts.getHttpPort(),
                                cfgOpts.getAlternateDomainXML().getAbsolutePath());
                    } else {
                        logger.log(Level.INFO, "Overriding HTTP port value set "
                                + "in {0} with " + cfgOpts.getHttpPort(),
                                cfgOpts.getAlternateDomainXML().getAbsolutePath());
                    }
                } else if (cfgOpts.isAutoBindHttp() == true) {
                    logger.log(Level.INFO, "Overriding HTTP port value set "
                            + "in {0} and auto-binding against "
                            + defaultHttpPort,
                            cfgOpts.getAlternateDomainXML().getAbsolutePath());
                } else {
                    logger.log(Level.INFO, "Overriding HTTP port value set "
                            + "in {0} with default value of "
                            + defaultHttpPort,
                            cfgOpts.getAlternateDomainXML().getAbsolutePath());
                }
            }

            if (cfgOpts.getRootDir() != null) {
                File configFile = new File(cfgOpts.getRootDir().getAbsolutePath()
                        + File.separator + "config" + File.separator
                        + "domain.xml");
                if (configFile.exists()) {
                    if (cfgOpts.getHttpPort() != Integer.MIN_VALUE) {
                        if (cfgOpts.isAutoBindHttp() == true) {
                            logger.log(Level.INFO, "Overriding HTTP port value "
                                    + "set in {0} and auto-binding against "
                                    + cfgOpts.getHttpPort(), configFile.getAbsolutePath());
                        } else {
                            logger.log(Level.INFO, "Overriding HTTP port value "
                                    + "set in {0} with " + cfgOpts.getHttpPort(),
                                    configFile.getAbsolutePath());
                        }
                    } else if (cfgOpts.isAutoBindHttp() == true) {
                        logger.log(Level.INFO, "Overriding HTTP port value "
                                + "set in {0} and auto-binding against "
                                + defaultHttpPort,
                                configFile.getAbsolutePath());
                    } else {
                        logger.log(Level.INFO, "Overriding HTTP port value "
                                + "set in {0} with default value of "
                                + defaultHttpPort,
                                configFile.getAbsolutePath());
                    }
                }
            }
        }
    }

    private void setArgumentsFromSystemProperties() {

        // load all from the resource
        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream("META-INF/deploy/payaramicro.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                for (Map.Entry<?, ?> entry : props.entrySet()) {
                    System.setProperty((String) entry.getKey(), (String) entry.getValue());
                }
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "", ex);
        }

        // Set the domain.xml
        String alternateDomainXMLStr = System.getProperty("payaramicro.domainConfig");
        if (alternateDomainXMLStr != null && !alternateDomainXMLStr.isEmpty()) {
            applicationDomainXml = alternateDomainXMLStr;
        }

        // Set the hazelcast config file
        String alternateHZConfigFileStr = System.getProperty("payaramicro.hzConfigFile");
        if (alternateHZConfigFileStr != null && !alternateHZConfigFileStr.isEmpty()) {
            try {
                cfgOpts.setAlternateHZConfigFile(Thread.currentThread().getContextClassLoader().getResource(alternateHZConfigFileStr).toURI());
            } catch (URISyntaxException ex) {
                logger.log(Level.WARNING, "payaramicro.hzConfigFile has invalid URI syntax and will be ignored", ex);
                cfgOpts.setAlternateHZConfigFile(null);
            }
        }

        cfgOpts.setAutoBindHttp(Boolean.getBoolean("payaramicro.autoBindHttp"));
        cfgOpts.setAutoBindRange(Integer.getInteger("payaramicro.autoBindRange", 5));
        cfgOpts.setAutoBindSsl(Boolean.getBoolean("payaramicro.autoBindSsl"));
        cfgOpts.setGenerateLogo(Boolean.getBoolean("payaramicro.logo"));
        logToFile = Boolean.getBoolean("payaramicro.logToFile");
        enableAccessLog = Boolean.getBoolean("payaramicro.enableAccessLog");
        enableAccessLogFormat = Boolean.getBoolean("payaramicro.enableAccessLogFormat");
        cfgOpts.setBooleanLogPropertiesFile(Boolean.getBoolean("payaramicro.logPropertiesFile"));
        cfgOpts.setEnableHealthCheck(Boolean.getBoolean("payaramicro.enableHealthCheck"));
        cfgOpts.setHttpPort(Integer.getInteger("payaramicro.port", Integer.MIN_VALUE));
        cfgOpts.setHzMulticastGroup(System.getProperty("payaramicro.mcAddress"));
        cfgOpts.setHzPort(Integer.getInteger("payaramicro.mcPort", Integer.MIN_VALUE));
        cfgOpts.setHzStartPort(Integer.getInteger("payaramicro.startPort", Integer.MIN_VALUE));
        cfgOpts.setHzClusterName(System.getProperty("payaramicro.clusterName"));
        cfgOpts.setHzClusterPassword(System.getProperty("payaramicro.clusterPassword"));
        cfgOpts.setLiteMember(Boolean.getBoolean("payaramicro.lite"));
        cfgOpts.setMaxHttpThreads(Integer.getInteger("payaramicro.maxHttpThreads", Integer.MIN_VALUE));
        cfgOpts.setMinHttpThreads(Integer.getInteger("payaramicro.minHttpThreads", Integer.MIN_VALUE));
        cfgOpts.setNoCluster(Boolean.getBoolean("payaramicro.noCluster"));
        cfgOpts.setDisablePhoneHome(Boolean.getBoolean("payaramicro.disablePhoneHome"));

        // Set the rootDir file
        String rootDirFileStr = System.getProperty("payaramicro.rootDir");
        if (rootDirFileStr != null && !rootDirFileStr.isEmpty()) {
            cfgOpts.setRootDir(new File(rootDirFileStr));
        }

        String name = System.getProperty("payaramicro.name");
        if (name != null && !name.isEmpty()) {
            instanceName = name;
        }
    }

    private void packageUberJar() {
        long start = System.currentTimeMillis();
        logger.info("Building Uber Jar... " + cfgOpts.getUberJar());
        String entryString;
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(cfgOpts.getUberJar()));) {
            // get the current payara micro jar
            URL url = this.getClass().getClassLoader().getResource("payara-boot.properties");
            JarURLConnection urlcon = (JarURLConnection) url.openConnection();

            // copy all entries from the existing jar file
            JarFile jFile = urlcon.getJarFile();
            Enumeration<JarEntry> entries = jFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                jos.putNextEntry(new JarEntry(entry.getName()));
                InputStream is = jFile.getInputStream(entry);
                if (entry.toString().contains("config/logging.properties") &&  cfgOpts.getLogPropertiesFile()) {
                   is = new FileInputStream(new File(cfgOpts.getUserLogPropertiesFile() ));
                }
     
                byte[] buffer = new byte[4096];
                int bytesRead = 0;
                while ((bytesRead = is.read(buffer)) != -1) {
                    jos.write(buffer, 0, bytesRead);
                }
                
                is.close();
                jos.flush();
                jos.closeEntry();
            }

            // create the directory entry
            JarEntry deploymentDir = new JarEntry("META-INF/deploy/");
            jos.putNextEntry(deploymentDir);
            jos.flush();
            jos.closeEntry();
            if (cfgOpts.getDeployments() != null) {
                for (File deployment : cfgOpts.getDeployments()) {
                    JarEntry deploymentEntry = new JarEntry("META-INF/deploy/" + deployment.getName());
                    jos.putNextEntry(deploymentEntry);
                    try (FileInputStream fis = new FileInputStream(deployment)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead = 0;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            jos.write(buffer, 0, bytesRead);
                        }
                        jos.flush();
                        jos.closeEntry();
                    } catch (IOException ioe) {
                        logger.log(Level.WARNING, "Error adding deployment " + deployment.getAbsolutePath() + " to the Uber Jar Skipping...", ioe);
                    }
                }
            }

            if (cfgOpts.getDeploymentRoot() != null) {
                for (File deployment : cfgOpts.getDeploymentRoot().listFiles()) {
                    if (deployment.isFile()) {
                        JarEntry deploymentEntry = new JarEntry("META-INF/deploy/" + deployment.getName());
                        jos.putNextEntry(deploymentEntry);
                        try (FileInputStream fis = new FileInputStream(deployment)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead = 0;
                            while ((bytesRead = fis.read(buffer)) != -1) {
                                jos.write(buffer, 0, bytesRead);
                            }
                            jos.flush();
                            jos.closeEntry();
                        } catch (IOException ioe) {
                            logger.log(Level.WARNING, "Error adding deployment " + deployment.getAbsolutePath() + " to the Uber Jar Skipping...", ioe);
                        }
                    }
                }
            }

            if (cfgOpts.getGAVs() != null) {
                try {
                    // Convert the provided GAV Strings into target URLs
                    getGAVURLs();
                    for (Map.Entry<String, URL> deploymentMapEntry : deploymentURLsMap.entrySet()) {
                        URL deployment = deploymentMapEntry.getValue();
                        String name = deploymentMapEntry.getKey();
                        try (InputStream is = deployment.openStream()) {
                            JarEntry deploymentEntry = new JarEntry("META-INF/deploy/" + name);
                            jos.putNextEntry(deploymentEntry);
                            byte[] buffer = new byte[4096];
                            int bytesRead = 0;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                jos.write(buffer, 0, bytesRead);
                            }
                            jos.flush();
                            jos.closeEntry();
                        } catch (IOException ioe) {
                            logger.log(Level.WARNING, "Error adding deployment " + name + " to the Uber Jar Skipping...", ioe);
                        }
                    }
                } catch (GlassFishException ex) {
                    logger.log(Level.SEVERE, "Unable to process maven deployment units", ex);
                }
            }

            // write the system properties file
            JarEntry je = new JarEntry("META-INF/deploy/payaramicro.properties");
            jos.putNextEntry(je);
            Properties props = new Properties();
            if (cfgOpts.getHzMulticastGroup() != null) {
                props.setProperty("payaramicro.mcAddress", cfgOpts.getHzMulticastGroup());
            }

            if (cfgOpts.getHzPort() != Integer.MIN_VALUE) {
                props.setProperty("payaramicro.mcPort", Integer.toString(cfgOpts.getHzPort()));
            }

            if (cfgOpts.getHzStartPort() != Integer.MIN_VALUE) {
                props.setProperty("payaramicro.startPort", Integer.toString(cfgOpts.getHzStartPort()));
            }

            props.setProperty("payaramicro.name", instanceName);

            if (cfgOpts.getRootDir() != null) {
                props.setProperty("payaramicro.rootDir", cfgOpts.getRootDir().getAbsolutePath());
            }

            if (cfgOpts.getAlternateDomainXML() != null) {
                props.setProperty("payaramicro.domainConfig", "META-INF/deploy/domain.xml");
            }

            if (cfgOpts.getMinHttpThreads() != Integer.MIN_VALUE) {
                props.setProperty("payaramicro.minHttpThreads", Integer.toString(cfgOpts.getMinHttpThreads()));
            }

            if (cfgOpts.getMaxHttpThreads() != Integer.MIN_VALUE) {
                props.setProperty("payaramicro.maxHttpThreads", Integer.toString(cfgOpts.getMaxHttpThreads()));
            }

            if (cfgOpts.getAlternateHZConfigFile() != null) {
                props.setProperty("payaramicro.hzConfigFile", "META-INF/deploy/hzconfig.xml");
            }

            if (cfgOpts.getHzClusterName() != null) {
                props.setProperty("payaramicro.clusterName", cfgOpts.getHzClusterName());
            }

            if (cfgOpts.getHzClusterPassword() != null) {
                props.setProperty("payaramicro.clusterPassword", cfgOpts.getHzClusterPassword());
            }

            props.setProperty("payaramicro.autoBindHttp", Boolean.toString(cfgOpts.isAutoBindHttp()));
            props.setProperty("payaramicro.autoBindSsl", Boolean.toString(cfgOpts.isAutoBindSsl()));
            props.setProperty("payaramicro.autoBindRange", Integer.toString(cfgOpts.getAutoBindRange()));
            props.setProperty("payaramicro.lite", Boolean.toString(cfgOpts.isLiteMember()));
            props.setProperty("payaramicro.enableHealthCheck", Boolean.toString(cfgOpts.isEnableHealthCheck()));
            props.setProperty("payaramicro.logo", Boolean.toString(cfgOpts.isGenerateLogo()));
            props.setProperty("payaramicro.logToFile", Boolean.toString(logToFile));
            props.setProperty("payaramicro.enableAccessLog", Boolean.toString(enableAccessLog));
            props.setProperty("payaramicro.enableAccessLogFormat", Boolean.toString(enableAccessLogFormat));
            props.setProperty("payaramicro.logPropertiesFile", Boolean.toString(logPropertiesFile));
            props.setProperty("payaramicro.noCluster", Boolean.toString(cfgOpts.isNoCluster()));
            props.setProperty("payaramicro.disablePhoneHome", Boolean.toString(cfgOpts.isDisablePhoneHome()));

            if (cfgOpts.getHttpPort() != Integer.MIN_VALUE) {
                props.setProperty("payaramicro.port", Integer.toString(cfgOpts.getHttpPort()));
            }

            if (cfgOpts.getSslPort() != Integer.MIN_VALUE) {
                props.setProperty("payaramicro.sslPort", Integer.toString(cfgOpts.getSslPort()));
            }

            // write all user defined system properties
            if (cfgOpts.getUserSystemProperties() != null) {
                Enumeration<String> names = (Enumeration<String>) cfgOpts.getUserSystemProperties().propertyNames();
                while (names.hasMoreElements()) {
                    String name = names.nextElement();
                    props.setProperty(name, cfgOpts.getUserSystemProperties().getProperty(name));
                }
            }

            props.store(jos, "");
            jos.flush();
            jos.closeEntry();

            // add the alternate domain.xml file if present
            if (cfgOpts.getAlternateDomainXML() != null && cfgOpts.getAlternateDomainXML().isFile() && cfgOpts.getAlternateDomainXML().canRead()) {
                try (InputStream is = new FileInputStream(cfgOpts.getAlternateDomainXML())) {
                    JarEntry domainXml = new JarEntry("META-INF/deploy/domain.xml");
                    jos.putNextEntry(domainXml);
                    byte[] buffer = new byte[4096];
                    int bytesRead = 0;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        jos.write(buffer, 0, bytesRead);
                    }
                    jos.flush();
                    jos.closeEntry();
                } catch (IOException ioe) {
                    logger.log(Level.WARNING, "Error adding alternative domain.xml to the Uber Jar Skipping...", ioe);
                }
            }

            // add the alternate hazelcast config to the uberJar
            if (cfgOpts.getAlternateHZConfigFile() != null) {
                try (InputStream is = cfgOpts.getAlternateHZConfigFile().toURL().openStream()) {
                    JarEntry domainXml = new JarEntry("META-INF/deploy/hzconfig.xml");
                    jos.putNextEntry(domainXml);
                    byte[] buffer = new byte[4096];
                    int bytesRead = 0;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        jos.write(buffer, 0, bytesRead);
                    }
                    jos.flush();
                    jos.closeEntry();
                } catch (IOException ioe) {
                    logger.log(Level.WARNING, "Error adding alternative hzconfig.xml to the Uber Jar Skipping...", ioe);
                }
            }

            logger.info("Built Uber Jar " + cfgOpts.getUberJar() + " in " + (System.currentTimeMillis() - start) + " (ms)");

        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating Uber Jar " + cfgOpts.getUberJar().getAbsolutePath(), ex);
        }

    }

    private void configureSecurity() {
        String instanceRootStr = System.getProperty("com.sun.aas.instanceRoot");
        File configDir = new File(instanceRootStr, "config");

        // Set security properties PAYARA-803
        if (System.getProperty("java.security.auth.login.config") == null) {
                System.setProperty("java.security.auth.login.config", new File(configDir.getAbsolutePath(),"login.conf").getAbsolutePath());
        }

        if (System.getProperty("java.security.policy") == null) {
                System.setProperty("java.security.policy", new File(configDir.getAbsolutePath(),"server.policy").getAbsolutePath());
        }

        // check keystore
        if (System.getProperty("javax.net.ssl.keyStore") == null) {
            System.setProperty("javax.net.ssl.keyStore",new File(configDir.getAbsolutePath(),"keystore.jks").getAbsolutePath());
        }

        // check truststore
        if (System.getProperty("javax.net.ssl.trustStore") == null) {
            System.setProperty("javax.net.ssl.trustStore",new File(configDir.getAbsolutePath(),"cacerts.jks").getAbsolutePath());
        }
    }

}
