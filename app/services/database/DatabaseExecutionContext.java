package services.database;

import javax.inject.Inject;

import akka.actor.ActorSystem;
import play.libs.concurrent.CustomExecutionContext;


public class DatabaseExecutionContext extends CustomExecutionContext {
	
	private static final String name = "database.dispatcher";
	
	@Inject
	public DatabaseExecutionContext(ActorSystem actorSystem) {
		super(actorSystem, name);
	}
}
