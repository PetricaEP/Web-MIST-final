package ep.db.mdp;

import java.awt.Color;
import java.awt.Shape;
import java.io.IOException;
import java.util.Random;
import java.util.stream.IntStream;

import org.jblas.FloatMatrix;
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

import cern.colt.matrix.tfloat.FloatFactory1D;
import cern.colt.matrix.tfloat.FloatFactory2D;
import cern.colt.matrix.tfloat.FloatMatrix1D;
import cern.colt.matrix.tfloat.FloatMatrix2D;
import cern.colt.matrix.tfloat.algo.decomposition.DenseFloatSingularValueDecomposition;
import cern.colt.matrix.tfloat.impl.DenseFloatMatrix2D;
import cern.jet.math.tfloat.FloatFunctions;
import me.tongfei.progressbar.ProgressBar;

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
	public FloatMatrix2D project(FloatMatrix2D x){
		FloatMatrix2D xs, ys;

		// Seleciona control points aleatoriamente
		int n = (int) Math.sqrt( x.rows() );
		int[] cpoints = IntStream.range(0, x.rows()).distinct().sorted().limit(n).toArray();

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
	public FloatMatrix2D project(FloatMatrix2D x, int[] cpoints, FloatMatrix2D ys){

		// Seleciona valores dos pontos de controle
		FloatMatrix2D xs = new DenseFloatMatrix2D(cpoints.length, x.columns());
		xs.assign(x.viewSelection(cpoints, null));

		int ninst = x.rows(),
				dim = x.columns(),
				a = xs.columns(),
				k = xs.rows(),
				p = ys.columns();

		assert dim == a;

		ProgressBar pb = new ProgressBar("MDP", ninst).start();
		
		FloatMatrix2D Y = FloatFactory2D.dense.make(ninst, p, 0.0f);

		for(int pt = 0; pt < ninst; pt++){
			// Calculo dos alfas
			FloatMatrix1D alpha = FloatFactory1D.dense.make(k, 0.0f);
			boolean skip = false;
			for( int i = 0; i < k; i++){
				// Verifica se o ponto a ser projetado é um ponto de controle
				// para evitar divisão por zero.
				FloatMatrix1D diff = xs.viewRow(i).copy().assign(x.viewRow(pt), FloatFunctions.minus);
				float norm2 =  (float) Math.sqrt(diff.zDotProduct(diff));
				if ( norm2 < TOL ){
					// ponto muito próximo ao ponto amostrado
					// posicionando de forma similar.
					Y.viewRow(pt).assign(ys.viewRow(i));
					skip = true;
					break;
				}

				alpha.setQuick(i, 1.0f / norm2);
			}

			if (skip)
				continue;

			float alphaSum = alpha.zSum();

			// Computa x~ e y~ (eq. 3)
			FloatMatrix1D xtilde = FloatFactory1D.dense.make(dim, 0.0f);
			FloatMatrix1D ytilde = FloatFactory1D.dense.make(p, 0.0f);

			xs.zMult(alpha, xtilde, 1.0f/alphaSum, 0, true);
			ys.zMult(alpha, ytilde, 1.0f/alphaSum, 0, true);

			FloatMatrix2D xhat = new DenseFloatMatrix2D(k,dim), 
					yhat = new DenseFloatMatrix2D(k,p);
			xhat.assign(xs);
			yhat.assign(ys);

			FloatMatrix2D xtilde2D = FloatFactory2D.dense.repeat(xtilde.like2D(1, dim), k, 1);
			FloatMatrix2D ytilde2D = FloatFactory2D.dense.repeat(ytilde.like2D(1, p), k, 1);

			// Computa x^ e y^ (eq. 6)
			xhat.assign(xtilde2D, FloatFunctions.minus);
			yhat.assign(ytilde2D, FloatFunctions.minus);

			FloatMatrix2D At, B;

			for(int i = 0; i < xhat.columns(); i++ )
				xhat.viewColumn(i).assign(alpha, (xx,yy) -> xx*(float)Math.sqrt(yy));
			for(int i = 0; i < yhat.columns(); i++ )
				yhat.viewColumn(i).assign(alpha, (xx,yy) -> xx*(float)Math.sqrt(yy));

			At = xhat.viewDice();
			B = yhat;

			DenseFloatSingularValueDecomposition svd = new DenseFloatSingularValueDecomposition( 
					At.zMult(B, null), true , false  );
			FloatMatrix2D U = svd.getU(), V = svd.getV();

			// eq. 7: M = UV
			FloatMatrix2D M = U.zMult(V.viewDice(), null); 

			//eq. 8: y = (x - xtil) * M + ytil
			M.zMult(xtilde.assign(x.viewRow(pt), (xx,yy) -> yy-xx), ytilde, 1, 1, true);
			Y.viewRow(pt).assign(ytilde);
			
			//Atualiza progresso
			pb.step();
		}
		
		pb.stepTo(pb.getMax());
		pb.stop();

		return Y;
	}

	public static void main(String[] args) throws IOException {

		FloatMatrix data = FloatMatrix.loadCSVFile("/Users/jose/Documents/freelancer/petricaep/lamp-python/iris.data");

		FloatMatrix2D x = new DenseFloatMatrix2D(data.getColumns(RangeUtils.interval(0, data.columns-1)).toArray2());

		int[] indices = new int[]{47,   3,  31,  25,  15, 118,  89,   6, 103,  65,  88,  38,  92};

		FloatMatrix2D ys = new DenseFloatMatrix2D(new float[][]{
			{ 0.64594878f, 0.21303289f},
			{ 0.71731767f,  0.396145f  },
			{ 0.70414944f, 0.65089645f},
			{ 0.57139458f,  0.4722532f },
			{ 0.76340806f,  0.25250587f},
			{ 0.61347666f,  0.8632922f},
			{ 0.56565112f,  0.54291614f},
			{ 0.80551708f, -0.02531856f},
			{-0.08270801f,  0.57582274f},
			{ 0.56379192f,  0.22470327f},
			{ 0.82288279f,  0.21620781f},
			{ 0.89253817f,  0.46421933f},
			{-0.02987608f,  0.6828974f }
		});

		Lamp lamp = new Lamp();
		FloatMatrix2D y = lamp.project(x, indices, ys);

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