package eventstore

import akka.testkit.TestProbe
import ReadDirection.Forward
import CatchUpSubscription._

/**
 * @author Yaroslav Klymko
 */
class StreamCatchUpSubscriptionActorSpec extends AbstractCatchUpSubscriptionActorSpec {
  "catch up subscription actor" should {

    "read events from given position" in new StreamCatchUpScope(Some(123)) {
      connection expectMsg readStreamEvents(123)
    }

    "read events from start if no position given" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)
    }

    "ignore read events with event number out of interest" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)

      actor ! readStreamEventsSucceed(3, false, event0, event1, event2)
      expectMsg(event0)
      expectMsg(event1)
      expectMsg(event2)

      connection expectMsg readStreamEvents(3)

      actor ! readStreamEventsSucceed(5, false, event0, event1, event2, event3, event4)

      expectMsg(event3)
      expectMsg(event4)

      connection expectMsg readStreamEvents(5)

      actor ! readStreamEventsSucceed(5, false, event0, event1, event2, event3, event4)

      expectNoMsg(duration)
      connection expectMsg readStreamEvents(5)
    }

    "ignore read events with event number out of interest when from number is given" in new StreamCatchUpScope(Some(1)) {
      connection expectMsg readStreamEvents(1)

      actor ! readStreamEventsSucceed(3, false, event0, event1, event2)
      expectMsg(event2)
      expectNoMsg(duration)

      connection expectMsg readStreamEvents(3)
    }

    "read events until none left and subscribe to new ones" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsSucceed(2, false, event1)

      expectMsg(event1)

      connection expectMsg readStreamEvents(2)
      actor ! readStreamEventsSucceed(2, true)

      connection expectMsg subscribeTo
    }

    "subscribe to new events if nothing to read" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsSucceed(0, true)
      connection expectMsg subscribeTo

      actor ! subscribeToStreamCompleted(1)

      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsSucceed(0, true)

      expectMsg(LiveProcessingStarted)
    }

    "stop reading events if actor stopped" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)
      actor.stop()
      expectActorTerminated()
    }

    "catch events that appear in between reading and subscribing" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)

      val position = 1
      actor ! readStreamEventsSucceed(2, false, event0, event1)

      expectMsg(event0)
      expectMsg(event1)

      connection expectMsg readStreamEvents(2)
      actor ! readStreamEventsSucceed(2, true)

      expectNoMsg(duration)
      connection expectMsg subscribeTo

      actor ! subscribeToStreamCompleted(4)

      connection expectMsg readStreamEvents(2)

      actor ! streamEventAppeared(event2)
      actor ! streamEventAppeared(event3)
      actor ! streamEventAppeared(event4)
      expectNoMsg(duration)

      actor ! readStreamEventsSucceed(3, false, event1, event2)
      expectMsg(event2)

      connection expectMsg readStreamEvents(3)

      actor ! streamEventAppeared(event5)
      actor ! streamEventAppeared(event6)
      expectNoMsg(duration)

      actor ! readStreamEventsSucceed(6, false, event3, event4, event5)

      expectMsg(event3)
      expectMsg(event4)
      expectMsg(LiveProcessingStarted)
      expectMsg(event5)
      expectMsg(event6)

      actor ! streamEventAppeared(event5)
      actor ! streamEventAppeared(event6)

      expectNoActivity
    }

    "stop subscribing if stop received when subscription not yet confirmed" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsSucceed(0, true)
      connection expectMsg subscribeTo
      actor.stop()
      expectActorTerminated()
    }

    "not unsubscribe if subscription failed" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsSucceed(0, true)

      connection expectMsg subscribeTo
      actor ! SubscriptionDropped(SubscriptionDropped.AccessDenied)
      expectActorTerminated()
    }

    "not unsubscribe if subscription failed if stop received " in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsSucceed(0, true)
      connection expectMsg subscribeTo
      expectNoActivity
      actor ! SubscriptionDropped(SubscriptionDropped.AccessDenied)
      expectActorTerminated()
    }

    "stop catching events that appear in between reading and subscribing if stop received" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)

      val position = 1
      actor ! readStreamEventsSucceed(2, false, event0, event1)

      expectMsg(event0)
      expectMsg(event1)

      connection expectMsg readStreamEvents(2)

      actor ! readStreamEventsSucceed(2, true)

      expectNoMsg(duration)
      connection expectMsg subscribeTo

      actor ! subscribeToStreamCompleted(5)

      connection expectMsg readStreamEvents(2)

      actor ! streamEventAppeared(event3)
      actor ! streamEventAppeared(event4)

      actor.stop()
      connection.expectMsg(UnsubscribeFromStream)
      expectActorTerminated()
    }

    "continue with subscription if no events appear in between reading and subscribing" in new StreamCatchUpScope() {
      val position = 0
      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsSucceed(position, true)

      connection expectMsg subscribeTo
      expectNoMsg(duration)

      actor ! subscribeToStreamCompleted(1)

      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsSucceed(position, true)

      expectMsg(LiveProcessingStarted)

      expectNoActivity
    }

    "continue with subscription if no events appear in between reading and subscribing and position is given" in new StreamCatchUpScope(Some(1)) {
      val position = 1
      connection expectMsg readStreamEvents(position)

      actor ! readStreamEventsSucceed(position, true)

      connection expectMsg subscribeTo
      expectNoMsg(duration)

      actor ! subscribeToStreamCompleted(1)

      expectMsg(LiveProcessingStarted)

      expectNoActivity
    }

    "forward events while subscribed" in new StreamCatchUpScope() {
      val position = 0
      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsSucceed(position, true)

      connection expectMsg subscribeTo
      expectNoMsg(duration)

      actor ! subscribeToStreamCompleted(1)

      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsSucceed(position, true)

      expectMsg(LiveProcessingStarted)

      actor ! streamEventAppeared(event1)
      expectMsg(event1)

      expectNoMsg(duration)

      actor ! streamEventAppeared(event2)
      actor ! streamEventAppeared(event3)
      expectMsg(event2)
      expectMsg(event3)
    }

    "ignore wrong events while subscribed" in new StreamCatchUpScope(Some(1)) {
      val position = 1
      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsSucceed(position, true)

      connection expectMsg subscribeTo
      actor ! subscribeToStreamCompleted(2)

      connection expectMsg readStreamEvents(position)
      actor ! readStreamEventsSucceed(position, true)

      expectMsg(LiveProcessingStarted)

      actor ! streamEventAppeared(event0)
      actor ! streamEventAppeared(event1)
      actor ! streamEventAppeared(event1)
      actor ! streamEventAppeared(event2)
      expectMsg(event2)
      actor ! streamEventAppeared(event2)
      actor ! streamEventAppeared(event1)
      actor ! streamEventAppeared(event3)
      expectMsg(event3)
      actor ! streamEventAppeared(event5)
      expectMsg(event5)
      actor ! streamEventAppeared(event4)
      expectNoMsg(duration)
    }

    "stop subscription when actor stopped and subscribed" in new StreamCatchUpScope(Some(1)) {
      connection expectMsg readStreamEvents(1)

      actor ! readStreamEventsSucceed(1, true)

      connection expectMsg subscribeTo
      actor ! subscribeToStreamCompleted(1)
      expectMsg(LiveProcessingStarted)

      actor ! streamEventAppeared(event2)
      expectMsg(event2)

      actor.stop()
      connection.expectMsg(UnsubscribeFromStream)
      expectActorTerminated()
    }

    "stop actor if read error" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsFailed()
      expectActorTerminated()
    }

    "stop actor if subscription error" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsSucceed(0, true)

      connection expectMsg subscribeTo
      actor ! SubscriptionDropped(SubscriptionDropped.AccessDenied)

      expectActorTerminated()
    }

    "stop actor if catchup read error" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsSucceed(0, true)

      connection expectMsg subscribeTo
      actor ! subscribeToStreamCompleted(1)

      connection expectMsg readStreamEvents(0)
      actor ! readStreamEventsFailed(ReadStreamEventsFailed.NoStream)

      connection expectMsg UnsubscribeFromStream

      expectActorTerminated()
    }

    "stop actor if connection stopped" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      system stop connection.ref
      expectActorTerminated()
    }

    "stop actor if client stopped" in new StreamCatchUpScope() {
      connection expectMsg readStreamEvents(0)
      val probe = TestProbe()
      probe watch actor
      system stop testActor
      expectActorTerminated(probe)
    }

    "not stop subscription if actor stopped and not yet subscribed" in new StreamCatchUpScope {
      connection expectMsg readStreamEvents(0)
      actor.stop()
      expectActorTerminated()
    }
  }

  abstract class StreamCatchUpScope(eventNumber: Option[Int] = None) extends AbstractScope {
    lazy val streamId = EventStream.Id(getClass.getEnclosingClass.getSimpleName + "-" + newUuid.toString)

    def newActor = new StreamCatchUpSubscriptionActor(
      connection.ref,
      testActor,
      streamId,
      eventNumber.map(EventNumber.apply),
      resolveLinkTos,
      readBatchSize)

    val event0 = newEvent(0)
    val event1 = newEvent(1)
    val event2 = newEvent(2)
    val event3 = newEvent(3)
    val event4 = newEvent(4)
    val event5 = newEvent(5)
    val event6 = newEvent(6)

    def newEvent(number: Int): Event = EventRecord(streamId, EventNumber(number), mock[EventData])

    def readStreamEvents(x: Int) =
      ReadStreamEvents(streamId, EventNumber(x), readBatchSize, Forward, resolveLinkTos = resolveLinkTos)

    def readStreamEventsSucceed(next: Int, endOfStream: Boolean, events: Event*) =
      ReadStreamEventsSucceed(
        events = events,
        nextEventNumber = EventNumber(next),
        lastEventNumber = mock[EventNumber.Exact],
        endOfStream = endOfStream,
        lastCommitPosition = next /*TODO*/ ,
        direction = Forward)

    def readStreamEventsFailed(reason: ReadStreamEventsFailed.Value = ReadStreamEventsFailed.StreamDeleted) =
      ReadStreamEventsFailed(ReadStreamEventsFailed.StreamDeleted, None, Forward)

    def subscribeToStreamCompleted(x: Int) = SubscribeToStreamCompleted(x, Some(EventNumber(x)))
  }
}
