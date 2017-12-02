package services.search;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import javax.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cern.colt.matrix.tfloat.FloatMatrix2D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import cern.colt.matrix.tint.IntMatrix1D;
import edu.uci.ics.jung.graph.Graph;
import ep.db.database.DatabaseService;
import ep.db.grid.grid2.EpanechnikovKernel;
import ep.db.grid.grid2.Grid;
import ep.db.grid.grid2.Kernel;
import ep.db.model.Author;
import ep.db.model.Document;
import ep.db.model.IDocument;
import ep.db.pagerank.Edge;
import ep.db.quadtree.Bounds;
import ep.db.quadtree.QuadTree;
import ep.db.quadtree.QuadTreeNode;
import ep.db.quadtree.Vec2;
import ep.db.utils.Configuration;
import ep.db.utils.Utils;
import play.Logger;
import play.db.Database;
import services.clustering.KMeans;
import services.database.DatabaseExecutionContext;
import services.database.PlayDatabaseWrapper;
import views.formdata.QueryData;
import views.formdata.SelectionData;

public class LocalDocumentSearcher implements DocumentSearcher {

	private final Logger.ALogger timeLogger = Logger.of("timing");

	private static final Pattern TERM_PATTERN = Pattern.compile(".*(\".*?\").*");

	private static final String TEMP_DIRECTORY = System.getProperty("java.io.tmpdir");

	private static final int GRID_SIZE_Y = 96;

	private static final int GRID_SIZE_X = 256;

	private static Configuration configuration;

	private QuadTree quadTree;
	
//	private Grid grid;
	
//	private Kernel kernel;

	private DatabaseService dbService;
	
	private DatabaseExecutionContext executionContext;


	@Inject
	public LocalDocumentSearcher(Database db, DatabaseExecutionContext context) {
		this.dbService = new DatabaseService(new PlayDatabaseWrapper(db));
		this.executionContext = context;
		configuration = Configuration.getInstance();
		initQuadTree();
		initGrid();
	}

	@Override
	public CompletionStage<String> search(QueryData queryData) throws Exception {
		return search(queryData, false, 0);
	}

	@Override
	public CompletionStage<String>  search(QueryData queryData, int count) throws Exception {
		return search(queryData, false, count);
	}

	@Override
	public CompletionStage<String>  search(QueryData queryData, boolean fetchNumberOfCitations) throws Exception {
		return search(queryData, fetchNumberOfCitations, 0);
	}

