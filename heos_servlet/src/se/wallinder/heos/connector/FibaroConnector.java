package se.wallinder.heos.connector;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import sun.misc.BASE64Encoder;

public class FibaroConnector {

   private final static Logger LOGGER = Logger.getLogger(FibaroConnector.class.getName());
   private final String fibaroHost;
   private final String fibaroAuth;

   /**
    * Private constructor
    */
   public FibaroConnector(String fibaroHost, String fibaroUser, String fibaroPassword) {
      LOGGER.setLevel(Level.WARNING);
      this.fibaroHost = fibaroHost;
      this.fibaroAuth = new BASE64Encoder().encode((fibaroUser + ":" + fibaroPassword).getBytes());
   }

   /**
    * Sets the volume slider of the specified virtual device/slider to the given volume
    * 
    * @param virtualDeviceID The Id of the virtual device
    * @param sliderID The id of the slider
    * @param volume The volume to set
    * @return True if ok, false if not
    */
   public boolean setVolumeSlider(String virtualDeviceID, String sliderID, int volume) {
      return sendCommand(
            "http://" + fibaroHost + "/api/callAction?deviceID=" + virtualDeviceID + "&name=setSlider&arg1=" + "3" + "&arg2=" + String.valueOf(volume));
   }

   /**
    * Sets the given text on the specified virtual device/label
    * 
    * @param virtualDeviceID The Id of the virtual device
    * @param labelID The id of the label
    * @param text The text to set
    * @return True if ok, false if not
    */
   public boolean setTextLabel(String virtualDeviceID, String labelID, String text) {
      return sendCommand(
            "http://" + fibaroHost + "/api/callAction?deviceID=" + virtualDeviceID + "&name=setProperty&arg1=ui." + labelID + ".value&arg2=" + text);
   }

   /**
    * Sends a command to a Fibaro (using an URL)
    * 
    * @param fibaroURL The Fibaro URL
    * @return True if ok, false if not
    */
   private synchronized boolean sendCommand(String fibaroURL) {
      URL url;
      HttpURLConnection connection;
      int responseCode = -1;
      String response = null;
      try {
         LOGGER.info("Sending command: " + fibaroURL);
         url = new URL(fibaroURL);
         connection = (HttpURLConnection) url.openConnection();
         connection.setRequestMethod("GET");
         connection.setRequestProperty("Authorization", "Basic " + fibaroAuth);
         response = connection.getResponseMessage();
         responseCode = connection.getResponseCode();
         LOGGER.info("Received response: " + response);
      } catch (Exception e) {
         LOGGER.severe("Error while sending command: " + fibaroURL);
         return false;
      }
      return responseCode == HttpURLConnection.HTTP_ACCEPTED ? true : false;
   }

}
