package webapp.runner.launch;

public abstract class AsyncTomcatTaskRunner {

  private AsyncTomcatResource tomcatResource;

  public AsyncTomcatTaskRunner() {
    this(new AsyncTomcatResource());
  }

  protected AsyncTomcatTaskRunner(final AsyncTomcatResource tomcatResource) {
    this.tomcatResource = tomcatResource;
  }

  protected abstract void performTest() throws Exception;

  public void run(final CommandLineParams param) throws Exception {

    final TomcatHandle handle = tomcatResource.prepareProcessing(param);
    try {
      performTest();
    } finally {
      handle.close();
    }
  }
}
