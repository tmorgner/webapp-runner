package webapp.runner.launch.helper;

import org.apache.catalina.Host;
import org.apache.catalina.startup.Constants;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Isolates code copied from Tomcat, as these methods are not accessible otherwise.
 * This code obviously should be licensed as Apache-License-2.0, just like Tomcat.
 */
public class TomcatUtil {

  public static void silence(Host host, String ctx) {
    Logger.getLogger(TomcatUtil.getLoggerName(host, ctx)).setLevel(Level.WARNING);
  }

  public static URL getWebappConfigFile(Host host, String path, String url) {
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

}
