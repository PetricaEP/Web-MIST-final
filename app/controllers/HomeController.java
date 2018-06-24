package controllers;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;
import javax.inject.Named;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import jsmessages.JsMessages;
import jsmessages.JsMessagesFactory;
import jsmessages.japi.Helper;
import play.Logger;
import play.data.Form;
import play.data.FormFactory;
import play.libs.Scala;
import play.libs.concurrent.HttpExecutionContext;
import play.mvc.BodyParser;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Results;
import play.routing.JavaScriptReverseRouter;
import services.search.DocumentSearcher;
import views.formdata.QueryData;
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

	private HttpExecutionContext httpExecutionContext;

	/**
	 * Form factory
	 */
	private final FormFactory formFactory;

	private JsMessages  jsMessages;

	@Inject
	public HomeController(@Named("docSearcher") DocumentSearcher docSearcher, FormFactory formFactory,
			HttpExecutionContext ec, JsMessagesFactory jsMessagesFactory) {
		this.docSearcher = docSearcher;
		this.formFactory = formFactory;		
		this.httpExecutionContext = ec;		
		jsMessages = jsMessagesFactory.filtering( key -> key.startsWith("js."));
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

	public Result jsMessages() {				
		return ok(jsMessages.apply(Scala.Option("window.Messages"), Helper.messagesFromCurrentHttpContext()));
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
			return docSearcher.search(queryData.get()).thenApplyAsync((json) -> {				
				return ok(json);
			}, httpExecutionContext.current());
		}catch (Exception e) {
			return CompletableFuture.completedFuture(
					Results.ok(""));
		}
	}

	public CompletionStage<Result> zoom(){		
		Form<QueryData> queryData = formFactory.form(QueryData.class).bindFromRequest();
		if ( queryData.hasErrors() ){
			return CompletableFuture.completedFuture(
					Results.ok(""));
		}

		try {
			return docSearcher.search(queryData.get()).thenApplyAsync((json) -> {				
				return ok(json);
			}, httpExecutionContext.current());
		}catch (Exception e) {
			return CompletableFuture.completedFuture(
					Results.ok(""));
		}
	}

	@BodyParser.Of(BodyParser.Json.class)
	public CompletionStage<Result> references(){
		JsonNode json = request().body().asJson();
		if ( json.isArray() ) {
			ObjectMapper mapper = new ObjectMapper();
			ObjectReader reader = mapper.reader().forType(new TypeReference<List<Long>>(){});
			List<Long> docIds;
			try {
				docIds = reader.readValue(json);
			} catch (IOException e1) {
				return CompletableFuture.completedFuture(
						Results.ok(""));
			}			
			if ( docIds.size() > 0){
				try {
					CompletionStage<String> jsonResult = CompletableFuture.supplyAsync(
							() -> docSearcher.getDocumentsReferences(docIds));
					return jsonResult.thenApplyAsync((j) -> {
						return ok(j).as("application/json");
					}, httpExecutionContext.current());
				} catch (Exception e) {
					Logger.error("Can't search for documents. Query: " + docIds.toString(), e);
				}

				return CompletableFuture.completedFuture(
						Results.ok(""));
			}
		}

		return CompletableFuture.completedFuture(
				Results.ok(""));

	}

	public CompletionStage<Result> download(List<Long> docIds){
		CompletionStage<File> filePromise = CompletableFuture.supplyAsync(() -> docSearcher.downloadDocuments(docIds));
		return filePromise.thenApplyAsync(
				file -> { 
					return ok(file, false).as("application/x-download");
				}, httpExecutionContext.current());
	}

	/**
	 * Create javascript routes for this controller
	 * @return javascrit route
	 */
	public Result javascriptRoutes(){
		return ok(
				JavaScriptReverseRouter.create("jsRoutes",
						routes.javascript.HomeController.search(),
						routes.javascript.HomeController.zoom(),
						routes.javascript.HomeController.references(),
						routes.javascript.HomeController.download()
						//						routes.javascript.GraphController.getGraph()
						)).as("text/javascript");
	}
}
