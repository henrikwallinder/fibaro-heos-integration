package se.wallinder.heos.connector;

import static se.wallinder.heos.util.ServletConstants.HEOS_CMD_UNDER_PROCESS;
import static se.wallinder.heos.util.ServletConstants.HEOS_FAVORITES_ID;
import static se.wallinder.heos.util.ServletConstants.HEOS_PLAYLIST_ID;
import static se.wallinder.heos.util.ServletConstants.HEOS_PREFIX;
import static se.wallinder.heos.util.ServletConstants.HEOS_RESULT_STATE_PLAY;
import static se.wallinder.heos.util.ServletConstants.HEOS_RESULT_SUCCESS;
import static se.wallinder.heos.util.ServletConstants.HEOS_SLEEPTIME_IN_MS;
import static se.wallinder.heos.util.ServletConstants.HEOS_TIMEOUT_IN_MS;
import static se.wallinder.heos.util.ServletConstants.HEOS_TYPE_PLAYLIST;
import static se.wallinder.heos.util.ServletConstants.HEOS_TYPE_STATION;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import se.wallinder.heos.util.ServletConstants;

public class HEOSConnector {

   private final static Logger LOGGER = Logger.getLogger(HEOSConnector.class.getName());
   private Map<String, String> players;
   private Map<String, String> stations;
   private Map<String, String> playlists;
   private final String heosHost;
   private final String heosUser;
   private final String heosPassword;
   private Socket socket = null;

   /**
    * Protected Constructor
    */
   public HEOSConnector(String heosHost, String heosUser, String heosPassword) {
      LOGGER.setLevel(Level.WARNING);
      this.heosHost = heosHost;
      this.heosUser = heosUser;
      this.heosPassword = heosPassword;
      // Connect
      connect();
      players = getAllPlayers();
      stations = getAllStations();
      playlists = getAllPlaylists();
   }

   /**
    * Connects to the HEOS system
    */
   public void connect() {
      try {
         // If socket is already open, close it first
         if (socket != null && !socket.isClosed()) {
            try {
               socket.close();
            } catch (Exception e) {
               LOGGER.severe("Could not disconnect from HEOS " + heosHost + ":" + ServletConstants.HEOS_PORT);
            }
         }
         LOGGER.info("Connecting to HEOS " + heosHost + ":" + ServletConstants.HEOS_PORT);
         socket = new Socket(heosHost, ServletConstants.HEOS_PORT);
         socket.setKeepAlive(true);
      } catch (Exception e) {
         LOGGER.severe("Could not connect to HEOS " + heosHost + ":" + ServletConstants.HEOS_PORT);
      }
   }

   /**
    * Starts playback
    *
    * @param playerID The ID of the player
    * @return True if okay, false if not
    */
   public boolean play(String playerID) {
      // Always ungroup before playing if grouped
      if (isGrouped(playerID)) {
         ungroupPlayers(playerID);
      }
      // Start playback
      return startPlayback(playerID);
   }

   /**
    * Stops playback
    * 
    * @param playerID The ID of the player
    * @return True if okay, false if not
    */
   public boolean stop(String playerID) {
      // Always ungroup before stopping if grouped
      if (isGrouped(playerID)) {
         ungroupPlayers(playerID);
      }
      // Stop playback
      return stopPlayback(playerID);
   }

   /**
    * Sets the volume of a specified player
    * 
    * @param playerID The ID of the player
    * @param volume The volume to set
    * @return True if okay, false if not
    */
   public boolean volume(String playerID, int volume) {
      // Set volume
      return setVolume(playerID, volume);
   }

   /**
    * Plays the specified station (id) on the specified player
    * 
    * @param playerID The ID of the player
    * @param stationID The ID of the station
    * @return True if okay, false if not
    */
   public boolean station(String playerID, String stationID) {
      // Always ungroup before playing if grouped
      if (isGrouped(playerID)) {
         ungroupPlayers(playerID);
      }

      // If not signed in, sign in
      if (!isUserSignedIn(heosUser)) {
         signIn();
      }

      // If unsuccessful, log error
      boolean success = playStation(playerID, stationID);
      if (!success) {
         LOGGER.warning("Could not play station " + stationID + " on player " + playerID);
      }

      return success;
   }

