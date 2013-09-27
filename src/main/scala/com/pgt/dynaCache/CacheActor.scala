package com.pgt.dynaCache

import akka.actor.Actor
import akka.routing.ConsistentHashingRouter.ConsistentHashable
import akka.actor.ActorLogging

 
class CacheActor extends Actor with ActorLogging {
  var cache = Map.empty[String, String]

 override def preStart(): Unit =   {
  	log.info("Cache actor {} is starting", self.path.name)
  }
	override def postStop(): Unit = {
  	log.info("Cache actor {} is stopped", self.path.name)
	}

 
  def receive = {
    case Entry(key, value) ⇒ cache += (key -> value)
    case Get(key)          ⇒ {
    	log.debug("Cache actor {} getting key {}", self.path.name, key)
    	sender ! cache.get(key)
    }
    case Evict(key)        ⇒ cache -= key
  }
}
 
case class Evict(key: String)
 
case class Get(key: String) extends ConsistentHashable {
  override def consistentHashKey: Any = key
}
 
case class Entry(key: String, value: String)