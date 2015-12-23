package se.wallinder.heos.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

public class ServletProperties {

   private final static Logger LOGGER = Logger.getLogger(ServletProperties.class.getName());
   private final Properties properties;

   // HEOS
   private final String HEOS_HOST = "heos.host";
   private final String HEOS_USER = "heos.user";
   private final String HEOS_PASSWORD = "heos.password";
   // Fibaro
   private final String FIBARO_HOST = "fibaro.host";
   private final String FIBARO_USER = "fibaro.user";
   private final String FIBARO_PASSWORD = "fibaro.password";

   /**
    * Private constructor
    */
   public ServletProperties(InputStream propertiesFile) {
      properties = new Properties();
      try {
         properties.load(propertiesFile);
      } catch (IOException e) {
         LOGGER.severe("Could not load propertiesfile");
      }
   }

   /**
    * Gets the property HEOS host
    * 
    * @return The property HEOS host
    */
   public String getHeosHost() {
      return properties.getProperty(HEOS_HOST, "127.0.0.1");
   }

   /**
    * Gets the property HEOS user
    * 
    * @return The property HEOS user
    */
   public String getHeosUser() {
      return properties.getProperty(HEOS_USER, "user@host.com");
   }

   /**
    * Gets the property HEOS user password
    * 
    * @return The property HEOS user password
    */
   public String getHeosPassword() {
      return properties.getProperty(HEOS_PASSWORD, "abc123");
   }

   /**
    * Gets the property Fibaro host
    * 
    * @return The property Fibaro host
    */
   public String getFibaroHost() {
      return properties.getProperty(FIBARO_HOST, "127.0.0.1");
   }

   /**
    * Gets the property Fibaro user
    * 
    * @return The property Fibaro user
    */
   public String getFibaroUser() {
      return properties.getProperty(FIBARO_USER, "user@host.com");
   }

   /**
    * Gets the property Fibaro user password
    * 
    * @return The property Fibaro user password
    */
   public String getFibaroPassword() {
      return properties.getProperty(FIBARO_PASSWORD, "abc123");
   }

}
