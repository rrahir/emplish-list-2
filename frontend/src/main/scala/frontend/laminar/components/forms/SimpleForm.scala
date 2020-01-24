package frontend.laminar.components.forms

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{RunnableGraph, Sink, Source}
import com.raquo.airstream.eventbus.{EventBus, WriteBus}
import com.raquo.airstream.eventstream.EventStream
import models.errors.BackendError
import models.validators.FieldsValidator
import streams.sinks.WriteToBus._
import streams.sources.ReadFromEventStream._
import syntax.WithUnit
import com.raquo.laminar.api.L._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait SimpleForm[FormData] {

  private implicit val owner: Owner = new Owner {}

  type FormDataChanger = FormData => FormData

  implicit val formDataWithUnit: WithUnit[FormData]
  implicit val actorSystem: ActorSystem
  protected implicit def ec: ExecutionContext = actorSystem.dispatcher

  val formData: Var[FormData] = Var(formDataWithUnit.unit)
  private val formDataBus = new EventBus[FormData]()
  formDataBus.events.foreach(data => formData.update(_ => data))
  private val errors = new EventBus[Map[String, List[BackendError]]]()
  val $errors: EventStream[Map[String, List[BackendError]]] = errors.events // expose to kids
  val errorsWriter: WriteBus[Map[String, List[BackendError]]] = errors.writer

  private val formDataChanger: EventBus[FormDataChanger] = new EventBus[FormDataChanger]()
  private val formDataChangerWriter: WriteBus[FormDataChanger] = formDataChanger.writer

  /**
    * Allows to concretely make changes to the formData.
    */
  def createFormDataChanger[T](f: T => FormDataChanger): WriteBus[T] =
    formDataChangerWriter.contramapWriter(f)

  private val formDataEventWriter: WriteBus[FormData] = formDataBus.writer
  private val errorsEventWriter: WriteBus[Map[String, List[BackendError]]] = errors.writer

  val validator: FieldsValidator[FormData, BackendError]

  private val formDataSink = Sink.writeToBus(formDataEventWriter)
  private val errorsSink = Sink.foreach(errorsEventWriter.onNext)

  private val formSource: RunnableGraph[NotUsed] = Source
    .readFromEventStream(formDataChanger.events)
    .scan(formDataWithUnit.unit) { case (form, changer) => changer(form) }
    .wireTap(formData => println("The form data passes: " + formData)) // todo: remove that
    .alsoTo(formDataSink)
    .groupedWithin(10, 200.milliseconds)
    .map(_.last)
    .map(validator.validate)
    .to(errorsSink)

  def run(): Unit = formSource.run()

}
