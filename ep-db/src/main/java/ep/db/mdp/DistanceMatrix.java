package ep.db.mdp;

import org.jblas.FloatMatrix;

import ep.db.mdp.dissimilarity.Dissimilarity;

public class DistanceMatrix {

	private float maxDistance;
	private float minDistance;
	private int numElements;
	private FloatMatrix distmatrix;

	public DistanceMatrix(FloatMatrix matrix, Dissimilarity diss) {
		super();
		numElements = matrix.rows;
		distmatrix = computeDistances(matrix, diss);							
	}

	private FloatMatrix computeDistances(FloatMatrix matrix, Dissimilarity diss) {
		int size = numElements * (numElements - 1) / 2;
		FloatMatrix distmatrix = new FloatMatrix(size);
		maxDistance = Float.MIN_VALUE;
		minDistance = Float.MAX_VALUE;
		
		for(int i = numElements-1; i >= 0; i--) {
			for(int j = i-1; j >= 0; j--) {
				float dist = diss.calculate(matrix.getRow(i), matrix.getRow(j));				
				int p = numElements * j - j * (j +1)/2 + i - 1 - j;
				distmatrix.put(p, dist);	
				if ( dist > maxDistance ) 
					maxDistance = dist;
				if ( dist < minDistance)
					minDistance = dist;
				
			}
		}
		return distmatrix;
	}

	public DistanceMatrix(DistanceMatrix m) {
		super();		
		distmatrix = new FloatMatrix(m.distmatrix.toArray2());
		numElements = m.numElements;
		maxDistance = m.maxDistance;
		minDistance = m.minDistance;
	}

	public float getDistance(int i, int j) {
		if (i == j) return 0.0f;
		if ( i < j) {
			int aux = i;
			i = j;
			j = aux;
		}
		int p = numElements * j - j * (j +1)/2 + i - 1 - j;
		return distmatrix.get(p);
	}

//	public FloatMatrix getDistanceMatrix() {
//		return distmatrix;
//	}

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
		distmatrix.put(p, value);
	}

	public float getMaxDistance() {
		return maxDistance;
	}
	
	public float getMinDistance() {
		return minDistance;
	}

}
