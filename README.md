# Integration of Fibaro HC/HCL and Denon HEOS
Integration of the Fibaro HC/HCL smart home solution and the Denon HEOS multi-room system.

The integration has two parts:
* A Servlet which "talks" to the Denon HEOS system and exposes a simple GET-API.
* A Fibaro Virtual Device which sends GET-commands to the Servlet.

The Servlet currently supports the following operations:
* Play
* Stop
* Change volume
* Play station
* Play playlist
* Play aux input

It also supports sent updates of the volume and currently playing information to the Virtual Device.
### Installation
* Update settings.properties with IP/port/credentials for Fibaro HC/HCL and Denon HEOS.
* Build and deploy the Servlet (Tomcat is recommended).
* Open the servlet in a web browser, id:s for players and stations will be listed.
* Check Servlet log for warning/errors

### Versions
1.0&nbsp;&nbsp;&nbsp;&nbsp;First version.  
1.1&nbsp;&nbsp;&nbsp;&nbsp;Improved stability and error handling.  
1.2&nbsp;&nbsp;&nbsp;&nbsp;Added support for playing playlists and inputs.

