package webapp.runner.launch.factory;

import org.apache.catalina.connector.Connector;
import webapp.runner.launch.CommandLineParams;

import java.io.File;

public class ConnectorFactory {

  public Connector configureConnector(CommandLineParams commandLineParams) {
    Connector nioConnector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
    nioConnector.setPort(commandLineParams.port);

    if (commandLineParams.enableSSL) {
      configureSSL(commandLineParams, nioConnector);
    }

    if (commandLineParams.enableCompression) {
      nioConnector.setProperty("compression", "on");
      nioConnector.setProperty("compressableMimeType", commandLineParams.compressableMimeTypes);
    }

    if (commandLineParams.uriEncoding != null) {
      nioConnector.setURIEncoding(commandLineParams.uriEncoding);
    }
    return nioConnector;
  }

  protected void configureSSL(CommandLineParams commandLineParams, Connector nioConnector) {
    nioConnector.setSecure(true);
    nioConnector.setProperty("SSLEnabled", "true");
    String pathToTrustStore = System.getProperty("javax.net.ssl.trustStore");
    if (pathToTrustStore != null) {
      nioConnector.setProperty("sslProtocol", "tls");
      File truststoreFile = new File(pathToTrustStore);
      nioConnector.setAttribute("truststoreFile", truststoreFile.getAbsolutePath());
      System.out.println(truststoreFile.getAbsolutePath());
      nioConnector.setAttribute("trustStorePassword", System.getProperty("javax.net.ssl.trustStorePassword"));
    }
    String pathToKeystore = System.getProperty("javax.net.ssl.keyStore");
    if (pathToKeystore != null) {
      File keystoreFile = new File(pathToKeystore);
      nioConnector.setAttribute("keystoreFile", keystoreFile.getAbsolutePath());
      System.out.println(keystoreFile.getAbsolutePath());
      nioConnector.setAttribute("keystorePass", System.getProperty("javax.net.ssl.keyStorePassword"));
    }
    if (commandLineParams.enableClientAuth) {
      nioConnector.setAttribute("clientAuth", true);
    }
  }
}
