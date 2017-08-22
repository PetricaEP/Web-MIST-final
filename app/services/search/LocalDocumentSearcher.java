package services.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import javax.inject.Inject;

import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import cern.colt.matrix.tfloat.FloatMatrix2D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import cern.colt.matrix.tint.IntMatrix1D;
import edu.uci.ics.jung.graph.Graph;
import ep.db.database.DatabaseService;
import ep.db.model.Author;
import ep.db.model.Document;
import ep.db.model.IDocument;
import ep.db.pagerank.Edge;
import ep.db.quadtree.Bounds;
import ep.db.quadtree.QuadTree;
import ep.db.quadtree.QuadTreeNode;
import ep.db.quadtree.Vec2;
import ep.db.utils.Configuration;
import play.Logger;
import play.db.Database;
import services.clustering.KMeans;
import services.database.PlayDatabaseWrapper;
import views.formdata.QueryData;
import views.formdata.SelectionData;

public class LocalDocumentSearcher implements DocumentSearcher {

	private static final Pattern TERM_PATTERN = Pattern.compile(".*(\".*?\").*");

	private static final double PADDING = 6.0f;

	private static final float INC = 0.001f;
	
	private static Configuration configuration;

	private static QuadTree quadTree;
	
	private DatabaseService dbService;
	

	@Inject
	public LocalDocumentSearcher(Database db) {
		this.dbService = new DatabaseService(new PlayDatabaseWrapper(db));
		configuration = Configuration.getInstance();
		initQuadTree();
	}

	@Override
	public String search(QueryData queryData) throws Exception {
		return search(queryData, false, 0);
	}

	@Override
	public String search(QueryData queryData, int count) throws Exception {
		return search(queryData, false, count);
	}

	@Override
	public String search(QueryData queryData, boolean fetchNumberOfCitations) throws Exception {
		return search(queryData, fetchNumberOfCitations, 0);
	}

