package modules;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

import play.Environment;
import play.Logger;
import services.search.DocumentSearcher;

public class Module extends AbstractModule {

	private final Environment environment;
	private final Config configuration;

	public Module(Environment environment,
			Config configuration) {
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
		
		String configFilename = null;
		try {
			configFilename = configuration.getString("ep_db.config_file");
		} catch (ConfigException.Missing | ConfigException.WrongType e) {
			Logger.warn("No ep_db.config_file. Using default. ", e);
		}
		if ( configFilename != null ) {
			File configFile = new File(configFilename);		
			if (!configFile.exists())
				throw new RuntimeException(
					new FileNotFoundException("Cannot find ep-db configuration file: " +configFile.getAbsolutePath()));
		}
		
		ep.db.utils.Configuration config = ep.db.utils.Configuration.getInstance();
		try {
			config.loadConfiguration(configFilename);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		bind(ep.db.utils.Configuration.class)
		.toInstance(config);
		
		Locale.setDefault(Locale.ENGLISH);
	}
}