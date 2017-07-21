package modules;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import ep.db.database.DatabaseService;
import play.Configuration;
import play.Environment;
import services.search.DocumentSearcher;

public class Module extends AbstractModule {

	private final Environment environment;
	private final Configuration configuration;

	public Module(Environment environment,
			Configuration configuration) {
		this.environment = environment;
		this.configuration = configuration;
	}


	protected void configure() {        
		// Expect configuration like:
		// documentSearcher = services.search.LocalDocumentSearcher
		String bindingClassName = configuration.getString("documentSearcher");
		try {
			Class<? extends DocumentSearcher> bindingClass =
					environment.classLoader().loadClass(bindingClassName)
					.asSubclass(DocumentSearcher.class);

			bind(DocumentSearcher.class)
			.annotatedWith(Names.named("docSearcher"))
			.to(bindingClass);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		File configFile = new File(configuration.getString("ep_db.config_file"));
		if (!configFile.exists())
			throw new RuntimeException(
					new FileNotFoundException("Cannot find ep-db configuration file: " +configFile.getAbsolutePath()));
		ep.db.utils.Configuration config = new ep.db.utils.Configuration(configFile.getAbsolutePath());
		try {
			config.loadConfiguration();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		bind(ep.db.utils.Configuration.class)
		.toInstance(config);
	}
}