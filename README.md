ScalaNanomsg
===============

Scala bindings for nanomsg (http://nanomsg.org) using Futures and Actors

What is this ?
---------------

This repository contains Scala wrapper over JNI-based Java bindings
for nanomsg

How to build
-------------

1. Build the jnano library at https://github.com/anbhole/jnano. Follow the
   instructions in the README there.

2. Copy the jnano-0.1.jar file to the lib directory.

3. Use sbt compile to compile the repository

4. You can use sbt-pack(https://github.com/xerial/sbt-pack) to pack the library
   in a jar file.

Features
---------

1. Fully asynchronous using Scala Futures

2. Akka actor based implementation

3. Supports string, binary and zero copy messages

Documentation
-------------

In progress :)

Example
---------

Receiving using Futures

	val socket = new NanoSocket(SocketType.PULL);
	socket.bind(new TcpAddress("*", 40301));

	socket.readString() onSuccess {
		case StringMessage(s) => println(s)
	}


Receiving using Actors

	val context = ActorSystem.create("parent")
	val socketRef = context.actorOf(NanoSocketActor.props(SocketType.PULL), "pull-socket");
	socketRef ! Connect(new TcpAddress("*", 40301))

	val message = socketRef ? Receive(MessageType.STRING)
	message onSuccess {
		case StringMessage(s) => println(s)
	}

Sending using Futures

	val socket = new NanoSocket(SocketType.PUSH);
	socket.connect(new TcpAddress("localhost", 40301))

	socket.send(new StringMessage("Test string"))

Sending using Actors

	val context = ActorSystem.create("parent")
	val socketRef = context.actorOf(NanoSocketActor.props(SocketType.PUSH), "push-socket");
	socketRef ! Connect(new TcpAddress("localhost", 40301))

	socketRef ! StringMessage("String message")


Status
--------

We are using this library internally for our backend. It has been tested sufficiently.
Kindly report any issues or bugs.

License
-------

This project is released under the MIT license, as is the native
libnanomsg library.  See COPYING for more details.

Author
------

Abhijit Bhole (abhijit@searchx.in)


[1]: http://nanomsg.org/                          "nanomsg"
[2]: https://github.com/anbhole/jnano             "jnano library"