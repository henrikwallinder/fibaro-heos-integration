package se.wallinder.heos;

import static se.wallinder.heos.util.ServletConstants.FIBARO_VD_LABEL_ID;
import static se.wallinder.heos.util.ServletConstants.FIBARO_VD_SLIDER_ID;
import static se.wallinder.heos.util.ServletConstants.HEOS_DEFAULT_VOLUME;
import static se.wallinder.heos.util.ServletConstants.REQUEST_PARAM_COMMAND;
import static se.wallinder.heos.util.ServletConstants.REQUEST_PARAM_INPUT_NAME;
import static se.wallinder.heos.util.ServletConstants.REQUEST_PARAM_INPUT_PLAYER;
import static se.wallinder.heos.util.ServletConstants.REQUEST_PARAM_LABEL_TEXT;
import static se.wallinder.heos.util.ServletConstants.REQUEST_PARAM_PLAYER;
import static se.wallinder.heos.util.ServletConstants.REQUEST_PARAM_PLAYLIST;
import static se.wallinder.heos.util.ServletConstants.REQUEST_PARAM_STATION;
import static se.wallinder.heos.util.ServletConstants.REQUEST_PARAM_VIRTUAL_DEVICE;
import static se.wallinder.heos.util.ServletConstants.REQUEST_PARAM_VOLUME;
import static se.wallinder.heos.util.ServletConstants.SERVLET_VERSION;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import se.wallinder.heos.connector.FibaroConnector;
import se.wallinder.heos.connector.HEOSConnector;
import se.wallinder.heos.util.ServletConstants.HEOSCommands;
import se.wallinder.heos.util.ServletProperties;

/**
 * Servlet implementation class HEOS
 */
public class HEOSServlet extends HttpServlet {

   private final static long serialVersionUID = 1L;
   private final static Logger LOGGER = Logger.getLogger(HEOSServlet.class.getName());
   private final static long EVERY_HOUR_MS = 60 * 60 * 1000;
   private final Timer timer;
   private Date lastConnection;
   private ServletProperties properties;
   private HEOSConnector heosConnector;
   private FibaroConnector fibaroConnector;

   /**
    * Constructor
    */
   public HEOSServlet() {
      LOGGER.setLevel(Level.WARNING);
      timer = new Timer();
   }

   /**
    * Send heartbeat requests to the HEOS-system to keep the connection alive
    */
   class HeartbeatTimer extends TimerTask {
      @Override
      public void run() {
         // Check if connected
         boolean connected = heosConnector.isConnected();
         if (!connected) {
            heosConnector.connect();
            // Try again
            connected = heosConnector.isConnected();
            if (!connected) {
               LOGGER.warning("HEOS-system did not respond");
            }
         }
         lastConnection = connected ? new Date(System.currentTimeMillis()) : lastConnection;
      }
   }

   @Override
   public void init(ServletConfig config) throws ServletException {
      super.init(config);
      InputStream propertiesFile = getServletContext().getResourceAsStream("/WEB-INF/settings.properties");
      properties = new ServletProperties(propertiesFile);
      heosConnector = new HEOSConnector(properties.getHeosHost(), properties.getHeosUser(), properties.getHeosPassword());
      fibaroConnector = new FibaroConnector(properties.getFibaroHost(), properties.getFibaroUser(), properties.getFibaroPassword());
      timer.schedule(new HeartbeatTimer(), EVERY_HOUR_MS, EVERY_HOUR_MS);
   }

   @Override
   public void destroy() {
      timer.cancel();
      super.destroy();
   }

