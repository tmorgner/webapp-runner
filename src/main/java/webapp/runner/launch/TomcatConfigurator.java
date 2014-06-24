package webapp.runner.launch;

import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;

public interface TomcatConfigurator {
  public void configure(Tomcat tomcat, CommandLineParams commandLineParams,
                        ContextDefinition... warLocations);

  public void configureContext(Tomcat tomcat, CommandLineParams commandLineParams,
                               ContextDefinition warLocation, Context context);
}
