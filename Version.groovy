#!/usr/bin/env groovy
// Luca Francesca, 2014
package me.lucafrancesca.gpack
import javax.management.ObjectName
import javax.management.remote.JMXConnectorFactory as JmxFactory
import javax.management.remote.JMXServiceURL as JmxUrl
import java.util.regex.Matcher
import java.util.regex.Pattern
import groovy.json.*

/**
* Main class for the app
*
*/ 
class BVersion {
    // Variables
    private static timeStamp
    private static jmx   = [:]
    private static http  = [:]
    private static appsM = [:]
    // Config Variables
    private static path        = '/export/apps/appstatus'
    private static configFile  = "${path}/conf/source.txt"
    private static jmxFile     = "${path}/conf/jmx.txt"
    private static httpFile    = "${path}/conf/http.txt"  
    private static appNameFile = "${path}/app.json" 
    private static allFile     = "${path}/all.txt"
    private static separator   = '@' 
    /**
    * Constructor which loads the jmx and http specific settings and set a timestamp
    *
    */ 
    public BVersion() {
        loadJmxSpecs()
        loadHttpSpecs()
        writeJSon()
        timeStamp = new Date()
    } 
    /**
    * Load Jmx configuration map from file jmx.txt
    *
    * @return Nothing.
    */
    private static loadJmxSpecs() {
            def conf
            def string
            def app
            def jmxSpecsFile = new File(jmxFile)
            jmxSpecsFile.eachLine { line ->
            conf   = line.split(separator)
            string = conf[0].replaceAll("\\s","")
            app    = conf[1].replaceAll("\\s","")
            jmx.put(app,string)
            if (appsM.containsValue(app)) {
                 // avoid duplicates
            }
            else{
              appsM.put(app,app)
            
            } 
        }
    }   
    /**
    * Load Http configuration map from file http.txt
    *
    * @return Nothing.
    */
    private static loadHttpSpecs() {
     try {
             def conf
             def uri
             def appName
             def httpSpecsFile = new File(httpFile)
             def listForApp = []
             httpSpecsFile.eachLine { line ->
                 conf       = line.split(separator)
                 appName    = conf[0].replaceAll("\\s","")
                 uri        = conf[1].replaceAll("\\s","")
                 listForApp.add(uri)
                 http.put(appName,listForApp)
                 if (appsM.containsValue(appName)) {
                   // avoid duplicates
                 }
                 else {
                   appsM.put(appName,appName)
                 }
             }
      } catch (java.lang.ArrayIndexOutOfBoundsException e) {}
    }    
     /**
     * Write the JSON for the webGUI
     *
     * @return None.
     */
    private static writeJSon() {
       def data =  [
             apps:  appsM.collect() {entry -> [(entry.key): entry.value]} 
       ]
       def json = new JsonBuilder(data)
       def fOut = new File(appNameFile)
       fOut.delete() // just to be sure
       fOut.write(json.toPrettyString())
    } 
    /**
     * Get the env from the host
     *
     * @param host The host in question.
     * @return The host string.
     */
    static getEnv(host) {
        def hostMatcher = host.replaceAll("\\s","") =~ /[0-9]{2}(.*)/
        return hostMatcher[0][1]
    }
     /**
     * Get the env from the http host
     *
     * @param host The host in question.
     * @return The host string.
     */
    static getHttpEnv(host) {
        def hostMatcher = host.replaceAll("\\s","") =~ /[0-9]{2}(.*)/
        hostMatcher[0][1].split(":")[0]
    }
     /**
     * Get the tomcat version from the host string
     *
     * @param host The host in question.
     * @return The tomcat version string.
     */
    static TomVer(host) {
        def tomcatMatcher = host.replaceAll("\\s","") =~ /(.*)[\/](.*)/
        return tomcatMatcher[0][2]
    }
     /**
     * Get java version
     *
     * @param serverHost The host
     * @param serverPort The port
     * @return The version
     */
     static private getJavaVersion(serverHost, serverPort) {
        try {
           def serverUrl     = "service:jmx:rmi:///jndi/rmi://$serverHost:$serverPort/jmxrmi"
           def server        = JmxFactory.connect(new JmxUrl(serverUrl)).MBeanServerConnection
           def javaInfo      = new GroovyMBean(server, 'JMImplementation:type=MBeanServerDelegate').ImplementationVersion
           def hostEnv       = getEnv(serverHost)
           writeToallFile("</br><b> At $timeStamp,  Java on <i>$serverHost</i> is <b>$javaInfo</b></br>")
           return "</br><b>Java on <i>$serverHost</i> is <b>$javaInfo</b></br>"
        }
        catch(javax.management.InstanceNotFoundException e) { }
        catch(java.io.IOException e) { }
     }
    /**
     * Get app versions via HTTP
     *
     * @param appName The name of the app.
     * @param env The env
     * @return The version map.
     */
    private static HttpVersions(appName, env) {
        try {
        def versions = [:]
        def appVersionsList = [:]
        def version 
        def hostMatcher
        def host
        def app
        def uriList = http.get(appName)
        def appMatcher
        uriList.each() { element ->
               // derive host from uri
               hostMatcher = element  =~ /http\:\/\/(.*)\:[0-9]{2,4}\/(.*)/ 
               app = hostMatcher[0][2]
               host = hostMatcher[0][1] 
               if (app.contains(appName) && getHttpEnv(host) == env) {                
                   try {
                       version = new URL(element).getText()
                   }
                   catch(UnknownHostException e) { }
                   catch(FileNotFoundException e) {}
                   appVersionsList.put(host, version)
                   versions.put("${appName}", appVersionsList)
               }
               else {
                   return
               } 
        }
        return versions
        } catch (java.lang.ArrayIndexOutOfBoundsException e) {}
        catch (java.lang.IndexOutOfBoundsException e) {}
    }
     /**
     * Get app version via HTTP
     *
     * @param appName The name of the app.
     * @param env The env
     * @return Nothing
     */
    static private getHttpVersion(appName, env) {
        def appVer = HttpVersions(appName, env) // return is the last statement (by groovy default)
    }
    /**
     * Get app version via JMX
     *
     * @param host The host name
     * @param port The app port.
     * @param appName The name of the app.
     * @return The app string.
     */ 
    static private getJmxVersion(host, port, appName) {
         def res
         res = getJmxData(host, port, jmx[appName], appName)
         return res  
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
    static private getJmxData(serverHost, serverPort, mbeanString, versionAttributeName, appName) {
           try {
              def serverUrl     = "service:jmx:rmi:///jndi/rmi://$serverHost:$serverPort/jmxrmi"
              def server        = JmxFactory.connect(new JmxUrl(serverUrl)).MBeanServerConnection
              def serverInfo    = new GroovyMBean(server, mbeanString).getProperty(versionAttributeName)
              def hostEnv       = getEnv(serverHost)
              writeToallFile("</br><b> At $timeStamp,  <i>$appName</i> has <b>$serverInfo on $serverHost</b></br>")
              return "</br><b> At $timeStamp,  <i>$appName</i> has <b>$serverInfo on $serverHost</b></br>"
           }
           catch(javax.management.InstanceNotFoundException e) { }
           catch(java.io.IOException e) { }
       } 

    /**
     * Test function (usefull to check) reading from source.txt
     *
     * @param env The env
     * @param appName The app name.
     * @return Nothing
     */ 
    static getJmxVersionPrint(env, appName) {
        def siteFile = new File(configFile)
        def host
        def port
        def conf
        siteFile.eachLine { line ->
            conf = line.split(separator)
            host = conf[0].replaceAll("\\s","")
            port = conf[1].replaceAll("\\s","")
            if (getEnv(host) == env) {
                try {
                   println getJmxVersion(host, port, appName)
                 }
                 catch (NullPointerException e) { }
                 catch (java.lang.ArrayIndexOutOfBoundsException e) {}
            }
        }
    }
   /**
     * Pretty print the http versions of an app
     *
     * @param appName The app name.
     * @param env The env.
     * @return Nothing
     */     
    static getHttpVersionPrint(appName, env) {   
        getHttpVersion(appName, env).each() { key, value ->
          writeToallFile("</b></br>At $timeStamp, <b>$key</b> has those versions:</b></br>")
          println "</b></br><b>$key</b> has those versions:</b></br>"
          value.each() { k, v ->
             writeToallFile("\t - version $v in host $k</br>")
             println "\t - version $v in host $k</br>"
          }
        }
    }
     /**
     * Print all versions
     *
     * @param env The env
     * @return Nothing
     */   
    static printAll(env) {
         def app
         appsM.each() { element->
           app = element.key
           try {
               getJmxVersionPrint(env,app)
           }
           catch(javax.management.MalformedObjectNameException e) {}
           catch (java.lang.ArrayIndexOutOfBoundsException e) {}
           catch (java.lang.IndexOutOfBoundsException e) {}
        }
        appsM.each() { element->
           app = element.key
           try {
               getHttpVersionPrint(app,env)
           }
           catch(javax.management.MalformedObjectNameException e) {}
           catch (java.lang.ArrayIndexOutOfBoundsException e) {}
           catch (java.lang.IndexOutOfBoundsException e) {}
        }
    }
     /**
     * Write info to a file
     *
     * @param content The content to write.
     * @return Nothing
     */   
    private static writeToallFile(content) {
        new File(allFile).withWriterAppend{ out ->
            content.each {
                out << it 
            }
        }
        new File(allFile).append("\n")    
    }

}
//

def ver = new com.vcint.statusall.BVersion()

if (this.args.length == 0) {
  println "Usage is with those args: ENV APP TYPE"
}
else if (this.args.length == 1){
   ver.printAll(this.args[0]) 
}
else {
   
  try {
  switch (this.args[2]) { // switch on application name
       case "http":
           ver.getHttpVersionPrint(this.args[1],this.args[0])
           break
       case "jmx":  
           ver.getJmxVersionPrint(this.args[0],this.args[1])
           break  
       default:
           println "\t Only HTTP or JMX are supported"
   }  
  } 
  catch(java.lang.ArrayIndexOutOfBoundsException e) {} 
}
