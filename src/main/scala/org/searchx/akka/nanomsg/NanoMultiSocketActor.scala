package org.searchx.akka.nanomsg

import akka.actor.{PoisonPill, Props, ActorRef, Actor}
import org.searchx.akka.nanomsg.SocketType.SocketType
import org.searchx.akka.nanomsg.SocketType._
import org.searchx.akka.nanomsg._
import scala.collection.mutable.HashMap

class NanoMultSocketsSendActor(socketType : SocketType, debug : Boolean = false, debugPrfx : String = "") extends Actor {
  val socketRefs = HashMap[String, ActorRef]();

  class WorkerActor(successRcv : ActorRef) extends Actor {
    var repliesReceived : Int = 0;
    var zmessage : ZeroCopyMessage = null;

    def receive = {
      case ZeroCopyMessage(message, size) => {
        if (socketRefs.size > 1) {
          zmessage = ZeroCopyMessage(message, size);
          socketRefs.values foreach { _ ! NativeMessage(message, size) }
        } else if (socketRefs.size > 0) {
          socketRefs.values foreach { _ ! ZeroCopyMessage(message, size) }
        }
        else {
          zmessage = ZeroCopyMessage(message, size);
          self ! SendSuccess(0)
        }
      }

      case message : Message => {
        if (socketRefs.size > 0)
          socketRefs.values foreach { _ ! message }
        else
          self ! SendSuccess(0)
      }

      case SendSuccess(i) => {
        repliesReceived += 1;

        if (repliesReceived >= socketRefs.size) {
          if (zmessage != null) zmessage.free();
          successRcv ! SendSuccess(i);
          self ! PoisonPill
        }
      }

      case ex : Throwable => throw ex;
    }
  }

  def receive = {
    case message : Message => {
      val lastsender = sender;
      context.actorOf(Props(new WorkerActor(lastsender))) ! message;
    }

    case NewSocket(name) => if (!socketRefs.contains(name)) {
      socketRefs.put(name, context.actorOf(NanoSocketActor.props(socketType, debug, debugPrfx + s"_$name"), s"socket-$name"));
    }

    case NamedSocketCommand(name, command) => socketRefs.get(name) match {
      case Some(socket) => { socket ! command; }
      case None => throw new IllegalArgumentException("No such socket exists");
    }
  }
}

object NanoMultSocketsSendActor {
  def props(socketType : SocketType, debug : Boolean = false, debugPrfx : String = "") =
    Props(new NanoMultSocketsSendActor(socketType, debug, debugPrfx));
}