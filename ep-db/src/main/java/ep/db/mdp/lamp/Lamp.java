package ep.db.mdp.lamp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.jblas.FloatMatrix;
import org.jblas.Singular;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ep.db.mdp.clustering.BKMeans;
import ep.db.mdp.dissimilarity.Dissimilarity;
import ep.db.mdp.dissimilarity.DissimilarityType;
import ep.db.mdp.dissimilarity.Euclidean;
import ep.db.mdp.projection.ControlPointsType;
import ep.db.mdp.projection.IDMAPProjection;
import ep.db.mdp.projection.ProjectionData;
import ep.db.mdp.projection.ProjectorType;
import ep.db.utils.QuickSort;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;

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
	 * @return matriz de projeção multimensional (N x 2).
	 */
	public float[] project(FloatMatrix x, ProjectionData pdata){

		long start = System.currentTimeMillis();

		FloatMatrix xs = getSampleData(x, pdata);
		if ( xs == null )
			return null;
		sampledata  = xs.toArray2();

		IDMAPProjection idmap = new IDMAPProjection();
		sampleproj = idmap.project(xs, pdata);

		int n = x.rows;
		float[] proj_aux = new float[n * 2];

		int nrpartitions = nrthreads * 4; //number os pieces to split the data
		int step = (int) Math.ceil( (float) n / nrpartitions);
		int begin = 0, end = 0;
		ArrayList<ParallelSolver> threads = new ArrayList<ParallelSolver>();
		final ProgressBar pb = new ProgressBar("LAMP", n, 1000, System.out, ProgressBarStyle.ASCII);

		for (int i = 0; i < nrpartitions && begin < n; i++) 
		{
			end += step;
			end = (end > n) ? n : end;

			ParallelSolver ps = new ParallelSolver(pdata, proj_aux, sampledata, sampleproj, x, begin, end, pb, epsilon);
			threads.add(ps);
			logger.info("Partition: " + begin + " - " + end);
			begin = end;
		}

		if (end < n) {
			ParallelSolver ps = new ParallelSolver(pdata, proj_aux, sampledata, sampleproj, x, end, n, pb, epsilon);			
			threads.add(ps);
			logger.info("Partition: " + end + " - " + n);
		}

		try {
			pb.start();
			ExecutorService executor = Executors.newFixedThreadPool(nrthreads);
			executor.invokeAll(threads);
			executor.shutdownNow();			
			pb.stepTo(pb.getMax());
			pb.stop();
		} catch (InterruptedException ex) {
			logger.error("LAMP calculation was interrupeted:", ex);
		}

		long finish = System.currentTimeMillis();
		logger.info("Local Affine Multidimensional Projection (LAMP) time: {0}s", (finish - start) / 1000.0f);

		return proj_aux;
	}

	private FloatMatrix getSampleData(FloatMatrix x, ProjectionData pdata) {

		if ( pdata.getControlPointsChoice() == ControlPointsType.KMEANS) {
			Dissimilarity diss = new Euclidean();
			//clustering points
			BKMeans bkmeans = new BKMeans(pdata.getNumberControlPoints(), nrthreads);
			ArrayList<ArrayList<Integer>> clusters;
			clusters = bkmeans.execute(diss, x);
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
					if (clusters.get(c).size() > x.rows / pdata.getNumberControlPoints()) {
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
			controlPoints = IntStream.range(0, x.rows).distinct().sorted()
					.limit(pdata.getNumberControlPoints()).toArray();
		}
		else { // pdata.getControlPointsChoice() == ControlPointsType.REPRESENTATIVE
			// Replicate code from TH
			int cols = x.columns;
			int lines = x.rows;
			int np = cols;
			int dimension = lines;
			int ks = Math.min(np, dimension);
			int ns = Math.max(pdata.getNumberControlPoints(), ks);

			controlPoints = new int[ns];

			// 2. Calcule SVD in xt [U, S, V]
			FloatMatrix[] svd = Singular.fullSVD(x.transpose());

			// Transpose SVD 
			FloatMatrix vt = svd[2].transpose();

			// 3. Calcule control points (indices) 
			double[] p = new double[np];  
			int[] indices = new int[np];    //controlpoints

			for (int j = 0; j < np; j++)
			{
				double norm = 0;
				for (int i = 0; i < ks; i++)
				{
					double vj = vt.get(j, i);//(i, j);
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

		FloatMatrix sampledata_aux = new FloatMatrix(controlPoints.length, x.columns);
		sampledata_aux.copy(x.getRows(controlPoints));
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
//		pdata.setSourceFile("/Users/jose/Documents/freelancer/petricaep/projectionexplorer/all_reduced.data");
//		pdata.setSourceFile("/Users/jose/Documents/freelancer/petricaep/projectionexplorer/diabetes-normcols.data-notnull.data-NORM.data");
		pdata.setSourceFile("/Users/jose/Documents/freelancer/petricaep/ep-project/ep-db/documents100.data");

		FloatMatrix matrix = load(pdata.getSourceFile());
		pdata.setNumberControlPoints((int) Math.sqrt(matrix.rows));
		Lamp lamp = new Lamp();
		float[] proj = lamp.project(matrix, pdata);		
		FloatMatrix projM = new FloatMatrix(proj);		
		projM = projM.reshape(matrix.rows, 2);
		save("output-lamp.prj", projM.toArray2());		
	}
	
	public static void save(String filename, float[][] proj) throws IOException {
        BufferedWriter out = null;

        try {
            out = new BufferedWriter(new FileWriter(filename));

            //Writting the file header
            out.write("DY\r\n");
            out.write(Integer.toString(proj.length));
            out.write("\r\n");
            out.write(Integer.toString(proj[0].length));
            out.write("\r\n");

            //Writting the attributes
            out.write("x;y");                                   
            out.write("\r\n");            

            //writting the vectors            
            for (int i = 0; i < proj.length; i++) {
            		out.write(Integer.toString(i));
            		for(int j = 0; j < proj[0].length; j++) {
            			out.write(";");
            			out.write(Float.toString(proj[i][j]));
            		}
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

	public static FloatMatrix load(String filename) throws IOException {
		List<String> attributes = new ArrayList<String>();

		BufferedReader in = null;
		FloatMatrix matrix = null;
		try {
			in = new BufferedReader(new FileReader(filename));

			//read the header
			char[] header = in.readLine().trim().toCharArray();

			//checking
			if (header.length != 2) {
				throw new IOException("Wrong format of header information.");
			}

			//read the number of objects
			int nrobjs = Integer.parseInt(in.readLine());

			//read the number of dimensions
			int nrdims = Integer.parseInt(in.readLine());

			matrix = new FloatMatrix(nrobjs, nrdims);

			//read the attributes
			String line = in.readLine();
			StringTokenizer t1 = new StringTokenizer(line, ";");

			while (t1.hasMoreTokens()) {
				String token = t1.nextToken();
				attributes.add(token.trim());
			}

			//checking
			if (attributes.size() > 0 && attributes.size() != nrdims) {
				throw new IOException("The number of attributes does not match "
						+ "with the dimensionality of matrix (" + attributes.size()
						+ " - " + nrdims + ").");
			}

			//read the vectors
			int row = 0;
			while ((line = in.readLine()) != null && line.trim().length() > 0) {
				StringTokenizer t2 = new StringTokenizer(line, ";");

				//read the id
				t2.nextToken().trim();

				//the vector
				float[] vector = new float[nrdims];

				int index = 0;
				while (t2.hasMoreTokens()) {
					String token = t2.nextToken();
					float value = Float.parseFloat(token.trim());

					if (header[1] == 'Y') {
						if (t2.hasMoreTokens()) {
							if (index < nrdims) {
								vector[index] = value;
								index++;
							} else {
								throw new IOException("Vector with wrong number of "
										+ "dimensions!");
							}
						}
					} else if (header[1] == 'N') {
						if (index < nrdims) {
							vector[index] = value;
							index++;
						} else {
							throw new IOException("Vector with wrong number of "
									+ "dimensions!");
						}
					} else {
						throw new IOException("Unknown class data option");
					}
				}

				matrix.putRow(row, new FloatMatrix(vector));
				++row;
			}

			//checking
			if (matrix.rows != nrobjs) {
				throw new IOException("The number of vectors does not match "
						+ "with the matrix size (" + matrix.rows
						+ " - " + nrobjs + ").");
			}

		} catch (FileNotFoundException e) {
			throw new IOException("File " + filename + " does not exist!");
		} catch (IOException e) {
			throw new IOException(e.getMessage());
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ex) {
					logger.error(null, ex);
				}
			}
		}

		return matrix;
	}
}