package ep.db.pagerank;

import java.io.FileInputStream;
import java.util.Properties;

import org.apache.log4j.Logger;

import edu.uci.ics.jung.algorithms.scoring.PageRank;
import edu.uci.ics.jung.graph.DirectedGraph;
import ep.db.database.DatabaseService;
import ep.db.database.DefaultDatabase;
import ep.db.utils.Utils;

/**
 * Classe para cálculo das relevâncias de cada
 * documento no banco de dados.
 * 
 * @version 1.0
 * @since 2017
 *
 */
public class RelevanceCalculator {
	
	/**
	 * Logger
	 */
	private static Logger logger = Logger.getLogger(RelevanceCalculator.class);
	
	/**
	 * Fator-C padrão
	 */
	private static final double C = 0.85;
	
	/**
	 * Serviço de manipulação do banco de dados.
	 */
	private final DatabaseService dbService;
	
	/**
	 * Fator-C
	 */
	private final double c;
	
	/**
	 * Cria novo objeto para cálculo de relevância utilizando
	 * fator-C padrão ({@value #C} e configuração especificada.
	 * @param config configuração.
	 */
	public RelevanceCalculator( Properties config ) {
		this(config, C);
	}
	
	/**
	 * Cria novo objeto para cálculo de relevância utilizando
	 * fator-C e configuração especificada.
	 * @param config configuração.
	 * @param c fator-c.
	 */
	public RelevanceCalculator( Properties config, double c ) {
		this.dbService = new DatabaseService(new DefaultDatabase(config));
		this.c = c;
	}
	
	/**
	 * Atualiza relevâncias no banco de dados.
	 * @throws Exception erro ao recuperar ou atualizar relevâncias.
	 */
	public void updateRelevance() throws Exception {
		
		DirectedGraph<Long,Long> graph = null;
		try {
			graph = dbService.getCitationGraph();
		} catch (Exception e) {
			logger.error("Error while getting citation graph from database",e);
			throw e;
		}
		
		PageRank<Long, Long> pageRank = new PageRank<>(graph, c);
		pageRank.evaluate(); 
		
		try {
			dbService.updatePageRank(graph, pageRank);
		} catch (Exception e) {
			logger.error("Error updating page rank in database", e);
			throw e;
		}
	}
	
	/**
	 * Método main para cálculo/atualização das relevâncias.
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			Properties properties = new Properties();
			properties.load(new FileInputStream(Utils.PROP_FILE));
			
			System.out.println("Updating ranking...");
			RelevanceCalculator ranking = new RelevanceCalculator(properties);
			ranking.updateRelevance();
			System.out.println("Ranking successful updated");

		} catch (Exception e) {
			System.err.println("Some error has occured. See log file for more details");
			System.exit(-1);
		}
	}

}
