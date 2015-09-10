package org.searchx.akka.nanomsg

import java.util.concurrent.Executors

import org.nanomsg.{NNPollFD, NanoLibrary}
import org.searchx.akka.nanomsg.SocketType._

import scala.collection.mutable.HashMap
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

private object NanoLibraryO extends NanoLibrary  {
  println("Loaded nanomsg library")
}

private case class NanoSocketFD(socketfd : Int);

class NanoSocket( socketType : SocketType, rawSocket : Boolean = false, debug : Boolean = false, debugPrfx : String = "") {
  private val executor = Executors.newSingleThreadExecutor();
	private implicit val context = ExecutionContext.fromExecutor(executor);
	
  import org.searchx.akka.nanomsg.MessageType._
  import org.searchx.akka.nanomsg.NanoLibraryO._;
	
	implicit def socketTypeToNNOption(socketType : SocketType) : Int = socketType match {
		case REQ => NN_REQ
		case REP => NN_REP;
		case PUSH => NN_PUSH;
		case PULL => NN_PULL;
		case PUB => NN_PUB;
		case SUB => NN_SUB;
		case BUS => NN_BUS;
		case SURVEYOR => NN_SURVEYOR;
		case RESPONDENT => NN_RESPONDENT;
	};
	
	private def retvalCheck(retval : Int, errorMessage : String) : Int = {
		if (retval < 0) throw NanoSocketException(errorMessage + " : " + lastError);
		else retval;
	}
	
	private var closed = true;
	def isClosed = closed;
	private def errno = nn_errno();
	private def lastError = nn_strerror(errno);
	
	private def create( socketType : SocketType, rawSocket : Boolean = false) = Future { if (closed) {
		val fd = retvalCheck(nn_socket(if (rawSocket) AF_SP_RAW else AF_SP, socketType), "Failed to create socket");
		closed = false;
		if (debug) println("Socket created");
		fd;
	} else throw new NanoSocketException("Socket already open"); }
	
	private val nanoSocketFD = {
		new NanoSocketFD(Await.result(create(socketType, rawSocket), Duration.Inf));
	}
	
	private def socketfd = nanoSocketFD.socketfd;
	private val endpoints = new HashMap[Address, Int]();

	implicit def toString(address : Address) : String = address.toString

	private def bindOrConnect(address : Address, f : (Int, String) => Int) : Future[Int] = Future {
		if (debug) println(debugPrfx + ": Connecting to " + address);
		endpoints.getOrElseUpdate(address, retvalCheck(f(socketfd, address), "Failed to bind to " + address));
	}
	
	def bind(address : Address) = 
		bindOrConnect(address, nn_bind);
	
	def connect(address : Address) = 
		bindOrConnect(address, nn_connect);
	
	private def remove_(address : Address) : Int = {
		if (debug) println(debugPrfx + ": Removing endpoint " + address);
		endpoints.remove(address) match {
			case Some(endpoint) => retvalCheck(nn_shutdown(socketfd, endpoint), "Failed to remove endpoint from " + address);
			case None => throw new NanoSocketException("Not connected to " + address)
		}
	}

  def remove (address : Address) : Future[Int] = Future {
    remove_(address);
  }
	
	def close() = Future{
    if (!closed) {
      if (debug) println(s"$debugPrfx: Closing socket");
      endpoints.keys.foreach{ a => remove_(a) };
      retvalCheck(nn_close(socketfd), "Error closing socket");
      executor.shutdown();
      closed = true;
      if (debug) println(s"$debugPrfx: Socket closed");
    }
  }

//	private implicit def messageToAnyRef (message : Message) : AnyRef =  message match {
//		case m : StringMessage => m.message
//		case m : BinaryMessage => m.message
//	}

  private def read_(mType : MessageType, dontWait : Boolean) = {
    if (debug) println(s"$debugPrfx: Waiting to receive");

    val flags = if (dontWait) NN_DONTWAIT else 0;

    val result = mType match {
      case STRING => nn_recvstr(socketfd, flags);
      case BINARY =>  nn_recvbyte(socketfd, flags);
      case ZEROCOPY => {
        val nmsg = new org.nanomsg.NativeMessage();
        val ret = nn_recvzcopy(socketfd, flags, nmsg);
        if (ret < 0) null else nmsg;
      }
    }

    val message = result match {
      case s: String => StringMessage(s);
      case b: Array[Byte] => BinaryMessage(b);
      case n : org.nanomsg.NativeMessage => ZeroCopyMessage(n.message, n.size);
      case _ =>
        if (errno == EAGAIN)
          throw new NanoSocketTimeoutException();
        else
          throw new NanoSocketException(s"Incomplete message received from $debugPrfx : " + lastError);
    }

    if (debug) println(debugPrfx + ": Reply received: " + message);
    message;
  }

