package com.pgt.dynaCache

import language.postfixOps
import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.RootActorPath
import akka.actor.Terminated
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.Member
import akka.cluster.MemberStatus
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import akka.io.IO
import spray.can.Http
import spray.can.server.Stats
import spray.util._
import spray.http._
import akka.event._
import HttpMethods._
import MediaTypes._

import com.pgt.utils.HashAlgorithm;

case class TransformationJob(text: String)
case class TransformationResult(text: String)
case class JobFailed(reason: String, job: TransformationJob)
case object BackendRegistration

object TransformationFrontend {
  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val config =
      (if (args.nonEmpty) ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${args(0)}")
      else ConfigFactory.empty).withFallback(
        ConfigFactory.parseString("akka.cluster.roles = [frontend]")).
        withFallback(ConfigFactory.load())

    implicit val system = ActorSystem("ClusterSystem", config)
    val frontend = system.actorOf(Props[TransformationFrontend], name = "frontend")
	val backend = system.actorOf(Props[TransformationBackend], name = "backend")
	
    import system.dispatcher
    implicit val timeout = Timeout(5 seconds)

	Cluster(system) registerOnMemberUp {
    //val resizer = DefaultResizer(lowerBound = 2, upperBound = 15)
    //val nodeManager = system.actorOf(Props[NodeManager].withRouter(RoundRobinRouter(resizer = Some(resizer))))
    val nodeManager = system.actorOf(Props[NodeManager], name = "node-manager")
    /*for (n ← 1 to 120) {
      (frontend ? TransformationJob("hello-" + n)) onSuccess {
        case result ⇒ println(result)
      }
      // wait a while until next request,
      // to avoid flooding the console with output
      Thread.sleep(1000)

    }
    system.shutdown()*/
    val actSystem = ActorSystem()
    IO(Http)(actSystem) ! Http.Bind(nodeManager, interface = "localhost", port = 8085)
	}

  }
}

class TransformationBackend extends Actor {
 
  val cluster = Cluster(context.system)
 
  // subscribe to cluster changes, MemberUp
  // re-subscribe when restart
  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)
 
  def receive = {
    case TransformationJob(text) ⇒ 
		sender ! TransformationResult(HashAlgorithm.FNV1_64_HASH.hash(text).toString()/*text.toUpperCase*/)
    case state: CurrentClusterState ⇒
      state.members.filter(_.status == MemberStatus.Up) foreach register
    case MemberUp(m) ⇒ register(m)
  }
 
  def register(member: Member): Unit =
    if (member.hasRole("frontend"))
      context.actorSelection(RootActorPath(member.address) / "user" / "frontend") !
        BackendRegistration
}

class TransformationFrontend extends Actor {
 
  var backends = IndexedSeq.empty[ActorRef]
  var jobCounter = 0
 
  def receive = {
    case job: TransformationJob if backends.isEmpty ⇒
      sender ! JobFailed("Service unavailable, try again later", job)
 
    case job: TransformationJob ⇒
      jobCounter += 1
      backends(jobCounter % backends.size) forward job
 
    case BackendRegistration if !backends.contains(sender) ⇒
      context watch sender
      backends = backends :+ sender
 
    case Terminated(a) ⇒
      backends = backends.filterNot(_ == a)
  }
}

