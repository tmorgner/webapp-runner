package webapp.runner.launch.factory;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import webapp.runner.launch.CommandLineParams;
import webapp.runner.launch.helper.ContextDefinition;
import webapp.runner.launch.TomcatConfigurator;

import java.io.File;
import java.io.IOException;

public class TomcatFactory {

  public TomcatFactory() {
  }

  public Tomcat createTomcatServer(CommandLineParams commandLineParams,
                                   TomcatConfigurator configurator,
                                   ContextDefinition... warLocations) throws Exception {
    final Tomcat tomcat = new Tomcat();

    // set directory for temp files
    tomcat.setBaseDir(resolveTomcatBaseDir(commandLineParams.baseDir, commandLineParams.port));

    // initialize the connector
    Connector nioConnector = createConnectorFactory().configureConnector(commandLineParams);
    tomcat.setConnector(nioConnector);
    tomcat.getService().addConnector(tomcat.getConnector());
    tomcat.setPort(commandLineParams.port);
    if (commandLineParams.enableBasicAuth || commandLineParams.enableNaming ||
            commandLineParams.tomcatUsersLocation != null) {
      tomcat.enableNaming();
    }

    ContextFactory contextFactory = createContextFactory();
    for (ContextDefinition war : warLocations) {
      contextFactory.configureWarContext(configurator, commandLineParams, tomcat, war);
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
      new UserStoreFactory().configureUserStore(tomcat, commandLineParams);
    }
    return tomcat;
  }

  protected ContextFactory createContextFactory() {
    return new ContextFactory();
  }

  protected ConnectorFactory createConnectorFactory() {
    return new ConnectorFactory();
  }

  protected UserStoreFactory createUserStoreFactory() {
    return new UserStoreFactory();
  }
  /**
   * Gets or creates temporary Tomcat base directory within target dir
   *
   * @param port port of web process
   * @return absolute dir path
   * @throws IOException if dir fails to be created
   */
  protected String resolveTomcatBaseDir(String baseDirConfig, Integer port) throws IOException {

    return resolveBaseDirImpl(baseDirConfig, port);
  }

  public static String resolveBaseDirImpl(String baseDirConfig, Integer port) throws IOException {
    final File baseDir;
    if (baseDirConfig == null || "".equals(baseDirConfig)) {
      baseDir = new File(System.getProperty("user.dir") + "/target/tomcat." + port);
    } else {
      baseDir = new File(baseDirConfig);
    }
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


}
