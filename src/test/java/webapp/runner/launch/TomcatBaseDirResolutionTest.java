package webapp.runner.launch;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import webapp.runner.launch.factory.TomcatFactory;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;


/**
 * @author Ryan Brainard
 */
public class TomcatBaseDirResolutionTest {

  private static final Integer PORT = 1234;
  private static final File BASE_DIR = new File(System.getProperty("user.dir"), "/target/tomcat." + PORT);

  @Before
  @After
  public void clean() throws IOException {
    if (BASE_DIR.exists()) {
      if (BASE_DIR.isDirectory()) {
        FileUtils.deleteDirectory(BASE_DIR);
      }
      else {
        BASE_DIR.delete();
      }
    }
  }

  @Test
  public void testBaseDirNotExists() throws Exception {
    TomcatFactory.resolveBaseDirImpl(null, PORT);
    Assert.assertTrue(BASE_DIR.isDirectory());
  }

  @Test
  public void testBaseDirAlreadyExists() throws Exception {
    Assert.assertTrue(BASE_DIR.mkdirs());
    TomcatFactory.resolveBaseDirImpl(null, PORT);
    Assert.assertTrue(BASE_DIR.isDirectory());
  }

  @Test
  public void testBaseDirAlreadyExistsAsFile() throws Exception {
    BASE_DIR.getParentFile().mkdirs();
    PrintWriter printWriter = new PrintWriter(BASE_DIR);
    printWriter.append("");
    printWriter.close();
    Assert.assertTrue(BASE_DIR.isFile());

    try {
      TomcatFactory.resolveBaseDirImpl(null, PORT);
      Assert.fail();
    } catch (IOException e) {
      // expected
    }

    Assert.assertTrue(BASE_DIR.isFile());
  }

}
