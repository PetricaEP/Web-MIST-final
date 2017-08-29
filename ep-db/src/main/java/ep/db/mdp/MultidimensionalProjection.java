package ep.db.mdp;


import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cern.colt.list.tfloat.FloatArrayList;
import cern.colt.matrix.tfloat.FloatFactory1D;
import cern.colt.matrix.tfloat.FloatMatrix1D;
import cern.colt.matrix.tfloat.FloatMatrix2D;
import cern.jet.stat.tfloat.quantile.FloatQuantileFinder;
import cern.jet.stat.tfloat.quantile.FloatQuantileFinderFactory;
import ep.db.database.DatabaseService;
import ep.db.database.DefaultDatabase;
import ep.db.tfidf.LogaritmicInverseDocumentFrequencyTFIDF;
import ep.db.tfidf.LogaritmicTFIDF;
import ep.db.tfidf.RawInverseDocumentFrequencyTFIDF;
import ep.db.tfidf.TFIDF;
import ep.db.utils.Configuration;

/**
 * Classe para realizar projeção multidimensional
 * utilizando {@link Lamp}.
 * @version 1.0
 * @since 2017
 *
 */
public class MultidimensionalProjection {
	
	/**
	 * Logger
	 */
	private static Logger logger = LoggerFactory.getLogger(MultidimensionalProjection.class);
	
	/**
	 * Serviço para manipulação do banco de dados.
	 */
	private DatabaseService dbService;
	
	/**
	 * Normalizar projeção para intervalo [-1,1]
	 */
	private  boolean normalize;

	/**
	 * Configuracao.
	 */
	private Configuration config;

	/**
	 * Cria novo objeto para projeção multidimensional
	 * com a configuração dada.
	 * @param config configuração.
	 */
	public MultidimensionalProjection( Configuration config ) {
		this(config,true);
	}
	
	/**
	 * Cria novo objeto para projeção multidimensional
	 * com a configuração dada.
	 * @param config configuração.
	 */
	public MultidimensionalProjection( Configuration config, boolean normalize ) {
		this.dbService = new DatabaseService(new DefaultDatabase(config));
		this.normalize = normalize;
		this.config = config;
	}

	/**
	 * Realiza projeção multidimensional dos documentos
	 * no banco de dados.
	 * @throws Exception erro ao realizar projeção.
	 */
	public void project() throws Exception {

		// Constroi matriz de frequência de termos
		// ordenada decrescentemente pela relevancia.
		// Primeira coluna contém docIds.
		FloatMatrix2D matrix = null;
		
		TFIDF tfidf = getTFIDFWeightingScheme();
		
		try {
			System.out.println("Building frequency matrix (bag of words)...");
			 matrix = dbService.buildFrequencyMatrix(null, tfidf);
		} catch (Exception e) {
			logger.error("Error building frequency matrix", e);
			throw e;
		}

		// Colunas selecionadas = todas colunas - coluna doc_id
		final int[] cols = IntStream.range(1, matrix.columns()).distinct().sorted().toArray();
				
		// Realiza projeção multidimensional utilizando LAMP
		System.out.println("Projecting...");
		Lamp lamp = new Lamp();
		FloatMatrix2D y = lamp.project(matrix.viewSelection(null, cols), 
				Configuration.getInstance().isRandomControlPoints());
		
		List<Integer> outliers = null;
		if ( Configuration.getInstance().isDisableOutliers()  ){
			System.out.println("Outliers detection...");
			outliers = disableOutliers(y);
			System.out.println("Number of documents disabled (outliers detected): " + outliers.size());
		}
		
		int[] rows = null;
		
		if ( outliers != null && outliers.size() > 0){
			rows = new int[y.rows() - outliers.size()];
			for(int i = 0, k = 0; i < y.rows(); i++)
				if ( !outliers.contains(i) )
					rows[k++] = i;
			
			
		}
		
//		 Normaliza projeção para intervalo [-1,1]
		if ( normalize ){
			System.out.println("Normalizing to range [-1,1]...");
			normalizeProjections(y.viewSelection(rows, null));
		} 
		
		// Atualiza projeções no banco de dados.
		System.out.println("Updating databse...");
		updateProjections(matrix.viewColumn(0), y, outliers);
	}
	
	private TFIDF getTFIDFWeightingScheme() {
		TFIDF tfidf;
		switch (config.getTfidfWeightingScheme()) {
		case 1:
			tfidf = new RawInverseDocumentFrequencyTFIDF();
			break;
		case 2:
			tfidf = new LogaritmicInverseDocumentFrequencyTFIDF();
			break;
		case 3: 
			tfidf = new LogaritmicTFIDF();
			break;
		default:
			tfidf = new LogaritmicInverseDocumentFrequencyTFIDF();
			break;
		}
		return tfidf;
	}

	private List<Integer> disableOutliers(FloatMatrix2D y) {
		FloatMatrix1D distances = FloatFactory1D.dense.make(y.rows());
		
		// Calcula media
		for( int i = 0; i < y.rows(); i++){
			float dist = (float) Math.sqrt(y.viewRow(i).zDotProduct(y.viewRow(i)));
			distances.setQuick(i, dist);
		}
		
		FloatQuantileFinder quantile = 
				FloatQuantileFinderFactory.newFloatQuantileFinder(false, y.rows(), 0.001f, 0.0001f, 10000, null);
		FloatArrayList values = new FloatArrayList(distances.toArray());
		quantile.addAllOf(values);
		float[] phis = new float[]{0.25f, 0.75f};
		FloatArrayList q1q3 = quantile.quantileElements(new FloatArrayList(phis));
		float Q1 = q1q3.getQuick(0),
				Q3 = q1q3.get(1),
				IQR = 1.5f*(Q3-Q1);
		
		List<Integer> indices = new ArrayList<>();
		for(int i = 0; i < distances.size(); i++){
			float d = distances.getQuick(i);
			if ( d < Q1 - IQR || d > Q3 + IQR)
				indices.add(i);
		}
		
		return indices;
	}

	private void normalizeProjections(FloatMatrix2D y) {
		final float maxX = y.viewColumn(0).getMaxLocation()[0], 
				maxY = y.viewColumn(1).getMaxLocation()[0];
		final float minX = y.viewColumn(0).getMinLocation()[0],
				minY = y.viewColumn(1).getMinLocation()[0];
		
		y.viewColumn(0).assign( (v) -> 2 * (v - minX)/(maxX - minX) - 1 );
		y.viewColumn(1).assign( (v) -> 2 * (v - minY)/(maxY - minY) - 1 );
	}

	/**
	 * Atualiza projeções no banco de dados.
	 * @param y
	 * @param y 
	 * @param outliers 
	 * @throws Exception 
	 */
	private void updateProjections(FloatMatrix1D docIds, FloatMatrix2D y, List<Integer> outliers) throws Exception {
		try {
			dbService.updateXYProjections(docIds, y);
			if ( outliers != null && !outliers.isEmpty())
				dbService.disableDocuments(docIds.viewSelection(outliers.stream().mapToInt((i) -> i.intValue()).toArray()));
		} catch (Exception e) {
			logger.error("Error updating projections in database", e);
			throw e;
		}
	}
	
	/**
	 * Método main para calcúlo das projeções.
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			
			Configuration config = Configuration.getInstance();
			config.loadConfiguration();
			
			System.out.println("Updating MDP...");
			MultidimensionalProjection mdp = new MultidimensionalProjection(config);
			mdp.project();
			System.out.println("MDP successful updated");
			System.out.println("\n");

		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
	}
}