	private def readPoll(mType : MessageType, promise : Promise[Message]) : Unit = Future {
    val pollfd = Array(new NNPollFD(socketfd, NN_POLLIN));

//    if (debug) println(s"$debugPrfx: Polling to receive");
    val ret = nn_poll(pollfd, 1000);

    if (ret == 0)
      readPoll(mType, promise);
    else try {
      if (ret < 0)
        throw new NanoSocketException(s"Poll failed on socket $debugPrfx with error: " + lastError);
      else if ((pollfd(0).revents & NN_POLLIN) > 0) {
        promise.success(read_(mType, true));
      } else {
        throw new NanoSocketException(s"Unknown value for poll events returned on $debugPrfx ");
      }
    } catch {
      case e : NanoSocketTimeoutException => { readPoll(mType, promise);}
      case e : Throwable => { promise.failure(e); }
    }
	}

  private def readNoPoll(mType : MessageType, dontWait : Boolean, promise : Promise[Message]) : Unit = {
    val f = Future {
      read_(mType, dontWait)
    }

    f onSuccess {
      case m => promise.success(m)
    }

    f onFailure {
      case m => promise.failure(m)
    }
  }

  def read(mType : MessageType, dontWait : Boolean = false) : Future[Message] = {
    val promise = Promise[Message];
    if (dontWait)
      readNoPoll(mType, true, promise);
    else
      readPoll(mType, promise);
    promise.future;
  }

  def readString(dontWait : Boolean = false) : Future[Message] =
    read(STRING, dontWait);

  def readBytes(dontWait : Boolean = false) : Future[Message] =
    read(BINARY, dontWait);

  def readZeroCopy(dontWait : Boolean = false) : Future[Message] =
    read(ZEROCOPY, dontWait);
	
//	private implicit def toDebugString(message : Message) : String = message match {
//			case m : BinaryMessage => "Binary message of length " + m.message.length
//			case m : StringMessage => m.message
//		}

  private def send_(message : Message, dontWait : Boolean) : Int = {
    if (debug) println(debugPrfx + ": Request sending: " + message);

    val flags = if (dontWait) NN_DONTWAIT else 0;

    val sz = message match {
      case BinaryMessage(b) => nn_sendbyte(socketfd, b, flags);
      case StringMessage(s) => nn_sendstr(socketfd, s, flags);
      case ZeroCopyMessage(message, size) => nn_sendzcopy(socketfd, message, flags);
      case NativeMessage(message, size) => nn_sendnative(socketfd, message, size, flags);
      case _ => throw new IllegalArgumentException("Illegal message type")
    }

    if (dontWait && sz < 0 && errno == EAGAIN) throw new NanoSocketTimeoutException();
    if (sz < 0) throw new NanoSocketException("Incomplete message sent: " + lastError);

    if (debug) println(s"$debugPrfx: Request sent");
    sz
  }

  private def sendPoll(message : Message, promise : Promise[Int]) : Unit = {
    val pollfd = Array(new NNPollFD(socketfd, NN_POLLOUT));

//    if (debug) println(s"$debugPrfx: Polling to send");
    val ret = nn_poll(pollfd, 1000);

    if (ret == 0)
      sendPoll(message, promise);
    else try {
      if (ret < 0)
        throw new NanoSocketException(s"Poll failed on socket $debugPrfx with error: " + lastError);
      else if ((pollfd(0).revents & NN_POLLOUT) > 0) {
        promise.success(send_(message, true));
      } else {
        throw new NanoSocketException(s"Unknown value for poll events returned on $debugPrfx ");
      }
    } catch {
      case e : NanoSocketTimeoutException => { sendPoll(message, promise); }
      case e : Throwable => { promise.failure(e); }
    }
  }
  private def sendNoPoll(message : Message, dontWait : Boolean, promise : Promise[Int]) : Unit = {
    val f = Future {
      send_(message, dontWait);
    }

    f onSuccess {
      case m => promise.success(m)
    }

    f onFailure {
      case m => promise.failure(m)
    }
  }

  private def send_(message : Message, dontWait : Boolean, promise : Promise[Int]) : Unit = {
    if (dontWait)
      sendNoPoll(message, true, promise);
    else
      sendPoll(message, promise);
  }
	
	def send(message : Message, dontWait : Boolean = false) : Future[Int] = {

    message match {
      case MessageCollection(messages) => {
        Future.fold(messages.map(send(_, dontWait)))(0)((r, t) => r + t);
      }

      case NoMessage() => Future(0);

      case AsyncMessage(message) => {
        val promise = Promise[Int];
        message onSuccess {
          case m => send_(m, dontWait, promise);
        }

        message onFailure {
          case m => promise.failure(m);
        }

        promise.future;
      }

      case _ => {
        val promise = Promise[Int];
        send_(message, dontWait, promise);
        promise.future;
      }
    }

	}
}