package controllers;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Named;

import com.fasterxml.jackson.databind.JsonNode;

import play.Logger;
import play.data.Form;
import play.data.FormFactory;
import play.libs.concurrent.HttpExecution;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;
import play.routing.JavaScriptReverseRouter;
import services.database.DatabaseExecutionContext;
import services.search.DocumentSearcher;
import views.formdata.QueryData;
import views.formdata.SelectionData;
import views.html.Index;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
public class HomeController extends Controller {

	/**
	 * IndexSearcher for documents in database
	 */
	private final DocumentSearcher docSearcher;

	private DatabaseExecutionContext databaseContext;

	/**
	 * Form factory
	 */
	private final FormFactory formFactory;

	@Inject
	public HomeController(@Named("docSearcher") DocumentSearcher docSearcher, FormFactory formFactory,
			DatabaseExecutionContext context) {
		this.docSearcher = docSearcher;
		this.formFactory = formFactory;
		this.databaseContext = context;
	}

	/**
	 * An action that renders an HTML page with a welcome message.
	 * The configuration in the <code>routes</code> file means that
	 * this method will be called when the application receives a
	 * <code>GET</code> request with a path of <code>/</code>.
	 */
	public Result index() {
		Form<QueryData> queryData = formFactory.form(QueryData.class);	
		return ok(Index.render(queryData));
	}

	/**
	 * Searches for user request term. 
	 * @param term the search term
	 * @return Play result as Json
	 */
	public CompletionStage<Result> search(){

		Form<QueryData> queryData = formFactory.form(QueryData.class).bindFromRequest();
		if ( queryData.hasErrors() ){
			return CompletableFuture.completedFuture(
					Results.ok(""));
		}

		try {
			return docSearcher.search(queryData.get(), -1).thenApplyAsync((json) -> {
				return ok(json);
			}, databaseContext);
		}catch (Exception e) {
			return CompletableFuture.completedFuture(
					Results.ok(""));
		}
	}

	public CompletionStage<Result> references(List<Long> docIds){
		if ( docIds.size() > 0){
			try {
				CompletionStage<String> jsonResult = CompletableFuture.supplyAsync(
						() -> docSearcher.getDocumentsReferences(docIds));
				return jsonResult.thenApplyAsync((j) -> {
					return ok(j).as("application/json");
				}, databaseContext);
			} catch (Exception e) {
				Logger.error("Can't search for documents. Query: " + docIds.toString(), e);
			}

			return CompletableFuture.completedFuture(
					Results.ok(""));
		}

		return CompletableFuture.completedFuture(
				Results.ok(""));

	}

	public CompletionStage<Result> download(List<Long> docIds){
		CompletionStage<File> filePromise = CompletableFuture.supplyAsync(() -> docSearcher.downloadDocuments(docIds));
		return filePromise.thenApplyAsync(
				(file) -> ok(file, false).as("application/x-download"));
	}

	/**
	 * Create javascript routes for this controller
	 * @return javascrit route
	 */
	public Result javascriptRoutes(){
		return ok(
				JavaScriptReverseRouter.create("jsRoutes",
						routes.javascript.HomeController.search(),
						routes.javascript.HomeController.references(),
						routes.javascript.HomeController.download()
//						routes.javascript.GraphController.getGraph()
						)).as("text/javascript");
	}
}
