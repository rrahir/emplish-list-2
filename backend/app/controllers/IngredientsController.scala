package controllers

import cats.implicits._
import io.circe.generic.auto._
import javax.inject.Inject
import models.database.{Ingredients, Units}
import models.emplishlist.Ingredient
import models.errors.BackendError
import models.errors.BackendError._
import models.guards.{FullAuthGuardFactory, UserAction}
import monix.eval.Task
import play.api.Configuration
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.http.HttpErrorHandler
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import slick.jdbc.{JdbcBackend, JdbcProfile}
import utils.ReadsImplicits._
import utils.WriteableImplicits._
import utils.monix.SchedulerProvider
import models.database.dbProvider
import utils.playzio.PlayZIO._
import zio.{Has, ZLayer}
import zio.ZLayer.NoDeps

import scala.concurrent.ExecutionContext

final class IngredientsController @Inject()(
    assets: Assets,
    errorHandler: HttpErrorHandler,
    config: Configuration,
    authGuard: FullAuthGuardFactory,
    protected val dbConfigProvider: DatabaseConfigProvider,
    cc: ControllerComponents,
    val ec: ExecutionContext
) extends AbstractController(cc)
    with HasDatabaseConfigProvider[JdbcProfile]
    with SchedulerProvider
    with Ingredients
    with Units {

  val dbLayer: NoDeps[Nothing, Has[JdbcBackend#DatabaseDef]] = dbProvider(db)
  val unitsLayer: ZLayer[Any, Nothing, Has[Units.Service]] = dbLayer >>> Units.liveUnits

  def allIngredients: Action[AnyContent] = authGuard().async {
    ingredients.map(Ok(_)).runToFuture
  }

//  def allUnits: Action[AnyContent] = authGuard().async {
//    units.map(Ok(_)).runToFuture
//  }
  def allUnits: Action[AnyContent] = authGuard().zio(Units.units.orDie.map(Ok(_)).provideLayer(unitsLayer))

  def allStores: Action[AnyContent] = authGuard().async {
    stores.map(Ok(_)).runToFuture
  }

  def fetchIngredient(ingredientId: Int): Action[AnyContent] = authGuard().async {
    ingredientById(ingredientId).map(Ok(_)).runToFuture
  }

  def newIngredient: Action[Ingredient] = authGuard().async(parse.json[Ingredient]) {
    implicit request: UserAction.SessionRequest[Ingredient] =>
      val ingredient = request.body

      (for {
        ings <- Vector[Ingredient]().pure[Task] // existing ingredients are checked in `addIngredientIfNotExists`
        us <- units
        ss <- stores
        validator = Ingredient.validator(ings, us, ss)
        errors = validator(ingredient).pure[Option].filter(_.nonEmpty)
        finalErrors <- errors match {
          case None =>
            for {
              ingredientAdded <- addIngredientIfNotExists(ingredient)
              error = if (ingredientAdded) Map[String, List[BackendError]]()
              else Map("name" -> BackendError("validator.ingredientExists", ingredient.name).pure[List])
            } yield error
          case Some(es) => es.pure[Task]
        }
      } yield finalErrors)
        .map(errors => if (errors.isEmpty) Ok(ingredient) else BadRequest(errors))
        .runToFuture
  }

  def updateIngredientRoute(): Action[Ingredient] = authGuard().async(parse.json[Ingredient]) {
    implicit request: UserAction.SessionRequest[Ingredient] =>
      val ingredient = request.body

      (for {
        ings <- Vector[Ingredient]().pure[Task] // don't need to check that ingredient exist
        us <- units
        ss <- stores
        validator = Ingredient.validator(ings, us, ss)
        errors = validator(ingredient).pure[Option].filter(_.nonEmpty)
        finalErrors <- errors match {
          case Some(es) => es.pure[Task]
          case None =>
            for {
              ingredientUpdated <- updateIngredient(ingredient)
              error = if (ingredientUpdated) Map[String, List[BackendError]]()
              else Map("name" -> BackendError("validator.ingredientDoesNotExist", ingredient.name).pure[List])
            } yield error
        }
      } yield finalErrors)
        .map(errors => if (errors.isEmpty) Ok(ingredient) else BadRequest(errors))
        .runToFuture
  }

  def tags: Action[AnyContent] = authGuard().async {
    allIngredientsTag.map(Ok(_)).runToFuture
  }

}
