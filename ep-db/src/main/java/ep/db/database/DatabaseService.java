package ep.db.database;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.uci.ics.jung.algorithms.scoring.PageRank;
import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import ep.db.matrix.Matrix;
import ep.db.matrix.Pair;
import ep.db.matrix.SparseMatrix;
import ep.db.matrix.SparseVector;
import ep.db.model.Author;
import ep.db.model.Document;
import ep.db.model.IDocument;
import ep.db.model.Word;
import ep.db.pagerank.Edge;
import ep.db.quadtree.Bounds;
import ep.db.quadtree.QuadTree;
import ep.db.quadtree.QuadTreeBranchNode;
import ep.db.quadtree.QuadTreeLeafNode;
import ep.db.quadtree.QuadTreeNode;
import ep.db.quadtree.Vec2;
import ep.db.tfidf.TFIDF;
import ep.db.utils.Configuration;
import ep.db.utils.Utils;

/*
import ep.db.utils.Utils;*
 * Provedor de serviços com o banco de dados.
 * Inclui métodos para manipulação do banco: 
 * inserções, deleções, atualizações e consultas.
 * Todo o CRUD está concentrado nessa classe. 
 * @version 1.0
 * @since 2017
 */
public class DatabaseService {

	public static final int DOCUMENTS_GRAPH = 0;

	public static final int AUTHORS_GRAPH = 1;

	public static final Pattern TSV_VALUE_PATTERN = Pattern.compile("\\d+(\\w?)");

