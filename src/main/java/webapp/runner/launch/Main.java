/**
 * Copyright (c) 2012, John Simone
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *    Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *    following disclaimer.
 *
 *    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 *    the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *    Neither the name of John Simone nor the names of its contributors may be used to endorse or
 *    promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package webapp.runner.launch;

import com.beust.jcommander.JCommander;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.startup.Tomcat;
import webapp.runner.launch.factory.TomcatFactory;
import webapp.runner.launch.helper.ContextDefinition;

import java.io.File;


/**
 * This is the main entry point to webapp-runner. Helpers are called to parse the arguments.
 * Tomcat configuration and launching takes place here.
 */
@SuppressWarnings({"HardCodedStringLiteral", "LocalCanBeFinal"})
public class Main {

  public static void main(String[] args) throws Exception {

    CommandLineParams commandLineParams = new CommandLineParams();
    JCommander jCommander = new JCommander(commandLineParams, args);

    if (commandLineParams.help) {
      jCommander.usage();
      System.exit(1);
    }

    // default to src/main/webapp
    if (commandLineParams.paths.size() == 0) {
      commandLineParams.paths.add("src/main/webapp");
    }

    if (commandLineParams.paths.size() > 1) {
      System.out.println("WARNING: multiple paths are specified, but no longer supported. First path will be used.");
    }

    // Get the first path
    String path = commandLineParams.paths.get(0);
    File war = new File(path);

    if (!war.exists()) {
      System.err.println("The specified path \"" + path + "\" does not exist.");
      jCommander.usage();
      System.exit(1);
    }

    // Use the commandline context-path (or default)
    // warn if the contextPath doesn't start with a '/'. This causes issues serving content at the context root.
    if (commandLineParams.contextPath.length() > 0 && !commandLineParams.contextPath.startsWith("/")) {
      System.out.println("WARNING: You entered a path: [" + commandLineParams.contextPath + "]. Your path should start with a '/'. Tomcat will update this for you, but you may still experience issues.");
    }


    ContextDefinition context = new ContextDefinition(war, commandLineParams.contextPath);
    Tomcat tomcat = new TomcatFactory().createTomcatServer(commandLineParams, null, context);

    addShutdownHook(tomcat);

    tomcat.getServer().await();
  }

  /**
   * Stops the embedded Tomcat server.
   */
  public static void addShutdownHook(final Tomcat tomcat) {

    // add shutdown hook to stop server
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        try {
          if (tomcat != null) {
            tomcat.getServer().stop();
          }
        } catch (LifecycleException exception) {
          throw new RuntimeException("WARNING: Cannot Stop Tomcat " + exception.getMessage(), exception);
        }
      }
    });
  }
}
