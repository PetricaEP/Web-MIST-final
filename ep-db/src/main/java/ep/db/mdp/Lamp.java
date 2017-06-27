package ep.db.mdp;

import java.awt.Color;
import java.awt.Shape;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import org.jblas.DoubleMatrix;
import org.jblas.ranges.RangeUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;
import org.jfree.util.ShapeUtilities;

import cern.colt.matrix.tdouble.DoubleFactory1D;
import cern.colt.matrix.tdouble.DoubleFactory2D;
import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.algo.DenseDoubleAlgebra;
import cern.colt.matrix.tdouble.algo.decomposition.DenseDoubleSingularValueDecomposition;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;

/**
 * Implmentação do algoritmo LAMP para
 * projeção multidimensional.
 * <a href="http://ieeexplore.ieee.org/xpls/abs_all.jsp?arnumber=6065024">
 * http://ieeexplore.ieee.org/xpls/abs_all.jsp?arnumber=6065024</a> 
 * <i>(P.Joia, F.V Paulovich, D.Coimbra, J.A.Cuminato, & L.G.Nonato)</i>
 * @version 1.0
 * @since 2017
 *
 */
public class Lamp {

	/**
	 * Tolerância mínima padrão
	 */
	private static final double TOL = 1e-6;

	/**
	 * Gerador aleatório.
	 */
	private final Random rng;

	/**
	 * Cria uma novo objeto para projeção multidimensional,
	 * inicialize gerador aleatório.
	 */
	public Lamp() {
		this(0);
	}
	
	/**
	 * Cria um novo objeto para projeção multidimensional,
	 * inicializando o gerador aletória com a semente
	 * dada. 
	 * @param seed semente do gerador aleatório.
	 */
	public Lamp(long seed) {
		rng = new Random();
		if (seed > 0)
			rng.setSeed(seed);
	}

	/**
	 * Realiza projeção multidimensional para a matriz
	 * informada.
	 * @param x matriz com valores a serem projetados (N x M).
	 * @return matriz de projeção multimensional (N x 2).
	 */
	public DoubleMatrix2D project(DoubleMatrix2D x){
		DoubleMatrix2D xs, ys;

		// Seleciona control points aleatoriamente
		int n = (int) Math.sqrt( x.rows() );
		Set<Integer> sample = new HashSet<>(n);
		while( sample.size() < n ){
			Integer next = rng.nextInt( x.rows() );
			sample.add(next);
		}

		// Salva control points em vetor de inteiros.
		int[] cpoints = new int[n];
		Iterator<Integer> iter = sample.iterator();
		for(int j = 0; j < n && iter.hasNext(); j++){
			cpoints[j] = iter.next();
		}

		// Projeta control points usando MDS
		ForceScheme forceScheme = new ForceScheme();
		xs = x.viewSelection(cpoints, null).copy();
		ys = forceScheme.project(xs);

		// Projeta restante dos pontos
		return project(x, cpoints, ys);
	}

