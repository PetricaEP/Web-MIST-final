package ep.db.database;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.postgresql.ds.PGSimpleDataSource;

import ep.db.utils.Configuration;

/**
 * Classe para conexão com o banco de dados
 * @version 1.0
 * @since 2017
 *
 */
public class DefaultDatabase implements Database {
	
	private final Configuration config;
	
	private final DataSource ds;

	/**
	 * Configura e inicializa um novo {@link DataSource}
	 * para conexão com o banco de dados
	 * @param configuration configuração
	 */
	public DefaultDatabase(Configuration configuration) {
		this.config = configuration;
		this.ds = initializeDataSource();
	}

	/**
	 * Inicializa {@link DataSource}
	 * @return novo {@link DataSource}
	 */
	private DataSource initializeDataSource() {
		PGSimpleDataSource ds = new PGSimpleDataSource();
		ds.setServerName(config.getDbHost());
		ds.setDatabaseName(config.getDbName());
		ds.setPortNumber(config.getDbPort());
		ds.setUser(config.getDbUser());
		ds.setPassword(config.getDbPassword());
		return ds;
	}

	/**
	 * Retorna uma nova conexão com o banco de dados
	 * @return object da classe {@link Connection}
	 * @throws SQLException caso não seja possível obter uma
	 * conexão.
	 */
	public Connection getConnection() throws SQLException {
		return ds.getConnection();
	}

}
