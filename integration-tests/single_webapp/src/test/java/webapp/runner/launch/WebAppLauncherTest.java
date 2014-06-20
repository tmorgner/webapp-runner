package webapp.runner.launch;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@SuppressWarnings("HardCodedStringLiteral")
public class WebAppLauncherTest {

  private static final String JSP_CONTENT = "<html>\n" +
          "<body>\n" +
          "hello, JSP<br/>\n" +
          "<a href=\"/hello\">Hello, Servlet</a>\n" +
          "</body>\n" +
          "</html>\n";

  public WebAppLauncherTest() {
  }

  @Test
  public void testSimple() throws Exception {
    final AsyncTomcatTaskRunner test = new JspValidateRunner();

    final CommandLineParams p = new CommandLineParams();
    p.port = 9876;
    p.paths.add("src/main/webapp");
    System.out.println("BEFORE");
    test.run(p);
    System.out.println("FINISHED");
  }

  @Test
  public void testMavenCompiled() throws Exception {
    final AsyncTomcatTaskRunner test = new JspValidateRunner();

    final CommandLineParams p = new CommandLineParams();
    p.port = 9876;
    p.paths.add("target/single_webapp");
    System.out.println("BEFORE");
    test.run(p);
    System.out.println("FINISHED");
  }

  @Test
  public void testMavenWar() throws Exception {
    final AsyncTomcatTaskRunner test = new JspValidateRunner();

    final CommandLineParams p = new CommandLineParams();
    p.port = 9876;
    p.paths.add("target/single_webapp.war");
    System.out.println("BEFORE");
    test.run(p);
    System.out.println("FINISHED");
  }

  private static class JspValidateRunner extends AsyncTomcatTaskRunner {
    @Override
    protected void performTest() throws Exception {
      final URL url = new URL("http://localhost:9876/");
      System.out.println ("HERE");
      final HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
      Assert.assertEquals(200, urlConnection.getResponseCode());
      final InputStream inputStream = urlConnection.getInputStream();
      final ByteArrayOutputStream bout = new ByteArrayOutputStream();
      IOUtils.copy(inputStream, bout);
      final String test = bout.toString().replace("\r\n", "\n");
      Assert.assertEquals(JSP_CONTENT, test);
    }
  }
}
