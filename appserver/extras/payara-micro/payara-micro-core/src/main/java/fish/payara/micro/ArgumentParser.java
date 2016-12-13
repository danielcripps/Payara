package fish.payara.micro;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

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

public class ArgumentParser {
    private static final Logger logger = Logger.getLogger("PayaraMicro");
    
    private final ConfigOptions cfgOpts;
    private final String[] args;
    
    public ArgumentParser(String[] arguments, ConfigOptions cfg) {
        this.args = arguments;
        this.cfgOpts = cfg;
        ParseOptions();
    }
    
    private void ParseOptions() {
                for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (null != arg) {
                switch (arg) {
                    case "--port": {
                        String httpPortS = args[i + 1];
                        try {
                            cfgOpts.setHttpPort(Integer.parseInt(httpPortS));
                            if (cfgOpts.getHttpPort() < 1 || cfgOpts.getHttpPort() > 65535) {
                                throw new NumberFormatException("Not a valid tcp port");
                            }
                        } catch (NumberFormatException nfe) {
                            logger.log(Level.SEVERE, "{0} is not a valid http port number", httpPortS);
                            throw new IllegalArgumentException();
                        }
                        i++;
                        break;
                    }
                    case "--sslPort": {
                        String httpPortS = args[i + 1];
                        try {
                            cfgOpts.setSslPort(Integer.parseInt(httpPortS));
                            if (cfgOpts.getSslPort() < 1 || cfgOpts.getSslPort() > 65535) {
                                throw new NumberFormatException("Not a valid tcp port");
                            }
                        } catch (NumberFormatException nfe) {
                            logger.log(Level.SEVERE, "{0} is not a valid ssl port number and will be ignored", httpPortS);
                            throw new IllegalArgumentException();
                        }
                        i++;
                        break;
                    }
                    case "--version": {
                        String deployments = System.getProperty("user.dir");
                        System.err.println("deployments " + deployments);
                        try {
                            Properties props = new Properties();
                            InputStream input = PayaraMicro.class.getResourceAsStream("/config/branding/glassfish-version.properties");
                            props.load(input);
                            StringBuilder output = new StringBuilder();
                            if (props.getProperty("product_name").isEmpty() == false){
                                output.append(props.getProperty("product_name")+" ");
                            }
                            if (props.getProperty("major_version").isEmpty() == false){
                                output.append(props.getProperty("major_version")+".");
                            }
                            if (props.getProperty("minor_version").isEmpty() == false){
                                output.append(props.getProperty("minor_version")+".");
                            }
                            if (props.getProperty("update_version").isEmpty() == false){
                                output.append(props.getProperty("update_version")+".");
                            }
                            if (props.getProperty("payara_version").isEmpty() == false){
                                output.append(props.getProperty("payara_version"));
                            }
                            if (props.getProperty("payara_update_version").isEmpty() == false){
                                output.append("." + props.getProperty("payara_update_version"));
                            }
                            if (props.getProperty("build_id").isEmpty() == false){
                                output.append(" Build Number " + props.getProperty("build_id"));
                            }

                            System.err.println(output.toString());
                        } catch (FileNotFoundException ex) {
                            Logger.getLogger(PayaraMicro.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (IOException io){
                            Logger.getLogger(PayaraMicro.class.getName()).log(Level.SEVERE, null, io);
                        }
                        System.exit(1);
                        break;
                    }
                    case "--maxHttpThreads": {
                        String threads = args[i + 1];
                        try {
                            cfgOpts.setMaxHttpThreads(Integer.parseInt(threads));
                            if (cfgOpts.getMaxHttpThreads() < 2) {
                                throw new NumberFormatException("Maximum Threads must be 2 or greater");
                            }
                        } catch (NumberFormatException nfe) {
                            logger.log(Level.SEVERE, "{0} is not a valid maximum threads number and will be ignored", threads);
                            throw new IllegalArgumentException();
                        }
                        i++;
                        break;
                    }
                    case "--minHttpThreads": {
                        String threads = args[i + 1];
                        try {
                            cfgOpts.setMinHttpThreads(Integer.parseInt(threads));
                            if (cfgOpts.getMinHttpThreads() < 0) {
                                throw new NumberFormatException("Minimum Threads must be zero or greater");
                            }
                        } catch (NumberFormatException nfe) {
                            logger.log(Level.SEVERE, "{0} is not a valid minimum threads number and will be ignored", threads);
                            throw new IllegalArgumentException();
                        }
                        i++;
                        break;
                    }
                    case "--mcAddress":
                        cfgOpts.setHzMulticastGroup(args[i + 1]);
                        i++;
                        break;
                    case "--clusterName" :
                        cfgOpts.setHzClusterName(args[i+1]);
                        i++;
                        break;
                    case "--clusterPassword" :
                        cfgOpts.setHzClusterPassword(args[i+1]);
                        i++;
                        break;
                    case "--mcPort": {
                        String httpPortS = args[i + 1];
                        try {
                            cfgOpts.setHzPort(Integer.parseInt(httpPortS));
                            if (cfgOpts.getHzPort() < 1 || cfgOpts.getHzPort() > 65535) {
                                throw new NumberFormatException("Not a valid tcp port");
                            }
                        } catch (NumberFormatException nfe) {
                            logger.log(Level.SEVERE, "{0} is not a valid multicast port number and will be ignored", httpPortS);
                            throw new IllegalArgumentException();
                        }
                        i++;
                        break;
                    }
                    case "--startPort":
                        String startPort = args[i + 1];
                        try {
                            cfgOpts.setHzStartPort(Integer.parseInt(startPort));
                            if (cfgOpts.getHzStartPort() < 1 || cfgOpts.getHzStartPort() > 65535) {
                                throw new NumberFormatException("Not a valid tcp port");
                            }
                        } catch (NumberFormatException nfe) {
                            logger.log(Level.SEVERE, "{0} is not a valid port number and will be ignored", startPort);
                            throw new IllegalArgumentException();
                        }
                        i++;
                        break;
                    case "--name":
                        cfgOpts.setInstanceName(args[i + 1]);
                        i++;
                        break;
                    case "--deploymentDir":
                    case "--deployDir":
                        cfgOpts.setDeploymentRoot(new File(args[i + 1]));
                        if (!cfgOpts.getDeploymentRoot().exists() || !cfgOpts.getDeploymentRoot().isDirectory()) {
                            logger.log(Level.SEVERE, "{0} is not a valid deployment directory and will be ignored", args[i + 1]);
                            throw new IllegalArgumentException();
                        }
                        i++;
                        break;
                    case "--rootDir":
                        cfgOpts.setRootDir(new File(args[i + 1]));
                        if (!cfgOpts.getRootDir().exists() || !cfgOpts.getRootDir().isDirectory()) {
                            logger.log(Level.SEVERE, "{0} is not a valid root directory and will be ignored", args[i + 1]);
                            throw new IllegalArgumentException();
                        }
                        i++;
                        break;
                    case "--deploy":
                        File deployment = new File(args[i + 1]);
                        if (!deployment.exists() || !deployment.canRead()) {
                            logger.log(Level.SEVERE, "{0} is not a valid deployment path and will be ignored", deployment.getAbsolutePath());
                        } else {
                            if (cfgOpts.getDeployments() == null) {
                                cfgOpts.setDeployments(new LinkedList<File>());
                            }
                            cfgOpts.addDeployment(deployment);
                            //deployments.add(deployment);
                        }
                        i++;
                        break;
                    case "--domainConfig":
                        cfgOpts.setAlternateDomainXML(new File(args[i + 1]));
                        if (!cfgOpts.getAlternateDomainXML().exists() || !cfgOpts.getAlternateDomainXML().isFile() || !cfgOpts.getAlternateDomainXML().canRead() || !cfgOpts.getAlternateDomainXML().getAbsolutePath().endsWith(".xml")) {
                            logger.log(Level.SEVERE, "{0} is not a valid path to an xml file and will be ignored", cfgOpts.getAlternateDomainXML().getAbsolutePath());
                            throw new IllegalArgumentException();
                        }
                        i++;
                        break;
                    case "--noCluster":
                        cfgOpts.setNoCluster(true);
                        break;
                    case "--lite":
                        cfgOpts.setLiteMember(true);
                        break;
                    case "--hzConfigFile":
                        File testFile = new File(args[i + 1]);
                        if (!testFile.exists() || !testFile.isFile() || !testFile.canRead() || !testFile.getAbsolutePath().endsWith(".xml")) {
                            logger.log(Level.SEVERE, "{0} is not a valid path to an xml file and will be ignored", testFile.getAbsolutePath());
                            throw new IllegalArgumentException();
                        }
                        cfgOpts.setAlternateHZConfigFile(testFile.toURI());
                        i++;
                        break;
                    case "--autoBindHttp":
                        cfgOpts.setAutoBindHttp(true);
                        break;
                    case "--autoBindSsl":
                        cfgOpts.setAutoBindSsl(true);
                        break;
                    case "--autoBindRange":
                        String autoBindRangeString = args[i + 1];
                        try {
                            cfgOpts.setAutoBindRange(Integer.parseInt(autoBindRangeString));
                            if (cfgOpts.getAutoBindRange() < 1) {
                                throw new NumberFormatException("Not a valid auto bind range");
                            }
                        } catch (NumberFormatException nfe) {
                            logger.log(Level.SEVERE,
                                    "{0} is not a valid auto bind range number",
                                    autoBindRangeString);
                            throw new IllegalArgumentException();
                        }
                        i++;
                        break;
                    case "--enableHealthCheck":
                        try {
                            String enableHealthCheckString = args[i + 1];
                            cfgOpts.setEnableHealthCheck(Boolean.valueOf(enableHealthCheckString));
                        } catch (ArrayIndexOutOfBoundsException e) {
                            logger.log(Level.SEVERE, "Value must be given for this command.");
                        }
                        break;
                    case "--deployFromGAV":
                        if (cfgOpts.getGAVs() == null) {
                            cfgOpts.setGAVs(new LinkedList<String>());
                        }
                        
                        cfgOpts.addGAVs(args[i+1]);
                        i++;
                        break;
                    case "--additionalRepository":
                        try {
                            // If there isn't a trailing /, add one
                            if (!args[i + 1].endsWith("/")) {
                                cfgOpts.addRepositoryURL(new URL(args[i + 1] + "/"));
                            } else {
                                cfgOpts.addRepositoryURL(new URL(args[i + 1]));
                            }
                        } catch (MalformedURLException ex) {
                            logger.log(Level.SEVERE, "{0} is not a valid URL and will be ignored", args[i + 1]);
                        }

                        i++;
                        break;
                    case "--outputUberJar":
                        cfgOpts.setUberJar(new File(args[i + 1]));
                        i++;
                        break;
                    case "--systemProperties": {
                        File propertiesFile = new File(args[i + 1]);
                        cfgOpts.setUserSystemProperties(new Properties());
                        try (FileReader reader = new FileReader(propertiesFile)) {
                            cfgOpts.getUserSystemProperties().load(reader);
                            Enumeration<String> names = (Enumeration<String>) cfgOpts.getUserSystemProperties().propertyNames();
                            while (names.hasMoreElements()) {
                                String name = names.nextElement();
                                System.setProperty(name, cfgOpts.getUserSystemProperties().getProperty(name));
                            }
                        } catch (IOException e) {
                            logger.log(Level.SEVERE,
                                    "{0} is not a valid properties file",
                                    propertiesFile.getAbsolutePath());
                            throw new IllegalArgumentException(e);
                        }
                        if (!propertiesFile.isFile() && !propertiesFile.canRead()) {
                            logger.log(Level.SEVERE,
                                    "{0} is not a valid properties file",
                                    propertiesFile.getAbsolutePath());
                            throw new IllegalArgumentException();

                        }
                    }
                    break;
                    case "--disablePhoneHome":
                        cfgOpts.setDisablePhoneHome(true);
                        break;
                    case "--help":
                        System.err.println("Usage:\n  --noCluster  Disables clustering\n"
                                + "  --port <http-port-number> sets the http port\n"
                                + "  --sslPort <ssl-port-number> sets the https port number\n"
                                + "  --mcAddress <muticast-address> sets the cluster multicast group\n"
                                + "  --mcPort <multicast-port-number> sets the cluster multicast port\n"
                                + "  --clusterName <cluster-name> sets the Cluster Group Name\n"
                                + "  --clusterPassword <cluster-password> sets the Cluster Group Password\n"
                                + "  --startPort <cluster-start-port-number> sets the cluster start port number\n"
                                + "  --name <instance-name> sets the instance name\n"
                                + "  --rootDir <directory-path> Sets the root configuration directory and saves the configuration across restarts\n"
                                + "  --deploymentDir <directory-path> if set to a valid directory all war files in this directory will be deployed\n"
                                + "  --deploy <file-path> specifies a war file to deploy\n"
                                + "  --domainConfig <file-path> overrides the complete server configuration with an alternative domain.xml file\n"
                                + "  --minHttpThreads <threads-number> the minimum number of threads in the HTTP thread pool\n"
                                + "  --maxHttpThreads <threads-number> the maximum number of threads in the HTTP thread pool\n"
                                + "  --hzConfigFile <file-path> the hazelcast-configuration file to use to override the in-built hazelcast cluster configuration\n"
                                + "  --autoBindHttp sets autobinding of the http port to a non-bound port\n"
                                + "  --autoBindSsl sets autobinding of the https port to a non-bound port\n"
                                + "  --autoBindRange <number-of-ports> sets the maximum number of ports to look at for port autobinding\n"
                                + "  --lite sets the micro container to lite mode which means it clusters with other Payara Micro instances but does not store any cluster data\n"
                                + "  --enableHealthCheck <boolean> enables/disables Health Check Service (disabled by default).\n"
                                + "  --logo reveal the #BadAssFish\n"
                                + "  --deployFromGAV <list-of-artefacts> specifies a comma separated groupId,artifactId,versionNumber of an artefact to deploy from a repository\n"
                                + "  --additionalRepository <repo-url> specifies an additional repository to search for deployable artefacts in\n"
                                + "  --outputUberJar <file-path> packages up an uber jar at the specified path based on the command line arguments and exits\n"
                                + "  --systemProperties <file-path> Reads system properties from a file\n"
                                + "  --disablePhoneHome Disables sending of usage tracking information\n"
                                + "  --version Displays the version information\n"
                                + "  --logToFile <file-path> outputs all the Log entries to a user defined file\n"
                                + "  --logProperties <file-path> Allows user to set their own logging properties file\n"
                                + "  --accessLog <directory-path> Sets user defined directory path for the access log\n"
                                + "  --accessLogFormat Sets user defined log format for the access log\n"
                                + "  --help Shows this message and exits\n");
                        System.exit(1);
                        break;
                    case "--logToFile":
                        // setUserLogFile(args[i + 1]);
                        break;
                    case "--accessLog":
                        cfgOpts.setAccessLogFile(new File(args[i + 1]));
                        if (!cfgOpts.getAccessLogFile().exists() || !cfgOpts.getAccessLogFile().isDirectory() || !cfgOpts.getAccessLogFile().canWrite()) {
                            logger.log(Level.SEVERE, "{0} is not a valid directory for storing access logs as it must exist and be writable", cfgOpts.getAccessLogFile().getAbsolutePath());                            
                            throw new IllegalArgumentException();
                        }
                        //setAccessLogDir(file.getAbsolutePath());
			break;
                    case "--accessLogFormat":
                        cfgOpts.setAccessLogFormat(args[i + 1]);
                        //setAccessLogFormat(args[i + 1]);
			break;
                    case "--logProperties":
                        File x = new File(args[i+1]);
                        //File logPropertiesActualFile = args[i+1];
                        //cfgOpts.setLogPropertiesFile(args[i + 1]));
                        if (!x.exists() || !x.canRead() || x.isDirectory() ) {
                            logger.log(Level.SEVERE, "{0} is not a valid properties file path", x);
                            throw new IllegalArgumentException();
                        }
                        else {
                            cfgOpts.setLogPropertiesFile(x);
                        }
                        //setLogPropertiesFile(logPropertiesFile)
                        break;
                    case "--logo":
                        cfgOpts.setGenerateLogo(true);
                        break;
                }
            }
        }
    }
}