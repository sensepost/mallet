# Mallet, a framework for creating proxies

Mallet is a tool for creating proxies for arbitrary protocols, along similar lines to the familiar intercepting web proxies, just more generic.

It is built upon the Netty framework, and relies heavily on the Netty pipeline concept, which allows the graphical assembly of graphs of handlers. In the Netty world, handler instances provide frame delimitation (i.e. where does a message start and end), protocol decoding and encoding (converting a stream of bytes into Java objects, and back again, or converting a stream of bytes into a different stream of bytes - think compression and decompression), and higher level logic (actually doing something with those objects).

By following the careful separation of Codecs from Handlers that actually manipulate the messages, Mallet can benefit from the large library of existing Codecs, and avoid reimplementation of many protocols. The final piece of the puzzle is provided by a Handler that copies messages received on one pipeline to another pipeline, proxying those messages on to their final destination. 

Of course, while the messages are within Mallet, they can easily be tampered with, either with custom Handlers written in Java or a JSR-223 compliant scripting language, or manually, using one of the provided editors.

# Building Mallet

Mallet makes use of Maven, so compiling the code is a matter of

```
mvn package
```

To run it:

```
cd target/
java -jar mallet-1.0-SNAPSHOT-spring-boot.jar
```

There are a few sample graphs provided in the ```examples/``` directory. The JSON graphs expect a JSON client to connect to Mallet on localhost:9998/tcp, with the real server at localhost:9999/tcp. Only the last JSON graph (json5.mxe) makes any assumptions about the structure of the JSON messages being passed, so they should be applicable to any app that sends JSON messages.

The demo.mxe shows a complex graph, with two pipelines, both TCP and UDP. The TCP pipeline is built to support HTTP and HTTPS on ports 80 and 443 respectively, as well as WebSockets, while relaying any other traffic directly to its destination. The UDP pipeline is built to process DNS requests on localhost:1053/udp, replace queries for google.com with queries for www.sensepost.com, and forward the requests on to Google DNS servers.

# Feedback

Feedback is welcome. Please create issues where appropriate, or contact the author on Twitter @RoganDawes.
