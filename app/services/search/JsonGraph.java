package services.search;

import java.io.Serializable;
import java.util.Collection;

public class JsonGraph<V,E> implements Serializable{

	public Collection<V> nodes;
	
	public Collection<E> edges;
	
	public JsonGraph(Collection<V> vertices, Collection<E> edges) {
		this.nodes = vertices;
		this.edges = edges;
	}

}
