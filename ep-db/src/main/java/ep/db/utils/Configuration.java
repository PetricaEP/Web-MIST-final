package ep.db.utils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import ep.db.database.DatabaseService;

public class Configuration {
	
	/**
	 * Arquivo de configuração
	 */
	public static final String PROP_FILE = "conf/config.properties";

	private static final String DB_HOST = "db.host";

	private static final String DB_NAME = "db.database";

	private static final String DB_PORT = "db.port";

	private static final String DB_PASSWORD = "db.password";

	private static final String DB_USER = "db.user";

	private static final String DB_BATCH_SIZE = "db.batch_size";

	private static final String GROBID_CONFIG = "grobid.properties";

	private static final String MENDELEY_CLIENT_SECRET = "mendeley.client_secret";

	private static final String MENDELEY_HOST = "mendeley.host";

	private static final String MENDELEY_CLIENT_ID = "mendeley.client_id";

	private static final String GROBID_HOME = "grobid.home";

	private static final String MININUM_PERCENT_OF_TERMS = "minimumPercentOfTerms";

	private final Properties properties;

	private final String configFile;

	private String dbHost;

	private String dbName;

	private int dbPort;

	private String dbUser;

	private String mendeleyClientId;

	private String mendeleyHost;

	private String grobidHome;

	private String mendeleyClientSecret;

	private String grobidConfig;

	private int dbBatchSize;

	private String dbPassword;

	private float minimumPercentOfTerms;
	
	public Configuration() throws IOException {
		this(PROP_FILE);
	}
	
	public Configuration(String configFile) throws IOException{
		properties = new Properties();
		this.configFile = configFile;
		loadConfiguration();
	}

	private void loadConfiguration() throws IOException {
		try {
			properties.load(new FileInputStream(configFile));
		} catch (IOException e) {
			throw e;
		}
		
		dbHost = properties.getProperty(DB_HOST);
		dbName = properties.getProperty(DB_NAME);
		dbPort = Integer.parseInt(properties.getProperty(DB_PORT));
		dbUser = properties.getProperty(DB_USER);
		dbPassword = properties.getProperty(DB_PASSWORD);
		dbBatchSize = Integer.parseInt(properties.getProperty(DB_BATCH_SIZE));
		
		grobidHome = properties.getProperty(GROBID_HOME);
		grobidConfig = properties.getProperty(GROBID_CONFIG);
		
		mendeleyClientId = properties.getProperty(MENDELEY_CLIENT_ID);
		mendeleyClientSecret = properties.getProperty(MENDELEY_CLIENT_SECRET);
		mendeleyHost = properties.getProperty(MENDELEY_HOST);
		
		minimumPercentOfTerms = Float.parseFloat(properties.getProperty(MININUM_PERCENT_OF_TERMS));	
		DatabaseService.minimumPercentOfTerms = minimumPercentOfTerms;
	}
	
	public void save() throws IOException{
		properties.store(new FileOutputStream(configFile), null);
	}

	public String getDbHost() {
		return dbHost;
	}

	public void setDbHost(String dbHost) {
		this.dbHost = dbHost;
	}

	public String getDbName() {
		return dbName;
	}

	public void setDbName(String dbName) {
		this.dbName = dbName;
	}

	public int getDbPort() {
		return dbPort;
	}

	public void setDbPort(int dbPort) {
		this.dbPort = dbPort;
	}

	public String getDbUser() {
		return dbUser;
	}

	public void setDbUser(String dbUser) {
		this.dbUser = dbUser;
	}

	public String getMendeleyClientId() {
		return mendeleyClientId;
	}

	public void setMendeleyClientId(String mendeleyClientId) {
		this.mendeleyClientId = mendeleyClientId;
	}

	public String getMendeleyHost() {
		return mendeleyHost;
	}

	public void setMendeleyHost(String mendeleyHost) {
		this.mendeleyHost = mendeleyHost;
	}

	public String getGrobidHome() {
		return grobidHome;
	}

	public void setGrobidHome(String grobidHome) {
		this.grobidHome = grobidHome;
	}

	public String getMendeleyClientSecret() {
		return mendeleyClientSecret;
	}

	public void setMendeleyClientSecret(String mendeleyClientSecret) {
		this.mendeleyClientSecret = mendeleyClientSecret;
	}

	public String getGrobidConfig() {
		return grobidConfig;
	}

	public void setGrobidConfig(String grobidConfig) {
		this.grobidConfig = grobidConfig;
	}

	public int getDbBatchSize() {
		return dbBatchSize;
	}

	public void setDbBatchSize(int dbBatchSize) {
		this.dbBatchSize = dbBatchSize;
	}

	public String getDbPassword() {
		return dbPassword;
	}

	public void setDbPassword(String dbPassword) {
		this.dbPassword = dbPassword;
	}

	public float getMinimumPercentOfTerms() {
		return minimumPercentOfTerms;
	}

	public void setMinimumPercentOfTerms(float minimumPercentOfTerms) {
		this.minimumPercentOfTerms = minimumPercentOfTerms;
		DatabaseService.minimumPercentOfTerms = minimumPercentOfTerms;
	}
}
