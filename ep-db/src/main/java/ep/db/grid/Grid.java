/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ep.db.grid;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.math3.stat.descriptive.rank.Median;

import ep.db.model.IDocument;
import ep.db.quadtree.Bounds;
import ep.db.quadtree.QuadTree;
import ep.db.quadtree.Vec2;

/**
 *
 * @author Márcio Peres
 */
public class Grid {

	private static final float sqtr1Ln2 = (float)Math.sqrt(1/Math.log(2));
	
	private float data[];
	private Vec2 points[];
	private int width;
	private int height;
	private float bandwidth;
	private float cellSizeX;
	private float cellSizeY;

	public Grid(Bounds bounds, float bandwidth) {
		this.bandwidth = bandwidth;

		this.width = (int) Math.ceil(bounds.size().y / bandwidth) + 1;
		this.height =  width * 6 / 16;
//		this.height = (int) Math.ceil(bounds.size().x / bandiwidth) + 1;
		
		cellSizeX = bounds.size().x / (height - 1);
		cellSizeY = bandwidth;

		
		data = new float[height * width];
		points = new Vec2[height * width];



		//Canto superior esquerdo
		Vec2 p0 = new Vec2(bounds.getP1().x, bounds.getP1().y);

		//Creating Grid points from bound of qTree and bandwidth
		createPoints(p0);
	}

	public float[] getData() {
		return data;
	}

	public float[] getDataVec() {
		float[] vecData = new float[width*height];
		for (int i = 0; i < height; i++) {
			System.arraycopy(data[i], 0, vecData, i*width, width);
		}
		return vecData;
	}

	public Vec2[] getPoints() {
		return points;
	}

