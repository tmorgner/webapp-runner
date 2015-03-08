package webapp.runner.launch.factory;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.ExpandWar;
import org.apache.catalina.startup.Tomcat;
import webapp.runner.launch.CommandLineParams;
import webapp.runner.launch.SessionStore;
import webapp.runner.launch.TomcatConfigurator;
import webapp.runner.launch.helper.ContextDefinition;
import webapp.runner.launch.helper.OverrideContext;
import webapp.runner.launch.helper.TomcatUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.ServletSecurity;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.logging.Logger;

public class ContextFactory {

  private Logger logger = Logger.getLogger(ContextFactory.class.getName());

  private static final String AUTH_ROLE = "user";
  private static final String AUTH_METHOD = "BASIC";

  /*
   * Set up basic auth security on the entire application
   */
  protected void enableBasicAuth(Context ctx, boolean enableSSL) {
    LoginConfig loginConfig = new LoginConfig();
    loginConfig.setAuthMethod(AUTH_METHOD);
    ctx.setLoginConfig(loginConfig);
    ctx.addSecurityRole(AUTH_ROLE);

    SecurityConstraint securityConstraint = new SecurityConstraint();
    securityConstraint.addAuthRole(AUTH_ROLE);
    if (enableSSL) {
      securityConstraint.setUserConstraint(ServletSecurity.TransportGuarantee.CONFIDENTIAL.toString());
    }
    SecurityCollection securityCollection = new SecurityCollection();
    securityCollection.addPattern("/*");
    securityConstraint.addCollection(securityCollection);
    ctx.addConstraint(securityConstraint);
  }

  public void configureWarContext(TomcatConfigurator configurator,
                                     CommandLineParams commandLineParams,
                                     Tomcat tomcat,
                                     ContextDefinition war) throws IOException, ServletException {
    Context ctx = configureContext(commandLineParams, tomcat, war);
    configureShutdownHandler(commandLineParams, tomcat, ctx);

    Properties contextConfiguration = war.getContextConfiguration();
    for (Object key : contextConfiguration.keySet()) {
      String keyText = String.valueOf(key);
      ctx.addParameter(keyText, contextConfiguration.getProperty(keyText));
    }

    // set the context xml location if there is only one war
    String contextXml = war.getContextXml();
    URL contextUrl = computeContextXmlLocation(contextXml, tomcat.getHost(), ctx);
    if (contextUrl != null) {
      logger.fine("Using context config: " + contextXml);
      ctx.setConfigFile(contextUrl);
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
      enableBasicAuth(ctx, commandLineParams.enableSSL);
    }
    if (configurator != null) {
      configurator.configureContext(tomcat, commandLineParams, war, ctx);
    }
  }

  protected URL computeContextXmlLocation(String contextXml,
                                          Host host, Context ctx) throws MalformedURLException {
    if (contextXml != null) {
      return new File(contextXml).toURI().toURL();
    }
    else {
      return TomcatUtil.getWebappConfigFile(host, ctx.getDocBase(), ctx.getPath());
    }
  }

  protected Context configureContext(CommandLineParams commandLineParams,
                                     Tomcat tomcat,
                                     ContextDefinition context)
          throws IOException, ServletException {

    final String ctxName = context.getContextPath();
    final File war = context.getWar();
    if (commandLineParams.expandWar && war.isFile()) {
      File appBase = new File(commandLineParams.baseDir, tomcat.getHost().getAppBase());
      if (appBase.exists()) {
        appBase.delete();
      }
      appBase.mkdir();
      URL fileUrl = new URL("jar:" + war.toURI().toURL() + "!/");
      String expandedDir = ExpandWar.expand(tomcat.getHost(), fileUrl, "/expanded");
      logger.fine("Expanding " + war.getName() + " into " + expandedDir);
      logger.fine("Adding Context " + ctxName + " for " + expandedDir);
      return addWebapp(tomcat, ctxName, expandedDir);
    }
    else {
      logger.fine("Adding Context " + ctxName + " for " + war.getPath());
      return addWebapp(tomcat, ctxName, war.getAbsolutePath());
    }
  }

  protected Context addWebapp(Tomcat tomcat, String url, String path) {
    Host host = tomcat.getHost();
    TomcatUtil.silence(host, url);

    Context ctx = createContext();
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

  protected Context createContext() {
    return new OverrideContext();
  }


  public void configureShutdownHandler(CommandLineParams commandLineParams, final Tomcat tomcat, Context ctx) {
    if (!commandLineParams.shutdownOverride) {
      // allow Tomcat to shutdown if a context failure is detected
      final String ctxName = commandLineParams.contextPath;
      ctx.addLifecycleListener(new ContextShutdownListener(tomcat, ctxName));
    }
  }

  protected static class ContextShutdownListener implements LifecycleListener {
    private Logger logger = Logger.getLogger(ContextShutdownListener.class.getName());

    private final Tomcat tomcat;
    private final String ctxName;

    public ContextShutdownListener(Tomcat tomcat, String ctxName) {
      this.tomcat = tomcat;
      this.ctxName = ctxName;
    }

    public void lifecycleEvent(LifecycleEvent event) {
      if (event.getLifecycle().getState() == LifecycleState.FAILED) {
        Server server = tomcat.getServer();
        if (server instanceof StandardServer) {
          StandardServer standardServer = (StandardServer) server;
          logger.severe(
                  String.format("Context [%s] failed in [%s] lifecycle. Allowing Tomcat to shutdown.%n",
                          ctxName, event.getLifecycle().getClass().getName()));
          standardServer.stopAwait();
        }
      }
    }
  }
}
