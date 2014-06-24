package webapp.runner.launch;

import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Role;
import org.apache.catalina.Server;
import org.apache.catalina.core.StandardServer;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityCollection;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.startup.Constants;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.users.MemoryUserDatabase;
import org.apache.catalina.users.MemoryUserDatabaseFactory;

import javax.naming.CompositeName;
import javax.naming.StringRefAddr;
import javax.servlet.annotation.ServletSecurity;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TomcatUtil {
  private static final String AUTH_ROLE = "user";


  protected static URL getWebappConfigFile(Host host, String path, String url) {
      File docBase = new File(path);
      if (docBase.isDirectory()) {
          return getWebappConfigFileFromDirectory(host, docBase, url);
      } else {
          return getWebappConfigFileFromJar(host, docBase, url);
      }
  }

  private static URL getWebappConfigFileFromDirectory(Host host, File docBase, String url) {
      URL result = null;
      File webAppContextXml = new File(docBase, Constants.ApplicationContextXml);
      if (webAppContextXml.exists()) {
          try {
              result = webAppContextXml.toURI().toURL();
          } catch (MalformedURLException e) {
              Logger.getLogger(getLoggerName(host, url)).log(Level.WARNING,
                      "Unable to determine web application context.xml " + docBase, e);
          }
      }
      return result;
  }

  private static URL getWebappConfigFileFromJar(Host host, File docBase, String url) {
      URL result = null;
      JarFile jar = null;
      try {
          jar = new JarFile(docBase);
          JarEntry entry = jar.getJarEntry(Constants.ApplicationContextXml);
          if (entry != null) {
              result = new URL("jar:" + docBase.toURI().toString() + "!/"
                      + Constants.ApplicationContextXml);
          }
      } catch (IOException e) {
          Logger.getLogger(getLoggerName(host, url)).log(Level.WARNING,
                  "Unable to determine web application context.xml " + docBase, e);
      } finally {
          if (jar != null) {
              try {
                  jar.close();
              } catch (IOException e) {
                  // ignore
              }
          }
      }
      return result;
  }

  public static String getLoggerName(Host host, String ctx) {
    String loggerName = "org.apache.catalina.core.ContainerBase.[default].[";
    loggerName += host.getName();
    loggerName += "].[";
    loggerName += ctx;
    loggerName += "]";
    return loggerName;
  }

  /*
   * Set up basic auth security on the entire application
   */
  public static void enableBasicAuth(Context ctx, boolean enableSSL) {
    LoginConfig loginConfig = new LoginConfig();
    loginConfig.setAuthMethod("BASIC");
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

  public static void configureUserStore(final Tomcat tomcat, final CommandLineParams commandLineParams) throws Exception {
    String tomcatUsersLocation = commandLineParams.tomcatUsersLocation;
    if (tomcatUsersLocation == null) {
      tomcatUsersLocation = "../../tomcat-users.xml";
    }

    javax.naming.Reference ref = new javax.naming.Reference("org.apache.catalina.UserDatabase");
    ref.add(new StringRefAddr("pathname", tomcatUsersLocation));
    MemoryUserDatabase memoryUserDatabase =
            (MemoryUserDatabase) new MemoryUserDatabaseFactory().getObjectInstance(
                    ref,
                    new CompositeName("UserDatabase"),
                    null,
                    null);

    // Add basic auth user
    if (commandLineParams.basicAuthUser != null && commandLineParams.basicAuthPw != null) {

      memoryUserDatabase.setReadonly(false);
      Role user = memoryUserDatabase.createRole(AUTH_ROLE, AUTH_ROLE);
      memoryUserDatabase.createUser(
              commandLineParams.basicAuthUser,
              commandLineParams.basicAuthPw,
              commandLineParams.basicAuthUser).addRole(user);
      memoryUserDatabase.save();

    } else if (System.getenv("BASIC_AUTH_USER") != null && System.getenv("BASIC_AUTH_PW") != null) {

      memoryUserDatabase.setReadonly(false);
      Role user = memoryUserDatabase.createRole(AUTH_ROLE, AUTH_ROLE);
      memoryUserDatabase.createUser(
              System.getenv("BASIC_AUTH_USER"),
              System.getenv("BASIC_AUTH_PW"),
              System.getenv("BASIC_AUTH_USER")).addRole(user);
      memoryUserDatabase.save();
    }

    // Register memoryUserDatabase with GlobalNamingContext
    System.out.println("MemoryUserDatabase: " + memoryUserDatabase);
    tomcat.getServer().getGlobalNamingContext().addToEnvironment("UserDatabase", memoryUserDatabase);

    org.apache.catalina.deploy.ContextResource ctxRes =
            new org.apache.catalina.deploy.ContextResource();
    ctxRes.setName("UserDatabase");
    ctxRes.setAuth("Container");
    ctxRes.setType("org.apache.catalina.UserDatabase");
    ctxRes.setDescription("User database that can be updated and saved");
    ctxRes.setProperty("factory", "org.apache.catalina.users.MemoryUserDatabaseFactory");
    ctxRes.setProperty("pathname", tomcatUsersLocation);
    tomcat.getServer().getGlobalNamingResources().addResource(ctxRes);
    tomcat.getEngine().setRealm(new org.apache.catalina.realm.UserDatabaseRealm());
  }


  public static void configureShutdownHandler(CommandLineParams commandLineParams, final Tomcat tomcat, Context ctx) {
    if (!commandLineParams.shutdownOverride) {
      // allow Tomcat to shutdown if a context failure is detected
      final String ctxName = commandLineParams.contextPath;
      ctx.addLifecycleListener(new ContextShutdownListener(tomcat, ctxName));
    }
  }

  private static class ContextShutdownListener implements LifecycleListener {
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
          System.err.println("SEVERE: Context [" + ctxName + "] failed in [" + event.getLifecycle().getClass().getName() + "] lifecycle. Allowing Tomcat to shutdown.");
          standardServer.stopAwait();
        }
      }
    }
  }
}
