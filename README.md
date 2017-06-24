# Abandoned.
I went way too low-level for this project, without actually thinking at that low level. Predictably, the result is a bit of a mess. For ease of use with Spring, the server component of the successor to this project will likely just be JSON (or maybe protobuffers) on STOMP on Websockets. 

I'm leaving this up, but please don't judge me on this code. A lot of it is not good. I did a better job of architecting the code in the [SynchronizedMediaPlayer repo](https://github.com/NoahAndrews/SynchronizedMediaPlayer), though of course there is plenty there that I would do differently as well.

# SAVPP-java
Java library for the Synchronized Audio/Video Playback Protocol. I'm developing this protocol to be used in [this project.](https://github.com/NoahAndrews/SynchronizedMediaPlayer)

[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://raw.githubusercontent.com/NoahAndrews/SAVPP-java/master/LICENSE)

[![Build Status](https://img.shields.io/travis/NoahAndrews/SAVPP-java/master.svg?maxAge=2592000)](https://travis-ci.org/NoahAndrews/SAVPP-java)

[![Dependency Status](https://www.versioneye.com/user/projects/577c5dba649a6f000d0469ed//badge.svg?style=flat)](https://www.versioneye.com/user/projects/577c5dba649a6f000d0469ed/)

## Links
* General notes about how the protocol will work can be found in [NOTES.md.](NOTES.md)
* A WIP list of protocol messages can be found [here.](https://github.com/NoahAndrews/SAVPP-java/wiki/Messages) 

## Building
To build this, you will need to install protobuf-java into your local maven repository.