   /**
    * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
    */
   protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      try {

         // If no arguments - list players, stations and settings
         if (request.getParameterMap().keySet().isEmpty()) {
            createHeosInfoResponse(request, response);
            return;
         }

         // Get request parameters
         String player = request.getParameter(REQUEST_PARAM_PLAYER);
         String command = request.getParameter(REQUEST_PARAM_COMMAND);
         if (player == null || player.isEmpty() || command == null || command.isEmpty()) {
            LOGGER.warning("Invalid request, missing paramenters for player and command");
            response.getWriter().print("FAILED");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
         }

         // Verify player
         if (!heosConnector.getPlayers().containsKey(player)) {
            LOGGER.warning("Invalid request, invalid player: " + player);
            response.getWriter().print("FAILED");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
         }

         // Find out which command
         HEOSCommands heosCommand;
         try {
            heosCommand = HEOSCommands.valueOf(command.toUpperCase());
         } catch (IllegalArgumentException iae) {
            LOGGER.warning("Invalid request, invalid command: " + command);
            response.getWriter().print("FAILED");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
         }

         // Command okay, check if connected
         if (!heosConnector.isConnected()) {
            heosConnector.connect();
            // Check again
            if (!heosConnector.isConnected()) {
               LOGGER.severe("Not connected to the HEOS system");
               response.getWriter().print("FAILED");
               response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
               return;
            }
         }

         boolean result = false;
         switch (heosCommand) {

         /********
          * PLAY *
          ********/
         case PLAY:
            if (result = heosConnector.play(player)) {
               String fibaroVD = request.getParameter(REQUEST_PARAM_VIRTUAL_DEVICE);
               if (fibaroVD != null && !fibaroVD.isEmpty()) {
                  fibaroConnector.setTextLabel(fibaroVD, "label", URLEncoder.encode(heosConnector.getNowPlaying(player), "UTF-8"));
               }
            }
            break;

         /********
          * STOP *
          ********/
         case STOP:
            if (result = heosConnector.stop(player)) {
               String fibaroVD = request.getParameter(REQUEST_PARAM_VIRTUAL_DEVICE);
               if (fibaroVD != null && !fibaroVD.isEmpty()) {
                  fibaroConnector.setTextLabel(fibaroVD, FIBARO_VD_LABEL_ID, "");
               }
            }
            break;

         /***********
          * STATION *
          ***********/
         case STATION:
            // Find out which station to play
            String station = request.getParameter(REQUEST_PARAM_STATION) != null ? request.getParameter(REQUEST_PARAM_STATION) : "";
            if (!heosConnector.getStations().containsKey(station)) {
               LOGGER.warning("Invalid request, invalid station: " + station);
               response.getWriter().print("FAILED");
               response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
               return;
            }
            if (result = heosConnector.station(player, station)) {
               String fibaroVD = request.getParameter(REQUEST_PARAM_VIRTUAL_DEVICE);
               String labelText = request.getParameter(REQUEST_PARAM_LABEL_TEXT);
               if (fibaroVD != null && !fibaroVD.isEmpty() && labelText != null && !labelText.isEmpty()) {
                  fibaroConnector.setTextLabel(fibaroVD, FIBARO_VD_LABEL_ID, URLEncoder.encode(labelText, "UTF-8"));
               }
            }
            break;

         /************
          * PLAYLIST *
          ************/
         case PLAYLIST:
            // Find out which playlist to play
            String playlist = request.getParameter(REQUEST_PARAM_PLAYLIST) != null ? request.getParameter(REQUEST_PARAM_PLAYLIST) : "";
            if (!heosConnector.getPlaylists().containsKey(playlist)) {
               LOGGER.severe("Invalid request, invalid playlist: " + playlist);
               response.getWriter().print("FAILED");
               response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
               return;
            }
            if (result = heosConnector.playlist(player, playlist)) {
               String fibaroVD = request.getParameter(REQUEST_PARAM_VIRTUAL_DEVICE);
               if (fibaroVD != null && !fibaroVD.isEmpty()) {
                  String playlistName = heosConnector.getPlaylists().get(playlist);
                  fibaroConnector.setTextLabel(fibaroVD, FIBARO_VD_LABEL_ID, URLEncoder.encode(playlistName, "UTF-8"));
               }
            }
            break;

         /************
          * INPUT *
          ************/
         case INPUT:
            // Find out source player/input to play
            String inputPlayer = request.getParameter(REQUEST_PARAM_INPUT_PLAYER) != null ? request.getParameter(REQUEST_PARAM_INPUT_PLAYER) : "";
            String inputName = request.getParameter(REQUEST_PARAM_INPUT_NAME) != null ? request.getParameter(REQUEST_PARAM_INPUT_NAME) : "";
            if (!heosConnector.getPlayers().containsKey(inputPlayer)) {
               LOGGER.severe("Invalid request, invalid input player: " + inputPlayer);
               response.getWriter().print("FAILED");
               response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
               return;
            }
            if (result = heosConnector.input(player, inputPlayer, inputName)) {
               String fibaroVD = request.getParameter(REQUEST_PARAM_VIRTUAL_DEVICE);
               String labelText = request.getParameter(REQUEST_PARAM_LABEL_TEXT);
               if (fibaroVD != null && !fibaroVD.isEmpty() && labelText != null && !labelText.isEmpty()) {
                  fibaroConnector.setTextLabel(fibaroVD, FIBARO_VD_LABEL_ID, URLEncoder.encode(labelText, "UTF-8"));
               }
            }
            break;

         /***********
          * VOLUME *
          ***********/
         case VOLUME:
            // Find out volume
            try {
               String volumeValue = request.getParameter(REQUEST_PARAM_VOLUME) != null ? request.getParameter(REQUEST_PARAM_VOLUME) : "";
               int volume = Integer.parseInt(volumeValue);
               if (volume < 0 || volume > 100) {
                  LOGGER.warning("Invalid request, invalid volume: " + volume);
                  response.getWriter().print("FAILED");
                  response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                  return;
               }
               result = heosConnector.volume(player, volume);
            } catch (NumberFormatException nfe) {
               LOGGER.warning("Invalid request, invalid volume");
               response.getWriter().print("FAILED");
               response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
               return;
            }
            break;

         /*******************
          * ALARM & TRIGGER *
          *******************/
         case ALARM:
         case TRIGGER:
            // If TRIGGER and already playing - return
            if ((heosCommand == HEOSCommands.TRIGGER) && heosConnector.isPlaying(player)) {
               response.getWriter().print("SUCCESS");
               response.setStatus(HttpServletResponse.SC_OK);
               lastConnection = new Date(System.currentTimeMillis());
               return;
            }
            // Find out which station to play
            station = request.getParameter(REQUEST_PARAM_STATION) != null ? request.getParameter(REQUEST_PARAM_STATION) : "";
            if (!heosConnector.getStations().containsKey(station)) {
               LOGGER.severe("Invalid request, invalid station: " + station);
               response.getWriter().print("FAILED");
               response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
               return;
            }
            // Find out volume
            int volume = HEOS_DEFAULT_VOLUME;
            try {
               String volumeValue = request.getParameter(REQUEST_PARAM_VOLUME) != null ? request.getParameter(REQUEST_PARAM_VOLUME) : "";
               volume = Integer.parseInt(volumeValue);
               if (volume < 0 || volume > 100) {
                  LOGGER.severe("Invalid request, invalid volume: " + volume);
                  response.getWriter().print("FAILED");
                  response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                  return;
               }
            } catch (NumberFormatException nfe) {
               LOGGER.severe("Invalid request, invalid volume");
               response.getWriter().print("FAILED");
               response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
               return;
            }

            // First set volume, then play station
            if (result = (heosConnector.volume(player, volume) && heosConnector.station(player, station))) {
               // Update label
               String fibaroVD = request.getParameter(REQUEST_PARAM_VIRTUAL_DEVICE);
               String labelText = request.getParameter(REQUEST_PARAM_LABEL_TEXT);
               if (fibaroVD != null && !fibaroVD.isEmpty() && labelText != null && !labelText.isEmpty()) {
                  fibaroConnector.setTextLabel(fibaroVD, FIBARO_VD_LABEL_ID, URLEncoder.encode(labelText, "UTF-8"));
               }
               // Update slider - will result in another volume call
               if (fibaroVD != null && !fibaroVD.isEmpty()) {
                  fibaroConnector.setVolumeSlider(fibaroVD, FIBARO_VD_SLIDER_ID, volume);
               }
            }
            break;

         default:
            break;
         }

         // End of the road
         LOGGER.info(heosCommand.name() + " requested on player " + heosConnector.getPlayers().get(player) + ", result: " + (result ? "SUCCESS" : "FAILED"));
         response.getWriter().print(result ? "SUCCESS" : "FAILED");
         lastConnection = result ? new Date(System.currentTimeMillis()) : lastConnection;
         response.setStatus(HttpServletResponse.SC_OK);

      } catch (Exception e) {
         LOGGER.severe("Error while processing request: " + e.getMessage());
      }
   }

   /**
    * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse * response)
    */
   protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      // Post is not used!
      response.setStatus(HttpServletResponse.SC_OK);
   }

   /**
    * Creates a response with information about players, stations and settings
    * 
    * @param response The response which will be updated
    * @throws IOException
    */
   private void createHeosInfoResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
      response.setContentType("text/html;charset=UTF-8");
      PrintWriter writer = response.getWriter();
      writer.println("<html>");
      writer.println("<head><title>HEOS Servlet</title></head>");
      writer.println("<body style='background-color:#E0F2F7;color:#2E2E2E;font-family:monospace;font-size:12px;'>");

      // Heading
      writer.println("<h1 style='font-family:sans-serif;font-size:30px;color:#426d6e;margin-bottom: 5px;'>Denon HEOS Servlet</h1>");
      writer.println("<div>" + getValue("Servlet version") + SERVLET_VERSION + "</div>");

      // List settings
      writer.println("<h2 style='font-family:sans-serif;font-size:20px;color:#426d6e;margin-bottom: 5px;'>Settings</h1>");
      writer.println("<div>" + getValue("Settings file") + getServletContext().getResource("/WEB-INF/settings.properties").getPath() + "</div>");
      boolean isConnected = heosConnector.isConnected();
      lastConnection = isConnected ? new Date(System.currentTimeMillis()) : lastConnection;
      writer.println("<div>" + getValue("HEOS host") + properties.getHeosHost() + (isConnected ? " (connected)" : " (disconnected)") + "</div>");
      writer.println("<div>" + getValue("HEOS user") + properties.getHeosUser()
            + (heosConnector.isUserSignedIn(properties.getHeosUser()) ? " (signed in)" : " (signed out)") + "</div>");
      writer.println("<div>" + getValue("HEOS connection") + (lastConnection != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(lastConnection) : "-")
            + "</div>");
      writer.println("<div>" + getValue("Fibaro host") + properties.getFibaroHost() + "</div>");
      writer.println("<br><div>To change settings, update settings file and reload Servlet</div>");

      // List players
      writer.println("<h2 style='font-family:sans-serif;font-size:20px;color:#426d6e;margin-bottom: 5px;'>HEOS players</h1>");
      heosConnector.updatePlayers();
      Map<String, String> players = heosConnector.getPlayers();
      SortedSet<Entry<String, String>> sortedKeys = heosConnector.entriesSortedByValues(players);
      for (Entry<String, String> entry : sortedKeys) {
         String key = entry.getKey();
         String value = entry.getValue();
         writer.println("<div>" + getValue(key) + getValue(value) + heosConnector.getNowPlaying(key) + "</div>");
      }

      // List favorites
      writer.println("<h2 style='font-family:sans-serif;font-size:20px;color:#426d6e;margin-bottom: 5px;'>Favorite stations</h1>");
      heosConnector.updateStations();
      Map<String, String> stations = heosConnector.getStations();
      sortedKeys = heosConnector.entriesSortedByValues(stations);
      for (Entry<String, String> entry : sortedKeys) {
         String key = entry.getKey();
         String value = entry.getValue();
         writer.println("<div>" + getValue(key) + value + "</div>");
      }

      // List playlists
      writer.println("<h2 style='font-family:sans-serif;font-size:20px;color:#426d6e;margin-bottom: 5px;'>Playlists</h1>");
      heosConnector.updatePlaylists();
      Map<String, String> playlists = heosConnector.getPlaylists();
      sortedKeys = heosConnector.entriesSortedByValues(playlists);
      for (Entry<String, String> entry : sortedKeys) {
         String key = entry.getKey();
         String value = entry.getValue();
         writer.println("<div>" + getValue(key) + value + "</div>");
      }

      // API
      String playCommand = getBaseURL(request) + "?player=12345&command=play&vd=123";
      String stopCommand = getBaseURL(request) + "?player=12345&command=stop&vd=123";
      String volumeCommand = getBaseURL(request) + "?player=12345&command=volume&volume=50&vd=123";
      String stationCommand = getBaseURL(request) + "?player=12345&command=station&station=s12345&vd=123&labeltext=Example%20Station";
      String playlistCommand = getBaseURL(request) + "?player=12345&command=playlist&playlist=12345&vd=123&labeltext=Example%20Playlist";
      String inputCommand = getBaseURL(request) + "?player=12345&command=input&inputplayer=23456&inputname=inputs/optical_in_1&vd=123&labeltext=Input%20Aux";
      String alarmCommand = getBaseURL(request) + "?player=12345&command=alarm&station=s12345&volume=50&vd=123&labeltext=Station%20ABC";
      String triggerCommand = getBaseURL(request) + "?player=12345&command=trigger&station=s12345&volume=50&vd=123&labeltext=Station%20ABC";
      writer.println("<h2 style='font-family:sans-serif;font-size:20px;color:#426d6e;margin-bottom: 5px;'>API</h1>");
      writer.println("<div><b>" + getValue("Start playback") + "</b>" + playCommand + "</div>");
      writer.println("<div><b>" + getValue("Stop playback") + "</b>" + stopCommand + "</div>");
      writer.println("<div><b>" + getValue("Set volume") + "</b>" + volumeCommand + "</div>");
      writer.println("<div><b>" + getValue("Play station") + "</b>" + stationCommand + "</div>");
      writer.println("<div><b>" + getValue("Play playlist") + "</b>" + playlistCommand + "</div>");
      writer.println("<div><b>" + getValue("Play input") + "</b>" + inputCommand + "</div>");
      writer.println("<div><b>" + getValue("Alarm") + "</b>" + alarmCommand + "</div>");
      writer.println("<div><b>" + getValue("Trigger") + "</b>" + triggerCommand + "</div>");
      writer.println("<br><div>" + getValue("Parameters") + getValue("command") + "Command to run</div>");
      writer.println("<div>" + getValue("") + getValue("player") + "HEOS player (id)</div>");
      writer.println("<div>" + getValue("") + getValue("volume") + "Volume, 0 to 100 (value)</div>");
      writer.println("<div>" + getValue("") + getValue("station") + "Favorite station (id)</div>");
      writer.println("<div>" + getValue("") + getValue("playlist") + "Playlist (id)</div>");
      writer.println("<div>" + getValue("") + getValue("inputplayer") + "HEOS player (id) with input source</div>");
      writer.println("<div>" + getValue("") + getValue("inputname") + "Input source name</div>");
      writer.println("<div>" + getValue("") + getValue("vd") + "Fibaro virtual device (optional) (id)</div>");
      writer.println("<div>" + getValue("") + getValue("labeltext") + "Fibaro \"now playing\"-label text (optional) (string)</div>");
      writer.println(
            "<br><div>" + getValue("") + "\"Alarm\" always changes station and volume whereas \"Trigger\" doesn't if the player is already playing</div>");
      writer.println("<div>" + getValue("")
            + "Parameters \"vd\" (virtual device) and \"labeltext\" are used to update volume slider and labeltext in the Fibaro GUI</div>");
      writer.println("<div>" + getValue("") + "The slider id must be set to \"slider\" and the label id to \"label\"</div>");

      writer.println("</body>");
      writer.println("</html>");
      response.setStatus(HttpServletResponse.SC_OK);
   }

   /**
    * @param request The HTTP request
    * @return The base URL of this servlet
    */
   private String getBaseURL(HttpServletRequest request) {
      return "http://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
   }

   /**
    * Formats a value
    * 
    * @param value The value
    * @return The formatted value string
    */
   private String getValue(String value) {
      return String.format("%1$-20s", value).replace(" ", "&nbsp;");
   }

}
