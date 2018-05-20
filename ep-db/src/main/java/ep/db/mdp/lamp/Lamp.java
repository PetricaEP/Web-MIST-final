package ep.db.mdp.lamp;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ep.db.matrix.Matrix;
import ep.db.matrix.MatrixFactory;
import ep.db.matrix.SingularValueDecomposition;
import ep.db.mdp.clustering.BKMeans;
import ep.db.mdp.dissimilarity.Dissimilarity;
import ep.db.mdp.dissimilarity.DissimilarityType;
import ep.db.mdp.dissimilarity.Euclidean;
import ep.db.mdp.projection.ControlPointsType;
import ep.db.mdp.projection.IDMAPProjection;
import ep.db.mdp.projection.ProjectionData;
import ep.db.mdp.projection.ProjectorType;
import ep.db.utils.Configuration;
import ep.db.utils.QuickSort;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;

/**
 * Implmentação do algoritmo LAMP para
 * projeção multidimensional.
 * <a href="http://ieeexplore.ieee.org/xpls/abs_all.jsp?arnumber=6065024">
 * http://ieeexplore.ieee.org/xpls/abs_all.jsp?arnumber=6065024</a> 
 * <i>(P.Joia, F.V Paulovich, D.Coimbra, J.A.Cuminato, L.G.Nonato)</i>
 * @version 1.0
 * @since 2017
 *
 */
public class Lamp {

	private static final int NRTHREADS_DEFAULT = 8;

	private static Logger logger = LoggerFactory.getLogger(Lamp.class);

	private final double epsilon = 1.0e-4;

	private float[][] sampledata = null;

	private float[][] sampleproj = null;

	private int[] controlPoints;

	private int nrthreads;

	/**
	 * Cria uma novo objeto para projeção multidimensional,
	 * inicialize gerador aleatório.
	 */
	public Lamp() {
		this(NRTHREADS_DEFAULT);
	}

	public Lamp(int nrthreads) {
		this.nrthreads = nrthreads;
	}

	/**
	 * Realiza projeção multidimensional para a matriz
	 * informada.
	 * @param x matriz com valores a serem projetados (N x M).
	 * @param pdata configuracoes para projecao.
	 * @return matriz de projeção multimensional (N x 2).
	 */
	public float[] project(Matrix x, ProjectionData pdata){

		long start = System.currentTimeMillis();

		Matrix xs = getSampleData(x, pdata);
		if ( xs == null )
			return null;
		sampledata  = xs.toMatrix();

		IDMAPProjection idmap = new IDMAPProjection();
		sampleproj = idmap.project(xs, pdata);

		int n = x.getRowCount();
		float[] proj_aux = new float[n * 2];

		int nrpartitions = nrthreads * 4; //number os pieces to split the data
		int step = (int) Math.ceil( (float) n / nrpartitions);
		int begin = 0, end = 0;										
		List<ParallelSolver> threads = new ArrayList<>();

		final ProgressBar pb = new ProgressBar("LAMP", n, 1000, System.out, ProgressBarStyle.ASCII);
		pb.start();

		final int svdType = Configuration.getInstance().getSvdType();
		for (int i = 0; i < nrpartitions && begin < n; i++) 
		{
			end += step;
			end = (end > n) ? n : end;

			ParallelSolver ps = new ParallelSolver(pdata, proj_aux, sampledata, sampleproj, x, begin, end, epsilon, pb, svdType);
			threads.add(ps);
			logger.info("Partition: " + begin + " - " + end);

			begin = end;						
		}

		if (end < n) {
			ParallelSolver ps = new ParallelSolver(pdata, proj_aux, sampledata, sampleproj, x, end, n, epsilon, pb, svdType);
			threads.add(ps);	
			logger.info("Partition: " + end + " - " + n);
		}


		try {
			ExecutorService executor = Executors.newFixedThreadPool(nrthreads);
			executor.invokeAll(threads);
			executor.shutdown();		
		} catch (Exception ex) {
			logger.error("LAMP calculation error:", ex);
		}

		pb.stepTo(pb.getMax());
		pb.stop();			

		long finish = System.currentTimeMillis();
		logger.info("Local Affine Multidimensional Projection (LAMP) time: {}s", (finish - start) / 1000.0f);

		return proj_aux;
	}

