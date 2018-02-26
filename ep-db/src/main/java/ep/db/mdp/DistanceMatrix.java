package ep.db.mdp;

import java.util.Arrays;

import ep.db.matrix.Matrix;
import ep.db.mdp.dissimilarity.Dissimilarity;

public class DistanceMatrix {

	private float maxDistance;
	private float minDistance;
	private int numElements;
	private float[] distmatrix;

	public DistanceMatrix(Matrix matrix, Dissimilarity diss) {
		super();
		numElements = matrix.getRowCount();

		int size = numElements * (numElements - 1) / 2;
		distmatrix = new float[size];
		maxDistance = Float.MIN_VALUE;
		minDistance = Float.MAX_VALUE;

		for(int i = numElements-1; i >= 0; i--) {
			for(int j = i-1; j >= 0; j--) {
				float dist = diss.calculate(matrix.getRow(i), matrix.getRow(j));				
				int p = numElements * j - j * (j +1)/2 + i - 1 - j;
				distmatrix[p] = dist;	
				
				if ( dist > maxDistance && dist >= 0.0f) 
					maxDistance = dist;
				if ( dist < minDistance && dist >= 0.0f)
					minDistance = dist;
			}
		}
	}	

	public DistanceMatrix(DistanceMatrix m) {
		super();		
		distmatrix = Arrays.copyOf(m.distmatrix, m.distmatrix.length);
		numElements = m.numElements;
		maxDistance = m.maxDistance;
		minDistance = m.minDistance;
	}
	
	protected DistanceMatrix() {		
	}

	public float getDistance(int i, int j) {
		if (i == j) return 0.0f;
		if ( i < j) {
			int aux = i;
			i = j;
			j = aux;
		}
		int p = numElements * j - j * (j +1)/2 + i - 1 - j;
		assert p < distmatrix.length : "Index cannot be greater than matrix size " + distmatrix.length;
		return distmatrix[p];
	}

	public int getElementCount() {
		return numElements;
	}

	/**
	 * This method modify a distance in the distance matriz.
	 * @param indexA The number of the first point.
	 * @param indexB The number of the second point.
	 * @param value The new value for the distance between the two points.
	 */
	public void setDistance(int i, int j, float value) {
		if ( i == j ) return;
		if ( i < j ) {
			int aux = i;
			i = j;
			j = aux;
		}
		int p = numElements * j - j * (j +1)/2 + i - 1 - j;
		assert p < distmatrix.length : "Index cannot be greater than matrix size " + distmatrix.length;
		distmatrix[p] = value;
	}

	public float getMaxDistance() {
		return maxDistance;
	}

	public float getMinDistance() {
		return minDistance;
	}

}
