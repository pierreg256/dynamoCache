package com.pgt.dynaCache

import language.postfixOps
import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.RootActorPath
import akka.actor.Terminated
import akka.actor.ActorLogging
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent._
import akka.cluster.Member
import akka.cluster.MemberStatus
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import com.pgt.utils.HashAlgorithm;

class NodeManager extends Actor with ActorLogging {
 
  val cluster = Cluster(context.system)
  var status: String = "STARTING"
  var cache = Map.empty[Int, ActorRef]
 
  // subscribe to cluster changes, MemberUp
  // re-subscribe when restart
  override def preStart(): Unit =   {
  	log.info("Node Manager is starting: {}", self)
  	cluster.subscribe(self, classOf[MemberUp])
  }
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
  	case state: CurrentClusterState ⇒
      updateCache(state.members.filter(_.status == MemberStatus.Up) )
    case MemberUp(m) =>
    	log.info("Adding cluster member: {}", m)
    case MemberRemoved(m,_) =>
    	log.info("Removing cluster member: {}", m)
  	case _ =>
  		log.info("Node manager received a message...")
  	} 

  	def updateCache(members:scala.collection.immutable.SortedSet[Member]) = {
  		log.info("Upadatig cache: {}", members)
  	}
 
}