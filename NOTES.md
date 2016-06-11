# Notes about what the protocol will look like

* Built using [Protocol Buffers](https://developers.google.com/protocol-buffers/)
* Event based, so perhaps each packet has an event field, defined by an enum
* We need a way to measure latency
* Perhaps we wait to play until the current latency has elapsed. The computer
  receiving the play command would play immediately. For more than a
  single guest, the host computer could tell each guest how long they
  should wait before playing, so that they all start playing approximately
  when the computer with the longest latency gets the message.
* Pause commands include a timestamp, so that when they are received, 
  the receiver can rewind to that point. We need to handle for the case 
  that the receiver hasn't reached the pause point. In that case, the 
  receiver should inform the sender of its current timestamp, so that 
  the sender can do the rewinding
* If the media is paused for a user-definable bit, we should rewind a
  user-definable bit. The computer responsible for pausing the audio 
  should be the one to send a command to the other to rewind, with a 
  timestamp to rewind to. 
* When a connection is established, different things will need to be
  agreed on. The first thing is the file. All participants should send
  an md5 hash of the file to the host.
* The host sends the initial configuration bundle to the guests
* Guests can send requests to change the configuration, which the host
  then either denies or approves and distributes to all guests.
* Configuration includes length of pause required before skipping back,
  as well as length of automatic skip back.
* Configuration does not include length of skip buttons.
* Skipping/scrubbing events only send the final timestamp, nothing
  else. I suppose that means we'd need to do the latency delay thing. 
* This should support more than 2 users. But no direct connections
  between users, all communication goes through host.
* To measure latency, we'll need messages for ping requests and responses
* The MD5 hash should be sent in with the initial connection request
* HostResponse message contains an approve/deny enum
* Network communication will take place over a raw socket on [port 4440.](http://www.iana.org/assignments/service-names-port-numbers/service-names-port-numbers.xhtml?&page=82) 



# Notes about what the Java API will look like
### Error handling
* If it's an error about something the API consumer just did, we should throw an exception if possible
* If it's any other kind of error, like if a user disconnects, we should run a callback method
