package ep.db.mdp.dissimilarity;

import org.jblas.FloatMatrix;

public interface Dissimilarity {

	  public float calculate(FloatMatrix v1, FloatMatrix v2);
}