	private Matrix getSampleData(Matrix x, ProjectionData pdata) {

		if ( pdata.getControlPointsChoice() == ControlPointsType.KMEANS) {
			Dissimilarity diss = new Euclidean();
			//clustering points
			BKMeans bkmeans = new BKMeans(pdata.getNumberControlPoints(), true);
			ArrayList<ArrayList<Integer>> clusters;
			try {
				clusters = bkmeans.execute(diss, x);
			} catch (IOException e) {
				logger.error("Error calculation BKMeans", e);
				return null;
			}

			controlPoints = bkmeans.getMedoids(x);

			//if less medoids are returned than the expected (due to the
			//clustering method employed), choose on the clusters other
			//medoids
			ArrayList<Integer> medoids_aux = new ArrayList<Integer>();
			for (int i = 0; i < controlPoints.length; i++) {
				medoids_aux.add(controlPoints[i]);
			}

			while (medoids_aux.size() < pdata.getNumberControlPoints()) 
			{
				for (int c = 0; c < clusters.size()
						&& medoids_aux.size() < pdata.getNumberControlPoints(); c++) {
					if (clusters.get(c).size() > x.getRowCount() / pdata.getNumberControlPoints()) {
						for (int i = 0; i < clusters.get(c).size(); i++) {
							if (!medoids_aux.contains(clusters.get(c).get(i))) {
								medoids_aux.add(clusters.get(c).get(i));
								break;
							}
						}
					}
				}
			}

			controlPoints = new int[medoids_aux.size()];
			for (int i = 0; i < controlPoints.length; i++) {
				controlPoints[i] = medoids_aux.get(i);
			}
		}
		else if ( pdata.getControlPointsChoice() == ControlPointsType.RANDOM) {
			//Random choice
			controlPoints = IntStream.range(0, x.getRowCount()).distinct().sorted()
					.limit(pdata.getNumberControlPoints()).toArray();
		}
		else { // pdata.getControlPointsChoice() == ControlPointsType.REPRESENTATIVE
			// Replicate code from TH
			int lines = x.getRowCount();
			int cols = x.getDimensions();
			int np = lines;
			int dimension = cols;
			int ks = Math.min(np, dimension);
			int ns = Math.max(pdata.getNumberControlPoints(), ks);

			controlPoints = new int[ns];

			// 2. Calcule SVD in xt [U, S, V]
			Matrix V = x.transpose();			
			SingularValueDecomposition svd = new SingularValueDecomposition();
			svd.svd(V);

			// Transpose SVD 
			Matrix vt = svd.getVMatrix().transpose();

			// 3. Calcule control points (indices) 
			double[] p = new double[np];  
			int[] indices = new int[np];    //controlpoints

			for (int j = 0; j < np; j++)
			{
				double norm = 0;
				for (int i = 0; i < ks; i++)
				{
					double vj = vt.getRow(j).getValue(i); //(j, i);//(i, j);
					norm += vj * vj;
				}
				p[j] = norm;
				indices[j] = j;
			}

			QuickSort.sort(p, indices, 0, np - 1);        
			System.arraycopy(indices, 0, controlPoints, 0, ns); 

			if ( logger.isInfoEnabled() ) {
				StringBuilder sb = new StringBuilder("index control points...");
				for (int k = 0; k < controlPoints.length; k++) {
					sb.append(controlPoints[k]);
					sb.append(" ");
				}
				logger.info(sb.toString());
			}
		}

		Matrix sampledata_aux;		
		sampledata_aux = MatrixFactory.getInstance(x.getClass());
		if ( sampledata_aux == null) {
			logger.error("Can't create new matrix of class: " + x.getClass());
			return null;
		}	
		
		for (int i = 0; i < controlPoints.length; i++) 		
			sampledata_aux.addRow(x.getRow(controlPoints[i]));

		return sampledata_aux;
	}



	public static void main(String[] args) throws IOException {

		ProjectionData pdata = new ProjectionData();
		pdata.setControlPointsChoice(ControlPointsType.KMEANS);
		pdata.setDissimilarityType(DissimilarityType.EUCLIDEAN);
		pdata.setProjectorType(ProjectorType.FASTMAP);
		pdata.setNumberIterations(50);
		pdata.setFractionDelta(8.0f);	
		pdata.setPercentage(1.0f);
		pdata.setSourceFile("/Volumes/external/Mac/home-backup/jose/Documents/freelancer/petricaep/projectionexplorer/all_reduced.data");
		//		pdata.setSourceFile("/Users/jose/Documents/freelancer/petricaep/projectionexplorer/diabetes-normcols.data-notnull.data-NORM.data");
//		pdata.setSourceFile("/Users/jose/Documents/freelancer/petricaep/ep-project/ep-db/documents200k.data");

		Matrix matrix = MatrixFactory.getInstance(pdata.getSourceFile());		
		pdata.setNumberControlPoints((int) Math.sqrt(matrix.getRowCount()));
		Lamp lamp = new Lamp();
		float[] proj = lamp.project(matrix, pdata);							

		save("output-lamp.prj", proj);		
	}	

	public static void save(String filename, float[] proj) throws IOException {
		BufferedWriter out = null;
		final int size = proj.length / 2; 
		try {
			out = new BufferedWriter(new FileWriter(filename));

			//Writting the file header
			out.write("DY\r\n");
			out.write(Integer.toString(size));
			out.write("\r\n");
			out.write(Integer.toString(2));
			out.write("\r\n");

			//Writting the attributes
			out.write("x;y");                                   
			out.write("\r\n");            

			//writting the vectors            
			for (int i = 0; i < size; i++) {
				out.write(Integer.toString(i));				
				out.write(";");
				out.write(Float.toString(proj[i]));
				out.write(";");
				out.write(Float.toString(proj[i + size]));				
				out.write(";1.0");
				out.write("\r\n");
			}

		} catch (IOException ex) {
			throw new IOException("Problems written \"" + filename + "\"!");
		} finally {
			//close the file
			if (out != null) {
				try {
					out.flush();
					out.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		}
	}
}