	public void printData() {
		System.out.println("\nData:");
		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				String d = String.format("%.4f", data[j +i*width]).replace(',', '.');
				System.out.printf("%s, ", d);
			}
			System.out.printf("\n");
		}
	}

	public void printData(String filePath) {
		try {
			Writer writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(filePath), "utf-8"));

			for (int i = 0; i < height; i++) {
				for (int j = 0; j < width; j++) {
					String d = String.format("%e", data[i*width + j]).replace(',', '.');
					writer.write(d + ", ");
				}
			}
			writer.close();
		} catch (Exception ex) {
			Logger.getLogger(Grid.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private void createPoints(Vec2 p0){
		//p0 = top left corner
		Vec2 p = new Vec2(p0);
		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				points[i*width + j] = new Vec2(p.x + cellSizeX/2, p.y + cellSizeY/2);
//				points[i*width + j] = new Vec2(p);
				p.y += cellSizeY;
			}
			p.x += cellSizeX;
			p.y = p0.y;
		}
	}

	//*******************
	//QUADTREE METHOD
	//*******************
	public void evaluate(QuadTree qTree, Kernel k) {

		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				data[i*width + j] = evaluatePointQuadTree(qTree, bandwidth, k, points[i * width + j]);
			}
		}
	}

	private float evaluatePointQuadTree(QuadTree qTree, float bandwidth, Kernel k, Vec2 p) {

		List<IDocument> elements = new ArrayList<>();
		//Buscando os elementos próximos do ponto p
		qTree.findNeighbors(p, bandwidth, elements,  new ArrayList<>(), 0);

		if (elements.isEmpty()) {
			return 0;
		}

		float valueAtP = 0;
		for (IDocument element : elements) {
			float distance = Vec2.distance(p, element.getPos());
			valueAtP += k.eval(distance / bandwidth);
		}
		return valueAtP;// / (elements.size() * bandwidth);
	}

	//*******************
	//GRID METHODS (CELL)
	//*******************
	public void evaluate(List<Vec2> values, Kernel k) {
		valuate(values, k, 1);
	}

	public void devaluate(List<Vec2> values, Kernel k) {
		valuate(values, k, -1);
	}

	private void valuate(List<Vec2> values, Kernel k, int evalDeval) {

		List<Vec2> pointListCellGrid[][] = new ArrayList[height - 1][width - 1];
		createPointListCellGrid(values, pointListCellGrid);

		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				data[i*width + j] += evalDeval * evaluatePointGrid(pointListCellGrid, i, j, k); // / (values.size() * bandwidth);
			}
		}
	}

	//calculate the value of the point (i, j). Lookup for points from 4 neighbor cells, and use that is bandwith from center point
	private float evaluatePointGrid(List<Vec2> pointListCellGrid[][], int i, int j, Kernel k) {

		//Select values from list "valuesCell", adding in list "values" those at least bandwidth from "p"
		//Their distances are added in list "distantes"
		Vec2 p = points[i*width + j];
		List<Float> distances = new ArrayList<>();
		if (i > 0 && j > 0) { //top left cell
			selectPointListCell(pointListCellGrid[i - 1][j - 1], p, distances);
		}
		if (i < (height - 1) && j > 0) { //botton left cell
			selectPointListCell(pointListCellGrid[i][j - 1], p, distances);
		}
		if (i > 0 && j < (width - 1)) { //top right cell
			selectPointListCell(pointListCellGrid[i - 1][j], p, distances);
		}
		if (i < (height - 1) && j < (width - 1)) { //botton right cell
			selectPointListCell(pointListCellGrid[i][j], p, distances);
		}

		if (distances.isEmpty()) {
			return 0;
		}

		//compute the value at point p, use the pré-computed distances
		float valueAtP = 0;
		for (float distance : distances) {
			valueAtP += k.eval(distance / bandwidth);
		}
		return valueAtP;// / (distances.size() * bandwidth);
	}

	//Select distances from values of the list "valuesCell", adding in list "values" those at least bandwidth from "p"
	//the distances will be used to calc the Kernel
	private void selectPointListCell(List<Vec2> pointListCell, Vec2 p, List<Float> distances) {
		for (Vec2 value : pointListCell) {
			float distance = Vec2.distance(p, value);
			if (distance < bandwidth) {
				distances.add(distance);
			}
		}
	}

	//Creating the cell list. Eath point goes to a cell grid
	private void createPointListCellGrid(List<Vec2> values, List<Vec2>[][] pointListCellGrid) {
		//creating cell lists
		for (int i = 0; i < height-1; i++) {
			for (int j = 0; j < width-1; j++) {
				pointListCellGrid[i][j] = new ArrayList<>();
			}
		}

		//corner top-left
		Vec2 p0 = points[0];
		for (Vec2 value : values) {
			int i = (int) (Math.abs(value.x - p0.x) / cellSizeX);
			int j = (int) (Math.abs(value.y - p0.y) / cellSizeY);
			if ( i == height-1) --i;
			if ( j == width-1) --j;
			pointListCellGrid[i][j].add(value);
		}
	}

	//From: http://pro.arcgis.com/en/pro-app/tool-reference/spatial-analyst/how-kernel-density-works.htm#ESRI_SECTION1_B6405A4584AA4250BE7CB071928B60F1
	public static float calcBandWidth(List<Vec2> values) {

		int n = values.size();

		//Calculate the mean center of the input points
		Vec2 mean = new Vec2();
		for (Vec2 value : values) {
			mean.add(value);
		}
		mean.multiply(1f/n);

		//Calculate the distance from the mean center for all points.
		float distanceMedian = 0;
		float stdDistance = 0;        
		double[] distances = new double[n];
		int i = 0;
		for (Vec2 value : values) {
			float squaredDistance = Vec2.squaredDistance(value, mean);
			distances[i] = Math.sqrt(squaredDistance);
			stdDistance += squaredDistance;
			++i;
		}
		
		//Calculate the Standard Distance, SD.
		stdDistance = (float)Math.sqrt(stdDistance/n);

		//Calculate the median of these distances, Dm.
		Median median = new Median();
		distanceMedian = (float) median.evaluate(distances);
//		distanceMedian = distanceTotal/n;

		return 0.9f*Math.min(stdDistance, sqtr1Ln2*distanceMedian)*(float)Math.pow(n, -0.2);        
	}

	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;
	}

}
