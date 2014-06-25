package webapp.runner.launch.helper;

import org.apache.catalina.core.StandardContext;

public class OverrideContext extends StandardContext {

  public OverrideContext() {
  }

  @Override
  public void addParameter(String name, String value) {
    if (findParameter(name) == null) {
      super.addParameter(name, value);
    }
  }
}
