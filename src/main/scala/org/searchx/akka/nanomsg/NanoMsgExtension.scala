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