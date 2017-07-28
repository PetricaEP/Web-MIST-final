package ep.db.mdp;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cern.colt.matrix.tfloat.FloatMatrix2D;
import ep.db.database.DatabaseService;
import ep.db.database.DefaultDatabase;
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
	}

	/**
	 * Realiza projeção multidimensional dos documentos
	 * no banco de dados.
	 * @throws Exception erro ao realizar projeção.
	 */
	public void project() throws Exception {

		// Constroi matriz de frequência de termos
		FloatMatrix2D matrix = null;
		try {
			 matrix = dbService.buildFrequencyMatrix(null);
		} catch (Exception e) {
			logger.error("Error building frequency matrix", e);
			throw e;
		}

		// Realiza projeção multidimensional utilizando LAMP
		Lamp lamp = new Lamp();
		FloatMatrix2D y = lamp.project(matrix);
		
//		 Normaliza projeção para intervalo [-1,1]
		if ( normalize ){
			normalizeProjections(y);
		}
		
		// Atualiza projeções no banco de dados.
		updateProjections(y);
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
	 * @throws Exception 
	 */
	private void updateProjections(FloatMatrix2D y) throws Exception {
		try {
			dbService.updateXYProjections(y);
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