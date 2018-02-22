package ep.db.mdp.projection;

import ep.db.mdp.DistanceMatrix;

public class FastmapProjection  extends Projector {

	public FastmapProjection() {
		this.targetDimension = 2;
	}

	public FastmapProjection(int targetDimension) {
		this.targetDimension = targetDimension;
	}

	public float[][] project(DistanceMatrix dmat) {
		float[][] points = null;
		dmat = (DistanceMatrix) new DistanceMatrix(dmat);

		points = new float[dmat.getElementCount()][];
        for (int i = 0; i < dmat.getElementCount(); i++) {
            points[i] = new float[this.targetDimension];
        }        

		if (points.length < 4) {
			this.doTheFastmapLessThan4Points(points, dmat);
		} else {
			this.doTheFastmap(points, dmat);
		}

		this.normalize(points);
		return points;
	}

	public void doTheFastmapLessThan4Points(float[][] points, DistanceMatrix dmat) {
		if (points.length == 1) {
			points[0][0] = 0;
			points[0][1] = 0;
		} else if (points.length == 2) {
			points[0][0] = 0;
			points[0][1] = 0;
			points[1][0] = dmat.getDistance(0, 1);
			points[1][1] = 0;
		} else if (points.length == 3) {
			points[0][0] = 0;
			points[0][1] = 0;
			points[1][0] = dmat.getDistance(0, 1);
			points[1][1] = 0;
			points[2][0] = dmat.getDistance(0, 1);
			points[2][1] = dmat.getDistance(1, 2);
		}
	}

	private void doTheFastmap(float[][] points, DistanceMatrix dmat) {
		int currentDimension = 0;

		final int size = dmat.getElementCount();
		while (currentDimension < this.targetDimension) 
		{
			//choosen pivots for this recursion
			int[] lvchoosen = this.chooseDistantObjects(dmat);
			float lvdistance = dmat.getDistance(lvchoosen[0], lvchoosen[1]);

			//if the distance between the pivots is 0, then set 0 for each instance for this dimension
			if (lvdistance == 0) {
				//for each instance in the table
				for (int lvi = 0; lvi < size; lvi++) {
					points[lvi][currentDimension] = 0.0f;
				}
			} else { //if the distance is not equal to 0, then
				//instances iterator
				for (int lvi = 0; lvi < size; lvi++) {
					//current dimension xi = (distance between the instance and the first pivot)^2+(distance between both pivots)^2
					//								  -(distance between the instance and the secod pivot)^2)
					//all divided by 2 times the (distance between both pivots)

					float lvxi = (float) ((Math.pow(dmat.getDistance(lvchoosen[0], lvi), 2) +
							Math.pow(dmat.getDistance(lvchoosen[0], lvchoosen[1]), 2) -
							Math.pow(dmat.getDistance(lvi, lvchoosen[1]), 2)) /
							(2 * dmat.getDistance(lvchoosen[0], lvchoosen[1])));

					points[lvi][currentDimension] = lvxi;
				}

				//updating the distances table with equation 4 of Faloutsos' paper (in detail below)
				if (currentDimension < this.targetDimension - 1) {
					this.updateDistances(dmat, points, currentDimension);
				}
			}

			//Increase the current dimension
			currentDimension++;
		}
	}

	private int[] chooseDistantObjects(DistanceMatrix dmat) {
		int[] choosen = new int[2];
		int x = 0, y = 1;
		
		//System.out.println("Choose..." + dmat.getElementCount());
		for (int i = 0; i < dmat.getElementCount() - 1; i++) {
			for (int j = i + 1; j < dmat.getElementCount(); j++) {
				if (dmat.getDistance(x, y) < dmat.getDistance(i, j)) {
					x = i;
					y = j;
				}
			}
		}

		choosen[0] = x;
		choosen[1] = y;

		return choosen;
	}

	private void updateDistances(DistanceMatrix dmat, float[][] points, int currentDimension) {
		//for each instance
		for (int lvinst = 0; lvinst < dmat.getElementCount(); lvinst++) {
			//for each another instance
			for (int lvinst2 = lvinst + 1; lvinst2 < dmat.getElementCount(); lvinst2++) {
				float value = (float) (Math.sqrt(Math.abs(Math.pow(dmat.getDistance(lvinst, lvinst2), 2) -
						Math.pow((points[lvinst][currentDimension] -
								points[lvinst2][currentDimension]), 2))));

				dmat.setDistance(lvinst, lvinst2, value);
				dmat.setDistance(lvinst2, lvinst, value);
			}
		}
	}

	private int targetDimension; //The number of dimensions to reduce

}