   /**
    * Plays the specified playlist (id) on the specified player
    * 
    * @param playerID The ID of the player
    * @param playlistID The ID of the playlist
    * @return True if okay, false if not
    */
   public boolean playlist(String playerID, String playlistID) {
      // Always ungroup before playing if grouped
      if (isGrouped(playerID)) {
         ungroupPlayers(playerID);
      }

      // If not signed in, sign in
      if (!isUserSignedIn(heosUser)) {
         signIn();
      }

      // If unsuccessful, log error
      boolean success = playPlaylist(playerID, playlistID);
      if (!success) {
         LOGGER.warning("Could not play playlist " + playlistID + " on player " + playerID);
      }

      return success;
   }

   /**
    * Plays an input of a player on a player
    * 
    * @param playerID The ID of the player
    * @param inputPlayerID The ID of the player with the input
    * @param inputName The name of the input
    * @return True if okay, false if not
    */
   public boolean input(String playerID, String inputPlayerID, String inputName) {
      // Always ungroup before playing if grouped
      if (isGrouped(playerID)) {
         ungroupPlayers(playerID);
      }

      // If unsuccessful, log error
      boolean success = playInput(playerID, inputPlayerID, inputName);
      if (!success) {
         LOGGER.warning("Could not play input " + inputName + " of player " + inputPlayerID + " on player " + playerID);
      }

      return success;
   }

   /**
    * Finds out if connected to the HEOS system by trying to send a heartbeat
    * 
    * @return True if connected, false if not
    */
   public boolean isConnected() {
      return validateResult(sendCommand("system/heart_beat", ""), HEOS_RESULT_SUCCESS);
   }

   /**
    * Gets now playing media for a given player
    * 
    * @param playerID The player ID
    * @return The now playing or empty string if none
    */
   public String getNowPlaying(String playerID) {
      String jsonResult = sendCommand("player/get_now_playing_media", "?pid=" + playerID);
      if (jsonResult == null) {
         LOGGER.warning("Could not get now playing");
         return "";
      }
      JSONParser parser = new JSONParser();
      try {
         JSONObject rootObject = (JSONObject) parser.parse(jsonResult);
         JSONObject payload = (JSONObject) rootObject.get("payload");
         String station = (String) payload.get("station");
         String song = (String) payload.get("song");
         if (station != null && !station.isEmpty()) {
            return station;
         } else if (song != null && !song.isEmpty()) {
            return song;
         }
      } catch (ParseException pe) {
         LOGGER.severe("Could not parse result when getting players");
      }
      return "";
   }

   /**
    * Updates available players
    * 
    * @return A map with all player ID:s and names
    */
   public void updatePlayers() {
      players = getAllPlayers();
   }

   /**
    * Gets all available players
    * 
    * @return A map with all player ID:s and names
    */
   public Map<String, String> getPlayers() {
      return players;
   }

   /**
    * Updates available stations
    * 
    * @return A map with all player ID:s and names
    */
   public void updateStations() {
      stations = getAllStations();
   }

   /**
    * Gets all favorite stations
    * 
    * @return A map with all station ID:s and names
    */
   public Map<String, String> getStations() {
      return stations;
   }

   /**
    * Updates available playlists
    * 
    * @return A map with all player ID:s and names
    */
   public void updatePlaylists() {
      playlists = getAllPlaylists();
   }

   /**
    * Gets all playlists
    * 
    * @return A map with all playlist ID:s and names
    */
   public Map<String, String> getPlaylists() {
      return playlists;
   }

   /**
    * Gets all available players
    * 
    * @return A map with all player ID:s and names
    */
   private Map<String, String> getAllPlayers() {
      Map<String, String> players = new HashMap<>();
      String jsonResult = sendCommand("player/get_players", "");
      if (jsonResult == null) {
         LOGGER.warning("Could not get players");
         return players;
      }
      JSONParser parser = new JSONParser();
      try {
         JSONObject rootObject = (JSONObject) parser.parse(jsonResult);
         JSONArray payload = (JSONArray) rootObject.get("payload");
         Iterator iterator = payload.iterator();
         while (iterator.hasNext()) {
            JSONObject element = (JSONObject) iterator.next();
            String pid = String.valueOf((Long) element.get("pid"));
            String name = (String) element.get("name");
            players.put(pid, name);
         }
      } catch (ParseException pe) {
         LOGGER.severe("Could not parse result when getting players");
      }
      return players;
   }

