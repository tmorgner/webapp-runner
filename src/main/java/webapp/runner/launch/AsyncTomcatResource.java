package webapp.runner.launch;

import org.apache.catalina.LifecycleState;
import org.apache.catalina.startup.Tomcat;

import java.io.File;

public class AsyncTomcatResource implements TomcatConfigurator {

  private static class ThreadHandle implements TomcatHandle {
    private Thread tomcatRunner;

    private ThreadHandle(final Thread tomcatRunner) {
      this.tomcatRunner = tomcatRunner;
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
    final Tomcat tomcatServer = Main.createTomcatServer(param, this, contextDefinition);
    assertServerUp(tomcatServer);

    return new TomcatServerHandle(startServer(tomcatServer), tomcatServer);
  }

  protected TomcatHandle startServer(final Tomcat tomcatServer) {
    final Thread tomcatRunner = new Thread(new Runnable() {
      @Override
      public void run() {
        tomcatServer.getServer().await();
      }
    });

    tomcatRunner.start();
    return new ThreadHandle(tomcatRunner);
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

  protected void fail(final String message) {
    throw new IllegalStateException(message);
  }
}
