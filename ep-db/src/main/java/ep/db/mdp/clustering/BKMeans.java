/* ***** BEGIN LICENSE BLOCK *****
 *
 * Copyright (c) 2005-2007 Universidade de Sao Paulo, Sao Carlos/SP, Brazil.
 * All Rights Reserved.
 *
 * This file is part of Projection Explorer (PEx).
 *
 * How to cite this work:
 * 
@inproceedings{paulovich2007pex,
author = {Fernando V. Paulovich and Maria Cristina F. Oliveira and Rosane 
Minghim},
title = {The Projection Explorer: A Flexible Tool for Projection-based 
Multidimensional Visualization},
booktitle = {SIBGRAPI '07: Proceedings of the XX Brazilian Symposium on 
Computer Graphics and Image Processing (SIBGRAPI 2007)},
year = {2007},
isbn = {0-7695-2996-8},
pages = {27--34},
doi = {http://dx.doi.org/10.1109/SIBGRAPI.2007.39},
publisher = {IEEE Computer Society},
address = {Washington, DC, USA},
}
 * 
 * PEx is free software: you can redistribute it and/or modify it under 
 * the terms of the GNU General Public License as published by the Free 
 * Software Foundation, either version 3 of the License, or (at your option) 
 * any later version.
 *
 * PEx is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 *
 * This code was developed by members of Computer Graphics and Image
 * Processing Group (http://www.lcad.icmc.usp.br) at Instituto de Ciencias
 * Matematicas e de Computacao - ICMC - (http://www.icmc.usp.br) of 
 * Universidade de Sao Paulo, Sao Carlos/SP, Brazil. The initial developer 
 * of the original code is Fernando Vieira Paulovich <fpaulovich@gmail.com>.
 *
 * Contributor(s): Rosane Minghim <rminghim@icmc.usp.br>
 *
 * You should have received a copy of the GNU General Public License along 
 * with PEx. If not, see <http://www.gnu.org/licenses/>.
 *
 * ***** END LICENSE BLOCK ***** */

package ep.db.mdp.clustering;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ep.db.matrix.Matrix;
import ep.db.matrix.MatrixFactory;
import ep.db.matrix.SparseMatrix;
import ep.db.matrix.Vector;
import ep.db.mdp.dissimilarity.Dissimilarity;
import ep.db.mdp.dissimilarity.Euclidean;
import ep.db.utils.Utils;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;

/**
 *
 * @author Fernando Vieira Paulovich
 */
public class BKMeans {

	private static Logger logger = LoggerFactory.getLogger(BKMeans.class);

	protected int nrclusters;

	protected ArrayList<ArrayList<Integer>> clusters;

	protected Matrix centroids;

	protected Dissimilarity diss;

	protected static final float EPSILON = 0.00001f;

	protected int nrIterations = 15;

	
	/** Creates a new instance of BKmeans
	 * @param nrclusters 
	 * @param nThreads
	 */
	public BKMeans(int nrclusters) {
		this.nrclusters = nrclusters;
	}

	public ArrayList<ArrayList<Integer>> execute(Dissimilarity diss, Matrix matrix) throws IOException {
		long start = System.currentTimeMillis();
		this.diss = diss;
		this.clusters = new ArrayList<ArrayList<Integer>>();
		this.centroids = MatrixFactory.getInstance(matrix.getClass());

		//initially the gCluster has all elements
		int size1 = matrix.getRowCount();
		ArrayList<Integer> gCluster = new ArrayList<Integer>(size1);
		for (int i = 0; i < size1; i++) {
			gCluster.add(i);
		}

		this.clusters.add(gCluster);

		//considering just one element as the centroid
		try {
			this.centroids.addRow((Vector)matrix.getRow(0).clone());
		} catch (CloneNotSupportedException e) {
			logger.error("Clone not supported", e);
			return null;
		}
		
		ProgressBar pb = new ProgressBar("BKmeans",this.nrclusters, 1000, System.out, ProgressBarStyle.ASCII);
		pb.start();
		for (int j = 0; j < this.nrclusters - 1; j++) {
			//Search the cluster with the bigger number of elements
			gCluster = this.getClusterToSplit(this.clusters);

			//split the greatest cluster into two clusters
			if (gCluster.size() > 1) {
				this.splitCluster(matrix, diss, gCluster);
			}
			pb.step();
		}
		pb.stepTo(pb.getMax());
		pb.stop();		

		//removing possible empty clusters
		for (int i = this.clusters.size() - 1; i >= 0; i--) {
			if (this.clusters.get(i).size() <= 0) {
				this.clusters.remove(i);

				logger.warn("The Bissection k-means algorithm is returning an " +
						"empty cluster. Number of requested clusters : " + this.nrclusters);
			}
		}

		int removed = 0;

		for (int i = this.clusters.size() - 1; i >= 0; i--) {
			if (this.clusters.get(i).size() == 0) {
				this.clusters.remove(i);
				this.centroids.removeRow(i);
				removed++;
			}
		}

		if (removed > 0) {
			logger.warn("The Bissection k-means algorithm is returning " +
					"empty clusters. Removed: " + removed);
		}

		long finish = System.currentTimeMillis();
		logger.info("Bisecting K-Means time: " + (finish - start) / 1000.0f + "s");

		if ( logger.isInfoEnabled() ) {
			String tmp = "Clusters sizes: ";
			for (int i = 0; i < this.clusters.size(); i++) 
				tmp += this.clusters.get(i).size() + " ";
			logger.info(tmp.trim());
		}

		return this.clusters;
	}

