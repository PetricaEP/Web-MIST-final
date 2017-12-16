//package ep.db.database_test;
//
//import static org.junit.Assert.fail;
//
//import java.io.IOException;
//import java.util.List;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//import org.junit.Assert;
//import org.junit.Test;
//
//import ep.db.database.Database;
//import ep.db.database.DatabaseService;
//import ep.db.database.DefaultDatabase;
//import ep.db.model.Document;
//import ep.db.utils.Configuration;
//
//public class TestDatabase {
//
//	private static final Pattern TERM_PATTERN = Pattern.compile(".*(\".*?\").*");
//	
//	public TestDatabase() {
//		
//	}
//	
//	@Test
//	public void getAllSimpleDocuments(){
//		Configuration config;
//		try {
//			config = Configuration.getInstance()
//					.loadConfiguration();
//		} catch (IOException e) {
//			e.printStackTrace();
//			fail();
//			return;
//		}
//		
//		Database db = new DefaultDatabase(config);
//		DatabaseService dbService = new DatabaseService(db);
//		
//		List<Document> docs;
//		try {
//			docs = dbService.getAllSimpleDocuments();
//		} catch (Exception e) {
//			e.printStackTrace();
//			fail();
//			return;
//		}
//		
//		Assert.assertNotNull(docs);
//		Assert.assertTrue(docs.size() > 0);
//	}
//	
//	@Test
//	public void getSimpleDocuments(){
//		Configuration config;
//		try {
//			config = Configuration.getInstance()
//					.loadConfiguration();
//		} catch (IOException e) {
//			e.printStackTrace();
//			fail();
//			return;
//		}
//		
//		Database db = new DefaultDatabase(config);
//		DatabaseService dbService = new DatabaseService(db);
//		
//		List<Document> docs;
//		try {
//			String query = buildQuery("information retrival");
//			docs = dbService.getSimpleDocuments(query, -1);
//		} catch (Exception e) {
//			e.printStackTrace();
//			fail();
//			return;
//		}
//		
//		Assert.assertNotNull(docs);
//		Assert.assertTrue(docs.size() > 0);
//	}
//	
//	
//	private String buildQuery(String terms) {
//		if ( terms.trim().isEmpty() )
//			return null;
//
//		String op = "|";
//		StringBuilder query = new StringBuilder();
//		// Checa consulta usando padrão para expressões entre aspas:
//		// busca por frases
//		Matcher m = TERM_PATTERN.matcher(terms);
//		while( m.matches() ){
//			// Extrai grupo entre aspas;
//			String term = m.group(1);
//			// Atualiza String de busca removendo grupo
//			// extraido
//			String f = terms.substring(0, m.start(1));
//			String l = terms.substring(m.end(1)); 
//			terms =  f + l;
//
//			// Remove aspas do termo
//			term = term.substring(1, term.length()-1);
//
//			// Divide o term em tokens e adiciona na query (ts_query)
//			// utilizando operador FOLLOWED BY (<->)
//			String[] split = term.split("\\s+");
//
//			// Caso não seja o primeiro termo
//			// adiciona operador OR (|)
//			if ( query.length() > 0)
//				query.append(op);
//
//			query.append("(");
//			query.append(split[0]);
//			for(int i = 1; i < split.length; i++){
//				query.append(" <-> ");
//				query.append(split[i]);
//			}
//			query.append(")");
//
//			// Atualize Matcher
//			m = TERM_PATTERN.matcher(terms);
//		}
//
//		// Ainda tem termos a serem processados?
//		if ( terms.length() > 0){
//
//			// Caso não seja o primeiro termo
//			// adiciona operador OR (|)
//			if ( query.length() > 0)
//				query.append(op);
//
//			String[] split = terms.split("\\s+");
//
//			// O sinal negativo indica exclusão na busca, 
//			// operador negação ! em SQL
//
//			if ( split[0].charAt(0) == '-')
//				query.append("!");
//			query.append(split[0]);
//
//			for(int i = 1; i < split.length; i++){
//				query.append(op);
//				if ( split[i].charAt(0) == '-')
//					query.append("!");
//				query.append(split[i]);
//			}
//		}
//
//		return query.toString();
//	}
//}
