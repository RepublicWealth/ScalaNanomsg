package org.searchx.akka.nanomsg

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import org.searchx.akka.nanomsg.SocketType._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class NanoSocketActor(socketType : SocketType, debug : Boolean, debugPrfx : String) extends Actor {
  import context.dispatcher;
	private var socketp : Option[NanoSocket] = None ;
	
	private def socket = socketp match {
		case Some(socket) => socket;
		case None => throw new NanoSocketException("Socket not open yet");
	}

	def receive = {
		case message : Message => ioCommandHandler(SendCommand(message));
		
		case command : IOCommand =>	ioCommandHandler(command); 
		
		case command : SocketCommand => socketCommandHandler(command);
	}
	
	private def ioCommandHandler(command : IOCommand) = command match {
		case _ : ReceiveCommand => {
			val lastsender = sender
			val	msg : Future[Message] = command match {
				case Receive(messageType) => socket.read(messageType);
				case _ => throw new UnknownError();
			}
			
			msg onSuccess {
				case m => {
					lastsender ! m;
				}
			};
			
			msg onFailure {
				case m => 
					lastsender ! m
			}
		};
		
		case SendCommand(message) => {
			val lastsender = sender;
			val res = socket.send(message);
			
			res onSuccess {
				case i => { lastsender ! SendSuccess(i);  }
			}
			
			res onFailure {
				case m => { lastsender ! m; }
			}
		}

    case ex : Throwable => throw ex;
	}
	
	private def socketCommandHandler(command : SocketCommand) = {
		val lastsender = sender
		val future = command match {
			case Bind(address) => 
				socket.bind(address);
			case Connect(address) =>
				socket.connect(address);
			case Remove(address) =>
				socket.remove(address);
			case Close =>
				socket.close();
			case _ => throw new UnknownError();
		}
		
		future onSuccess {
			case _ => lastsender ! CommandSuccess;
		}
		
		future onFailure {
			case m => lastsender ! m;
		}
	}

	override def postStop = {
    println(s"$debugPrfx: Closing socket");
		Await.ready(socket.close(), Duration.Inf);
	}
	
	override def preStart = {
		socketp = Some(new NanoSocket(socketType, false, debug, debugPrfx));
	}
}

object NanoSocketActor {
  def props(socketType : SocketType, debug : Boolean = false, debugPrfx : String = "") =
    Props(new NanoSocketActor(socketType, debug, debugPrfx));
}