	/**
	 * Realiza projeção multidimensional para a matriz informada.
	 * @param x matriz com valores a serem projetados (N x M).
	 * @param cpoints índices dos pontos de controle na matriz <code>x</code>
	 * a serem utilizados na projeção.
	 * @param ys projeção muldimensional para os pontos de controle 
	 * (<code>cpoints.length</code> x 2). 
	 * @return
	 */
	public DoubleMatrix2D project(DoubleMatrix2D x, int[] cpoints, DoubleMatrix2D ys){

		// Seleciona valores dos pontos de controle
		DoubleMatrix2D xs = x.viewSelection(cpoints, null).copy();

		int ninst = x.rows(),
				dim = x.columns();
		int k = cpoints.length,
				a = xs.columns();
		int p = ys.columns();

		assert dim == a;

		
		DenseDoubleAlgebra alg = new DenseDoubleAlgebra();
		
		DoubleMatrix2D Y = DoubleFactory2D.dense.make(ninst, p, 0.0);

		for (int pt = 0; pt < ninst; pt++){
			// Calculo dos alfas
			DoubleMatrix1D alpha = DoubleFactory1D.dense.make(k, 0.0);
			boolean skip = false;
			for( int i = 0; i < k; i++){
				// Verifica se o ponto a ser projetado é um ponto de controle
				// para evitar divisão por zero.
				double norm2 = alg.norm2( xs.viewRow(i).copy().assign(x.viewRow(pt), DoubleFunctions.minus)); 
				if ( norm2 < TOL ){
					// ponto muito próximo ao ponto amostrado
					// posicionando de forma similar.
					Y.viewRow(pt).assign(ys.viewRow(i));
					skip = true;
					break;
				}

				alpha.setQuick(i, 1.0 / norm2);
			}

			if ( skip )
				continue;

			double alphaSum = alpha.zSum();

			// Computa x~ e y~ (eq. 3)
			DoubleMatrix1D xtilde = DoubleFactory1D.dense.make(dim, 0.0);
			DoubleMatrix1D ytilde = DoubleFactory1D.dense.make(p, 0.0);

			xtilde = alg.mult(xs.viewDice(), alpha).assign(DoubleFunctions.div(alphaSum));
			ytilde = alg.mult(ys.viewDice(), alpha).assign(DoubleFunctions.div(alphaSum));

			DoubleMatrix2D xhat = xs.copy(), yhat = ys.copy();

			// Computa x^ e y^ (eq. 6)
			for( int i = 0; i < xs.rows(); i++){
				xhat.viewRow(i).assign(xtilde, DoubleFunctions.minus);
				yhat.viewRow(i).assign(ytilde, DoubleFunctions.minus);
			}

			DoubleMatrix2D At, B;

			// Sqrt(alpha)
			alpha.assign(DoubleFunctions.sqrt);
			for(int i = 0; i < xhat.columns(); i++ )
				xhat.viewColumn(i).assign(alpha, DoubleFunctions.mult);
			for(int i = 0; i < yhat.columns(); i++ )
				yhat.viewColumn(i).assign(alpha, DoubleFunctions.mult);

			At = xhat.viewDice();
			B = yhat;

			DenseDoubleSingularValueDecomposition svd = new DenseDoubleSingularValueDecomposition( 
					At.zMult(B, null), true , false  );
			DoubleMatrix2D U = svd.getU(), V = svd.getV();

			// eq. 7: M = UV
			DoubleMatrix2D M = U.zMult(V.viewDice(), null); 

			//eq. 8: y = (x - xtil) * M + ytil
			DoubleMatrix1D rowX = x.viewRow(pt).copy();
			rowX = M.viewDice().zMult(rowX.assign(xtilde, DoubleFunctions.minus),null).assign(ytilde, DoubleFunctions.plus);
			Y.viewRow(pt).assign(rowX);
		}

		return Y;
	}

	public static void main(String[] args) throws IOException {

		DoubleMatrix data = DoubleMatrix.loadCSVFile("/Users/jose/Documents/freelancer/petricaep/lamp-python/iris.data");

		DoubleMatrix2D x = new DenseDoubleMatrix2D(data.getColumns(RangeUtils.interval(0, data.columns-1)).toArray2());

		int[] indices = new int[]{47,   3,  31,  25,  15, 118,  89,   6, 103,  65,  88,  38,  92};

		DoubleMatrix2D ys = new DenseDoubleMatrix2D(new double[][]{
			{ 0.64594878, 0.21303289},
			{ 0.71731767,  0.396145  },
			{ 0.70414944, 0.65089645},
			{ 0.57139458,  0.4722532 },
			{ 0.76340806,  0.25250587},
			{ 0.61347666,  0.8632922 },
			{ 0.56565112,  0.54291614},
			{ 0.80551708, -0.02531856},
			{-0.08270801,  0.57582274},
			{ 0.56379192,  0.22470327},
			{ 0.82288279,  0.21620781},
			{ 0.89253817,  0.46421933},
			{-0.02987608,  0.6828974 }
		});

		Lamp lamp = new Lamp();
		DoubleMatrix2D y = lamp.project(x, indices, ys);

		XYSeriesCollection dataset = new XYSeriesCollection();
        XYSeries series = new XYSeries("Random");
		for( int i = 0; i < y.rows(); i++){
			for(int j = 0; j < y.columns(); j++){
				System.out.print(String.format("%e ", y.get(i,j)));
			}
			series.add(y.get(i, 0), y.get(i, 1));
			System.out.println();
		}

		dataset.addSeries(series);
		
		JFreeChart jfreechart = ChartFactory.createScatterPlot("MDP", "X","Y", dataset);
		Shape cross = ShapeUtilities.createDiagonalCross(3, 1);
        XYPlot xyPlot = (XYPlot) jfreechart.getPlot();
        xyPlot.setDomainCrosshairVisible(true);
        xyPlot.setRangeCrosshairVisible(true);
        XYItemRenderer renderer = xyPlot.getRenderer();
        renderer.setSeriesShape(0, cross);
        renderer.setSeriesPaint(0, Color.red);
		
		final ChartPanel panel = new ChartPanel(jfreechart, true);
		panel.setPreferredSize(new java.awt.Dimension(500, 270));

		panel.setMinimumDrawHeight(10);
		panel.setMaximumDrawHeight(2000);
		panel.setMinimumDrawWidth(20);
		panel.setMaximumDrawWidth(2000);

		ApplicationFrame frame = new ApplicationFrame("MDP");
		frame.setContentPane(panel);
		frame.pack();
		RefineryUtilities.centerFrameOnScreen(frame);
		frame.setVisible(true);
	}
}