	/**
	 * SQL para inserção de um novo documento
	 */
	private static final String INSERT_DOC = "INSERT INTO documents AS d (title, authors, doi, keywords, abstract, "
			+ "publication_date, volume, pages, issue, container, container_issn, language, path, enabled, bibtex) "
			+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::regconfig, ?, ?, ?) ON CONFLICT (doi) DO UPDATE "
			+ "SET title = coalesce(d.title, excluded.title),"
			+ "authors = coalesce(d.authors, excluded.authors),"
			+ "keywords=coalesce(d.keywords, excluded.keywords), "
			+ "abstract = coalesce(d.abstract, excluded.abstract),"
			+ "publication_date = coalesce(d.publication_date, excluded.publication_date), "
			+ "volume = coalesce(d.volume, excluded.volume), "
			+ "pages = coalesce(d.pages, excluded.pages), "
			+ "issue = coalesce(d.issue, excluded.issue), "
			+ "container = coalesce(d.container, excluded.container), "
			+ "container_issn = coalesce(d.container_issn, excluded.container_issn), "
			+ "language = coalesce(d.language, excluded.language), "
			+ "path = coalesce(d.path, excluded.path), "
			+ "enabled = coalesce(d.enabled, excluded.enabled), "
			+ "bibtex = coalesce(d.bibtex,excluded.bibtex)";

	/**
	 * SQL para inserção de novo autor
	 */
	private static final String INSERT_AUTHOR = "INSERT INTO authors as a (aut_name) "
			+ "VALUES (?) ON CONFLICT (aut_name) DO UPDATE "
			+ "SET aut_name = coalesce(a.aut_name,excluded.aut_name); ";

	/**
	 * SQL para inserção de relação entre documento-autor
	 */
	private static final String INSERT_DOC_AUTHOR = "INSERT INTO document_authors as a (doc_id,aut_id) VALUES(?,?) "
			+ "ON CONFLICT DO NOTHING";

	/**
	 * SQL para inserção de citações
	 */
	private static final String INSERT_REFERENCE = "INSERT INTO citations(doc_id, ref_id) VALUES (?, ?) "
			+ "ON CONFLICT DO NOTHING";

	/**
	 * SQL para remoção de documento
	 */
	private static final String DELETE_DOC = "DELETE FROM documents WHERE doc_id = ?";

	/**
	 * SQL para atualização das coordenadas X,Y resultantes de projeção multidimensional
	 */
	private static final String UPDATE_XY = "UPDATE documents_data SET x = ?, y = ? WHERE doc_id = ?";

	/**
	 * SQL para atualização da relevancia de um documento
	 */
	private static final String UPDATE_RELEVANCE_DOCUMENTS = "UPDATE documents_data SET relevance_doc = ? WHERE doc_id = ?";

	/**
	 * SQL para atualização da relevancia de um autor.
	 */
	private static final String UPDATE_RELEVANCE_AUTHORS = "UPDATE authors SET relevance = ? WHERE aut_id = ?";

	private static final String SQL_SELECT_COLUMNS = "d.doc_id, d.doi, d.title, d.keywords, d.publication_date, "
			+ "dd.x, dd.y, dd.rank, d.authors, d.bibtex, d.words ";	

	private static final String SEARCH_SQL = "SELECT " + SQL_SELECT_COLUMNS + " FROM documents d "
			+ "INNER JOIN documents_data dd ON d.doc_id = dd.doc_id AND dd.rank IS NOT NULL,"
			+ "to_tsquery(?) query WHERE query @@ tsv AND d.enabled is TRUE "
			+ "ORDER BY rank DESC OFFSET ?";	

	private static final String SEARCH_SQL_ALL = "SELECT " + SQL_SELECT_COLUMNS + " FROM documents d "
			+ "INNER JOIN documents_data dd ON d.doc_id = dd.doc_id AND dd.rank IS NOT NULL "
			+ "ORDER BY dd.rank DESC OFFSET ?";

	private static final String ADVANCED_SEARCH_SQL = "SELECT " + SQL_SELECT_COLUMNS + " FROM documents d " 
			+ " INNER JOIN documents_data dd ON d.doc_id = dd.doc_id AND dd.rank IS NOT NULL %s "
			+ " ORDER BY dd.rank DESC OFFSET ?";

	private static final String ADVANCED_SEARCH_AUTHORS_INNER_JOIN_SQL = " INNER JOIN (SELECT doc_id FROM document_authors da INNER JOIN "
			+ "authors a ON da.aut_id = a.aut_id, to_tsquery(?) aut_query WHERE aut_query @@ aut_name_tsv) a "
			+ "ON a.doc_id = d.doc_id ";

	private static final String SEARCH_SQL_ALL_MAX_RANK = "SELECT x,y FROM documents_data d WHERE "
			+ "(x >= ? AND x <= ?) AND (y >= ? AND y <= ?) ORDER BY rank DESC";

	private static final String LOAD_XY_SQL = "SELECT x,y FROM documents_data d WHERE "
			+ "(x > ? AND x <= ?) AND (y > ? AND y <= ?)";

	private static final String SEARCH_SQL_DOC_IDS = "SELECT " + SQL_SELECT_COLUMNS + " FROM documents d "
			+ "INNER JOIN documents_data dd ON d.doc_id = dd.doc_id "
			+ "WHERE d.doc_id IN %s AND d.enabled is TRUE "
			+ "ORDER BY rank DESC";	

	private static final String PRE_DOCUMENTS_NODE_SQL = "SELECT s.* FROM (";

	private static final String DOCUMENTS_NODE_SQL = "SELECT " + SQL_SELECT_COLUMNS 
			//+ ",rank() over (partition by dd.node_id order by dd.rank desc) as r "
			+ " FROM documents_data dd INNER JOIN documents d "
			+ "ON d.doc_id = dd.doc_id WHERE dd.node_id = ?";

	private static final String POST_DOCUMENTS_NODE_SQL = ") s, to_tsquery(?) query WHERE query @@ tsv AND s.enabled is TRUE ORDER BY s.rank DESC";

	private static final String NODE_DATA_SQL = "SELECT *, (select count(*) as nDocuments FROM documents_data dn "
			+ "where dn.node_id=n.node_id)  FROM nodes n ORDER BY node_id";

	private static final String DELETE_NODE_DATA_SQL = "DELETE FROM nodes;";

	private static final String INSERT_NODE_SQL = "INSERT INTO nodes( node_id, isleaf, rankmax, rankmin, "
			+ "parent_id, depth, index) VALUES (?, ?, ?, ?, ?, ?, ?);";	

	private static final String UPDATE_DOC_DATA_NODE_ID_SQL = "UPDATE documents_data SET node_id = ? WHERE doc_id = ?";						

	private static final String AUTHORS_GRAPH_SQL = "SELECT a.aut_id source,a.aut_name source_name, a.relevance source_rank,"
			+ "at.aut_id target,at.aut_name target_name, at.relevance target_relevance "
			+ "FROM ( SELECT c.doc_id,c.ref_id,da.aut_id,at.aut_name, at.relevance FROM citations c INNER JOIN document_authors da ON "
			+ "c.doc_id = da.doc_id INNER JOIN authors at ON da.aut_id = at.aut_id ) a INNER JOIN document_authors rda ON "
			+ "a.ref_id = rda.doc_id INNER JOIN authors at ON rda.aut_id = at.aut_id";

	private static final String DOCS_GRAPH_SQL = "SELECT d.doc_id, r.doc_id, d.doi, d.title, d.keywords, d.publication_date, "
			+ "dd.x, dd.y, dd.rank, "
			+ "r.doi, r.title, r.keywords, r.publication_date, rr.x, rr.y, rr.rank "
			+ "FROM citations c INNER JOIN documents d "
			+ "ON c.doc_id = d.doc_id INNER JOIN documents r ON c.ref_id = r.doc_id "
			+ "INNER JOIN documents_data dd on d.doc_id = dd.doc_id INNER JOIN documents_data rr "
			+ "ON r.doc_id = rr.doc_id ORDER BY c.doc_id, c.doc_id";

	private static final String DOCUMENTS_GRAPH_SQL = "SELECT doc_id, ref_id FROM citations ORDER BY doc_id, ref_id";

	private static final String AUTHROS_GRAPH_SQL = "SELECT a.aut_id source, ra.aut_id target FROM citations c "
			+ "INNER JOIN document_authors a ON c.doc_id = a.doc_id INNER JOIN document_authors ra ON c.ref_id = ra.doc_id";

	private static final String PAGE_RANK_SQL_FUNC_DOCS = "SELECT calpagerank_docs(?);";

	private static final String PAGE_RANK_SQL_FUNC_AUTHORS = "SELECT calpagerank_authors(?);";

	private static final String UPDATE_DOCUMENTS_RANK = "SELECT update_documents_rank(?,?);";

	private static final String SELECT_TERMS_FREQ_FROM_TSV = "SELECT d.doc_id, tsv::varchar tsv_str FROM "
			+ "documents d INNER JOIN documents_data da ON d.doc_id = da.doc_id %s ORDER BY da.rank DESC, d.doc_id";

	/**
	 * Data source
	 */
	private Database db;

	/**
	 * Cria um novo serviço para manipulação do banco de dados, 
	 * com processamento em lote padrão (batchSize = 50).
	 * @param db banco de dados
	 */
	public DatabaseService(Database db) {
		this.db = db;
	}

	/**
	 * Retorna o número total de documentos na base.
	 * @return inteiro com número total de documentos.
	 * @throws Exception erro ao executar consulta.
	 */
	public int getNumberOfDocuments() throws Exception {
		try ( Connection conn = db.getConnection();){
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT count(*) FROM documents");
			if ( rs.next() ){
				return rs.getInt(1);
			}
			return 0;
		}catch( Exception e){
			throw e;
		}
	}

	public Matrix getFrequencyMatrix(long[] seletecDocIds, TFIDF tfidfCalc, List<Long> docIds) throws Exception {

		// Retorna numero de documentos e ocorrencia total dos termos
		int numberOfDocuments;
		if ( seletecDocIds == null )
			numberOfDocuments = getNumberOfDocuments();
		else
			numberOfDocuments = seletecDocIds.length;

		// Constroi consulta caso docIds != null
		StringBuilder sql = new StringBuilder();
		if ( seletecDocIds != null ){
			sql.append(" WHERE d.doc_id IN (");
			sql.append(seletecDocIds[0]);
			for(int i = 1; i < seletecDocIds.length; i++){
				sql.append(",");
				sql.append(seletecDocIds[i]);
			}
			sql.append(")");
		}

		String where = sql.toString();
		Configuration config = Configuration.getInstance();

		// Recupera frequencia indiviual de cada termo na base de dados (todos os documentos)
		final Map<String, Integer> termsCount = getTermsCounts(where);

		// Mapeamento termo -> coluna na matriz (bag of words)
		final Map<String, Integer> termsToColumnMap = new HashMap<>();
		int c = 0;
		for(String key : termsCount.keySet()){
			termsToColumnMap.put(key, c);
			++c;
		}

		tfidfCalc.setTermsCount(termsCount);

		// Popula matriz com frequencia dos termos em cada documento
		Matrix matrix;
		if ( config.isUsePreCalculatedFreqs() )
			matrix = buildFrequencyMatrix(numberOfDocuments, termsCount.size(), termsToColumnMap, where, tfidfCalc, docIds);
		else
			matrix = buildFrequencyMatrixFromTSV(numberOfDocuments, termsCount.size(), termsToColumnMap, where, tfidfCalc, docIds);
		return matrix;
	}

	/**
	 * Insere novo documento ao banco de dados ou atualiza campos
	 * caso já exista na base documento com mesmo DOI.
	 * @param doc documento a ser inserido.
	 * @return id do novo documento.
	 * @throws Exception erro ao executar inserção.
	 */
	public long addDocument(Document doc) throws Exception {
		long docId = -1;

		boolean isCompleted = Utils.isDocumentCompleted(doc);

		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(INSERT_DOC, Statement.RETURN_GENERATED_KEYS);
			stmt.setString(1, doc.getTitle());
			stmt.setString(2, Utils.authorsToString(doc.getAuthors()));
			stmt.setString(3, doc.getDOI());
			stmt.setString(4, doc.getKeywords());
			stmt.setString(5, doc.getAbstract());
			stmt.setInt(6, Utils.extractYear(doc.getPublicationDate()));
			stmt.setString(7, doc.getVolume());
			stmt.setString(8, doc.getPages());
			stmt.setString(9, doc.getIssue());
			stmt.setString(10, doc.getContainer());
			stmt.setString(11, doc.getISSN());
			stmt.setString(12, doc.getLanguage());
			stmt.setString(13, doc.getPath());
			stmt.setBoolean(14, isCompleted);
			stmt.setString(15, doc.getBibTEX());
			stmt.executeUpdate();
			ResultSet rs = stmt.getGeneratedKeys();
			if (rs.next()){
				docId = rs.getLong(1);
				doc.setId(docId);
			}
		}catch( Exception e){
			throw e;
		}

		// Em caso de succeso, insere autores na
		// tabela authors e também atribui ao documento
		// seus autores na tabela document_authors
		if ( docId > 0 ){
			try {
				addAuthors(Arrays.asList(doc));
				addDocumetAuthors(Arrays.asList(doc));
			}catch (Exception e) {
				System.err.println(e.getMessage());
			}			
		}

		return docId;
	}	

	/**
	 * Insere todos os documentos da lista dada no bando de dados, 
	 * atualizando campos caso já exista algum documento com mesmo DOI.
	 * <p>Para inserção de multiplos documentos esse método é mais eficiente do que
	 * multiplas chamadas do método {@link #addDocument(Document)} uma vez que realiza
	 * inserção em batch.</p>
	 * @param documents documentos a serem inseridos.
	 * @return vetor com id dos documentos inseridos (excluidos os id's dos documentos
	 * atualizados).
	 * @throws Exception erro ao adicionar documentos.
	 */
	public long[] addDocuments(List<Document> documents) throws Exception {
		List<Long> docIds = new ArrayList<>();

		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(INSERT_DOC, Statement.RETURN_GENERATED_KEYS);

			int count = 0;

			for( Document doc: documents ){
				boolean isCompleted = Utils.isDocumentCompleted(doc);

				stmt.setString(1, doc.getTitle());
				stmt.setString(2, Utils.authorsToString(doc.getAuthors()));
				stmt.setString(3, doc.getDOI());
				stmt.setString(4, doc.getKeywords());
				stmt.setString(5, doc.getAbstract());
				stmt.setInt(6, Utils.extractYear(doc.getPublicationDate()));
				stmt.setString(7, doc.getVolume());
				stmt.setString(8, doc.getPages());
				stmt.setString(9, doc.getIssue());
				stmt.setString(10, doc.getContainer());
				stmt.setString(11, doc.getISSN());
				stmt.setString(12, doc.getLanguage());
				stmt.setString(13, doc.getPath());
				stmt.setBoolean(14, isCompleted);
				stmt.setString(15, doc.getBibTEX());

				stmt.addBatch();

				if (++count % Configuration.getInstance().getDbBatchSize() == 0){
					stmt.executeBatch();
					getGeneratedKeys(docIds, stmt.getGeneratedKeys());
				}
			}

			stmt.executeBatch();
			getGeneratedKeys(docIds, stmt.getGeneratedKeys());

			for(int i = 0; i < docIds.size(); i++)
				documents.get(i).setId(docIds.get(i));

		}catch( Exception e){
			throw e;
		}

		if ( docIds.size() > 0 ){
			addAuthors(documents);
			addDocumetAuthors(documents);
		}

		return docIds.stream().mapToLong(l->l).toArray();
	}

	/**
	 * Adiciona autores dos documentos dados no banco de dados
	 * @param documents documentos para quais os autores devem ser inseridos
	 * ou atualizados no banco de dados.
	 * @return id's do autores corretamente inseridos (excluídos id's dos registros
	 * atualizados).
	 * @throws Exception erro ao executar inserção.
	 */
	private long[] addAuthors(List<Document> documents) throws Exception {
		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(INSERT_AUTHOR, Statement.RETURN_GENERATED_KEYS);
			int count = 0;

			List<Long> ids = new ArrayList<>();

			for( Document doc : documents){
				for( Author author : doc.getAuthors() ){
					stmt.setString(1, author.getName());
					stmt.addBatch();

					if(++count % Configuration.getInstance().getDbBatchSize() == 0){
						stmt.executeBatch();
						getGeneratedKeys(ids, stmt.getGeneratedKeys());
					}
				}
			}

			stmt.executeBatch();
			getGeneratedKeys(ids, stmt.getGeneratedKeys());

			int i = 0;
			for( Document doc : documents){
				for( Author author : doc.getAuthors() ){
					author.setId(ids.get(i));
					++i;
				}
			}

			return ids.stream().mapToLong(l->l).toArray();

		}catch( Exception e){
			throw e;
		}
	}

	/**
	 * Adiciona ligação entre documento e autor no banco de dados.
	 * @param docs documentos para quais as ligações documento-autor serão
	 * inseridas.
	 * @throws Exception erro ao executar inserção.
	 */
	private void addDocumetAuthors(List<Document> docs) throws Exception {
		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(INSERT_DOC_AUTHOR, Statement.RETURN_GENERATED_KEYS);

			int count = 0;
			for( Document doc : docs ){
				for( Author aut : doc.getAuthors() ){
					stmt.setLong(1, doc.getId());
					stmt.setLong(2,aut.getId());
					stmt.addBatch();

					if (++count % Configuration.getInstance().getDbBatchSize() == 0){
						stmt.executeBatch();
					}
				}
			}
			stmt.executeBatch();
		}catch (Exception e) {
			throw e;
		}
	}

	/**
	 * Insere na lista dada (ids) os id's gerados automaticamente
	 * pelo banco de dados.
	 * @param ids lista com id's gerados automaticamente.
	 * @param generatedKeys {@link ResultSet} resultante da inserção.
	 * @throws SQLException erro ao recuperar id's do {@link ResultSet}.
	 */
	private void getGeneratedKeys(List<Long> ids, ResultSet generatedKeys) throws SQLException {
		while (generatedKeys.next()){
			ids.add(generatedKeys.getLong(1));
		}
	}

	/**
	 * Remove documento da base de dados.
	 * @param id id do documento a ser removido.
	 * @throws Exception erro ao executar remoção.
	 */
	public void deleteDocument(long id) throws Exception {
		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(DELETE_DOC);
			stmt.setLong(1, id);
			stmt.executeUpdate();
		}catch( Exception e){
			throw e;
		}
	}

	/**
	 * Adiciona uma referência ao banco de dados.
	 * <p>Este método irá adicionar o documento de referência
	 * caso não exista no bando de dados e adicionar citação
	 * docId -&gt; ref</p>
	 * @param docId id do documento que faz a citação.
	 * @param ref documento citado.
	 * @throws Exception erro ao executar inserção.
	 */
	public void addReference(long docId, Document ref) throws Exception {
		//Insere referencia do banco de dados
		long refId = addDocument(ref);
		if ( refId > 0){
			// Em caso de sucesso, adiciona citação
			try ( Connection conn = db.getConnection();){
				PreparedStatement stmt = conn.prepareStatement(INSERT_REFERENCE);
				stmt.setLong(1, docId);
				stmt.setLong(2, refId);
				stmt.executeUpdate();
			}catch( Exception e){
				throw e;
			}
		}
	}

	/**
	 * Adiciona todos as referência de um documento ao banco de dados. 
	 * @param docId id do documento que faz a citação
	 * @param refs documentos citados. 
	 * @throws Exception erro ao executar inserção.
	 */
	public void addReferences(long docId, List<Document> refs) throws Exception {
		long[] refIds = addDocuments(refs);
		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(INSERT_REFERENCE);
			for(int i = 0; i < refIds.length; i++){
				if ( refIds[i] > 0){
					stmt.setLong(1, docId);
					stmt.setLong(2, refIds[i]);
					stmt.addBatch();
				}
			}
			stmt.executeBatch();
		}catch( Exception e){
			throw e;
		}
	}

	/**
	 * Retorna mapa com contagem do numero de documentos que um termo ocorre, 
	 * ou seja, numero de documentos que um termo apareceu.
	 * @param where parte da consultam em SQL indicando id's do documentos a serem
	 * considerados ou <code>null</code> para todos os documentos.
	 * @param minNumberOfTerms
	 * @param maxNumberOfTerms 
	 * @return mapa de termos ordenados pela contagem absoluta.
	 * @throws Exception
	 */
	private Map<String, Integer> getTermsCounts(String where) throws Exception {
		try ( Connection conn = db.getConnection();){

			String sql = "SELECT word,ndoc FROM ts_stat('SELECT tsv FROM documents d";
			if ( where != null && !where.isEmpty() )
				sql += where;
			sql += String.format("') WHERE nentry > %d AND nentry < %d AND ndoc > %d AND ndoc < %d", 
					Configuration.getInstance().getMinimumNumberOfEntries(), 
					Configuration.getInstance().getMaximumNumberOfEntries(),
					Configuration.getInstance().getMinimumNumberOfDocuments(),
					Configuration.getInstance().getMaximumNumberOfDocuments());

			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql);
			Map<String,Integer> termsCount = new HashMap<>();
			while( rs.next() ){
				String term = rs.getString("word");
				int ndoc = rs.getInt("ndoc");
				termsCount.put(term, ndoc);
			}
			return termsCount;

		}catch( Exception e){
			throw e;
		}
	}

	/**
	 * Constroi matrix de frequência de termos (bag of words). 
	 * @param termsCount mapa com os termos ordenados por frequência. 
	 * @param termsToColumnMap mapa de termos para indice da coluna na matrix de frequência.
	 * @param where clause WHERE em SQL para filtragem de documentos por id's.
	 * caso contrário a frequência absoluta é considerada.
	 * @param docIds
	 * @throws Exception erro ao executar consulta.
	 */
	private Matrix buildFrequencyMatrix(int numberOfDocs, int numTerms, Map<String, Integer> termsToColumnMap,
			String where, TFIDF tfidfCalc, List<Long> docIds) throws Exception {		
		try ( Connection conn = db.getConnection();){			
			String sql = "SELECT d.doc_id, freqs FROM documents d INNER JOIN documents_data da "
					+ "ON d.doc_id = da.doc_id";
			if ( where != null)
				sql += where;
			sql += " ORDER BY da.rank DESC, d.doc_id";

			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql);

			// Numero de documentos			
			ObjectMapper mapper = new ObjectMapper();

			if (docIds != null )
				docIds.clear();
			else
				docIds = new ArrayList<>(numberOfDocs);

			Matrix matrix = new SparseMatrix();
			while( rs.next() ){
				long docId = rs.getLong(1);
				String terms = rs.getString(2);
				docIds.add(docId);

				if ( terms != null && !terms.isEmpty() ){

					List<Map<String,Object>> t = mapper.readValue(terms, 
							new TypeReference<List<Map<String,Object>>>(){});
					ArrayList<Pair> values = new ArrayList<>();					
					for(Map<String,Object> o : t){
						String term = (String) o.get("word");
						if ( termsToColumnMap.containsKey(term)){
							double freq = ((Number) o.get("nentry")).doubleValue();

							double tfidf = tfidfCalc.calculate(freq, (int) numberOfDocs, term);
							if ( freq != 0 )
								tfidf += Math.log(freq);

							if ( tfidf != 0){
								int col = termsToColumnMap.get(term);
								values.add(new Pair(col, (float) tfidf)); 								
							}
						}
					}

					matrix.addRow(new SparseVector(values, Long.toString(docId), 1.0f, numTerms));
				}
			}

			return matrix;
		}catch( Exception e){
			throw e;
		}
	}

	/**
	 * Constroi matrix de frequência de termos (bag of words). 
	 * @param matrix matrix de frequência de termos já inicializada
	 * @param termsCount mapa com os termos ordenados por frequência. 
	 * @param termsToColumnMap mapa de termos para indice da coluna na matrix de frequência.
	 * @param where clause WHERE em SQL para filtragem de documentos por id's.
	 * caso contrário a frequência absoluta é considerada.
	 * @param docIds 
	 * @throws Exception erro ao executar consulta.
	 */
	private Matrix buildFrequencyMatrixFromTSV(int numberOfDocs, int numTerms, Map<String, Integer> termsToColumnMap,
			String where, TFIDF tfidfCalc, List<Long> docIds) throws Exception {
		try ( Connection conn = db.getConnection();){

			String sql = String.format(SELECT_TERMS_FREQ_FROM_TSV, where != null ? where : "");
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql);

			// Numero de documentos
			if (docIds != null )
				docIds.clear();
			else
				docIds = new ArrayList<>(numberOfDocs);

			Matrix matrix = new SparseMatrix();
			while( rs.next() ){
				long docId = rs.getLong(1);
				Map<String, String> terms = parseTSV(rs.getString(2));				

				if ( terms != null && !terms.isEmpty() ){
					ArrayList<Pair> values = new ArrayList<>();

					for(String term : terms.keySet()){

						if ( termsToColumnMap.containsKey(term)){
							String value = terms.get(term);
							// Divide valores para contagem de termos e pesos
							float[] freqWeight = splitValue(value);
							double tfidf = tfidfCalc.calculate(freqWeight[1], numberOfDocs, term);
							int col = termsToColumnMap.get(term);
							values.add(new Pair(col, (float) tfidf));							
						}	
					}
					matrix.addRow(new SparseVector(values, Long.toString(docId), 1.0f, numTerms));
					docIds.add(docId);
				}
			}		

			return matrix;
		}catch( Exception e){
			throw e;
		}
	}

	private Map<String, String> parseTSV(String tsv) {
		if ( tsv != null && !tsv.trim().isEmpty()){
			return Arrays.stream(tsv.split("\\s+"))
					.map((s) -> s.split(":"))
					.collect(Collectors.toMap((e) -> e[0].replace("'", ""), (e) -> e[1]));
		}
		return null;
	}

	private float[] splitValue(String value) {
		String[] f = value.split(",");
		Float[] weigths = Configuration.getInstance().getWeights();
		float weight = 0;
		for(String s : f){
			Matcher m = TSV_VALUE_PATTERN.matcher(s);
			if ( m.matches() ){
				switch (m.group(1)) {
				case "A":
					weight += weigths[0];
					break;
				case "B":
					weight += weigths[1];
					break;
				case "C":
					weight += weigths[2];
					break;
				case "D":
					weight += weigths[3];
					break;
				default:
					weight += weigths[0];
					break;
				}
			}
		}

		return new float[]{f.length, weight};
	}

	/**
	 * Atualiza projeção dos documentos
	 * @param docIds matrix 1D (vetor) com docId's.
	 * @param proj matrix de projeção N x 2, onde N é o 
	 * número de documentos.
	 * @throws Exception erro ao executar atualização.
	 */
	public void updateXYProjections(List<Long> docIds, float[] proj) throws Exception {
		Connection conn = null;
		try { 
			conn = db.getConnection();
			conn.setAutoCommit(false);

			PreparedStatement pstmt = conn.prepareStatement(UPDATE_XY);

			int size = proj.length / 2;
			int doc = 0;
			for(int i = 0; i < docIds.size(); i++){
				long id = (long) docIds.get(i);
				pstmt.setDouble(1, proj[doc]);
				pstmt.setDouble(2, proj[doc + size]);
				pstmt.setLong(3, id);
				pstmt.addBatch();
				++doc;

				if ( doc % Configuration.getInstance().getDbBatchSize() == 0)
					pstmt.executeBatch();
			}

			pstmt.executeBatch();
			conn.commit();

		}catch( Exception e){
			if ( conn != null )
				conn.rollback();
			throw e;
		}finally {
			if ( conn != null )
				conn.close();
		}
	}

	/**
	 * Retorna grafo de citação
	 * @param type {@link DatabaseService#AUTHORS_GRAPH} ou {@link DatabaseService#DOCUMENTS_GRAPH}.ß¬
	 * @return grafo direcionado com citações.
	 * @throws Exception erro ao executar consulta.
	 */
	public DirectedGraph<Long,Long> getCitationGraph(int type) throws Exception {

		try ( Connection conn = db.getConnection();){
			Statement stmt = conn.createStatement();
			String sql;
			if (type == DOCUMENTS_GRAPH)
				sql = DOCUMENTS_GRAPH_SQL;
			else if ( type == AUTHORS_GRAPH)
				sql = AUTHROS_GRAPH_SQL;
			else
				throw new IllegalArgumentException("Unkown graph type: " + type);

			ResultSet rs = stmt.executeQuery(sql);

			DirectedGraph<Long, Long> graph = new DirectedSparseGraph<>();
			long e = 0;
			while( rs.next() ){
				long source = rs.getLong(1);
				long target = rs.getLong(2);

				if ( !graph.containsVertex(source))
					graph.addVertex(source);
				if ( !graph.containsVertex(target))
					graph.addVertex(target);

				graph.addEdge(e, source, target);
				++e;
			}
			return graph;

		}catch( Exception e){
			throw e;
		}
	}

	/**
	 * Retorna grafo de citação
	 * @return grafo direcionado com citações.
	 * @throws Exception erro ao executar consulta.
	 */
	public DirectedGraph<Document,Edge<Long>> getDocumentsGraph() throws Exception {

		try ( Connection conn = db.getConnection();){
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(DOCS_GRAPH_SQL);

			DirectedGraph<Document,Edge<Long>> graph = new DirectedSparseGraph<>();
			while( rs.next() ){
				long docId = rs.getLong(1);
				long refId = rs.getLong(2);

				Document doc = createDocument(docId, rs, 3);
				Document ref = createDocument(refId, rs, 10);

				if ( !graph.containsVertex(doc))
					graph.addVertex(doc);
				if ( !graph.containsVertex(ref))
					graph.addVertex(ref);

				graph.addEdge(new Edge<Long>(docId, refId), doc, ref);
			}
			return graph;

		}catch( Exception e){
			throw e;
		}
	}

	//d.doc_id, r.doc_id, d.doi, d.title, d.keywords, d.publication_date, "
	//	+ "dd.x, dd.y, dd.relevance, "
	//	+ "r.doi, r.title, r.keywords, r.publication_date, rr.x, rr.y, rr.relevance "
	private Document createDocument(long docId, ResultSet rs, int i) throws SQLException {
		Document doc = new Document();
		doc.setId( docId );
		doc.setDOI( rs.getString(i++) );
		doc.setTitle( rs.getString(i++) );
		doc.setKeywords(rs.getString(i++));
		doc.setPublicationDate(rs.getString(i++));
		doc.setX(rs.getFloat(i++));
		doc.setY(rs.getFloat(i++));
		doc.setRank(rs.getFloat(i));
		return doc;
	}

	/**
	 * Retorna grafo de citação
	 * @return grafo direcionado com citações.
	 * @throws Exception erro ao executar consulta.
	 */
	public DirectedGraph<Author, Edge<Long>> getAuthorsGraph() throws Exception {

		try ( Connection conn = db.getConnection();){
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(AUTHORS_GRAPH_SQL);

			DirectedGraph<Author, Edge<Long>> graph = new DirectedSparseGraph<>();
			while( rs.next() ){
				long source = rs.getLong(1);
				String sName = rs.getString(2);
				float sRank = rs.getFloat(3);
				long target = rs.getLong(4);
				String tName = rs.getString(5);
				float tRank = rs.getFloat(3);

				Author sAuthor = new Author(sName);
				sAuthor.setId(source);
				sAuthor.setRank(sRank);

				if ( !graph.containsVertex(sAuthor))
					graph.addVertex(sAuthor);

				Author tAuthor = new Author(tName);
				tAuthor.setId(target);
				tAuthor.setRank(tRank);

				if ( !graph.containsVertex(tAuthor))
					graph.addVertex(tAuthor);

				Edge<Long> e = graph.findEdge(sAuthor, tAuthor); 
				if ( e == null )
					graph.addEdge(new Edge<Long>(source, target), sAuthor, tAuthor);
				else
					e.weight++;
			}
			return graph;

		}catch( Exception e){
			throw e;
		}
	}

	/**
	 * Atualiza relevância dos documentos.
	 * @param graph grafo de citações.
	 * @param pageRank relevância dos documentos (pagerank).	
	 * @param type {@link DatabaseService#AUTHORS_GRAPH} ou {@link DatabaseService#DOCUMENTS_GRAPH}.
	 * @throws Exception erro ao executar atualização.
	 */
	public void updatePageRank(DirectedGraph<Long, Long> graph, PageRank<Long,Long> pageRank, int type) throws Exception {
		Connection conn = null;
		try { 
			conn = db.getConnection(); 
			conn.setAutoCommit(false);

			String sql;
			if ( type == DOCUMENTS_GRAPH )
				sql = UPDATE_RELEVANCE_DOCUMENTS;
			else if (type == AUTHORS_GRAPH)
				sql = UPDATE_RELEVANCE_AUTHORS;
			else
				return;

			PreparedStatement pstmt = conn.prepareStatement(sql);
			int i = 0;
			final int batchSize = Configuration.getInstance().getDbBatchSize();
			for(Long docId : graph.getVertices()){
				pstmt.setDouble(1, pageRank.getVertexScore(docId));
				pstmt.setLong(2, docId);
				pstmt.addBatch();
				++i;

				if (i % batchSize == 0)
					pstmt.executeBatch();
			}

			pstmt.executeBatch();
			conn.commit();

		}catch( Exception e){
			if ( conn != null )
				conn.rollback();
			throw e;
		}finally {
			if ( conn != null )
				conn.close();
		}
	}

	public Map<Long, List<Long>> getReferences(List<Long> docIds) throws Exception {
		try ( Connection conn = db.getConnection();){
			String sql = "SELECT doc_id, ref_id FROM citations";
			if ( docIds != null ){
				StringBuilder sb = new StringBuilder();
				sb.append(docIds.get(0));
				for(int i = 1; i < docIds.size(); i++){
					sb.append(",");
					sb.append(docIds.get(i));
				}
				sql = sql + " WHERE doc_id IN(" + sb.toString() + ") AND ref_id IN(" + sb.toString() + ")";
			}
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(sql.toString());

			Map<Long, List<Long>> map = new HashMap<>();
			while( rs.next() ){
				long docId = rs.getInt(1);
				long refId = rs.getLong(2);
				List<Long> refs = map.get(docId);
				if ( refs == null){
					refs = new ArrayList<>();
					map.put(docId, refs);
				}
				refs.add(refId);
			}
			return map;
		}catch( Exception e){
			throw e;
		}
	}

	/**
	 * Retorna documentos que satisfazem a consulta especificada.
	 * @param querySearch {@link String} com consulta para ts_query
	 * @param densities a {@link List} contendo {@link Vec2} pontos para
	 * mapa de densidade (excluindo-se os primeiro <code>maxDocs</code>). 
	 * @param maxDocs numero máximo de documentos a serem retornados.
	 * @param page numero de pagina (paginacao).
	 * @return uma {@link List} com {@link IDocument} encontrados.
	 * @throws Exception error ao recuperar documentos
	 */
	public List<IDocument> getSimpleDocuments(String querySearch,
			List<Vec2> densities, int maxDocs, int page) throws Exception {

		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(SEARCH_SQL);

			stmt.setString(1, querySearch); // consulta TS_QUERY
			stmt.setInt(2, page * maxDocs); //OFFSET			

			try (ResultSet rs = stmt.executeQuery()){

				List<IDocument> docs  = new ArrayList<>(maxDocs);
				boolean next = rs.next();
				for(int i = 0; i < maxDocs && next; i++){
					Document doc = newSimpleDocument( rs );
					docs.add(doc);
					next = rs.next();
				}

				while( next ){
					float x = rs.getFloat(6),
							y = rs.getFloat(7);
					densities.add(new Vec2(x, y));
					next = rs.next();
				}

				return docs;
			}catch (SQLException e) {
				throw e;
			}
		}catch( Exception e){
			throw e;
		}
	}	

	/**
	 * Retorna todos os documentos na base
	 * @return a {@link List} com todos os {@link IDocument} na base de dados.
	 * @throws Exception erro ao acessar banco de dados
	 */
	public List<IDocument> getAllSimpleDocuments() throws Exception {
		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(SEARCH_SQL_ALL);
			stmt.setInt(1, 0);
			try (ResultSet rs = stmt.executeQuery()){
				List<IDocument> docs = new ArrayList<>();
				while ( rs.next() ){
					IDocument doc = newSimpleDocument( rs );
					docs.add(doc);
				}
				return docs;
			}catch (SQLException e) {
				throw e;
			}
		}catch( Exception e){
			throw e;
		}
	}

	public List<Vec2> loadXY(float x1, float y1, float x2, float y2) throws Exception {
		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(SEARCH_SQL_ALL_MAX_RANK);
			//			stmt.setFloat(1, maxRank);
			stmt.setFloat(1, x1);
			stmt.setFloat(2, x2);
			stmt.setFloat(3, y1);
			stmt.setFloat(4, y2);

			try (ResultSet rs = stmt.executeQuery()){
				List<Vec2> points = new ArrayList<>();
				while ( rs.next() ){
					Vec2 point = new Vec2(rs.getFloat(1), rs.getFloat(2));
					points.add(point);
				}
				return points;
			}catch (SQLException e) {
				throw e;
			}
		}catch( Exception e){
			throw e;
		}
	}

	public List<Vec2> loadXY(List<IDocument> documents, float x1, float y1, float x2, float y2) throws Exception {
		try ( Connection conn = db.getConnection();){

			StringBuilder sql = new StringBuilder(LOAD_XY_SQL);
			if ( documents != null && !documents.isEmpty()){
				sql.append(" AND doc_id NOT IN (");
				for(IDocument doc : documents){
					sql.append(doc.getId());
					sql.append(",");
				}
				sql.delete(sql.length()-1, sql.length());
				sql.append(")");
			}

			PreparedStatement stmt = conn.prepareStatement(sql.toString());
			stmt.setFloat(1, x1);
			stmt.setFloat(2, x2);
			stmt.setFloat(3, y1);
			stmt.setFloat(4, y2);

			try (ResultSet rs = stmt.executeQuery()){
				List<Vec2> points = new ArrayList<>();
				while ( rs.next() ){
					Vec2 point = new Vec2(rs.getFloat(1), rs.getFloat(2));
					points.add(point);
				}
				return points;
			}catch (SQLException e) {
				throw e;
			}
		}catch( Exception e){
			throw e;
		}
	}

	public List<Document> getSimpleDocuments(Long[] docIds) throws Exception {
		try ( Connection conn = db.getConnection();){
			String sql = String.format(SEARCH_SQL_DOC_IDS,  
					Arrays.toString(docIds).replaceAll("\\[", "(").replaceAll("\\]", ")"));
			PreparedStatement stmt = conn.prepareStatement(sql);

			try (ResultSet rs = stmt.executeQuery()){
				List<Document> docs = new ArrayList<>();
				while ( rs.next() ){
					Document doc = newSimpleDocument( rs );
					docs.add(doc);
				}
				return docs;
			}catch (SQLException e) {
				throw e;
			}
		}catch( Exception e){
			throw e;
		}
	}

	public List<IDocument> getAllSimpleDocuments(List<Vec2> densities, int maxDocs, int page) throws Exception {

		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(SEARCH_SQL_ALL);
			stmt.setInt(1, page * maxDocs); //OFFSET		
			long start = System.nanoTime();			
			try (ResultSet rs = stmt.executeQuery()){			
				long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
				System.out.println("Exec: " + elapsed);

				List<IDocument> docs = new ArrayList<>(maxDocs);
				boolean next = rs.next();
				start = System.nanoTime();
				for(int i = 0; i < maxDocs && next; i++){
					Document doc = newSimpleDocument( rs );
					docs.add(doc);
					next = rs.next();
				}
				elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
				System.out.println("Docs: " + elapsed);

				// Documentos para mapa de densidade
				start = System.nanoTime();
				while( next ){
					float x = rs.getFloat(6),
							y = rs.getFloat(7);
					densities.add(new Vec2(x, y));
					next = rs.next();
				}
				elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
				System.out.println("Points: " + elapsed);

				return docs;

			}catch (SQLException e) {
				throw e;
			}
		}catch( Exception e){
			throw e;
		}

	}

	public List<IDocument> getAdvancedSimpleDocuments(String querySearch, String authors, String yearStart, 
			String yearEnd, List<Vec2> densities, int maxDocs, int page ) throws Exception {
		try ( Connection conn = db.getConnection();){

			StringBuilder where = new StringBuilder();

			// Adiciona SQL para busca por autores			
			if ( authors != null && !authors.isEmpty() ){
				where.append(ADVANCED_SEARCH_AUTHORS_INNER_JOIN_SQL);
			}

			// Adiciona SQL para busca por termos no documento
			if ( querySearch != null )
				where.append(", to_tsquery(?) query WHERE query @@ tsv AND d.enabled is TRUE ");			
			else
				where.append(" WHERE d.enabled is TRUE ");		

			if ( !yearStart.isEmpty() )
				where.append(" AND publication_date >= ? ");
			if ( !yearEnd.isEmpty() )
				where.append(" AND publication_date <= ? ");

			PreparedStatement stmt = conn.prepareStatement(
					String.format(ADVANCED_SEARCH_SQL, where.toString()));

			// Adiciona parâmetros
			int index = 1;			
			// String de buca dos autores
			if ( !authors.isEmpty() )
				stmt.setString(index++, authors);			

			// String de busca por documentos
			if (querySearch != null)
				stmt.setString(index++, querySearch);			

			// Ano
			if ( !yearStart.isEmpty() )
				stmt.setInt(index++, Integer.parseInt(yearStart));
			if ( !yearEnd.isEmpty() )
				stmt.setInt(index++, Integer.parseInt(yearEnd));

			//Offset
			stmt.setInt(index, page * maxDocs);			

			try (ResultSet rs = stmt.executeQuery()){
				List<IDocument> docs = new ArrayList<IDocument>(maxDocs);
				boolean next = rs.next();
				for(int i = 0; i < maxDocs && next; i++){
					Document doc = newSimpleDocument( rs );
					docs.add(doc);
					next = rs.next();
				}

				while( next ){
					float x = rs.getFloat(6),
							y = rs.getFloat(7);
					densities.add(new Vec2(x, y));
					next = rs.next();
				}

				return docs;
			}catch (SQLException e) {
				throw e;
			}
		}catch( Exception e){
			throw e;
		}
	}

	private Document newSimpleDocument(ResultSet rs) throws SQLException, JsonParseException, JsonMappingException, IOException {
		//		d.doc_id, d.doi, d.title, d.keywords, d.publication_date, "
		//		dd.x, dd.y, dd.rank, d.authors, d.bibtex, d.words
		Document doc = new Document();
		doc.setId( rs.getLong(1) );
		doc.setDOI( rs.getString(2) );
		doc.setTitle( rs.getString(3) );
		doc.setKeywords(rs.getString(4));
		doc.setPublicationDate(rs.getString(5));
		doc.setX(rs.getFloat(6));
		doc.setY(rs.getFloat(7));
		doc.setRank(rs.getFloat(8));
		doc.setAuthors(Utils.getAuthors(rs.getString(9)));
		doc.setBibTEX(rs.getString(10));

		// JSON parser das M palavras mais relevantes
		String terms = rs.getString(11);
		if ( terms != null && !terms.isEmpty() ){
			ObjectMapper mapper = new ObjectMapper();
			List<Map<String,Object>> t = mapper.readValue(terms, 
					new TypeReference<List<Map<String,Object>>>(){});
			int maxNumberOfWords = Math.min(Configuration.getInstance().getNumberOfMostFrequentWords(), t.size());
			if(maxNumberOfWords > 0) {
				Word[] words = new Word[maxNumberOfWords];				
				for(int i = 0; i < maxNumberOfWords; i++){
					Map<String,Object> o = t.get(i);
					String term = (String) o.get("word");				
					int count = ((Number) o.get("nentry")).intValue();					
					words[i] = new Word(term, count);					
				}			
				doc.setWords(words);
			}
		}

		return doc;
	}	

	public List<IDocument> getDocumentsFromNode(long nodeId, Bounds rectangle, String query, int offset, int limit) throws Exception{
		List<IDocument> docs = new ArrayList<>();
		try (Connection conn = db.getConnection();) {
			StringBuilder sb = new StringBuilder();
			if ( query == null) {
				sb.append(DOCUMENTS_NODE_SQL);
				if ( rectangle != null )
					sb.append(" AND x >= ? AND x <= ? AND y >= ? AND y <= ?");
				sb.append(" ORDER BY rank DESC");
				if (limit != -1)
					sb.append(" LIMIT ? OFFSET ?");
			}
			else{
				sb.append(PRE_DOCUMENTS_NODE_SQL);
				sb.append(DOCUMENTS_NODE_SQL);
				if ( rectangle != null )
					sb.append(" AND x >= ? AND x <= ? AND y >= ? AND y <= ?");				
				if (limit != -1)
					sb.append(" LIMIT ? OFFSET ?");				
				sb.append(POST_DOCUMENTS_NODE_SQL);
			}

			PreparedStatement stmt = conn.prepareStatement(sb.toString());					
			stmt.setLong(1, nodeId);

			int pIndex = 1;
			if ( rectangle != null ) {
				stmt.setFloat(++pIndex, rectangle.getP1().x);
				stmt.setFloat(++pIndex, rectangle.getP2().x);
				stmt.setFloat(++pIndex, rectangle.getP1().y);
				stmt.setFloat(++pIndex, rectangle.getP2().y);
			}			

			if ( limit != -1)
				stmt.setInt(++pIndex, limit);			

			if ( offset != -1)
				stmt.setInt(++pIndex, offset);			

			if ( query != null)										
				stmt.setString(++pIndex, query);			

			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) { 
					Document doc = newSimpleDocument(rs);
					docs.add(doc);
				}
				return docs;
			} catch (SQLException e) {
				throw e;
			}
		} catch (Exception e) {
			throw e;
		}
	}

	public List<IDocument> getDocumentsFromNode(long nodeId, Bounds rectangle, String query) throws Exception{
		return getDocumentsFromNode(nodeId, rectangle, query, -1, -1);
	}

	public QuadTree loadQuadTree(QuadTree qTree) throws Exception {

		try (Connection conn = db.getConnection();) {
			String sql = NODE_DATA_SQL;
			PreparedStatement stmt = conn.prepareStatement(sql);
			HashMap<Integer, QuadTreeNode> nodesMap = new HashMap<>();
						
			try (ResultSet rs = stmt.executeQuery()) {
				
				int leafCount = 0, branchCount = 0, treeDepth = 1;				
				while (rs.next()) {
					int node_id = rs.getInt(1);
					boolean isLeaf = rs.getBoolean(2);
					float rankMax = rs.getFloat(3);
					float rankMin = rs.getFloat(4);
					int parent_id = rs.getInt(5);
					if ( rs.wasNull() )
						parent_id = -1;
					int depth = rs.getInt(6);
					int index = rs.getInt(7);

					if ( depth > treeDepth )
						treeDepth = depth;

					QuadTreeBranchNode parent = parent_id < 0 ? null : (QuadTreeBranchNode) nodesMap.get(parent_id);
					QuadTreeNode node;
					if (isLeaf) {
						node = new QuadTreeLeafNode(depth, index, node_id, rankMax, rankMin, parent);
						++leafCount;					
					} else {
						node = new QuadTreeBranchNode(depth, index, node_id, rankMax, rankMin, parent);
						++branchCount;
					}
					nodesMap.put(node_id, node);


					if (parent != null) {
						parent.setChild(index, nodesMap.get(node_id));
					}
				}
				qTree.setRoot((QuadTreeBranchNode) nodesMap.get(0));
				qTree.setDepth(treeDepth);
				qTree.setBranchCount(branchCount);
				qTree.setLeafCount(leafCount);				
			}
						
			conn.close();
		} catch (Exception e) {
			System.out.println(e.getMessage());
			throw e;
		}

		return qTree;
	}

	public boolean persistQuadTree(QuadTree quadTree) throws Exception {
		boolean result = true;
		try (Connection conn = db.getConnection();) {			

			// Remove nodes (QuadTree)
			PreparedStatement stmt = conn.prepareStatement(DELETE_NODE_DATA_SQL);
			stmt.execute();
			stmt.close();

			// Insere novos nodes
			stmt = conn.prepareStatement(INSERT_NODE_SQL);								

			List<QuadTreeNode> nodes = new ArrayList<>(quadTree.getBranchCount() + quadTree.getLeafCount());
			List<QuadTreeLeafNode> leaves = new ArrayList<>(quadTree.getLeafCount());
			nodes.add(quadTree.getRoot());
			int count = 0;
			final int batchSize = Configuration.getInstance().getDbBatchSize();

			while (!nodes.isEmpty()) {
				QuadTreeNode node = nodes.remove(0);

				//INSERT INTO nodes( node_id, isleaf, rankmax, rankmin, parent_id, depth, index)
				stmt.setLong(1, node.getNodeId());
				stmt.setBoolean(2, node.isLeaf());
				stmt.setFloat(3, node.getRankMax());
				stmt.setFloat(4, node.getRankMin());
				
				long pId = node.getParentNodeId();
				if ( pId != -1 )
					stmt.setLong(5, pId);
				else
					stmt.setNull(5, Types.BIGINT);
				
				stmt.setInt(6, node.getDepth());
				stmt.setInt(7, node.getIndex());

				stmt.addBatch();
				++count;

				if (count % batchSize == 0)
					stmt.executeBatch();

				// Insere child nodes na fila de insercao
				if (!node.isLeaf()) {
					QuadTreeBranchNode branch = (QuadTreeBranchNode) node;
					for (int i = 0; i < 4; i++) {
						if (branch.getChild(i) != null) {
							nodes.add(branch.getChild(i));
						}
					}				
				}
				// Guarda folhas para atualizar documents_data(node_id)
				else
					leaves.add((QuadTreeLeafNode) node);
			}			
			stmt.executeBatch();
			stmt.close();

			// Atualiza tabela documents_data com node_id							
			PreparedStatement leafStmt = conn.prepareStatement(UPDATE_DOC_DATA_NODE_ID_SQL);
			for(QuadTreeLeafNode leaf : leaves) {
				for (int i = 0; i < leaf.size(); i++) {
					IDocument d = leaf.getDocument(i);
					leafStmt.setLong(1, leaf.getNodeId());
					leafStmt.setLong(2, d.getId());											
					leafStmt.addBatch();

					if ( i % batchSize == 0){
						leafStmt.executeBatch();
					}
				}
			}

			leafStmt.executeBatch();	
			leafStmt.close();

		} catch (Exception e) {			
			System.out.println(e.getMessage());
			throw e;
		}
		return result;
	}

	public void disableDocuments(long[] docIds) throws SQLException {
		Connection conn = null;
		try { 
			conn = db.getConnection();
			conn.setAutoCommit(false);

			PreparedStatement pstmt = conn.prepareStatement("UPDATE documents SET enabled = FALSE WHERE doc_id = ?");

			int doc = 0;
			for(int i = 0; i < docIds.length; i++){
				long id = docIds[i];
				pstmt.setDouble(1, id);
				pstmt.addBatch();
				++doc;

				if ( doc % Configuration.getInstance().getDbBatchSize() == 0)
					pstmt.executeBatch();
			}

			pstmt.executeBatch();
			conn.commit();

		}catch( Exception e){
			if ( conn != null )
				conn.rollback();
			throw e;
		}finally {
			if ( conn != null )
				conn.close();
		}
	}

	public void calPageRank(int type, double alpha) throws SQLException {
		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt; 
			if ( type == DatabaseService.DOCUMENTS_GRAPH)
				stmt = conn.prepareStatement(PAGE_RANK_SQL_FUNC_DOCS);
			else
				stmt = conn.prepareStatement(PAGE_RANK_SQL_FUNC_AUTHORS);
			stmt.setDouble(1, alpha);
			stmt.execute();

		}catch (SQLException e) {
			throw e;
		}
	}

	public void updateDocumentsRank() throws SQLException {
		try ( Connection conn = db.getConnection();){
			PreparedStatement stmt = conn.prepareStatement(UPDATE_DOCUMENTS_RANK);
			stmt.setDouble(1, Configuration.getInstance().getDocumentRelevanceFactor());
			stmt.setDouble(2, Configuration.getInstance().getAuthorsRelevanceFactor());
			stmt.execute();
		}catch (SQLException e) {
			throw e;
		}
	}


}
