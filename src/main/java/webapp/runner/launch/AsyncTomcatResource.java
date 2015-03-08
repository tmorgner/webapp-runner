package webapp.runner.launch;

import org.apache.catalina.Context;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.startup.Tomcat;
import webapp.runner.launch.factory.TomcatFactory;
import webapp.runner.launch.helper.ContextDefinition;

import java.io.File;

public class AsyncTomcatResource implements TomcatConfigurator {

  private static class ThreadHandle implements TomcatHandle {
    private Thread tomcatRunner;
    private int port;

    private ThreadHandle(final Thread tomcatRunner, int port) {
      this.tomcatRunner = tomcatRunner;
      this.port = port;
    }

    @Override
    public int getPort() {
      return port;
    }

    @Override
    public void close() throws Exception {
      tomcatRunner.join();
    }

    @Override
    public void join() throws Exception {
      tomcatRunner.join();
    }
  }

  private static class TomcatServerHandle implements TomcatHandle {
    private Tomcat tomcatServer;
    private TomcatHandle parent;

    private TomcatServerHandle(final TomcatHandle parent, final Tomcat tomcatServer) {
      this.parent = parent;
      this.tomcatServer = tomcatServer;
    }

    @Override
    public int getPort() {
      return tomcatServer.getConnector().getPort();
    }

    @Override
    public void join() throws Exception {
      parent.join();
    }

    @Override
    public void close() throws Exception {
      tomcatServer.stop();
      tomcatServer.destroy();
      parent.close();
    }
  }

  @Override
  public void configure(Tomcat tomcat, CommandLineParams commandLineParams, ContextDefinition... warLocations) {
  }

  public TomcatHandle prepareProcessing(final CommandLineParams param) throws Exception {
    final String path = param.paths.get(0);
    final File war = new File(path);
    if (!war.exists()) {
      fail(String.format("File [%s] does not exist.", war));
    }

    final ContextDefinition contextDefinition = new ContextDefinition(war);
    return startServer(param, contextDefinition);
  }

  public TomcatHandle startServer(final CommandLineParams param,
                                  final ContextDefinition... contextDefinition) throws Exception {
    final Tomcat tomcatServer = createTomcatFactory().createTomcatServer(param, this, contextDefinition);
    assertServerUp(tomcatServer);

    return new TomcatServerHandle(startServer(tomcatServer), tomcatServer);
  }

  protected TomcatFactory createTomcatFactory() {
    return new TomcatFactory();
  }

  protected TomcatHandle startServer(final Tomcat tomcatServer) {
    final Thread tomcatRunner = new Thread(new Runnable() {
      @Override
      public void run() {
        tomcatServer.getServer().await();
      }
    });

    tomcatRunner.start();
    return new ThreadHandle(tomcatRunner, tomcatServer.getConnector().getPort());
  }

  protected void assertServerUp(final Tomcat tomcat) {
    if (tomcat.getHost().getState() != LifecycleState.STARTED) {
      fail("Host not started");
    }
    if (tomcat.getConnector().getState() != LifecycleState.STARTED) {
      fail("Connector not started");
    }
    if (tomcat.getEngine().getState() != LifecycleState.STARTED) {
      fail("Engine not started");
    }
    if (tomcat.getServer().getState() != LifecycleState.STARTED) {
      fail("Server not started");
    }
  }

  @Override
  public void configureContext(Tomcat tomcat,
                               CommandLineParams commandLineParams,
                               ContextDefinition warLocation,
                               Context context) {
  }

  protected void fail(final String message) {
    throw new IllegalStateException(message);
  }
}
