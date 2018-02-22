package ep.db.mdp.lamp;

import java.util.Arrays;
import java.util.concurrent.Callable;

import org.jblas.FloatMatrix;
import org.jblas.Singular;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ep.db.mdp.projection.ProjectionData;
import me.tongfei.progressbar.ProgressBar;

class ParallelSolver implements Callable<Integer> {

	private static Logger logger = LoggerFactory.getLogger(ParallelSolver.class);

	private ProjectionData pdata;
	private FloatMatrix matrix;
	private final float[] projection;
	private final float[][] sampledata;
	private final float[][] sampleproj;
	private double epsilon;
	private int begin;
	private int end;
	private ProgressBar pb;

	public ParallelSolver(ProjectionData pdata, float[] projection, float[][] sampledata, float[][] sampleproj,
			FloatMatrix matrix, int begin, int end, ProgressBar pb, double epsilon) {
		this.pdata = pdata;
		this.projection = projection;
		this.sampledata = sampledata;
		this.sampleproj = sampleproj;
		this.matrix = matrix;
		this.begin = begin;
		this.end = end;
		this.pb = pb;
		this.epsilon = epsilon;
	}

	@Override
	public Integer call() throws Exception {
		try {					
			createTransformation();
			return 0;
		}catch (Exception e) {
			logger.error("Error in thread: " + Thread.currentThread().getName() + ". Partition: " + begin + ", " + end, e);
			throw e;
		}
	}

	private void createTransformation() 
	{
		// dimensions
		int len = projection.length / 2;
		int d = matrix.columns;      // origin space: dimension
		int k = sampledata.length;    // sampling:  instances
		int r = sampleproj[0].length;  // projected: dimension
		int n = Math.max((int) (sampledata.length * pdata.getPercentage()), 1); //number neighbors

		// scalars
		float Wsum, aij, w, wsqrt;
		float v00, v10, v01, v11;
		float uj0, uj1, x, y, diff;

		// arrays 1d
		float[] X;
		float[] P, Psum, Pstar;
		float[] Q, Qsum, Qstar;

		Pstar = new float[d];
		Qstar = new float[r];

		//used for limiting the weights
		int[] neighbors_index;
		float[] local_W;

		neighbors_index = new int[n];
		local_W = new float[n];

		// arrays 2d
		FloatMatrix AtB = new FloatMatrix(d,r); 

		// Starting calcs
		int p, i, j, m;
		for (p = begin; p <= end; p++) {
			// point to be projected
			X = matrix.getRow(p).toArray();

			//==============================================================
			// STEP 1: Obtain W, Pstar and Qstar
			//==============================================================
			Psum = new float[d];
			Qsum = new float[r];
			Wsum = 0;
			boolean jump = false;

			//obtaining local W
			Arrays.fill(local_W, Float.POSITIVE_INFINITY);

			for (i = 0; i < k; i++) {
				P = sampledata[i];
				Q = sampleproj[i];

				w = 0;
				for (j = 0; j < d; j++) {
					w += (X[j] - P[j]) * (X[j] - P[j]);
				}

				// coincident points
				if (w < epsilon) {
//					synchronized (projection) {
						projection[p] = Q[0];
						projection[len + p] = Q[1];
//					}
					jump = true;
					break;
				}

				if (w < local_W[n - 1]) {
					for (j = 0; j < n; j++) {
						if (local_W[j] > w) {
							for (m = n - 1; m > j; m--) {
								local_W[m] = local_W[m - 1];
								neighbors_index[m] = neighbors_index[m - 1];
							}

							local_W[j] = w;
							neighbors_index[j] = i;
							break;
						}
					}
				}
			}

			if (jump) {
				continue;
			}

			for (i = 0; i < n; i++) {
				P = sampledata[neighbors_index[i]];
				Q = sampleproj[neighbors_index[i]];

				local_W[i] = 1 / local_W[i];

				for (j = 0; j < d; j++) {
					Psum[j] = Psum[j] + P[j] * local_W[i];
				}

				Qsum[0] = Qsum[0] + Q[0] * local_W[i];
				Qsum[1] = Qsum[1] + Q[1] * local_W[i];

				Wsum = Wsum + local_W[i];
			}

			for (j = 0; j < d; j++) {
				Pstar[j] = Psum[j] / Wsum;
			}

			Qstar[0] = Qsum[0] / Wsum;
			Qstar[1] = Qsum[1] / Wsum;

			//==============================================================
			// STEP 2: Obtain Phat, Qhat, A and B
			//==============================================================
			//calculating AtB
			for (i = 0; i < d; i++) {
				x = 0;
				y = 0;

				for (j = 0; j < n; j++) {
					P = sampledata[neighbors_index[j]];
					Q = sampleproj[neighbors_index[j]];

					wsqrt = (float) Math.sqrt(local_W[j]);

					aij = (P[i] - Pstar[i]) * wsqrt;

					x = x + (aij * ((Q[0] - Qstar[0]) * wsqrt));
					y = y + (aij * ((Q[1] - Qstar[1]) * wsqrt));
				}

				AtB.put(i, 0, x);
				AtB.put(i, 1, y);
			}

			//==============================================================
			// STEP 3: Projection
			//==============================================================

			// SVD Computation      	[U, S, V]			
			FloatMatrix[] svd = Singular.fullSVD(AtB)	;	
			FloatMatrix V = svd[2];
			FloatMatrix U = svd[0];

			v00 = (float) V.get(0, 0);
			v01 = (float) V.get(0, 1);
			v10 = (float) V.get(1, 0);
			v11 = (float) V.get(1, 1);

			x = 0;
			y = 0;
			for (j = 0; j < d; j++) {
				diff = (X[j] - Pstar[j]);
				uj0 = (float) U.get(j, 0);
				uj1 = (float) U.get(j, 1);

				x += diff * (uj0 * v00 + uj1 * v01);
				y += diff * (uj0 * v10 + uj1 * v11);
			}

			x = x + Qstar[0];
			y = y + Qstar[1];

			// Add point in the projection
//			synchronized (projection) {			
				projection[p] = x;
				projection[len + p] = y;
//			}

			// Update progress bar
			pb.step();
		}
	}
}