   /**
    * Gets the users favorite stations
    * 
    * @return A map with all station ID:s and names
    */
   private Map<String, String> getAllStations() {
      // If not signed in, sign in
      if (!isUserSignedIn(heosUser)) {
         signIn();
      }

      Map<String, String> stations = new HashMap<>();
      String jsonResult = sendCommand("browse/browse", "?sid=" + HEOS_FAVORITES_ID);
      if (jsonResult == null) {
         LOGGER.warning("Could not get stations");
         return stations;
      }
      JSONParser parser = new JSONParser();
      try {
         JSONObject rootObject = (JSONObject) parser.parse(jsonResult);
         JSONArray payload = (JSONArray) rootObject.get("payload");
         Iterator iterator = payload.iterator();
         // Payload contains all favorties, only add stations...
         while (iterator.hasNext()) {
            JSONObject element = (JSONObject) iterator.next();
            String type = (String) element.get("type");
            String mid = (String) element.get("mid");
            String name = (String) element.get("name");
            if (type != null && type.contentEquals(HEOS_TYPE_STATION)) {
               stations.put(mid, name);
            }
         }
      } catch (ParseException pe) {
         LOGGER.severe("Could not parse result when getting stations");
      }
      return stations;
   }

   /**
    * Gets the users playlists
    * 
    * @return A map with all playlist ID:s and names
    */
   private Map<String, String> getAllPlaylists() {
      // If not signed in, sign in
      if (!isUserSignedIn(heosUser)) {
         signIn();
      }

      Map<String, String> stations = new HashMap<>();
      String jsonResult = sendCommand("browse/browse", "?sid=" + HEOS_PLAYLIST_ID);
      if (jsonResult == null) {
         LOGGER.warning("Could not get playlists");
         return stations;
      }
      JSONParser parser = new JSONParser();
      try {
         JSONObject rootObject = (JSONObject) parser.parse(jsonResult);
         JSONArray payload = (JSONArray) rootObject.get("payload");
         Iterator iterator = payload.iterator();
         // Payload should contain all playlists, check type to be sure
         while (iterator.hasNext()) {
            JSONObject element = (JSONObject) iterator.next();
            String type = (String) element.get("type");
            String cid = (String) element.get("cid");
            String name = (String) element.get("name");
            if (type != null && type.contentEquals(HEOS_TYPE_PLAYLIST)) {
               stations.put(cid, name);
            }
         }
      } catch (ParseException pe) {
         LOGGER.severe("Could not parse result when getting playlists");
      }
      return stations;
   }

   /**
    * @param map The map to sort
    * @return A sorted map
    */
   public <K, V extends Comparable<? super V>> SortedSet<Map.Entry<K, V>> entriesSortedByValues(Map<K, V> map) {
      SortedSet<Map.Entry<K, V>> sortedEntries = new TreeSet<Map.Entry<K, V>>(new Comparator<Map.Entry<K, V>>() {
         @Override
         public int compare(Map.Entry<K, V> e1, Map.Entry<K, V> e2) {
            int res = e1.getValue().compareTo(e2.getValue());
            return res != 0 ? res : 1;
         }
      });
      sortedEntries.addAll(map.entrySet());
      return sortedEntries;
   }

   /**
    * Finds out if a user is signed in
    * 
    * @param username The name of the user
    * @return True if signed in, false if not
    */
   public boolean isUserSignedIn(String username) {
      return validateResult(sendCommand("system/check_account", ""), "signed_in&un=" + username);
   }

   /**
    * Finds out if a player is playing
    * 
    * @param playerID The ID of the player
    * @return True if playing, false if not
    */
   public boolean isPlaying(String playerID) {
      return validateResult(sendCommand("player/get_play_state", "?pid=" + playerID), HEOS_RESULT_STATE_PLAY);
   }

   /**
    * Finds out if a player is grouped
    * 
    * @param playerID The ID of the player
    * @return True if group, false if not
    */
   private boolean isGrouped(String playerID) {
      return validateResult(sendCommand("group/get_groups", ""), "\"pid\": " + playerID);
   }

   /**
    * Tries to sign in to HEOS
    * 
    * @return True if ok, false if not
    */
   private boolean signIn() {
      return validateResult(sendCommand("system/sign_in", "?un=" + heosUser + "&pw=" + heosPassword), HEOS_RESULT_SUCCESS);
   }

   /**
    * Creates a group with the specified player (e.g. clears groups)
    * 
    * @param playerID The ID of the player
    * @return True if ok, false if not
    */
   private boolean ungroupPlayers(String playerID) {
      return validateResult(sendCommand("group/set_group", "?pid=" + playerID), HEOS_RESULT_SUCCESS);
   }