	@Override
	public CompletionStage<String> search(QueryData queryData, boolean fetchNumberOfCitations, int count) throws Exception {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return searchData(queryData, fetchNumberOfCitations, count);
			} catch (Exception e) {
				Logger.error("Can't search for data", e);
				return null;
			}
		}, executionContext);
	}
	
	public String searchData(QueryData queryData, boolean fetchNumberOfCitations, int count) throws Exception {
		
		long start, elapsed;
		Map<String, Object> result = new HashMap<>();

		try {
			start = System.nanoTime();
			String query = buildQuery(queryData);
			elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			timeLogger.info(String.format("Query building: %d", elapsed));

			List<Document> docs = new ArrayList<>();
			List<Vec2> points = new ArrayList<>();
			float[] densities = null;
			final int maxDocs = queryData.getMaxDocs();

			start = System.nanoTime();
			if ( ! queryData.getAuthor().isEmpty() || ! queryData.getYearStart().isEmpty() || ! queryData.getYearEnd().isEmpty() ){
				String op = getOperator(queryData.getOperator());
				String authors = queryData.getAuthor().replaceAll("\\s+", op);
				dbService.getAdvancedSimpleDocuments(query, authors, queryData.getYearStart(), 
						queryData.getYearEnd(), count, docs, points, maxDocs);
			}
			else if ( query != null )
				dbService.getSimpleDocuments(query, count, docs, points, maxDocs);
			else{
				dbService.getAllSimpleDocuments(docs, points, maxDocs);
			}
			elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			timeLogger.info(String.format("Quering DB: %d", elapsed));

			if( docs.size() > 0){

				//Clustering
				final int numClusters = queryData.getNumClusters();
				start = System.nanoTime();
				IntMatrix1D clusters = clustering(docs, numClusters);
				elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
				timeLogger.info(String.format("Clustering: %d", elapsed));

				//Atribui id cluster
				start = System.nanoTime();
				IntStream.range(0, docs.size()).parallel().forEach( (i) -> {
					docs.get(i).setCluster(clusters.get(i));
				});
				elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
				timeLogger.info(String.format("Set cluster numbers: %d", elapsed));

				if ( points != null && !points.isEmpty()){
					int densityMap = configuration.getDensityMapCalculation();
					if ( densityMap == Configuration.DENSITY_MAP_SERVER ){
						
						start = System.nanoTime();
						// Calcula coordenadas max/min dos documentos
						// a serem exibidos para calcular camada do grid
						float minX = docs.get(0).getX(), maxX = docs.get(0).getX(), 
								minY = docs.get(0).getY(), maxY = docs.get(0).getY();
						for(int i = 1; i < points.size(); i++){
							Vec2 p = points.get(i);
							if ( p.x > maxX) maxX = p.x;
							if ( p.x  < minX) minX = p.x;
							if ( p.y > maxY) maxY = p.y;
							if ( p.y < minY) minY = p.y;
						}
						
						EpanechnikovKernel k = new EpanechnikovKernel(1.0f);
						Grid grid = new Grid(GRID_SIZE_X, GRID_SIZE_Y, new Bounds(minX,minY,maxX,maxY));
						grid.evaluate(points, k);
						int gridSize[] = new int[]{GRID_SIZE_X,GRID_SIZE_Y};
						densities = grid.getData();
						elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
						timeLogger.info(String.format("Creating Grid (KDE 2D): %d", elapsed));

						result.put("densities", densities);
						result.put("bounds", new float[]{minX,minY,maxX,maxY});
						result.put("gridSize", gridSize);
						result.put("densityMap", Configuration.DENSITY_MAP_SERVER);
					}
					else {
						result.put("densities", points);
						result.put("densityMap", Configuration.DENSITY_MAP_CLIENT);
					}
				}

				result.put("documents", docs);
				result.put("nclusters", numClusters);
				result.put("op", "search");
				result.put("minRadiusPerc", configuration.getMinRadiusSizePercent());
				result.put("maxRadiusPerc", configuration.getMaxRadiusSizePercent());
			}
			else{
				result.put("documents", new ArrayList<Document>(0));
				result.put("densities", null);
				result.put("nclusters", 0);
				result.put("op", "search");
			}
		}catch (Exception e) {
			Logger.error("Unkown error!", e);
			result.put("documents", new ArrayList<Document>(0));
			result.put("densities", null);
			result.put("nclusters", 0);
			result.put("op", "search");
		}

		start = System.nanoTime();
		ObjectMapper mapper = new ObjectMapper();
		try {
			String json = mapper.writeValueAsString(result);
			elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			timeLogger.info(String.format("Converting to JSON: %d", elapsed));
			return json;
		} catch (JsonProcessingException e) {
			Logger.error("JSON processing error: " + result.toString(), e);
			return "";
		}
	}

	@Override
	public String zoom(SelectionData selectionData) {

		long start, elapsed;
		Map<String, Object> result = new HashMap<>();

		try {
			float x1, y1, x2, y2;
			final int maxDocs = selectionData.getMaxDocs();

			x1 = selectionData.getStart()[0];
			y1 = selectionData.getStart()[1];
			x2 = selectionData.getEnd()[0];
			y2 = selectionData.getEnd()[1];

			float aux;
			if ( x1 > x2){
				aux = x1;
				x1 = x2;
				x2 = aux;
			}
			if ( y1 > y2 ){
				aux = y1;
				y1 = y2;
				y2 = aux;
			}

			Bounds rectangle = new Bounds(x1, y1, x2, y2);

			List<IDocument> documents = new ArrayList<>();
			List<QuadTreeNode> nodes = new ArrayList<>();

			start = System.nanoTime();
			quadTree.findInRectangle(rectangle, documents, nodes, maxDocs);
			elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			timeLogger.info(String.format("Find in quadtree: %d", elapsed));

			// Ordena por relevancia (descrescente)
			start = System.nanoTime();
			documents.sort(Comparator.comparing((IDocument d) -> d.getRank()).reversed());
			elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			timeLogger.info(String.format("Sorting results: %d", elapsed));
			
			float minRank = documents.get(documents.size()-1).getRank();
			
			start = System.nanoTime();
			List<Vec2> points = loadXY(minRank, x1, y1, x2, y2);
			
			elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			timeLogger.info(String.format("Loading x,y points: %d", elapsed));
			float[] densities = null;

			if ( documents.size() > 0){

				if ( points != null ){
					int densityMap = configuration.getDensityMapCalculation();
					if ( densityMap == Configuration.DENSITY_MAP_SERVER ){
						
						start = System.nanoTime();
						Kernel k = new EpanechnikovKernel(1.0f);
						Grid grid = new Grid(GRID_SIZE_X, GRID_SIZE_Y, rectangle);
						grid.evaluate(points, k);
						int gridSize[] = new int[]{GRID_SIZE_X,GRID_SIZE_Y};
						densities = grid.getData();
						elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
						timeLogger.info(String.format("Creating Grid (KDE 2D): %d", elapsed));
						
						result.put("densities", densities);
						result.put("gridSize", gridSize);
						result.put("bounds", new float[]{x1,y1,x2,y2});
						result.put("densityMap", Configuration.DENSITY_MAP_SERVER);
					}
					else {
						result.put("densities", points);
						result.put("densityMap", Configuration.DENSITY_MAP_CLIENT);
					}
				}

				start = System.nanoTime();
				final int numClusters = selectionData.getNumClusters();
				IntMatrix1D clusters = clustering(documents, numClusters);
				elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
				timeLogger.info(String.format("Clustering results: %d", elapsed));

				//Atribui id cluster
				start = System.nanoTime();
				final List<IDocument> docs = documents;
				IntStream.range(0, docs.size()).parallel().forEach( (i) -> {
					docs.get(i).setCluster(clusters.get(i));
				});
				elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
				timeLogger.info(String.format("Set cluster numbers: %d", elapsed));

				
				// Somente documentos selecionados para exibição,
				// demais documentos irão compor o mapa de densidade.
				result.put("documents", documents);
				result.put("nclusters", numClusters);
				result.put("op", "zoom");
				result.put("bounds", new float[]{x1,y1,x2,y2});
				result.put("minRadiusPerc", configuration.getMinRadiusSizePercent());
				result.put("maxRadiusPerc", configuration.getMaxRadiusSizePercent());
			}
			else{
				result.put("documents", new ArrayList<Document>(0));
				result.put("nclusters", 0);
				result.put("op", "zoom");
			}

		} catch( Exception e){
			Logger.error("Unkown error!", e);
			result.put("documents", new ArrayList<Document>(0));
			result.put("nclusters", 0);
			result.put("op", "zoom");
		}

		ObjectMapper mapper = new ObjectMapper();
		try {
			start = System.nanoTime();
			String json = mapper.writeValueAsString(result);
			elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
			timeLogger.info(String.format("Converting to JSON: %d", elapsed));
			return json;
		} catch (JsonProcessingException e) {
			Logger.error("JSON processing error: " + result.toString(), e);
			return "";
		}
	}

	private List<Vec2> loadXY(float maxRank, float x1, float y1, float x2, float y2) {
		try {
			return dbService.loadXY(x1, y1, x2, y2);
		} catch (Exception e) {
			Logger.error("Can't load <x,y> coordinates for documents", e);
			return null;
		}
	}

	public String getDocumentsReferences(List<Long> docIds){
		// References
		final Map<Long, List<Long>> references;
		Map<Long, List<Long>> ref;
		try {
			ref = dbService.getReferences(docIds);
		} catch (Exception e) {
			Logger.error("Can't get references for documents", e);
			ref = new HashMap<>(0);
		}
		references = ref;


		Map<String, Object> result = new HashMap<>();
		result.put("references", references);
		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.writeValueAsString(result);
		} catch (JsonProcessingException e) {
			Logger.error("JSON processing error: " + result.toString(), e);
			return "";
		}
	}

	public File downloadDocuments(List<Long> docIds){

		List<Document> docs;
		try {
			docs = dbService.getSimpleDocuments(docIds.toArray(new Long[docIds.size()]));
		} catch (Exception e) {
			Logger.error("Can't get selected documents: " + docIds.toString(),e);
			return null;
		}

		File outputFile;
		try {
			outputFile = exportToFile(docs);
		} catch (Exception e) {
			Logger.error("Can't export documents to file: " + docIds.toString(),e);
			return null;
		}
		return outputFile;
	}

	private File exportToFile(List<Document> docs) throws Exception {
		File file = new File(TEMP_DIRECTORY +  File.separator + "download_" + System.nanoTime() + ".csv");
		try( BufferedWriter bw = new BufferedWriter(new FileWriter(file))){
			bw.write("DOI,Title,Authors,Year,BibTEX");
			bw.newLine();
			for(Document doc : docs){
				String line = String.format("%s,%s,%s,%s,%s", 
						doc.getDOI(), 
						doc.getTitle(),
						Utils.authorsToString(doc.getAuthors()),
						doc.getPublicationDate(),
						doc.getBibTEX()
						);
				bw.write(line);
				bw.newLine();
			}
		}catch (Exception e) {
			throw e;
		}
		return file;
	}

	private IntMatrix1D clustering(List<? extends IDocument> documents, int numClusters) {
		// Matrix para K-means
		FloatMatrix2D matrix = new DenseFloatMatrix2D(documents.size(),2);
		for(int i = 0; i < documents.size(); i++){
			matrix.setQuick(i, 0, documents.get(i).getX());
			matrix.setQuick(i, 1, documents.get(i).getY());
		}

		KMeans kmeans = new KMeans();
		kmeans.cluster(matrix, numClusters);
		IntMatrix1D clusters = kmeans.getClusterAssignments();

		return clusters;
	}

	private void initQuadTree() {
		Vec2 p1 = new Vec2(-1,-1);
		Vec2 p2 = new Vec2(2,2);
		Bounds bounds = new Bounds(p1,p2);
		quadTree = new QuadTree(bounds, configuration.getQuadTreeMaxDepth(), 
				configuration.getQuadTreeMaxElementsPerBunch(), 
				configuration.getQuadTreeMaxElementsPerLeaf(), dbService);
		try {
			quadTree.loadQuadTree();
		} catch (Exception e) {
			Logger.error("Can't load QuadTree from Database", e);
			return;
		}
	}
	
	private void initGrid(){
//		kernel = new EpanechnikovKernel(1.0f);
//		Bounds bounds = new Bounds(-1, -1, 1, 1);
//		grid = new Grid(GRID_SIZE_X, GRID_SIZE_Y, bounds);
//		try {
//			List<Vec2> points = dbService.loadXY();
//			grid.evaluate(points, kernel);
//		} catch (Exception e) {
//			Logger.error("Can't load XY (Grid) from Database", e);
//			return;
//		}
	}

	@Override
	public String getDocumentsGraph() {
		Graph<Document, Edge<Long>> graph;
		try {
			graph = dbService.getDocumentsGraph();
		} catch (Exception e) {
			Logger.error("Can't get citation graph",e);
			return null;
		}

		try{
			return graphToJson(graph);
		} catch (JsonProcessingException e) {
			Logger.error("Could not write authors graph as JSON",e);
			return null;
		}
	}

	@Override
	public String getAuthorsGraph() {
		Graph<Author, Edge<Long>> graph;
		try {
			graph = dbService.getAuthorsGraph();
		} catch (Exception e) {
			Logger.error("Can't get authors graph",e);
			return null;
		}

		try{
			return graphToJson(graph);
		} catch (JsonProcessingException e) {
			Logger.error("Could not write authors graph as JSON",e);
			return null;
		}
	}

	private <V,E> String graphToJson(Graph<V, E> graph) throws JsonProcessingException {
		final JsonGraph<V,E> jsonGraph = new JsonGraph<>(graph.getVertices(), graph.getEdges());
		ObjectMapper mapper = new ObjectMapper();
		return mapper.writeValueAsString(jsonGraph);	
	}

	private String buildQuery(QueryData queryData) {
		String terms = queryData.getTerms();
		if ( terms.trim().isEmpty() )
			return null;

		String op = getOperator(queryData.getOperator());

		StringBuilder query = new StringBuilder();
		// Checa consulta usando padrão para expressões entre aspas:
		// busca por frases
		Matcher m = TERM_PATTERN.matcher(terms);
		while( m.matches() ){
			// Extrai grupo entre aspas;
			String term = m.group(1);
			// Atualiza String de busca removendo grupo
			// extraido
			String f = terms.substring(0, m.start(1));
			String l = terms.substring(m.end(1)); 
			terms =  f + l;

			// Remove aspas do termo
			term = term.substring(1, term.length()-1);

			// Divide o term em tokens e adiciona na query (ts_query)
			// utilizando operador FOLLOWED BY (<->)
			String[] split = term.split("\\s+");

			// Caso não seja o primeiro termo
			// adiciona operador OR (|)
			if ( query.length() > 0)
				query.append(op);

			query.append("(");
			query.append(split[0]);
			for(int i = 1; i < split.length; i++){
				query.append(" <-> ");
				query.append(split[i]);
			}
			query.append(")");

			// Atualize Matcher
			m = TERM_PATTERN.matcher(terms);
		}

		// Ainda tem termos a serem processados?
		if ( terms.length() > 0){

			// Caso não seja o primeiro termo
			// adiciona operador OR (|)
			if ( query.length() > 0)
				query.append(op);

			String[] split = terms.split("\\s+");

			// O sinal negativo indica exclusão na busca, 
			// operador negação ! em SQL

			if ( split[0].charAt(0) == '-')
				query.append("!");
			query.append(split[0]);

			for(int i = 1; i < split.length; i++){
				query.append(op);
				if ( split[i].charAt(0) == '-')
					query.append("!");
				query.append(split[i]);
			}
		}

		return query.toString();
	}

	private String getOperator(String operator) {
		switch (operator) {
		case "or":		
			return "|";
		case "and":
			return "&";
		default:
			return "|";
		}
	}	
}
