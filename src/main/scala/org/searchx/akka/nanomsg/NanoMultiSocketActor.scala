/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Abhijit Bhole, SearchX
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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