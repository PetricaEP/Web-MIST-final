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

import ep.db.model.IDocument;
import ep.db.quadtree.Bounds;
import ep.db.quadtree.QuadTree;
import ep.db.quadtree.Vec2;

/**
 *
 * @author Márcio Peres
 */
public class Grid {

	private float data[];
	private Vec2 points[];
	private int width;
	private int height;
	private float cellSizeX;
	private float cellSizeY;
	private float bandwidth = 10;

	public Grid(int width, int height, Bounds bounds) {
		this.width = width;
		this.height = height;
		data = new float[width*height];
		points = new Vec2[width*height];

		cellSizeX = bounds.size().x / (height - 1);
		cellSizeY = bounds.size().y / (width- 1);

		//Canto superior esquerdo
		Vec2 p0 = new Vec2(bounds.getP1().x, bounds.getP1().y);

		//Creating Grid points from bound of qTree and bandwidth
		createPoints(p0);  
	}

	public float[] getData(){
		return data;
	}

//	public float[] getData(Bounds bounds, int[] gridSize) {
//		Vec2 p0 = points[0];
//		int i1 = (int) (Math.abs(bounds.getP1().x - p0.x) / cellSizeX);
//		int j1 = (int) (Math.abs(bounds.getP1().y - p0.y) / cellSizeY);
//		int i2 = (int) (Math.abs(bounds.getP2().x - p0.x) / cellSizeX);
//		int j2 = (int) (Math.abs(bounds.getP2().y - p0.y) / cellSizeY);
//		int height = j1 - j2;
//		int width = i2-i1;
//
//		float[] data = new float[width * height];
//		for(int i = i1, k = 0; i < i2; i++ ){
//			for(int j = j1; j < j2; j++, k++)
//				data[k] = this.data[i*height + j];
//		}
//
//		if ( gridSize != null && gridSize.length == 2){
//			gridSize[0] = width;
//			gridSize[1] = height;
//		}
//
//		return data;
//	}

	public Vec2[] getPoints() {
		return points;
	}

	public void printData() {
		System.out.println("\nData:");
		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				String d = String.format("%.4f", data[j + i*width]).replace(',', '.');
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
	
	public void printPoints(String filePath, List<Vec2> values) {
		try {
			Writer writer = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(filePath), "utf-8"));

			for (Vec2 v : values) {
					String d = String.format("%e, %e", v.x, v.y);
					writer.write(d);
					writer.write("\n");
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
				data[i*height + j] = evaluatePointQuadTree(qTree, cellSizeX, k, points[i*width + j]);
			}
		}
	}

	private float evaluatePointQuadTree(QuadTree qTree, float bandwidth, Kernel k, Vec2 p) {

		List<IDocument> elements = new ArrayList<>();
		//Buscando os elementos próximos do ponto p
		qTree.findNeighbors(p, bandwidth, elements, new ArrayList<>(), 0);

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

//		double[] xValues = new double[values.size()];
//		final int size = values.size();
//		for(int i = 0; i < size; i++)
//			xValues[i] = values.get(i).x;
//		
//		//Calculate bandwidth based on standard deviation of data
//		StandardDeviation std = new StandardDeviation();
//		final double stdDev = std.evaluate(xValues);
//		bandwidth = (float) (stdDev * 2.20 * Math.pow(size, -1/5));
		
		List<Vec2> pointListCellGrid[][] = new ArrayList[height - 1][width - 1];
		createPointListCellGrid(values, pointListCellGrid);

		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				data[i*width+ j] += evalDeval * evaluatePointGrid(pointListCellGrid, i, j, k) / (values.size() * bandwidth);
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
//			if (distance < bandwidth) {
				distances.add(distance);
//			}
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
}