	public Matrix getCentroids() {
		return this.centroids;
	}

	public int[] getMedoids(Matrix matrix) {
		int[] m = new int[this.centroids.getRowCount()];

		for (int i = 0; i < m.length; i++) {
			int point = -1;
			float distance = Float.MAX_VALUE;

			for (int j = 0; j < this.clusters.get(i).size(); j++) {
				float distance2 = this.diss.calculate(this.centroids.getRow(i),
						matrix.getRow(this.clusters.get(i).get(j)));

				if (distance > distance2) {
					point = this.clusters.get(i).get(j);
					distance = distance2;
				}
			}

			m[i] = point;
		}

		return m;
	}

	protected ArrayList<Integer> getClusterToSplit(ArrayList<ArrayList<Integer>> clusters) {
		ArrayList<Integer> gCluster = clusters.get(0);

		for (int i = 0; i < clusters.size(); i++) {
			if (clusters.get(i).size() > gCluster.size()) {
				gCluster = clusters.get(i);
			}
		}

		return gCluster;
	}

	protected void splitCluster(Matrix matrix, Dissimilarity diss, ArrayList<Integer> gCluster) {

		long start = System.nanoTime();
		this.centroids.removeRow(clusters.indexOf(gCluster));
		this.clusters.remove(gCluster);
		long end = System.nanoTime();
		logger.info("Remove centronids: " + ((end  - start) / 1e9f));

		//getting the two pivots
		start = System.nanoTime();
		int[] pivots = this.getPivots(matrix, diss, gCluster);
		end = System.nanoTime();
		logger.info("Get pivots: " + ((end - start)/10e9));

		//Create two new clusters
		ArrayList<Integer> cluster_1 = new ArrayList<Integer>();
		cluster_1.add(pivots[0]);		

		ArrayList<Integer> cluster_2 = new ArrayList<Integer>();
		cluster_2.add(pivots[1]);
		
		Vector centroid_1, centroid_2;
		try {
			centroid_1 = (Vector) matrix.getRow(pivots[0]).clone();
			centroid_2 = (Vector) matrix.getRow(pivots[1]).clone();
		} catch (CloneNotSupportedException e) {
			logger.error("Clone no supported", e);
			return;
		}
		
		int iterations = 0;

		do {
			start = System.nanoTime();
			centroid_1 = this.calculateMean(matrix, cluster_1);
			centroid_2 = this.calculateMean(matrix, cluster_2);
			end = System.nanoTime();
			logger.info("Calc. means: " + ((end-start)/1e9));

			cluster_1.clear();
			cluster_2.clear();

			//For each cluster
			int size = gCluster.size();
			for (int i = 0; i < size; i++) {
				Vector aux = matrix.getRow(gCluster.get(i));
				float distCentr_1 = diss.calculate(aux, centroid_1);
				float distCentr_2 = diss.calculate(aux, centroid_2);

				if (distCentr_1 < distCentr_2) {
					cluster_1.add(gCluster.get(i));
				} else if (distCentr_2 < distCentr_1) {
					cluster_2.add(gCluster.get(i));
				} else {
					if (cluster_1.size() > cluster_2.size()) {
						cluster_2.add(gCluster.get(i));
					} else {
						cluster_1.add(gCluster.get(i));
					}
				}
			}

			if (cluster_1.size() < 1) {
				cluster_1.add(cluster_2.get(0));
				cluster_2.remove(cluster_2.get(0));
			} else if (cluster_2.size() < 1) {
				cluster_2.add(cluster_1.get(0));
				cluster_1.remove(cluster_1.get(0));
			}

		} while (++iterations < this.nrIterations);

		//Add the two new clusters
		this.clusters.add(cluster_1);
		this.clusters.add(cluster_2);

		//add the new centroids
		this.centroids.addRow(centroid_1);
		this.centroids.addRow(centroid_2);
	}

