package ep.db.mdp;

import java.util.ArrayList;

import cern.colt.matrix.tfloat.FloatMatrix2D;
import ep.db.mdp.dissimilarity.Dissimilarity;


public class ForceScheme {

	private static final float EPSILON = 0.0000001f;

	private final float fractionDelta;

	private int[] index;

	public ForceScheme(float fractionDelta, int numberPoints) {
		this.fractionDelta = fractionDelta;
		//Create the indexes and shuffle them
		ArrayList<Integer> index_aux = new ArrayList<Integer>();
		for (int i = 0; i < numberPoints; i++) {
			index_aux.add(i);
		}

		this.index = new int[numberPoints];
		for (int ind = 0, j = 0; j < this.index.length; ind += index_aux.size() / 10, j++) {
			if (ind >= index_aux.size()) {
				ind = 0;
			}

			this.index[j] = index_aux.get(ind);
			index_aux.remove(ind);
		}
	}

	public float iteration(DistanceMatrix dmat, float[][] projection) {
		float error = 0.0f;

		if (projection[0].length == 2) {
			//for each instance
			for (int ins1 = 0; ins1 < projection.length; ins1++) {
				int instance = this.index[ins1];

				//for each other instance
				for (int ins2 = 0; ins2 < projection.length; ins2++) {
					int instance2 = this.index[ins2];

					if (instance == instance2) {
						continue;
					}

					//distance between projected instances
					float x1x2 = (projection[instance2][0] - projection[instance][0]);
					float y1y2 = (projection[instance2][1] - projection[instance][1]);
					float dr2 = (float) Math.sqrt(x1x2 * x1x2 + y1y2 * y1y2);

					if (dr2 < EPSILON) {
						dr2 = EPSILON;
					}

					float drn = dmat.getDistance(instance, instance2);
					float normdrn = (drn - dmat.getMinDistance());

					if(dmat.getMaxDistance() > dmat.getMinDistance()) {
						normdrn = normdrn / (dmat.getMaxDistance() - dmat.getMinDistance());
					}

					//Calculating the (fraction of) delta
					float delta = normdrn - dr2;
					delta *= Math.abs(delta);
					//                delta = (float) Math.sqrt(Math.abs(delta));
					delta /= this.fractionDelta;

					error += Math.abs(delta);

					//moving ins2 -> ins1
					projection[instance2][0] += delta * (x1x2 / dr2);
					projection[instance2][1] += delta * (y1y2 / dr2);
				}
			}

			error /= (projection.length * projection.length) - projection.length;
		} else if (projection[0].length == 3) {
			//for each instance
			for (int ins1 = 0; ins1 < projection.length; ins1++) {
				int instance = this.index[ins1];

				//for each other instance
				for (int ins2 = 0; ins2 < projection.length; ins2++) {
					int instance2 = this.index[ins2];

					if (instance == instance2) {
						continue;
					}

					//distance between projected instances
					float x1x2 = (projection[instance2][0] - projection[instance][0]);
					float y1y2 = (projection[instance2][1] - projection[instance][1]);
					float z1z2 = (projection[instance2][2] - projection[instance][2]);

					float dr3 = (float) Math.sqrt(x1x2 * x1x2 + y1y2 * y1y2 + z1z2 * z1z2);

					if (dr3 < EPSILON) {
						dr3 = EPSILON;
					}

					float drn = dmat.getDistance(instance, instance2);
					float normdrn = (drn - dmat.getMinDistance()) /
							(dmat.getMaxDistance() - dmat.getMinDistance());

					//Calculating the (fraction of) delta
					float delta = normdrn - dr3;
					delta *= Math.abs(delta);
					//                delta = (float) Math.sqrt(Math.abs(delta));
					delta /= this.fractionDelta;

					error += Math.abs(delta);

					//moving ins2 -> ins1
					projection[instance2][0] += delta * (x1x2 / dr3);
					projection[instance2][1] += delta * (y1y2 / dr3);
					projection[instance2][2] += delta * (z1z2 / dr3);
				}
			}

			error /= (projection.length * projection.length) - projection.length;
		}

		return error;
	}

	public float iteration(FloatMatrix2D matrix, Dissimilarity diss, float[][] projection) {
		DistanceMatrix dmat = new DistanceMatrix(matrix, diss);
		return iteration(dmat, projection);
	}
}