# quartz4s
Quarts scheduler library using cats-effect queues for handling results.

### Import
```scala
libraryDependencies ++= Seq(
  "com.itv" %% "quartz4s-core"     % "1.0.0-SNAPSHOT",
)
```

The project uses a quartz scheduler, and as scheduled messages are generated from Quartz they are
decoded and put onto an `cats.effect.std.Queue`.

#### Components for scheduling jobs:
* a `QuartzTaskScheduler[F[_], A]` which schedules jobs of type `A`
* a `JobDataEncoder[A]` which encodes job data in a map for the given job of type `A`

#### Components for responding to scheduled messages:
* a job factory which is triggered by quartz when a scheduled task occurs and creates messages to put on the queue
* a `JobDecoder[A]` which decodes the incoming message data map into an `A`
* the decoded message is put onto the provided `cats.effect.std.Queue`


## Usage:

## Create some job types
We need to have a set of types to encode and decode.
We provide the ability to encode/decode an object as a `Map[String, String]`, which works perfectly for 
putting data into the quartz `JobDataMap`. (Heavily inspired by [extruder](https://janstenpickle.github.io/extruder/)).
```scala
import com.itv.scheduler.{JobDataEncoder, JobDecoder}
import com.itv.scheduler.extruder.semiauto._

sealed trait ParentJob
case object ChildObjectJob     extends ParentJob
case class UserJob(id: String) extends ParentJob

object ParentJob {
  implicit val jobDataEncoder: JobDataEncoder[ParentJob] = deriveJobEncoder[ParentJob]
  implicit val jobDecoder: JobDecoder[ParentJob]         = deriveJobDecoder[ParentJob]
  //or, simply: implicit val jobCodec: JobCodec[ParentJob] = deriveJobCodec[ParentJob]
}
```

### Create a JobFactory
There are 2 options when creating a `CallbackJobFactory`: auto-acked and manually acked messages.

#### Auto-Acked messages
Scheduled jobs from quartz are immediately acked and the resulting message of type `A` is placed on a `Queue[F, A]`.
If the message taken from the queue isn't handled cleanly then the resulting quartz job won't be re-run,
as it has already been marked as successful. 
```scala
import cats.effect._
import com.itv.scheduler._
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global

val jobMessageQueue = Queue.unbounded[IO, ParentJob].unsafeRunSync()
// jobMessageQueue: Queue[IO, ParentJob] = cats.effect.std.Queue$BoundedQueue@1ff336aa
val autoAckJobFactory = MessageQueueJobFactory.autoAcking[IO, ParentJob](jobMessageQueue)
// autoAckJobFactory: Resource[IO, AutoAckingQueueJobFactory[IO, ParentJob]] = Bind(
//   source = Bind(
//     source = Bind(
//       source = Allocate(
//         resource = cats.effect.kernel.Resource$$$Lambda$12737/0x0000000803267318@e3e44bb
//       ),
//       fs = cats.effect.kernel.Resource$$Lambda$12739/0x00000008032653d0@36119bf6
//     ),
//     fs = cats.effect.std.Dispatcher$$$Lambda$12740/0x00000008032657a0@739003a4
//   ),
//   fs = cats.effect.kernel.Resource$$Lambda$12739/0x00000008032653d0@16b343a5
// )
```

#### Manually Acked messages
Scheduled jobs are received but only acked with quartz once the handler has completed via an `acker: MessageAcker[F, A]`.

Scheduled jobs from quartz are bundled into a `message: A` and an `acker: MessageAcker[F, A]`.
The items in the queue are each a `Resource[F, A]` which uses the message and acks the message as the `Resource` is `use`d.

Alternatively the lower-level way of handling each message is via a queue of
`AckableMessage[F, A](message: A, acker: MessageAcker[F, A])` items where the message is explicitly acked by the user.

In both cases, the quartz job is only marked as complete once the `acker.complete(result: Either[Throwable, Unit])` is called.
```scala
// each message is wrapped as a `Resource` which acks on completion
val ackableJobResourceMessageQueue = Queue.unbounded[IO, Resource[IO, ParentJob]].unsafeRunSync()
// ackableJobResourceMessageQueue: Queue[IO, Resource[IO, ParentJob]] = cats.effect.std.Queue$BoundedQueue@54e7ca34
val ackingResourceJobFactory: Resource[IO, AckingQueueJobFactory[IO, Resource, ParentJob]] =
  MessageQueueJobFactory.ackingResource(ackableJobResourceMessageQueue)
// ackingResourceJobFactory: Resource[IO, AckingQueueJobFactory[IO, Resource, ParentJob]] = Bind(
//   source = Bind(
//     source = Bind(
//       source = Allocate(
//         resource = cats.effect.kernel.Resource$$$Lambda$12737/0x0000000803267318@459f2a55
//       ),
//       fs = cats.effect.kernel.Resource$$Lambda$12739/0x00000008032653d0@2f43eeb8
//     ),
//     fs = cats.effect.std.Dispatcher$$$Lambda$12740/0x00000008032657a0@5f5da003
//   ),
//   fs = cats.effect.kernel.Resource$$Lambda$12739/0x00000008032653d0@34fc6524
// )

// each message is wrapped as a `AckableMessage` which acks on completion
val ackableJobMessageQueue = Queue.unbounded[IO, AckableMessage[IO, ParentJob]].unsafeRunSync()
// ackableJobMessageQueue: Queue[IO, AckableMessage[IO, ParentJob]] = cats.effect.std.Queue$BoundedQueue@ae4c751
val ackingJobFactory: Resource[IO, AckingQueueJobFactory[IO, AckableMessage, ParentJob]] =
  MessageQueueJobFactory.acking(ackableJobMessageQueue)
// ackingJobFactory: Resource[IO, AckingQueueJobFactory[IO, AckableMessage, ParentJob]] = Bind(
//   source = Bind(
//     source = Bind(
//       source = Allocate(
//         resource = cats.effect.kernel.Resource$$$Lambda$12737/0x0000000803267318@5d2ca17b
//       ),
//       fs = cats.effect.kernel.Resource$$Lambda$12739/0x00000008032653d0@715e2bc0
//     ),
//     fs = cats.effect.std.Dispatcher$$$Lambda$12740/0x00000008032657a0@1ee4d6a1
//   ),
//   fs = cats.effect.kernel.Resource$$Lambda$12739/0x00000008032653d0@6905ac62
// )
```

### Creating a scheduler
```scala
val quartzProperties = QuartzProperties(new java.util.Properties())
// quartzProperties: QuartzProperties = QuartzProperties(properties = {})
val schedulerResource: Resource[IO, QuartzTaskScheduler[IO, ParentJob]] =
  autoAckJobFactory.flatMap { jobFactory => 
    QuartzTaskScheduler[IO, ParentJob](quartzProperties, jobFactory)
  }
// schedulerResource: Resource[IO, QuartzTaskScheduler[IO, ParentJob]] = Bind(
//   source = Bind(
//     source = Bind(
//       source = Bind(
//         source = Allocate(
//           resource = cats.effect.kernel.Resource$$$Lambda$12737/0x0000000803267318@e3e44bb
//         ),
//         fs = cats.effect.kernel.Resource$$Lambda$12739/0x00000008032653d0@36119bf6
//       ),
//       fs = cats.effect.std.Dispatcher$$$Lambda$12740/0x00000008032657a0@739003a4
//     ),
//     fs = cats.effect.kernel.Resource$$Lambda$12739/0x00000008032653d0@16b343a5
//   ),
//   fs = <function1>
// )
```

### Using the scheduler
```scala
import java.time.Instant
import org.quartz.{CronExpression, JobKey, TriggerKey}

def scheduleCronJob(scheduler: QuartzTaskScheduler[IO, ParentJob]): IO[Option[Instant]] =
  scheduler.scheduleJob(
    JobKey.jobKey("child-object-job"),
    ChildObjectJob,
    TriggerKey.triggerKey("cron-test-trigger"),
    CronScheduledJob(new CronExpression("* * * ? * *"))
  )

def scheduleSingleJob(scheduler: QuartzTaskScheduler[IO, ParentJob]): IO[Option[Instant]] =
  scheduler.scheduleJob(
    JobKey.jobKey("single-user-job"),
    UserJob("user-123"),
    TriggerKey.triggerKey("scheduled-single-test-trigger"),
    JobScheduledAt(Instant.now.plusSeconds(2))
  )
```
