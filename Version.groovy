#!/usr/bin/env groovy -w
// Luca Francesca, 2015
package me.lucafrancesca.gpack
import javax.management.ObjectName
import javax.management.remote.JMXConnectorFactory as JmxFactory
import javax.management.remote.JMXServiceURL as JmxUrl
import java.util.regex.Matcher
import java.util.regex.Pattern


class AppVersion {
    public AppVersion() {
    } 

    /**
     * Get JMX data from host
     *
     * @param serverHost The host name
     * @param serverPort The app port.
     * @param mbeanString The JM string
     * @param versionAttributeName The JM attribute
     * @param appName The name of the app. 
     * @return The app string.
     */ 
    private static getJmxVersion(serverHost, serverPort, mbeanString, versionAttributeName, appName) {
           try {
              def serverUrl     = "service:jmx:rmi:///jndi/rmi://$serverHost:$serverPort/jmxrmi"
              def server        = JmxFactory.connect(new JmxUrl(serverUrl)).MBeanServerConnection
              def serverInfo    = new GroovyMBean(server, mbeanString).getProperty(versionAttributeName)
              return [serverHost, serverInfo]
           }
           catch(Exception e) { }   
    } 
    /**
     * Get HTTP data from URI
     *
     * @param  URI The URI 
     * @return The version.
     */ 
    private static getHttpVersions(URI) {
        try {
            new URL(URI).getText()        
        }
        catch (Exception e) { }
    }
    /**
     * Get data 
     *
     * @param  appName The app name 
     * @param  appEnv  The app env 
     * @return The version.
     */ 
    public static getData(AppName, appEnv) {
        def conf
        def env
        def appName
        def clusterName
        def tmpVersion
        def platform1Version
        def platform2Version
        def appVersionProtocol
        def appVersionURI
        def platform1Name
        def platform1Protocol
        def platform1VersionURI
        def platform2Name
        def platform2Protocol
        def platform2VersionURI
        def hosts = []
        def appList = []
        def tmpHost
        new File("H:\\working\\config.txt").each { line ->
            conf                      = line.split('#')
            env                       = conf[0]
            appName                   = conf[1]
            clusterName               = conf[2]
            appVersionProtocol        = conf[3]
            appVersionURI             = conf[4]
            platform1Name             = conf[5]
            platform1Protocol         = conf[6]
            platform1VersionURI       = conf[7]
            platform2Name             = conf[8]
            platform2Protocol         = conf[9]
            platform2VersionURI       = conf[10]
            hosts                     = conf[11].split(',') 
            if (appName == AppName && env == appEnv) {
                def conf2 
                def tmp
                def port        
                def mbeansString 
                def mbeansProp   
                hosts.each { hostApp ->
                    tmpHost = hostApp
                    if (appVersionProtocol == "JMX") {
                        conf2 = appVersionURI.split('@')
                        port         = conf2[0]
                        mbeansString = conf2[1]
                        mbeansProp   = conf2[2]
                        tmp =  getJmxVersion(hostApp, port, mbeansString, mbeansProp, AppName)
                        tmpVersion = tmp[1]
                        } 
                    else {
                        tmpVersion =  getHttpVersions(appVersionURI)
                        }
                    if (platform1Protocol == "JMX") {
                        conf = platform1VersionURI.split('@')
                        port         = conf[0]
                        mbeansString = conf[1]
                        mbeansProp   = conf[2]
                        tmp =  getJmxVersion(hostApp, port, mbeansString, mbeansProp, AppName)
                        platform1Version = tmp[1]
                        } 
                    else {
                        tmpVersion =  getHttpVersions(platform1VersionURI)
                        }
                    if (platform2Protocol == "JMX") {
                        conf = platform2VersionURI.split('@')
                        port         = conf[0]
                        mbeansString = conf[1]
                        mbeansProp   = conf[2]
                        tmp =  getJmxVersion(hostApp, port, mbeansString, mbeansProp, AppName)
                        platform2Version = tmp[1]
                        } 
                    else {
                        tmpVersion = getHttpVersions(platform2VersionURI)
                        }
                        appList.add([tmpHost, tmpVersion, platform1Name, platform1Version, platform2Name, platform2Version, clusterName])
                }
            } else { //discard the uneeded clutter 
            }
        }
        
        def jsonBuilder = new groovy.json.JsonBuilder()
           jsonBuilder.app(
                env: env,
                name: appName,
                appversion: tmpVersion,
                hosts: appList.collect {[name: it[0],appversion: it[1],platform1Name: it[2],
                                        platform1Version: it[3], platform2Name:it[4],
                                        platform2Version: it[5], memberOf: it[6] ] }
            )
       return jsonBuilder.toPrettyString()
    }
 
}

def ver = new me.lucafrancesca.gpack.AppVersion()
