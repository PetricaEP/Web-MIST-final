package ep.db.mdp;

import java.io.FileInputStream;
import java.util.Properties;

import org.apache.log4j.Logger;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import ep.db.database.DatabaseService;
import ep.db.database.DefaultDatabase;
import ep.db.utils.Utils;

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
	private static Logger logger = Logger.getLogger(MultidimensionalProjection.class);
	
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
	public MultidimensionalProjection( Properties config ) {
		this(config,true);
	}
	
	/**
	 * Cria novo objeto para projeção multidimensional
	 * com a configuração dada.
	 * @param config configuração.
	 */
	public MultidimensionalProjection( Properties config, boolean normalize ) {
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
		DoubleMatrix2D matrix = null;
		try {
			 matrix = dbService.buildFrequencyMatrix(null);
		} catch (Exception e) {
			logger.error("Error building frequency matrix", e);
			throw e;
		}
		
		// Realiza projeção multidimensional utilizando LAMP
		Lamp lamp = new Lamp();
		DoubleMatrix2D y = lamp.project(matrix);
		
//		 Normaliza projeção para intervalo [-1,1]
		if ( normalize ){
			normalizeProjections(y);
		}
		// Atualiza projeções no banco de dados.
		updateProjections(y);
	}
	
	private void normalizeProjections(DoubleMatrix2D y) {
		final double maxX = y.viewColumn(0).getMaxLocation()[0], 
				maxY = y.viewColumn(1).getMaxLocation()[0];
		final double minX = y.viewColumn(0).getMinLocation()[0],
				minY = y.viewColumn(1).getMinLocation()[0];
		
		y.viewColumn(0).assign( (v) -> 2 * (v - minX)/(maxX - minX) - 1 );
		y.viewColumn(1).assign( (v) -> 2 * (v - minY)/(maxY - minY) - 1 );
	}

	/**
	 * Atualiza projeções no banco de dados.
	 * @param y
	 * @throws Exception 
	 */
	private void updateProjections(DoubleMatrix2D y) throws Exception {
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
			Properties properties = new Properties();
			properties.load(new FileInputStream(Utils.PROP_FILE));
			
			System.out.println("Updating MDP...");
			MultidimensionalProjection mdp = new MultidimensionalProjection(properties);
			mdp.project();
			System.out.println("MDP successful updated");

		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
	}
}
