package ep.db.database_test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import ep.db.database.DatabaseService;
import ep.db.database.DefaultDatabase;
import ep.db.matrix.Matrix;
import ep.db.tfidf.InverseDocumentFrequencyTFIDF;
import ep.db.tfidf.TFIDF;
import ep.db.utils.Configuration;

public class TestDatabase {

	public static void main(String[] args) {
		try {

			Configuration config = Configuration.getInstance();			
			if (args.length > 0) {
				File configFile = new File(args[0]);
				config.loadConfiguration(configFile.getAbsolutePath());
			}
			else {			
				config.loadConfiguration();
			}

			DatabaseService dbService = new DatabaseService(new DefaultDatabase(config));
			List<Long> docIds = new ArrayList<>();
			TFIDF tfidf = new InverseDocumentFrequencyTFIDF();
			Matrix matrix = dbService.getDocumentTermMatrix(null, tfidf, docIds);				

		} catch (Exception e) {
			e.printStackTrace(System.err);
		}
	}


}
