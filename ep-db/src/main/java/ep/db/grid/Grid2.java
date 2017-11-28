/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ep.db.grid;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ep.db.model.IDocument;
import ep.db.quadtree.Bounds;
import ep.db.quadtree.QuadTree;
import ep.db.quadtree.Vec2;

/**
 *
 * @author Márcio Peres
 */
public class Grid2 {

	private double data[];
	private Vec2 points[];
	private int width;
	private int height;

	public Grid2(int width, int height) {
		this.width = width;
		this.height = height;
		data = new double[width * height];
		points = new Vec2[width * height];
	}

	public double[] getData() {
		return data;
	}

	public Vec2[] getPoints() {
		return points;
	}

	public void printData(){
		System.out.println("\nData:");
		for (int i = 0; i < data.length; i++) {
			if ( i % width == 0)
				System.out.printf("\n");
			String d = String.format("%.6f", data[i]).replace(',', '.');
			System.out.printf("%s, ", d);                
		}

	}

	public void evaluate(QuadTree qTree, Kernel k) {

		Bounds bounds = qTree.boundingBox();

		//Para o calculo dos pontos será considerado dois tamanhos de células (caso width e height forem diferentes)
		//Porém para o raio será considerado o tamanho em X
		float cellSizeX = bounds.size().x / (width - 1);
		float cellSizeY = bounds.size().y / (height - 1);

		//Canto superior esquerdo
		Vec2 p0 = new Vec2(bounds.getP1().x, bounds.getP2().y);

		Vec2 p = new Vec2(p0);
		for (int i = 0; i < data.length; i++) {
			points[i] = new Vec2(p);
			data[i] = evaluatePointQuadTree(qTree, cellSizeX, k, p);
			p.x += cellSizeX;

			if ( i % width == 0){
				p.y -= cellSizeY;
				p.x = p0.x;
			}
		}
	}

	private float evaluatePointQuadTree(QuadTree qTree, double bandwidth, Kernel k, Vec2 p) {

		List<IDocument> elements = new ArrayList<>();
		//Buscando os elementos próximos do ponto p
		qTree.findNeighbors(p, bandwidth, elements, new ArrayList<>(), 0f);

		if(elements.isEmpty())
			return 0;

		float valueAtP = 0;
		for (IDocument element : elements) {
			float distance = (float) Vec2.distance(p, element.getPos());
			valueAtP += k.eval( (float) (distance / bandwidth));
		}
		return valueAtP;// / (elements.size() * bandwidth);
	}

	public void evaluate(List<Vec2> values, Bounds bounds, Kernel k) {

		//Para o calculo dos pontos será considerado os tamanhos em X e em Y
		float cellSizeX = bounds.size().x / (width - 1);
		float cellSizeY = bounds.size().y / (height - 1);

		List<Vec2> valuesGrid[][] = new ArrayList[width - 1][height - 1];
//		for (int i = 0; i < valuesGrid.length; i++) {
//			for (int j = 0; j < valuesGrid[i].length; j++) {
//				valuesGrid[i][j] = new ArrayList<>();
//			}            
//		}

		//Canto superior esquerdo
		Vec2 p0 = new Vec2(bounds.getP1().x, bounds.getP1().y);
		Vec2 p = new Vec2(p0);
		double scaled = values.size() * cellSizeX * cellSizeX ;
		for (int i = 0; i < valuesGrid.length; i++) {
            for (int j = 0; j < valuesGrid[i].length; j++) {
            	float px = bounds.getP1().x + cellSizeX * j;
            	float py = bounds.getP1().y + cellSizeY * i;	
                points[i*height + j] = new Vec2(px,py);
                double sum = 0.0;
                for(int l = 0; l < values.size(); l++){
                	Vec2 v = values.get(l);
                	float ux = (px - v.x) / cellSizeX;
                	float uy = (py - v.y) / cellSizeX;
                	double distance = Vec2.distance(v, new Vec2(ux,uy));
                	sum += k.eval(distance);
                }
                data[i*height + j] = sum / scaled;
            }
        }
	}

	private double evaluatePointListGrid(List<Vec2> valuesGrid[][], int i, int j, float bandwidth, Kernel k, Vec2 p) {

		//Select values from list "valuesCell", adding in list "values" those at least bandwidth from "p"
		//Their distances are added in list "distances"
		List<Double> distances = new ArrayList<>();
		if (i > 0 && j > 0) { //top left cell
			selectPointListCell(valuesGrid[i - 1][j - 1], bandwidth, p, distances);
		}
		if (i < (width-1) && j > 0) { //bottom left cell
			selectPointListCell(valuesGrid[i][j - 1], bandwidth, p, distances);
		}
		if (i > 0 && j < (height-1)) { //top right cell
			selectPointListCell(valuesGrid[i - 1][j], bandwidth, p, distances);
		}
		if (i < (width-1) && j < (height-1)) { //bottom right cell
			selectPointListCell(valuesGrid[i][j], bandwidth, p, distances);
		}

		if(distances.isEmpty())
			return 0;

		//compute the value at point p, use the pré-computed distances
		double valueAtP = 0;
		for (double distance : distances) {
			valueAtP += k.eval(distance / bandwidth);
		}
		return valueAtP;// / (distances.size() * bandwidth);
	}

	//Select values from list "valuesCell", adding in list "values" those at least bandwidth from "p"
	private void selectPointListCell(List<Vec2> valuesCell, float bandwidth, Vec2 p, List<Double> distances) {
		for (Vec2 value : valuesCell) {
			double distance = Vec2.distance(p, value);
			if (distance < bandwidth) {
				distances.add(distance);
			}
		}
	}
	
	public static void main(String[] args) {
		Grid2 grid = new Grid2(50, 50);
//		EpanechnikovKernel k = new EpanechnikovKernel(7);
		GaussianKernel k = new GaussianKernel();
		List<Vec2> values = new ArrayList<>();
		
		try( BufferedReader br = new BufferedReader(
				new FileReader(new File("/Users/jose/Documents/freelancer/petricaep/points50.csv")))){
			String line = null;
			line = br.readLine();
			while ( line != null){
				String[] f = line.split("\\s*,\\s*");
				Vec2 p = new Vec2(Float.parseFloat(f[0]), Float.parseFloat(f[1]));
				values.add(p);
				line = br.readLine();
			}
				
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
				
		Bounds bounds = new Bounds(-1,-1,1,1);
		grid.evaluate(values, bounds, k);
		grid.printData();
	}

	public static float[] readDensities() {
		float[] densities = new float[1024*1024];
		try( BufferedReader br = new BufferedReader(
				new FileReader(new File("/Users/jose/Documents/freelancer/petricaep/densities.csv")))){
			String line = null;
			line = br.readLine();
			int i  = 0;
			while ( line != null & i < 1024*1024){
				String[] f = line.split("\\s*,\\s*");
				for( String v : f){
					densities[i] = Float.parseFloat(v.trim());
					++i;
				}
				line = br.readLine();
			}
				
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		
		return densities;
	}
}