	@Override
	public String search(QueryData queryData, boolean fetchNumberOfCitations, int count) throws Exception {

		Map<String, Object> result = new HashMap<>();

		try {
			String query = buildQuery(queryData);


			List<Document> docs;

			if ( ! queryData.getAuthor().isEmpty() || ! queryData.getYearStart().isEmpty() || ! queryData.getYearEnd().isEmpty() )
				docs = dbService.getAdvancedSimpleDocuments(query, queryData.getAuthor(), queryData.getYearStart(), 
						queryData.getYearEnd(), count);
			else if ( query != null )
				docs = dbService.getSimpleDocuments(query, count);
			else
				docs = dbService.getAllSimpleDocuments();

			if( docs.size() > 0){

				// Realiza interpolacão das relevancias 
				// para obter  os raios de cada documento.
				int index = interpolateRadii(docs, queryData.getWidth(), queryData.getHeight(), 0);

				// Somente documentos selecionados para exibição,
				// demais documentos irão compor o mapa de densidade.
				List<Document> selected = docs.subList(0, index);

				// Coleta doc_ids
				long docIds[] = selected.parallelStream().mapToLong((doc) -> doc.getId()).toArray();

				// References
				Map<Long, List<Long>> references = dbService.getReferences(docIds);

				//Clustering
				final int numClusters = queryData.getNumClusters();
				IntMatrix1D clusters = clustering(selected, numClusters);

				//Atribui id cluster e referencias
				IntStream.range(0, selected.size()).parallel().forEach( (i) -> {
					selected.get(i).setReferences(references.get(selected.get(i).getId()));
					selected.get(i).setCluster(clusters.get(i));
				});

				double[][] densities = new double[docs.size()-index][2];
				for(int i = index, j = 0; i < docs.size() && j < densities.length; i++, j++){
					densities[j][0] = docs.get(i).getX();
					densities[j][1] = docs.get(i).getY();
				}

				result.put("documents", selected);
				result.put("density", densities);
				result.put("nclusters", numClusters);
				result.put("op", "search");
			}
			else{
				result.put("documents", new ArrayList<Document>(0));
				result.put("nclusters", 0);
				result.put("op", "search");
			}
		}catch (Exception e) {
			Logger.error("Unkown error!", e);
			result.put("documents", new ArrayList<Document>(0));
			result.put("nclusters", 0);
			result.put("op", "search");
		}

		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.writeValueAsString(result);
		} catch (JsonProcessingException e) {
			Logger.error("JSON processing error: " + result.toString(), e);
			return "";
		}
	}

	@Override
	public String zoom(SelectionData selectionData) {
		Map<String, Object> result = new HashMap<>();

		try {
			float x1, y1, x2, y2;
			
			x1 = selectionData.getStart()[0];
			y1 = selectionData.getStart()[1];
			x2 = selectionData.getEnd()[0];
			y2 = selectionData.getEnd()[1];
			
//			x1 = mapX(x1, selectionData.getWidth()); // - 0.2f;
//			y1 = mapY(y1, selectionData.getHeight()); // - 0.2f;
//
//			x2 = mapX(x2, selectionData.getWidth());
//			y2 = mapY(y2, selectionData.getHeight());
			
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
			quadTree.findInRectangle(rectangle, documents, nodes);
			
			if ( documents.size() > 0){
				// Ordena por relevancia (descrescente)
				documents.sort(Comparator.comparing((IDocument d) -> d.getRank()).reversed());

				final int numClusters = selectionData.getNumClusters();
				IntMatrix1D clusters = clustering(documents, numClusters);

				// Coleta doc_ids
				long docIds[] = documents.parallelStream().mapToLong((doc) -> doc.getId()).toArray();

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

				//Atribui id cluster e referencias
				IntStream.range(0, documents.size()).parallel().forEach( (i) -> {
					documents.get(i).setReferences(references.get(documents.get(i).getId()));
					documents.get(i).setCluster(clusters.get(i));
				});

				// Realiza interpolacão das relevancias 
				// para obter  os raios de cada documento.
				int index = interpolateRadii(documents, 
						selectionData.getWidth(), 
						selectionData.getHeight(),
						selectionData.getZoomLevel());

				// Valores min/max das coordenadas
				// x,y para interpolação com as dimensões
				// da tela
				float minX = documents.get(0).getX(), 
						maxX = minX, 
						minY = documents.get(0).getY(), 
						maxY = minY;
				
				double[][] densities = new double[documents.size()-index][2];
				for(int i = 0, j = 0; i < documents.size(); i++){
					IDocument doc = documents.get(i);
					float x = doc.getX();
					float y = doc.getY();
					
					if ( i >= index){
						densities[j][0] = x;
						densities[j][1] = y;
						++j;
					}
					
					//Atualiza min/max
					if ( x > maxX) maxX = x;
					if ( x < minX) minX = x;
					if ( y > maxY) maxY = y;
					if ( y < minY) minY = y;
				}


				// Somente documentos selecionados para exibição,
				// demais documentos irão compor o mapa de densidade.
				
				result.put("documents", documents.subList(0, index));
				result.put("density", densities);
				result.put("nclusters", numClusters);
				result.put("op", "zoom");
				result.put("min_max", new float[]{minX, maxX, minY, maxY});
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
			return mapper.writeValueAsString(result);
		} catch (JsonProcessingException e) {
			Logger.error("JSON processing error: " + result.toString(), e);
			return "";
		}
	}

	private static float mapX(float x, float width) {
		return (quadTree.boundingBox().size().x * ((x / width) - 0.5f));
	}

	private static float mapY(float y, float height) {
		return (quadTree.boundingBox().size().y * (0.5f - (y / height)));
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
		quadTree.loadQuadTree();
	}

	private int interpolateRadii(List<? extends IDocument> docs, float width, float height, int zoomLevel) {
		float maxRev = docs.get(0).getRank() * 1e5f,
				minRev = docs.get(docs.size()-1).getRank() * 1e5f,
				maxRadius = width * configuration.getMaxRadiusSizePercent(), //* (zoomLevel+1),
				minRadius = width * configuration.getMinRadiusSizePercent(); // * (zoomLevel+1);
		
		UnivariateFunction f;

		// Se todos documentos tem mesma relevancia então todos
		// irao possuir mesmo raio
		if ( minRev == maxRev )
			f = ((x) -> maxRadius);
		else{
			double[] x = generateInterpolationXY(minRev, maxRev, 10);
			double[] y = generateInterpolationXY(minRadius, maxRadius, 10);
			
			// Ajusta x e y para que ambos possuam mesmo tamanho 
			int diffSize = Math.abs(x.length - y.length);
			if ( diffSize != 0){
				if ( x.length > y.length){
					y = Arrays.copyOf(y, x.length);
					for(int i = y.length - diffSize, j = 1; i < y.length; i++, j++)
						y[i] = maxRadius + (j * INC);
				}
				else{
					x = Arrays.copyOf(x, y.length);
					for(int i = x.length - diffSize, j = 1; i < x.length; i++, j++)
						x[i] = maxRev + (j * INC);
				}
			}
			
			LinearInterpolator radiusInterpolator = new LinearInterpolator();
			f = radiusInterpolator.interpolate(x, y);
		}

		final double maxArea = width * height;
		double area = 0.0;
		int index = 0;
		while ( area < maxArea && index < docs.size()){
			IDocument d = docs.get(index);
			double r = f.value(d.getRank() * 1e5f);
			d.setRadius(r);
			// Aprox. formas para quadrados de lado 2r + padding.
			area += 4 * (r + PADDING) * (r + PADDING) ;
			++index;
		}
		return index;
	}

	private static double[] generateInterpolationXY(float start, float stop, int count) {
		float step = (stop - start) / Math.max(1, count);
		float end = stop,
				ini = start;
		start = (float) Math.ceil(start / step);
		stop = (float) Math.floor(stop / step);

		int n = (int) Math.ceil(stop - start + 1);
		double[] ticks = new double[n];
		int i = 0;
		ticks[0] = ini;
		while (++i < n) ticks[i] = (start + i) * step;

		if ( ticks[n-1] < end ){
			ticks = Arrays.copyOf(ticks, ticks.length+1);
			ticks[ticks.length-1] = end;
		}
		return ticks;
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

		String op;
		switch (queryData.getOperator()) {
		case "or":		
			op = "|";
			break;
		case "and":
			op = "&";
			break;
		default:
			op = "|";
			break;
		}

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
}
