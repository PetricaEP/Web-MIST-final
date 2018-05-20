package ep.db.mdp.lamp;

import java.util.Arrays;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ep.db.matrix.DenseMatrix;
import ep.db.matrix.DenseVector;
import ep.db.matrix.Matrix;
import ep.db.matrix.SVD;
import ep.db.matrix.SVDFactory;
import ep.db.matrix.Vector;
import ep.db.mdp.projection.ProjectionData;
import me.tongfei.progressbar.ProgressBar;

class ParallelSolver implements Callable<Integer> {

	private static Logger logger = LoggerFactory.getLogger(ParallelSolver.class);

	private ProjectionData pdata;
	private Matrix matrix;
	private final float[] projection;
	private final float[][] sampledata;
	private final float[][] sampleproj;
	private double epsilon;
	private int begin;
	private int end;
	private int svdType;
	private ProgressBar pb;

	public ParallelSolver(ProjectionData pdata, float[] projection, float[][] sampledata, float[][] sampleproj,
			Matrix matrix, int begin, int end, double epsilon, ProgressBar pb, int svdType) {
		this.pdata = pdata;
		this.projection = projection;
		this.sampledata = sampledata;
		this.sampleproj = sampleproj;
		this.matrix = matrix;
		this.begin = begin;
		this.end = end;
		this.epsilon = epsilon;
		this.pb = pb;
		this.svdType = svdType;
	}

	@Override
	public Integer call() {
		try {					
			createTransformation();		
			return 0;
		}catch (Exception e) {
			logger.error("Error in thread: " + Thread.currentThread().getName() + ". Partition: " + begin + ", " + end, e);
			throw e;
		}finally {
			
		}
	}

	private void createTransformation() 
	{
		// dimensions
		int len = projection.length / 2;
		int d = matrix.getDimensions();      // origin space: dimension
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
		Matrix AtB = new DenseMatrix(); //(d,r); 		

		// Starting calcs
		int p, i, j, m;
		for (p = begin; p < end; p++) {
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
					projection[p] = Q[0];
					projection[len + p] = Q[1];
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
//				x = 0;
//				y = 0;
				float[] xy = new float[2];
				for (j = 0; j < n; j++) {
					P = sampledata[neighbors_index[j]];
					Q = sampleproj[neighbors_index[j]];

					wsqrt = (float) Math.sqrt(local_W[j]);

					aij = (P[i] - Pstar[i]) * wsqrt;

					xy[0] = xy[0] + (aij * ((Q[0] - Qstar[0]) * wsqrt));
					xy[1] = xy[1] + (aij * ((Q[1] - Qstar[1]) * wsqrt));
				}
				
				
				Vector row = new DenseVector(xy);
				AtB.addRow(row);
//				AtB.setEntry(i, 0, x);
//				AtB.setEntry(i, 1, y);				
			}

			//==============================================================
			// STEP 3: Projection
			//==============================================================

			// SVD Computation      	[U, S, V]
			SVD svd = SVDFactory.createSVD(svdType);
			svd.svd(AtB);
			float[][] V = svd.getV();
			float[][] U = svd.getU();			

			v00 = V[0][0]; 
			v01 = V[0][1]; 
			v10 = V[1][0]; 
			v11 = V[1][1];

			x = 0;
			y = 0;
			for (j = 0; j < d; j++) {
				diff = (X[j] - Pstar[j]);
				uj0 = U[j][0];
				uj1 = U[j][1];

				x += diff * (uj0 * v00 + uj1 * v01);
				y += diff * (uj0 * v10 + uj1 * v11);
			}

			x = x + Qstar[0];
			y = y + Qstar[1];
			
			projection[p] = x;
			projection[len + p] = y;
			
			pb.step();
		}
	}
}
