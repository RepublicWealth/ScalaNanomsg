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

package org.searchx.akka.nanomsg;

object SocketType extends Enumeration {
	type SocketType = Value
	val REQ, REP, PUSH, PULL, PUB, SUB, BUS, SURVEYOR, RESPONDENT = Value
};


object MessageType extends Enumeration {
  type MessageType = Value
  val BINARY, STRING, ZEROCOPY = Value
};

import org.searchx.akka.nanomsg.MessageType._
import org.searchx.akka.nanomsg.SocketType._

import scala.concurrent.Future
;

sealed trait Address;
case class TcpAddress (hostname : String, port : Integer) extends Address {
  override def toString = "tcp://" + hostname + ":" + port;
}
case class IpcAddress (identifier : String) extends Address {
  override def toString = "ipc:///tmp/" + identifier;
}
case class InprocAddress (identifier : String) extends Address {
  override def toString = "inproc://" + identifier;
}

sealed trait Message;
case class StringMessage(message : String) extends Message{
  override def toString : String = {
    if (message.length > 1000)
      message.substring(0, 490) + "..." + message.substring(message.length - 490, message.length)
    else
      message
  }
}
case class BinaryMessage(message : Array[Byte]) extends Message;
case class NativeMessage(message : Long, size : Long) extends Message;
case class MessageCollection(messages : Seq[Message]) extends Message;
case class NoMessage() extends Message;
case class AsyncMessage(message : Future[Message]) extends Message;
case class ZeroCopyMessage(message : Long, size : Long) extends Message {
  def free() = {
    NanoLibraryO.nn_freemsg(message);
  }
}


sealed trait Param;
sealed trait Command extends Param;
sealed trait SocketCommand extends Command;
case class Create(socketType : SocketType) extends SocketCommand;
case class Bind(address : Address) extends SocketCommand;
case class Connect(address : Address) extends SocketCommand;
case class Remove(address : Address) extends SocketCommand;
case object Close extends SocketCommand;
case class NewSocket(name : String) extends SocketCommand;
case class NamedSocketCommand(name : String, command : SocketCommand) extends SocketCommand;
case class ReceiveSocketCommand(command : SocketCommand) extends SocketCommand;
case class SendSocketCommand(command : SocketCommand) extends SocketCommand;

sealed trait IOCommand extends Command;
sealed trait ReceiveCommand extends IOCommand;
case class Receive(messageType : MessageType) extends ReceiveCommand;
//case object ReceiveString extends Receive(STRING);
//case object ReceiveBytes extends Receive(BINARY);
case class SendCommand(message : Message) extends IOCommand;

sealed trait IOResult;
case object CommandSuccess extends IOResult;
case class SendSuccess(result : Int) extends IOResult;

case class NanoSocketException(message : String) extends Exception(message);
case class NanoSocketTimeoutException() extends Exception;