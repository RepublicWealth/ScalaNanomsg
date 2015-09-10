package org.searchx.akka.nanomsg;

import scala.collection.mutable.HashMap
import akka.actor.{ActorContext, ActorRef, ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider, Props}

import scala.collection.mutable.ArrayBuffer

class NanoMsgExtension(val system: ActorSystem) extends Extension {
	import org.searchx.akka.nanomsg.NanoLibraryO._;
	
	private val socketActors = new ArrayBuffer[ActorRef];
	system.registerOnTermination {
		socketActors foreach (_ ! Close);
		nn_term();
	}
	
	def newSocket(name : String, socketParams: Param*)(implicit context: ActorContext): ActorRef = {
		val actor = context.actorOf(Props(classOf[NanoSocketActor], socketParams), name);
		socketActors.append(actor);
		return actor;
	}
}

object NanoMsgExtension extends ExtensionId[NanoMsgExtension] with ExtensionIdProvider {
  def lookup() = NanoMsgExtension
  override def createExtension(system: ExtendedActorSystem): NanoMsgExtension = new NanoMsgExtension(system)
}