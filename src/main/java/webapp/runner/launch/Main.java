/**
 * Copyright (c) 2012, John Simone
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *    Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *    following disclaimer.
 *
 *    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 *    the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *    Neither the name of John Simone nor the names of its contributors may be used to endorse or
 *    promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package webapp.runner.launch;

import com.beust.jcommander.JCommander;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.ExpandWar;
import org.apache.catalina.startup.Tomcat;

import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is the main entry point to webapp-runner. Helpers are called to parse the arguments.
 * Tomcat configuration and launching takes place here.
 */
@SuppressWarnings({"HardCodedStringLiteral", "LocalCanBeFinal"})
public class Main {

  public static void main(String[] args) throws Exception {

    CommandLineParams commandLineParams = new CommandLineParams();

    JCommander jCommander = new JCommander(commandLineParams, args);

    if (commandLineParams.help) {
      jCommander.usage();
      System.exit(1);
    }

    // default to src/main/webapp
    if (commandLineParams.paths.size() == 0) {
      commandLineParams.paths.add("src/main/webapp");
    }

    if (commandLineParams.paths.size() > 1) {
      System.out.println("WARNING: multiple paths are specified, but no longer supported. First path will be used.");
    }

    // Get the first path
    String path = commandLineParams.paths.get(0);
    File war = new File(path);

    if (!war.exists()) {
      System.err.println("The specified path \"" + path + "\" does not exist.");
      jCommander.usage();
      System.exit(1);
    }

    // Use the commandline context-path (or default)
    // warn if the contextPath doesn't start with a '/'. This causes issues serving content at the context root.
    if (commandLineParams.contextPath.length() > 0 && !commandLineParams.contextPath.startsWith("/")) {
      System.out.println("WARNING: You entered a path: [" + commandLineParams.contextPath + "]. Your path should start with a '/'. Tomcat will update this for you, but you may still experience issues.");
    }


    Tomcat tomcat = createTomcatServer(commandLineParams, null, new ContextDefinition(war, commandLineParams.contextPath));

    addShutdownHook(tomcat);

    tomcat.getServer().await();
  }

  public static Tomcat createTomcatServer(CommandLineParams commandLineParams,
                                          TomcatConfigurator configurator,
                                          ContextDefinition... warLocations) throws Exception {
    final Tomcat tomcat = new Tomcat();

    // set directory for temp files
    tomcat.setBaseDir(resolveTomcatBaseDir(commandLineParams.port));

    // initialize the connector
    Connector nioConnector = configureConnector(commandLineParams);
    tomcat.setConnector(nioConnector);
    tomcat.getService().addConnector(tomcat.getConnector());
    tomcat.setPort(commandLineParams.port);
    if (commandLineParams.enableBasicAuth || commandLineParams.tomcatUsersLocation != null) {
      tomcat.enableNaming();
    }

    for (ContextDefinition war : warLocations) {
      configureWarContext(configurator, commandLineParams, tomcat, war);
    }

    if (configurator != null) {
      configurator.configure(tomcat, commandLineParams, warLocations);
    }

    //start the server
    tomcat.start();

        /*
         * NamingContextListener.lifecycleEvent(LifecycleEvent event)
         * cannot initialize GlobalNamingContext for Tomcat until
         * the Lifecycle.CONFIGURE_START_EVENT occurs, so this block
         * must sit after the call to tomcat.start() and it requires
         * tomcat.enableNaming() to be called much earlier in the code.
         */
    if (commandLineParams.enableBasicAuth || commandLineParams.tomcatUsersLocation != null) {
      TomcatUtil.configureUserStore(tomcat, commandLineParams);
    }
    return tomcat;
  }

  private static void configureWarContext(TomcatConfigurator configurator,
                                          CommandLineParams commandLineParams,
                                          Tomcat tomcat,
                                          ContextDefinition war) throws IOException, ServletException {
    Context ctx = configureContext(commandLineParams, tomcat, war);
    TomcatUtil.configureShutdownHandler(commandLineParams, tomcat, ctx);

    Properties contextConfiguration = war.getContextConfiguration();
    for (Object key : contextConfiguration.keySet()) {
      String keyText = String.valueOf(key);
      ctx.addParameter(keyText, contextConfiguration.getProperty(keyText));
    }

    // set the context xml location if there is only one war
    String contextXml = war.getContextXml();
    if (contextXml != null) {
      System.out.println("Using context config: " + contextXml);
      ctx.setConfigFile(new File(contextXml).toURI().toURL());
    }

    // set the session manager
    if (commandLineParams.sessionStore != null) {
      SessionStore.getInstance(commandLineParams.sessionStore).configureSessionStore(commandLineParams, ctx);
    }

    //set the session timeout
    if (commandLineParams.sessionTimeout != null) {
      ctx.setSessionTimeout(commandLineParams.sessionTimeout);
    }

    if (commandLineParams.enableBasicAuth) {
      TomcatUtil.enableBasicAuth(ctx, commandLineParams.enableSSL);
    }
    if (configurator != null) {
      configurator.configureContext(tomcat, commandLineParams, war, ctx);
    }
  }