	protected int[] getPivots(Matrix matrix, Dissimilarity diss, ArrayList<Integer> gCluster) {
		ArrayList<Pivot> pivots_aux = new ArrayList<Pivot>();
		int[] pivots = new int[2];

		//choosing the first pivot
		Vector mean = this.calculateMean(matrix, gCluster);

		float size = 1 + (gCluster.size() / 10);
		for (int i = 0; i < size; i++) {
			int el = (int) ((gCluster.size() / size) * i);
			int aux = gCluster.get(el);			
			float distance = diss.calculate(mean, matrix.getRow(aux));
			pivots_aux.add(new Pivot(distance, aux));
		}

		Collections.sort(pivots_aux);

		pivots[0] = pivots_aux.get((int) (pivots_aux.size() * 0.75f)).id;

		//choosing the second pivot
		pivots_aux.clear();

		for (int i = 0; i < size; i++) {
			int el = (int) ((gCluster.size() / size) * i);
			int aux = gCluster.get(el);
			float distance = diss.calculate(matrix.getRow(pivots[0]), matrix.getRow(aux));
			pivots_aux.add(new Pivot(distance, aux));
		}

		Collections.sort(pivots_aux);

		pivots[1] = pivots_aux.get((int) (pivots_aux.size() * 0.75f)).id;

		return pivots;
	}

	protected Vector calculateMean(Matrix matrix, ArrayList<Integer> cluster) {									
		long start = System.nanoTime();		
		Vector mean = Utils.mean(matrix, cluster);												
		long end = System.nanoTime(); 
		logger.info("Mean: " + ((end-start)/1e9));
	
		return mean;
	}

	public class Pivot implements Comparable<Pivot> {

		public Pivot(float distance, int id) {
			this.distance = distance;
			this.id = id;
		}

		@Override
		public int compareTo(Pivot o) {
			if (Math.abs(this.distance -  o.distance) <= EPSILON) {
				return 0;
			} else if (this.distance - o.distance > EPSILON) {
				return 1;
			} else {
				return -1;
			}
		}

		public float distance;
		public int id;
	}		
	
	public static void main(String[] args) {
		Dissimilarity diss = new Euclidean();
		BKMeans bkmeans = new BKMeans(10);
//		float[][] values = new float[][]{
//			{5.1f,3.5f,1.4f,0.2f},
//			{4.9f,3f,1.4f,0.2f},
//			{4.7f,3.2f,1.3f,0.2f},
//			{4.6f,3.1f,1.5f,0.2f},
//			{5f,3.6f,1.4f,0.2f},
//			{5.4f,3.9f,1.7f,0.4f},
//			{4.6f,3.4f,1.4f,0.3f},
//			{5f,3.4f,1.5f,0.2f},
//			{4.4f,2.9f,1.4f,0.2f},
//			{4.9f,3.1f,1.5f,0.1f},
//			{5.4f,3.7f,1.5f,0.2f},
//			{4.8f,3.4f,1.6f,0.2f},
//			{4.8f,3f,1.4f,0.1f},
//			{4.3f,3f,1.1f,0.1f},
//			{5.8f,4f,1.2f,0.2f},
//			{5.7f,4.4f,1.5f,0.4f},
//			{5.4f,3.9f,1.3f,0.4f},
//			{5.1f,3.5f,1.4f,0.3f},
//			{5.7f,3.8f,1.7f,0.3f},
//			{5.1f,3.8f,1.5f,0.3f},
//			{5.4f,3.4f,1.7f,0.2f},
//			{5.1f,3.7f,1.5f,0.4f},
//			{4.6f,3.6f,1f,0.2f},
//			{5.1f,3.3f,1.7f,0.5f},
//			{4.8f,3.4f,1.9f,0.2f},
//			{5f,3f,1.6f,0.2f},
//			{5f,3.4f,1.6f,0.4f},
//			{5.2f,3.5f,1.5f,0.2f},
//			{5.2f,3.4f,1.4f,0.2f},
//			{4.7f,3.2f,1.6f,0.2f},
//			{4.8f,3.1f,1.6f,0.2f},
//			{5.4f,3.4f,1.5f,0.4f},
//			{5.2f,4.1f,1.5f,0.1f},
//			{5.5f,4.2f,1.4f,0.2f},
//			{4.9f,3.1f,1.5f,0.2f},
//			{5f,3.2f,1.2f,0.2f},
//			{5.5f,3.5f,1.3f,0.2f},
//			{4.9f,3.6f,1.4f,0.1f},
//			{4.4f,3f,1.3f,0.2f},
//			{5.1f,3.4f,1.5f,0.2f},
//			{5f,3.5f,1.3f,0.3f},
//			{4.5f,2.3f,1.3f,0.3f},
//			{4.4f,3.2f,1.3f,0.2f},
//			{5f,3.5f,1.6f,0.6f},		
//		};
//		
		Matrix matrix = new SparseMatrix();
		try {
			matrix.load("documents200k.data");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		
		ArrayList<ArrayList<Integer>> clusters;
		try {
			clusters = bkmeans.execute(diss, matrix);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		int[] controlPoints = bkmeans.getMedoids(matrix);
		
		for(List<Integer> cl : clusters) {
			System.out.print("Cluster: ");
			for(Integer i : cl)
				System.out.print(i + ", ");
			System.out.println();
		}
		
		System.out.println(Arrays.toString(controlPoints));		
		
		
		
		
	}
}
