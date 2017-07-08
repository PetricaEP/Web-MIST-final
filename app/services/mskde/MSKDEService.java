package services.mskde;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.FileReader;

import javax.inject.Inject;
import javax.swing.JFrame;
import javax.swing.JPanel;

import com.google.common.collect.ImmutableMap;

import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.colt.matrix.tdouble.impl.DenseDoubleMatrix2D;
import ep.db.database.DatabaseService;
import play.Logger;
import play.db.Database;
import play.db.Databases;
import services.database.PlayDatabaseWrapper;
import services.kde.QuarticKernelFunction;

public class MSKDEService {

	private DatabaseService dbService;

	private int gridSize = 151;

	private double bandwidth = 3;

	private int numClusters = 4;

	@Inject
	public MSKDEService(Database db) {
		dbService = new DatabaseService(new PlayDatabaseWrapper(db));
	}


	public void calculate(){
		DoubleMatrix2D xy;
		try {
//			xy = dbService.getProjections(null, null);
		} catch (Exception e) {
			Logger.error("Error retriving projection from DB", e);
			return;
		}
		
//		File file = new File("geyser.csv");
//		try (BufferedReader br = new BufferedReader(new FileReader(file))){
//			String line = null;
//			line = br.readLine();
//			
//			int i = 0;
//			while( line != null){
//				String[] f = line.split(",");
//				xy.setQuick(i, 0, Double.parseDouble(f[0]));
//				xy.setQuick(i, 1, Double.parseDouble(f[1]));
//				line = br.readLine();
//				++i;
//			}
//		}catch (Exception e) {
//			
//		}
		
		double[][] data = new double[110][2];
		int i = 0;
		try ( BufferedReader br = new BufferedReader(new FileReader("docs_xy.csv"))){
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
		
		 xy = new DenseDoubleMatrix2D(data);

		MSKDE mskde = new MSKDE(new QuarticKernelFunction(), gridSize, numClusters);
		GeneralPath[] contours = mskde.compute(xy);

		final Color[] colors = generatePallete(contours.length);

		JFrame frame = new JFrame("MSKDE");
		JPanel panel = new JPanel();

		frame.getContentPane().add(panel);
		frame.pack();
		frame.setSize(800, 600);
		frame.setVisible(true);

		Graphics2D g2 = (Graphics2D) panel.getGraphics();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		AffineTransform xf = new AffineTransform();
//		xf.translate(-11, -1);
		xf.scale(3, 3);
		for (i = 0; i < contours.length; i++) {
			Shape iso = xf.createTransformedShape(contours[i]); // Remapped every pan & zoom.
			g2.setColor(colors[i]);
			g2.fill(iso);
			g2.setColor(Color.gray);
			g2.draw(iso);
		}
		
		g2.setColor(Color.black);
		for (i = 0; i < xy.rows(); i++) {
			Point2D point = new Point2D.Double(xy.getQuick(i, 0), xy.getQuick(i, 1));
			Rectangle2D rect = new Rectangle2D.Double(point.getX() - 0.5, point.getY() - 0.5, 1, 1);
			g2.fill(xf.createTransformedShape(rect));
		}
	}

	private Color[] generatePallete(int n) {
		Color[] cols = new Color[n];
		for (int i = 0; i < n; i++)
			cols[i] = Color.getHSBColor((float) i / n, 1, 1);
		return cols;
	}


	public static void main(String[] args) {

		play.db.Database db = Databases.createFrom("org.postgresql.Driver", "jdbc:postgresql://localhost/petrica_db", 
				ImmutableMap.of(
						"username", "postgres",
						"password", "kurt1234"
						));
		MSKDEService mskdeService = new MSKDEService(db);
		mskdeService.calculate();
	}
}
