package services.mskde;

import java.awt.geom.GeneralPath;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

import cern.colt.list.tfloat.FloatArrayList;
import cern.colt.list.tint.IntArrayList;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.tfloat.FloatMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;
import play.Logger;
import services.clustering.KMeans;
import services.kde.KernelDensityEstimator2D;
import services.kde.KernelFunction;
import services.marchingsquares.MarchingSquares;

public class MSKDE {
	
	private final int gridSize;

	private final KernelFunction kernel;

	private int numClusters;

	private KernelDensityEstimator2D kde;
	
	
	public MSKDE(KernelFunction kernel, int gridSize, int numClusters) {
		super();
		this.kernel = kernel;
		this.gridSize = gridSize;
		this.numClusters = numClusters;
	}


	public GeneralPath[] compute(DoubleMatrix2D xy){
		// Computa KDE
		kde = new KernelDensityEstimator2D(kernel, gridSize);
		kde.compute(xy, null);
			
		double[][] densities = kde.getKDE();
		double[] values = Arrays.stream(densities).flatMapToDouble(Arrays::stream).toArray();
		KMeans kmeans = new KMeans();
		DoubleMatrix1D valuesMatrix = new DenseDoubleMatrix1D(values);
		kmeans.cluster(valuesMatrix, numClusters);
		FloatMatrix2D clusters = kmeans.getPartition();
		
		double[] levels = new double[clusters.columns()];
		for( int k = 0; k < clusters.columns(); k++ ){
			IntArrayList indices = new IntArrayList();
			clusters.viewColumn(k).getNonZeros(indices, new FloatArrayList());
			double c = valuesMatrix.viewSelection(indices.elements()).aggregate(DoubleFunctions.max, DoubleFunctions.identity);
			levels[k] = c;
		}
		
		Arrays.sort(levels);
		
		// Marching Squares
		MarchingSquares ms = new MarchingSquares();
		GeneralPath[] contours;
		try {
			contours = ms.buildContours(kde.getKDE(), levels);
		} catch (InterruptedException | ExecutionException e) {
			Logger.error("Error executing Marching Squares algorithm.", e);
			return null;
		}
		
		return contours;
	}
	
	
	public int cellsX(){
		return kde.getKDE().length;
	}
	
	public int cellsY(){
		return kde.getKDE()[0].length;
	}

}
