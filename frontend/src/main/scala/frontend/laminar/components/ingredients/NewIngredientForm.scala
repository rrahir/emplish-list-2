package frontend.laminar.components.ingredients

import akka.actor.ActorSystem
import com.raquo.laminar.api.L._
import com.raquo.laminar.nodes.ReactiveHtmlElement
import frontend.laminar.components.Component
import frontend.laminar.components.forms.{ListForm, SimpleForm, Tags}
import frontend.laminar.components.helpers.forms.InputString
import frontend.laminar.components.helpers.{InputSearch, InputTags}
import frontend.laminar.utils.ActorSystemContainer
import frontend.utils.http.DefaultHttp._
import io.circe.generic.auto._
import models.emplishlist.{Ingredient, IngredientUnit, Store}
import models.errors.BackendError
import models.validators.FieldsValidator
import org.scalajs.dom.html.Form
import sttp.client.Response
import syntax.WithUnit
import urldsl.language.QueryParameters.dummyErrorImpl.{param => qParam}

import scala.util.{Failure, Success}

final class NewIngredientForm(
    initialIngredient: Option[Ingredient],
    ingredients: Vector[Ingredient],
    units: Vector[IngredientUnit],
    stores: Vector[Store],
    lastIngredientAdded: Observer[(Ingredient, Boolean)]
)(
    implicit val actorSystem: ActorSystem,
    val formDataWithUnit: WithUnit[Ingredient]
) extends Component[Form]
    with SimpleForm[Ingredient] {
  val validator: FieldsValidator[Ingredient, BackendError] = Ingredient.validator(ingredients, units, stores)

  def submit(): Unit = {
    validator.validate(formData.now) match {
      case m if m.isEmpty =>
        println("no error, should send to server")
        boilerplate
          .post(
            initialIngredient match {
              case Some(ingredient) =>
                pathWithMultipleParams(
                  qParam[Int]("id").createParamsMap(ingredient.id),
                  "ingredients",
                  "update-ingredient"
                )
              case None => path("ingredients", "add-ingredient")
            }
          )
          .body(formData.now)
          .response(responseAs[Ingredient])
          .send()
          .onComplete {
            case Success(Response(Right(Right(ingredient)), _, _, _, _)) =>
              clearForm()
              lastIngredientAdded.onNext((ingredient, initialIngredient.isEmpty))
            case Success(Response(Left(Right(errors)), _, _, _, _)) =>
              println(errors)
              errorsWriter.onNext(errors)
            case Failure(exception) =>
              throw exception
            case _ =>
              throw new Exception("Failure during de-serialization")
          }
      case m =>
        println("there are errors")
        errorsWriter.onNext(m)
    }

  }

  val element: ReactiveHtmlElement[Form] = {

    val nameChanger = createFormDataChanger((name: String) => _.copy(name = name))
    val storesChanger = createFormDataChanger((stores: List[Store]) => _.copy(stores = stores))
    val unitChanger = createFormDataChanger((unit: IngredientUnit) => _.copy(unit = unit))
    val tagsChanger = createFormDataChanger((tags: List[String]) => _.copy(tags = tags))

    run()

    initialIngredient match {
      case Some(ingredient) => createFormDataChanger[Ingredient](ingredient => _ => ingredient).onNext(ingredient)
      case None             => // do nothing
    }

    form(
      onSubmit.preventDefault --> (_ => submit()),
      fieldSet(
        InputString("Ingredient name ", formData.signal.map(_.name), nameChanger)
      ),
      fieldSet(
        "Unit measure ",
        InputSearch[IngredientUnit](
          formData.signal.map(_.unit),
          units.toList,
          unit1 => unit2 => unit2.name.toLowerCase.contains(unit1.name.toLowerCase),
          name => IngredientUnit(name),
          _.name,
          unitChanger,
          Some(units.contains)
        )
      ),
      ListForm[Store](
        "Stores",
        formData.signal.map(_.stores.zipWithIndex),
        storesChanger.contramapWriter[List[(Store, Int)]](_.map(_._1)),
        StoreInput(_, _, _, stores.toList)
      ),
      fieldSet(
        Tags.tagsExplanation("ingredient"),
        br(),
        InputTags(formData.signal.map(_.tags), tagsChanger)
      ),
      input(tpe := "submit", value := "Submit", className <-- $errors.map(_.isEmpty).map(if (_) "valid" else "invalid"))
    )

  }
}

object NewIngredientForm {

  def apply(
      initialIngredient: Option[Ingredient],
      ingredients: Vector[Ingredient],
      units: Vector[IngredientUnit],
      stores: Vector[Store],
      lastIngredientAdded: Observer[(Ingredient, Boolean)]
  )(
      implicit actorSystemContainer: ActorSystemContainer,
      formDataWithUnit: WithUnit[Ingredient]
  ): NewIngredientForm = {
    import actorSystemContainer._
    new NewIngredientForm(initialIngredient, ingredients, units, stores, lastIngredientAdded)
  }

}