   /**
    * Starts to play on the specified player
    * 
    * @param playerID The ID of the player
    * @return True if ok, false if not
    */
   private boolean startPlayback(String playerID) {
      return validateResult(sendCommand("player/set_play_state", "?pid=" + playerID + "&state=play"), HEOS_RESULT_SUCCESS);
   }

   /**
    * Stops to play on the specified player
    * 
    * @param playerID The ID of the player
    * @return True if ok, false if not
    */
   private boolean stopPlayback(String playerID) {
      return validateResult(sendCommand("player/set_play_state", "?pid=" + playerID + "&state=stop"), HEOS_RESULT_SUCCESS);
   }

   /**
    * Sets the volume of a specified player
    * 
    * @param playerID The ID of the player
    * @param volume The volume to set
    * @return True if ok, false if not
    */
   private boolean setVolume(String playerID, int volume) {
      return validateResult(sendCommand("player/set_volume", "?pid=" + playerID + "&level=" + String.valueOf(volume)), HEOS_RESULT_SUCCESS);
   }

   /**
    * Plays the station of a specified player
    * 
    * @param playerID The ID of the player
    * @param stationID The ID of the station
    * @return True if ok, false if not
    */
   private boolean playStation(String playerID, String stationID) {
      return validateResult(sendCommand("browse/play_stream", "?pid=" + playerID + "&sid=" + HEOS_FAVORITES_ID + "&mid=" + stationID), HEOS_RESULT_SUCCESS);
   }

   /**
    * Plays the playlist of a specified player, existing queue is replaced
    * 
    * @param playerID The ID of the player
    * @param playlistID The ID of the playlist
    * @return True if ok, false if not
    */
   private boolean playPlaylist(String playerID, String playlistID) {
      return validateResult(sendCommand("browse/add_to_queue", "?pid=" + playerID + "&sid=" + HEOS_PLAYLIST_ID + "&cid=" + playlistID + "&aid=4"),
            HEOS_RESULT_SUCCESS);
   }

   /**
    * Plays the input of a specified player on a specified player
    * 
    * @param playerID The ID of the player
    * @param inputPlayerID The ID of the player with the input
    * @param inputName The name of the input
    * @return True if ok, false if not
    */
   private boolean playInput(String playerID, String inputPlayerID, String inputName) {
      return validateResult(sendCommand("browse/play_input", "?pid=" + playerID + "&spid=" + inputPlayerID + "&input=" + inputName), HEOS_RESULT_SUCCESS);
   }

   /**
    * Validates a result with an expected result
    * 
    * @param result The actual result
    * @param expectedResult The expected result
    * @return True of valid, false if not
    */
   private boolean validateResult(String result, String expectedResult) {
      return result != null && expectedResult != null && result.contains(expectedResult);
   }

   /**
    * Sends a command to the HEOS
    * 
    * @param command The command to send
    * @param arguments The command arguments
    * @param expectedResult The expected result (or part of expected result)
    * 
    * @return The command result or null if none/error
    */
   private synchronized String sendCommand(String command, String arguments) {
      // Check parameters
      if (command == null || command.isEmpty() || arguments == null) {
         LOGGER.severe("Invalid command arguments");
         return null;
      }

      // Execute command
      String completeCommand = HEOS_PREFIX + command + arguments;
      try {
         // Send command
         PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
         LOGGER.info("Sending command: " + completeCommand);
         out.println(completeCommand);
         // Read response
         String response = "";
         long startTime = System.currentTimeMillis();
         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
         do {
            response = in.ready() ? in.readLine() : null;
            // If a response
            if (response != null && !response.isEmpty()) {
               // Check that response is for sent command
               if (!response.contains("\"command\": \"" + command + "\"")) {
                  LOGGER.severe("Response did not match command: " + completeCommand);
                  return null;
               }
               LOGGER.info("Received response: " + response);
               // Only return if a valid response, e.g. not under process
               if (!response.contains(HEOS_CMD_UNDER_PROCESS)) {
                  return response;
               }
            }
            Thread.sleep(HEOS_SLEEPTIME_IN_MS);
         } while ((System.currentTimeMillis() - startTime) < HEOS_TIMEOUT_IN_MS);
         // Timeout occured
         LOGGER.warning("Timeout while sending command: " + completeCommand);
      } catch (Exception e) {
         LOGGER.severe("Error while sending command: " + completeCommand);
      }
      // Something went wrong, return null;
      return null;
   }

}
