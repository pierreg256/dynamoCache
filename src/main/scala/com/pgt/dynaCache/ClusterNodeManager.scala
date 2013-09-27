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
import akka.routing.DefaultResizer
import akka.routing.RoundRobinRouter
import spray.can.Http
import spray.can.server.Stats
import spray.util._
import spray.http._
import akka.event._
import HttpMethods._
import MediaTypes._

import unfiltered.request.Seg

import scala.concurrent._
import ExecutionContext.Implicits.global
//import unfiltered.response._

import com.pgt.utils.HashAlgorithm;

class NodeManager extends Actor with SprayActorLogging {
 
  val cluster = Cluster(context.system)
  var status = "STARTING"
  val resizer = DefaultResizer(lowerBound = 2, upperBound = 255)
  val system = ActorSystem("ClusterSystem")
  var cache = system.actorOf(Props[CacheActor].withRouter(RoundRobinRouter(resizer = Some(resizer))))

  // subscribe to cluster changes, MemberUp
  // re-subscribe when restart
  override def preStart(): Unit =   {
  	log.info("Node Manager is starting: {}", self)
  	cluster.subscribe(self, classOf[MemberUp])

  }
  override def postStop(): Unit = cluster.unsubscribe(self)

  def index(s: Int) = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
        <body>
          <h1>Akka Cluster!</h1>
          <p>{s} workers available</p>
        </body>
      </html>.toString()
    )
  )
  
  def index(s: String) = HttpResponse(
    entity = HttpEntity(`application/json`,
      <html>
        <body>
          <h1>Akka Cluster!</h1>
          <p>{s} workers available</p>
        </body>
      </html>.toString()
    )
  )

  def receive = {
  	case state: CurrentClusterState ⇒
      updateCache(state.members.filter(_.status == MemberStatus.Up) )
    case MemberUp(m) =>
    	log.info("Adding cluster member: {}", m)
    case MemberRemoved(m,_) =>
    	log.info("Removing cluster member: {}", m)

    case _: Http.Connected => sender ! Http.Register(self)

    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      sender ! index(12)

    case HttpRequest(GET, Uri.Path(Seg("keys" :: keyName )) , _, _, _) =>
      val cacheResponse : Future[String] = future { cache ! new com.pgt.dynaCache.Get(keyName.mkString) }
      val originalSender = sender
      cacheResponse onSuccess {
        case text =>
          originalSender ! text
      }      

  	case _ =>
  		log.info("Node manager received a message...")
  	} 

  	def updateCache(members:scala.collection.immutable.SortedSet[Member]) = {
  		log.info("Upadatig cache: {}", members)
  	}
 
}