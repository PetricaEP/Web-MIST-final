package ep.db.utils;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import ep.db.model.IDocument;
import ep.db.quadtree.InfoVis;
import ep.db.quadtree.QuadTree;
import ep.db.quadtree.Vec2;

public class SinteticDataGenerator {

	public static void main(String[] args) {
		int max_depth, numberOfPoints, numberOfRegionsX, numberOfRegionsY;
		String outputfile;
		
		if ( args.length != 5 ){
			System.err.println("Wrong number of parameters!!!");
			System.err.println("Usage: SinteticDataGenerator maxDepth totalNumberOfPoints numberOfRegionsX numberofRegionsY outputFile.csv");
			return;
		}
		else{
			max_depth = Integer.parseInt(args[0]);
			numberOfPoints = Integer.parseInt(args[1]);
			numberOfRegionsX = Integer.parseInt(args[2]);
			numberOfRegionsY = Integer.parseInt(args[3]);
			outputfile = args[4];
		}

		Vec2 p0 = new Vec2(-1, -1);
		Vec2 size = new Vec2(2,2);
		List<IDocument> documents = new ArrayList<>();

		QuadTree quadTree = InfoVis.testRandomPointTree(p0, size, max_depth, numberOfPoints, documents, 
				numberOfRegionsX, numberOfRegionsY);

		File documentsData = new File(outputfile);
		try( BufferedWriter bw = new BufferedWriter(new FileWriter(documentsData))){
			for( IDocument d : documents){
				bw.write(String.format("%d,%f,%f%f", d.getId(), d.getX(), d.getY(), d.getRank()));
				bw.newLine();
			}
		}catch (IOException e) {
			e.printStackTrace();
		}

		JFrame frame = InfoVis.desenha(quadTree, 1600, 900);

		try
		{
			BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D graphics2D = image.createGraphics();
			frame.paint(graphics2D);
			ImageIO.write(image,"jpeg", new File(outputfile+".jpg"));
		}
		catch(Exception exception)
		{
			exception.printStackTrace();
		}
		
//		frame.dispose();
//		System.exit(0);
	}

}
