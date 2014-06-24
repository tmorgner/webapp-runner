package webapp.runner.launch;

import java.io.File;
import java.util.Properties;

public class ContextDefinition {
  private File war;
  private String contextPath;
  private String contextXml;
  private Properties contextConfiguration;

  public ContextDefinition(final File war) {
    this(war, "", null, new Properties());
  }

  public ContextDefinition(final File war, final String contextPath) {
    this(war, contextPath, null, new Properties());
  }

  public ContextDefinition(final File war, final String contextPath, final String contextXml) {
    this(war, contextPath, contextXml, new Properties());
  }

  public ContextDefinition(File war, String contextPath, String contextXml, Properties contextConfiguration) {
    if (contextConfiguration == null) {
      throw new NullPointerException();
    }
    if (war == null) {
      throw new NullPointerException();
    }
    this.war = war;
    this.contextPath = contextPath;
    this.contextXml = contextXml;
    this.contextConfiguration = contextConfiguration;

  }

  public Properties getContextConfiguration() {
    return contextConfiguration;
  }

  public String getContextXml() {
    return contextXml;
  }

  public File getWar() {
    return war;
  }

  public String getContextPath() {
    return contextPath;
  }
}
