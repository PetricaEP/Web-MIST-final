package ep.db.pagerank;

import java.io.Serializable;

public class Edge<T> implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 7508079864656378384L;

	public T source;
	
	public T target;
	
	public float weight = 1;
	
	
	public Edge(T source, T target) {
		this.source = source;
		this.target = target;
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((source == null) ? 0 : source.hashCode());
		result = prime * result + ((target == null) ? 0 : target.hashCode());
		return result;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Edge<T> other = (Edge<T>) obj;
		if (source == null) {
			if (other.source != null)
				return false;
		} else if (!source.equals(other.source))
			return false;
		if (target == null) {
			if (other.target != null)
				return false;
		} else if (!target.equals(other.target))
			return false;
		return true;
	}
	
	
}
