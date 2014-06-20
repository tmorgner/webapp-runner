package webapp.runner.launch;

import java.io.File;

public class ContextDefinition {
  private File war;
  private String contextPath;
  private String contextXml;

  public ContextDefinition(final File war) {
    this(war, "", null);
  }

  public ContextDefinition(final File war, final String contextPath) {
    this(war, contextPath, null);
  }

  public ContextDefinition(final File war, final String contextPath, final String contextXml) {
    this.war = war;
    this.contextPath = contextPath;
    this.contextXml = contextXml;
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
