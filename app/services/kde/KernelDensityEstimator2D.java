/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package services.kde;

import java.awt.Color;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;

import javax.swing.JFrame;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.block.BlockBorder;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.GrayPaintScale;
import org.jfree.chart.renderer.PaintScale;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.data.DomainOrder;
import org.jfree.data.general.DatasetChangeListener;
import org.jfree.data.general.DatasetGroup;
import org.jfree.data.xy.XYZDataset;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.RectangleInsets;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DoubleStatistic;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;

/**
 * Kernel Density Estimator:
 * 
 *
 */
public class KernelDensityEstimator2D {

	private final int gridSize;

	private final KernelFunction kernel;

	private double[][] table;

	public KernelDensityEstimator2D(KernelFunction kernel, int gridSize) {
		this.kernel = kernel;
		this.gridSize = gridSize;
	}

	public void compute(DoubleMatrix2D data, double[] h){

		double[][] H =  new double[data.columns()][data.columns()];
		if ( h == null ){
			H = selectBandwidth(data);
		}
		else if ( h.length == 1){
			for(int i = 0; i < h.length; i++ ){
				Arrays.fill(H[i], 0.0);
				H[i][i] = h[0];
			}
		}
		else if ( h.length == data.columns()){
			for(int i = 0; i < h.length; i++ ){
				Arrays.fill(H[i], 0.0);
				H[i][i] = h[i];
			}
		}
		else{
			return;
		}
		
		double bounds[] = selectBounds(data, 0.1);

		table = new double[gridSize][gridSize];

		//Iterate through each corner of cell.
		final double cellSizeX = (bounds[2] - bounds[0])/(gridSize-1),
				cellSizeY = (bounds[3] - bounds[1])/(gridSize-1);
		
		double scaled = data.rows() * H[0][0] * H[1][1];
		for( int y = 0; y < gridSize; y++ ) {
			for( int x = 0; x < gridSize; x++ ) {
				final double px = bounds[0] + cellSizeX * x;
				final double py = bounds[1] + cellSizeY * y;
				double sum = 0.0;

				//Compute contribution of each point.
				// TODO: optimize using QuadTree
				for( int p = 0; p < data.rows(); p++ ) {
					double ux = (px - data.getQuick(p, 0)) / H[0][0],
							uy = (py - data.getQuick(p, 1)) / H[1][1];
					sum += kernel.apply(ux,uy);
				}

				//Insert value into table.
				table[x][y] = sum / scaled;
			}
		}
	}

	private double[][] selectBandwidth(DoubleMatrix2D data) {
		DoubleMatrix2D cov = DoubleStatistic.covariance(data);
		cov.assign(DoubleFunctions.sqrt);
		double b = 1.0/Math.pow(data.rows(), 1.0/(data.columns()+4));
		cov.assign(DoubleFunctions.mult(b));
		return cov.toArray();
	}

	/**
	 * Selects bounds for a set of points.  Recommended margin is 0.25.
	 *
	 * @param points    Array containing points: [x0, y0, x1, y1...]
	 * @param off       Offset into point array.
	 * @param numPoints Number of points to use.
	 * @param margin    Margin around points, in units of the span of the points.
	 * @return 4x1 matrix [minX, minY, maxX, maxY]
	 */
	public double[] selectBounds( DoubleMatrix2D data, double margin ) {
		double x0 = data.viewColumn(0).getMinLocation()[0];
		double x1 = data.viewColumn(0).getMaxLocation()[0];
		double y0 = data.viewColumn(1).getMinLocation()[0];
		double y1 = data.viewColumn(1).getMaxLocation()[0];
		
		double mx = (x1 - x0) * margin;
		double my = (y1 - y0) * margin;
		return new double[]{ x0 - mx, y0 - my, x1 + mx, y1 + my };
	}

	public double[][] getKDE() {
		return table;
	}
	
	public static void main(String[] args) {
		
		
		File file = new File("sample.csv");
		double[][] data = new double[272][2];
		int i  = 0;
		try ( BufferedReader br = new BufferedReader(new FileReader(file))){
			String line = null;
			line = br.readLine();
			
			while( line != null){
				String f[] = line.split("\\s*,\\s*");
				data[i][0] = Double.parseDouble(f[0]);
				data[i][1] = Double.parseDouble(f[1]);
				++i;
				line = br.readLine();
			}
			
		}catch (Exception e) {
			// TODO: handle exception
		}
		
//		KernelFunction kernel = new GaussianKernelFunction();
		KernelFunction kernel = new QuarticKernelFunction();
		KernelDensityEstimator2D kde = new KernelDensityEstimator2D(kernel, 151);
		kde.compute(new DenseDoubleMatrix2D(data), null);
		
		System.out.println(new DenseDoubleMatrix2D(kde.getKDE()));
	}
	
}


