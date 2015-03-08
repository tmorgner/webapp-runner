package webapp.runner.launch;

public interface TomcatHandle {
  public int getPort();
  public void close() throws Exception;
  public void join() throws Exception;
}
