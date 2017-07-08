/*
 * This file is part of the Trickl Open Source Libraries.
 *
 * Trickl Open Source Libraries - http://open.trickl.com/
 *
 * Copyright (C) 2011 Tim Gee.
 *
 * Trickl Open Source Libraries are free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Trickl Open Source Libraries are distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this project.  If not, see <http://www.gnu.org/licenses/>.
 */
package services.clustering;

import org.apache.commons.math3.random.MersenneTwister;
import org.apache.commons.math3.random.RandomGenerator;

import cern.colt.matrix.AbstractMatrix;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tfloat.FloatMatrix1D;
import cern.colt.matrix.tfloat.FloatMatrix2D;
import cern.colt.matrix.tfloat.algo.FloatStatistic;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix1D;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import cern.colt.matrix.tfloat.impl.SparseFloatMatrix2D;
import cern.colt.matrix.tint.IntMatrix1D;
import cern.colt.matrix.tint.impl.DenseIntMatrix1D;


public class KMeans {

	private AbstractMatrix means;
	private FloatMatrix2D partition;
	private IntMatrix1D clusters;
	private int maxIterations = 1000;
	private RandomGenerator randomGenerator = new MersenneTwister();
	private PartitionGenerator partitionGenerator = new HardRandomPartitionGenerator();
	
	public KMeans() {
		
	}

	public void cluster(FloatMatrix2D data, int numClusters) {
		int n = data.rows(); // Number of documents
		int p = data.columns(); // Number of terms

		clusters = new DenseIntMatrix1D(n);
		partition = new SparseFloatMatrix2D(n, numClusters);
		partitionGenerator.setRandomGenerator(randomGenerator);
		partitionGenerator.generate(partition);

		FloatMatrix2D means = new DenseFloatMatrix2D(p, numClusters);

		boolean changedPartition = true;
		
		// Begin the main loop of alternating optimization
		for (int itr = 0; itr < maxIterations && changedPartition; ++itr) {
			// Get new prototypes (v) for each cluster using weighted median
			for (int k = 0; k < numClusters; k++) {

				double sumWeight = partition.viewColumn(k).zSum();

				for (int j = 0; j < p; j++) {
					double sumValue = 0;	

					for (int i = 0; i < n; i++) {
						double Um = partition.getQuick(i, k);
						sumValue += data.getQuick(i, j) * Um;
					}

					means.setQuick(j, k, (float) (sumValue / sumWeight));
				}
			}

			// Calculate distance measure d:
			FloatMatrix2D distances = new DenseFloatMatrix2D(n, numClusters);
			for (int k = 0; k < numClusters; k++) {
				for (int i = 0; i < n; i++) {
					// Euclidean distance calculation
					float distance = FloatStatistic.EUCLID.apply(means.viewColumn(k), data.viewRow(i));
					distances.setQuick(i, k, distance);
				}
			}

			// Get new partition matrix U:
			changedPartition = false;
			for (int i = 0; i < n; i++) {
				double minDistance = Double.MAX_VALUE;
				int closestCluster = 0;

				for (int k = 0; k < numClusters; k++) {
					// U = 1 for the closest prototype
					// U = 0 otherwise

					if (distances.getQuick(i, k) < minDistance) {
						minDistance = distances.getQuick(i, k);
						closestCluster = k;
					}
				}

				if (partition.getQuick(i, closestCluster) == 0) {
					changedPartition = true;

					for (int k = 0; k < numClusters; k++) {
						partition.setQuick(i, k, (k == closestCluster) ? 1 : 0);
					}
				}
				clusters.setQuick(i, closestCluster);
			}
		}
	}

	public void cluster(DoubleMatrix1D data, int numClusters) {
		int n = (int) data.size(); // Number of examples

		clusters = new DenseIntMatrix1D(n);
		partition = new SparseFloatMatrix2D(n, numClusters);
		partitionGenerator.setRandomGenerator(randomGenerator);
		partitionGenerator.generate(partition);

		FloatMatrix1D means = new DenseFloatMatrix1D(numClusters);

		boolean changedPartition = true;

		// Begin the main loop of alternating optimization
		for (int itr = 0; itr < maxIterations && changedPartition; ++itr) {
			// Get new prototypes (v) for each cluster using weighted median
			for (int k = 0; k < numClusters; k++) {

				float sumWeight = partition.viewColumn(k).zSum();
				float sumValue = 0;	

				for (int i = 0; i < n; i++) {
					float Um = partition.getQuick(i, k);
					sumValue += data.getQuick(i) * Um;
				}

				means.setQuick(k, sumValue / sumWeight);
			}

			// Calculate distance measure d:
			FloatMatrix2D distances = new DenseFloatMatrix2D(n, numClusters);
			for (int k = 0; k < numClusters; k++) {
				for (int i = 0; i < n; i++) {
					// Euclidean distance calculation
					float distance = (float) Math.abs(means.getQuick(k) - data.getQuick(i));
					distances.setQuick(i, k, distance);
				}
			}

			// Get new partition matrix U:
			changedPartition = false;
			for (int i = 0; i < n; i++) {
				double minDistance = Double.MAX_VALUE;
				int closestCluster = 0;

				for (int k = 0; k < numClusters; k++) {
					// U = 1 for the closest prototype
					// U = 0 otherwise

					if (distances.getQuick(i, k) < minDistance) {
						minDistance = distances.getQuick(i, k);
						closestCluster = k;
					}
				}

				if (partition.getQuick(i, closestCluster) == 0) {
					changedPartition = true;

					for (int k = 0; k < numClusters; k++) {
						partition.setQuick(i, k, (k == closestCluster) ? 1 : 0);
					}
				}
				clusters.setQuick(i, closestCluster);
			}
		}
	}

	public AbstractMatrix getMeans() {
		return means;
	}

	public FloatMatrix2D getPartition() {
		return partition;
	}

	public IntMatrix1D getClusterAssignments(){
		return clusters;
	}

	public int getMaxIterations() {
		return maxIterations;
	}

	public void setMaxIterations(int maxIterations) {
		this.maxIterations = maxIterations;
	}

	public RandomGenerator getRandomGenerator() {
		return randomGenerator;
	}

	public void setRandomGenerator(RandomGenerator random) {
		this.randomGenerator = random;
	}
}