  private static Context configureContext(CommandLineParams commandLineParams,
                                          Tomcat tomcat,
                                          ContextDefinition context)
          throws IOException, ServletException {

    final String ctxName = context.getContextPath();
    final File war = context.getWar();
    if (commandLineParams.expandWar && war.isFile()) {
      File appBase = new File(System.getProperty(Globals.CATALINA_BASE_PROP), tomcat.getHost().getAppBase());
      if (appBase.exists()) {
        appBase.delete();
      }
      appBase.mkdir();
      URL fileUrl = new URL("jar:" + war.toURI().toURL() + "!/");
      String expandedDir = ExpandWar.expand(tomcat.getHost(), fileUrl, "/expanded");
      System.out.println("Expanding " + war.getName() + " into " + expandedDir);
      System.out.println("Adding Context " + ctxName + " for " + expandedDir);
      return addWebapp(tomcat, ctxName, expandedDir);
    } else {
      System.out.println("Adding Context " + ctxName + " for " + war.getPath());
      return addWebapp(tomcat, ctxName, war.getAbsolutePath());
    }
  }


  public static Context addWebapp(Tomcat tomcat, String url, String path) {
    Host host = tomcat.getHost();
    silence(host, url);

    Context ctx = new OverrideContext();
    ctx.setName(url);
    ctx.setPath(url);
    ctx.setDocBase(path);
    ctx.addLifecycleListener(new Tomcat.DefaultWebXmlListener());

    ContextConfig ctxCfg = new ContextConfig();
    // prevent it from looking ( if it finds one - it'll have dup error )
    ctxCfg.setDefaultWebXml(tomcat.noDefaultWebXmlPath());
    ctx.addLifecycleListener(ctxCfg);

    host.addChild(ctx);
    return ctx;
  }

  private static void silence(Host host, String ctx) {
    Logger.getLogger(TomcatUtil.getLoggerName(host, ctx)).setLevel(Level.WARNING);
  }

  private static Connector configureConnector(CommandLineParams commandLineParams) {
    Connector nioConnector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
    nioConnector.setPort(commandLineParams.port);

    if (commandLineParams.enableSSL) {
      configureSSL(commandLineParams, nioConnector);
    }

    if (commandLineParams.enableCompression) {
      nioConnector.setProperty("compression", "on");
      nioConnector.setProperty("compressableMimeType", commandLineParams.compressableMimeTypes);
    }

    if (commandLineParams.uriEncoding != null) {
      nioConnector.setURIEncoding(commandLineParams.uriEncoding);
    }
    return nioConnector;
  }

  private static void configureSSL(CommandLineParams commandLineParams, Connector nioConnector) {
    nioConnector.setSecure(true);
    nioConnector.setProperty("SSLEnabled", "true");
    String pathToTrustStore = System.getProperty("javax.net.ssl.trustStore");
    if (pathToTrustStore != null) {
      nioConnector.setProperty("sslProtocol", "tls");
      File truststoreFile = new File(pathToTrustStore);
      nioConnector.setAttribute("truststoreFile", truststoreFile.getAbsolutePath());
      System.out.println(truststoreFile.getAbsolutePath());
      nioConnector.setAttribute("trustStorePassword", System.getProperty("javax.net.ssl.trustStorePassword"));
    }
    String pathToKeystore = System.getProperty("javax.net.ssl.keyStore");
    if (pathToKeystore != null) {
      File keystoreFile = new File(pathToKeystore);
      nioConnector.setAttribute("keystoreFile", keystoreFile.getAbsolutePath());
      System.out.println(keystoreFile.getAbsolutePath());
      nioConnector.setAttribute("keystorePass", System.getProperty("javax.net.ssl.keyStorePassword"));
    }
    if (commandLineParams.enableClientAuth) {
      nioConnector.setAttribute("clientAuth", true);
    }
  }

  /**
   * Gets or creates temporary Tomcat base directory within target dir
   *
   * @param port port of web process
   * @return absolute dir path
   * @throws IOException if dir fails to be created
   */
  protected static String resolveTomcatBaseDir(Integer port) throws IOException {
    final File baseDir = new File(System.getProperty("user.dir") + "/target/tomcat." + port);
    new File(baseDir, "webapps").mkdirs();

    if (!baseDir.isDirectory() && !baseDir.mkdirs()) {
      throw new IOException("Could not create temp dir: " + baseDir);
    }

    try {
      return baseDir.getCanonicalPath();
    } catch (IOException e) {
      return baseDir.getAbsolutePath();
    }
  }

  /**
   * Stops the embedded Tomcat server.
   */
  public static void addShutdownHook(final Tomcat tomcat) {

    // add shutdown hook to stop server
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        try {
          if (tomcat != null) {
            tomcat.getServer().stop();
          }
        } catch (LifecycleException exception) {
          throw new RuntimeException("WARNING: Cannot Stop Tomcat " + exception.getMessage(), exception);
        }
      }
    });
  }
}
