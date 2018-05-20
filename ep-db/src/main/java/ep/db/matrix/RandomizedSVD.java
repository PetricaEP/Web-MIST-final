package ep.db.matrix;

import org.jblas.Decompose;
import org.jblas.FloatMatrix;
import org.jblas.Singular;

public class RandomizedSVD implements SVD {
	// Compute a (truncated) randomized SVD of a JBLAS FloatMatrix
	public int numComponents = 2;
	public int niters = 5;

	private int numOversamples = 10;
	private boolean transpose = false;
	public FloatMatrix[] rsvd = new FloatMatrix[3];

	public RandomizedSVD(int numComponents, int niters) {
		this.numComponents = numComponents;
		this.niters = niters;	
	}
	
	public void svd(Matrix matrix) {
		FloatMatrix A = new FloatMatrix(matrix.toMatrix());
		
		transpose = A.rows > A.columns;
		rsvd[0] = new FloatMatrix(A.rows, numComponents);
		rsvd[1] = new FloatMatrix(numComponents);
		rsvd[2] = new FloatMatrix(A.columns, numComponents);
		if (transpose) {
			A = A.transpose();
		}

		FloatMatrix C = A.mmul(A.transpose());
		FloatMatrix Q = FloatMatrix.randn(A.rows, Math.min(A.rows, numComponents + numOversamples));
		for (int i = 0; i < niters; i++) {
			C.mmuli(Q, Q);
			Q = Decompose.lu(Q).l;
		}

		C.mmuli(Q, Q);
		Q = Decompose.qr(Q).q;
		FloatMatrix[] svd = Singular.fullSVD(Q.transpose().mmul(A));
		FloatMatrix W = Q.mmul(svd[0]);

		if (transpose) {
			for (int i = 0; i < numComponents; i++) {
				rsvd[0].putColumn(i, svd[2].getColumn(i));
				rsvd[1].put(i, svd[1].get(i));
				rsvd[2].putColumn(i, W.getColumn(i));
			}
		} else {
			for (int i = 0; i < numComponents; i++) {
				rsvd[0].putColumn(i, W.getColumn(i));
				rsvd[1].put(i, svd[1].get(i));
				rsvd[2].putColumn(i, svd[2].getColumn(i));
			}
		}
	}

	@Override
	public float[][] getV() {
		return rsvd[2].toArray2();
	}

	@Override
	public float[] getS() {
		return rsvd[1].toArray();
	}

	@Override
	public float[][] getU() {
		return rsvd[0].toArray2();
	}
}