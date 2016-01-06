package se.wallinder.heos.util;

/**
 * This class contains all constants
 *
 */
public class ServletConstants {

   private ServletConstants() {
      // Private constructor
   }

   /***********
    * SERVLET *
    ***********/

   // Request parameters
   public static final String SERVLET_VERSION = "1.2.0";
   public static final String REQUEST_PARAM_PLAYER = "player";
   public static final String REQUEST_PARAM_COMMAND = "command";
   public static final String REQUEST_PARAM_STATION = "station";
   public static final String REQUEST_PARAM_VOLUME = "volume";
   public static final String REQUEST_PARAM_INPUT_PLAYER = "inputplayer";
   public static final String REQUEST_PARAM_INPUT_NAME = "inputname";
   public static final String REQUEST_PARAM_PLAYLIST = "playlist";
   public static final String REQUEST_PARAM_VIRTUAL_DEVICE = "vd";
   public static final String REQUEST_PARAM_LABEL_TEXT = "labeltext";

   /********
    * HEOS *
    ********/

   // Enum for HEOS commands
   public static enum HEOSCommands {
      PLAY(), STOP(), VOLUME(), STATION(), PLAYLIST(), INPUT(), ALARM(), TRIGGER();
   }

   // HEOS specifics
   public static final int HEOS_PORT = 1255;
   public static final int HEOS_SLEEPTIME_IN_MS = 100;
   public static final int HEOS_TIMEOUT_IN_MS = 5000;
   public static final int HEOS_DEFAULT_VOLUME = 10;
   public static final String HEOS_PLAYLIST_ID = "1025";
   public static final String HEOS_FAVORITES_ID = "1028";
   public static final String HEOS_TYPE_STATION = "station";
   public static final String HEOS_TYPE_PLAYLIST = "playlist";

   // HEOS command results
   public static final String HEOS_PREFIX = "heos://";
   public static final String HEOS_RESULT_SIGNED_OUT = "\"message\": \"signed_out\"";
   public static final String HEOS_RESULT_NO_GROUPS = "\"payload\": []";
   public static final String HEOS_RESULT_STATE_PLAY = "state=play";
   public static final String HEOS_RESULT_STATE_STOP = "state=stop";
   public static final String HEOS_CMD_UNDER_PROCESS = "\"message\": \"command under process";
   public static final String HEOS_RESULT_SUCCESS = "\"result\": \"success\"";

   /**********
    * FIBARO *
    **********/

   // Slider and label
   public static final String FIBARO_VD_SLIDER_ID = "slider";
   public static final String FIBARO_VD_LABEL_ID = "label";
}
