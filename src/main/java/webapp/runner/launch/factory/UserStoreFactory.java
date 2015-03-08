package webapp.runner.launch.factory;

import org.apache.catalina.Role;
import org.apache.catalina.deploy.ContextResource;
import org.apache.catalina.realm.UserDatabaseRealm;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.users.MemoryUserDatabase;
import org.apache.catalina.users.MemoryUserDatabaseFactory;
import webapp.runner.launch.CommandLineParams;

import javax.naming.CompositeName;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

public class UserStoreFactory {
  private static final String AUTH_ROLE = "user";

  public void configureUserStore(final Tomcat tomcat, final CommandLineParams commandLineParams) throws Exception {
    String tomcatUsersLocation = commandLineParams.tomcatUsersLocation;
    if (tomcatUsersLocation == null || tomcatUsersLocation.isEmpty()) {
      tomcatUsersLocation = "../../tomcat-users.xml";
    }

    javax.naming.Reference ref = new javax.naming.Reference("org.apache.catalina.UserDatabase");
    ref.add(new StringRefAddr("pathname", tomcatUsersLocation));
    MemoryUserDatabase memoryUserDatabase = createemoryUserDatabase(commandLineParams, ref);

    // Register memoryUserDatabase with GlobalNamingContext
    System.out.println("MemoryUserDatabase: " + memoryUserDatabase);
    tomcat.getServer().getGlobalNamingContext().addToEnvironment("UserDatabase", memoryUserDatabase);

    ContextResource ctxRes = new ContextResource();
    ctxRes.setName("UserDatabase");
    ctxRes.setAuth("Container");
    ctxRes.setType("org.apache.catalina.UserDatabase");
    ctxRes.setDescription("User database that can be updated and saved");
    ctxRes.setProperty("factory", "org.apache.catalina.users.MemoryUserDatabaseFactory");
    ctxRes.setProperty("pathname", tomcatUsersLocation);
    tomcat.getServer().getGlobalNamingResources().addResource(ctxRes);
    tomcat.getEngine().setRealm(new UserDatabaseRealm());
  }

  protected MemoryUserDatabase createemoryUserDatabase(CommandLineParams commandLineParams,
                                                       Reference ref) throws Exception {
    MemoryUserDatabase memoryUserDatabase = (MemoryUserDatabase)
            new MemoryUserDatabaseFactory().getObjectInstance(ref, new CompositeName("UserDatabase"), null, null);

    // Add basic auth user
    if (commandLineParams.basicAuthUser != null && commandLineParams.basicAuthPw != null) {

      memoryUserDatabase.setReadonly(false);
      Role user = memoryUserDatabase.createRole(AUTH_ROLE, AUTH_ROLE);
      memoryUserDatabase.createUser(
              commandLineParams.basicAuthUser,
              commandLineParams.basicAuthPw,
              commandLineParams.basicAuthUser).addRole(user);
      memoryUserDatabase.save();

    } else if (System.getenv("BASIC_AUTH_USER") != null && System.getenv("BASIC_AUTH_PW") != null) {

      memoryUserDatabase.setReadonly(false);
      Role user = memoryUserDatabase.createRole(AUTH_ROLE, AUTH_ROLE);
      memoryUserDatabase.createUser(
              System.getenv("BASIC_AUTH_USER"),
              System.getenv("BASIC_AUTH_PW"),
              System.getenv("BASIC_AUTH_USER")).addRole(user);
      memoryUserDatabase.save();
    }
    return memoryUserDatabase;
  }

}
