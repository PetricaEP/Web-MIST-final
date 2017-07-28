/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ep.db.quadtree;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JFrame;

import ep.db.database.Database;
import ep.db.database.DatabaseService;
import ep.db.database.DefaultDatabase;
import ep.db.model.Document;
import ep.db.model.IDocument;
import ep.db.utils.Configuration;

/**
 *
 * @author Márcio Peres
 */
public class InfoVis {

    static MouseEvent mouseClick;

    static Vec2 qTreeSize = new Vec2(2, 2);
    static Vec2 qTreeP0 = new Vec2(-1, -1);
    static Point panelSize = new Point(1000, 500);
    static int depth = 14;
    static int numberOfObjects = 2000;
    static float radius = 0.2f;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {

    	Configuration config = Configuration.getInstance();
		try {
			config.loadConfiguration();
		} catch (IOException e) {
			System.err.println("Error reading configuration: ");
			e.printStackTrace();
			return;
		}
		
		Database db = new DefaultDatabase(config);
		DatabaseService dbService = new DatabaseService(db);
		
        QuadTree qTree = new QuadTree(new Bounds(new Vec2(qTreeP0), new Vec2(qTreeSize)),
        		config.getQuadTreeMaxDepth(),
        		config.getQuadTreeMaxElementsPerBunch(), 
        		config.getQuadTreeMaxElementsPerLeaf(),
        		dbService);
//        dbService.loadQuadTree(qTree);
        List<Document> docs = dbService.getAllSimpleDocuments();
        for(Document d : docs)
        	qTree.addElement(d);
        
        desenha(qTree, panelSize.x, panelSize.y);
    }

    public static void desenha(QuadTree quadTree, int panelWidth, int panelHeight) {
        Points points = new Points(quadTree, panelWidth, panelHeight);
        JFrame frame = new JFrame("Points");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(points);
        frame.setSize(panelWidth + 20, panelHeight + 50);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        frame.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                mouseClick = e;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                Point pointClick = new Point((int) mouseClick.getPoint().getX() - 9, (int) mouseClick.getPoint().getY() - 31);
                Point pointRelease = new Point((int) e.getPoint().getX() - 9, (int) e.getPoint().getY() - 31);
                Vec2 pointClickReal = new Vec2(mapX((int) pointClick.getX()), mapY((int) pointClick.getY()));
                Vec2 pointReleaseReal = new Vec2(mapX((int) pointRelease.getX()), mapY((int) pointRelease.getY()));
                clickedPointToLeftDownRightUp(pointClickReal, pointReleaseReal);
                clickedPointToLeftDownRightUp(pointClick, pointRelease);

                List<IDocument> selectedDocuments = new ArrayList<>();
                List<QuadTreeNode> selectedNodes = new ArrayList<>();
                //quadTree.findInRectangle(new Bounds(pointClickReal, new Vec2(pointReleaseReal.x - pointClickReal.x, pointReleaseReal.y - pointClickReal.y)), selectedElements, selectedNodes);
                selectedDocuments = quadTree.findInRectangleByRank(new Bounds(pointClickReal, new Vec2(pointReleaseReal.x - pointClickReal.x, pointReleaseReal.y - pointClickReal.y)), selectedDocuments, selectedNodes, 1);

                points.setSelectedList(selectedDocuments);
                points.setSelectedNodes(selectedNodes);
                points.setRectangleP1(pointClick);
                points.setRectangleP2(pointRelease);
                points.repaint();

            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.isControlDown()) {
                    double[][][] grid = grid(32, 32, qTreeP0, qTreeSize, quadTree);
                    points.setGrid(grid);
                    points.repaint();
                } else {
                    //Círculo
                    Point mouseClick = new Point((int) e.getPoint().getX() - 9, (int) e.getPoint().getY() - 31);
                    Vec2 pReal = new Vec2(mapX((int) mouseClick.getX()), mapY((int) mouseClick.getY()));
                    System.out.println("Click -> Original: " + mouseClick.toString() + " Original: " + pReal.toString());
                    pReal.x = pReal.x - 0.2f;
                    pReal.y = pReal.y - 0.2f;
                    Bounds rectangle = new Bounds(pReal,new Vec2(0.4f,0.4f));
                    List<IDocument> selectedList = new ArrayList<>();
                    List<QuadTreeNode> selectedNodes = new ArrayList<>();
                    quadTree.findInRectangle(rectangle, selectedList, selectedNodes);
//                    quadTree.findNeighbors(pReal, radius, selectedList, selectedNodes, 0);
                    points.setSelectedList(selectedList);
                    points.setSelectedNodes(selectedNodes);
                    for(int i = 0; i < selectedList.size(); i++){
                    	System.out.println("Selected: " + selectedList.get(i).getX() + ", " + 
                    			selectedList.get(i).getY());
                    }
                    points.setP(mouseClick);
                    points.setRadius(radius);
                    points.repaint();
                }
            }
        });
    }

    private static void clickedPointToLeftDownRightUp(Vec2 leftDown, Vec2 rightUp) {
        if (leftDown.x > rightUp.x) {
            float x = leftDown.x;
            leftDown.x = rightUp.x;
            rightUp.x = x;
        }
        if (leftDown.y > rightUp.y) {
            float y = leftDown.y;
            leftDown.y = rightUp.y;
            rightUp.y = y;
        }
    }

    private static void clickedPointToLeftDownRightUp(Point leftUp, Point rightDown) {
        if (leftUp.x > rightDown.x) {
            int x = leftUp.x;
            leftUp.x = rightDown.x;
            rightDown.x = x;
        }
        if (leftUp.y > rightDown.y) {
            int y = leftUp.y;
            leftUp.y = rightDown.y;
            rightDown.y = y;
        }
    }

    static float mapX(int x) {
        return (qTreeSize.x * ((x / (float) panelSize.x) - 0.5f));
    }

    static float mapY(int y) {
        return (qTreeSize.y * (0.5f - (y / (float) panelSize.y)));
    }

    private static QuadTree testRandomPointTree(Vec2 p0, Vec2 size, int max_depth, int numberOfPoints) {
        QuadTree qTree = new QuadTree(new Bounds(new Vec2(p0), new Vec2(size)), null);

        Random rand = new Random();
        int i = 0;

        while (true) {
            int nPointsLocal = rand.nextInt(numberOfPoints) / 8;
            float xMinRegion = rand.nextFloat() * (size.x - 0.5f) + p0.x;
            float yMinRegion = rand.nextFloat() * (size.y - 0.5f) + p0.y;
            float xMaxRegion = xMinRegion + rand.nextFloat() * 0.5f;
            float yMaxRegion = yMinRegion + rand.nextFloat() * 0.5f;

            for (int j = 0; j < nPointsLocal; j++) {
                IDocument d = new Document(i++, rand.nextFloat(),
                        rand.nextFloat() * (xMaxRegion - xMinRegion) + xMinRegion,
                        rand.nextFloat() * (yMaxRegion - yMinRegion) + yMinRegion);
                qTree.addElement(d);
            }
            if (i >= numberOfPoints * 0.8f) {
                break;
            }
        }

        for (; i < numberOfPoints; i++) {
            IDocument d = new Document(i, rand.nextFloat(), rand.nextFloat() * size.x + p0.x, rand.nextFloat() * size.y + p0.y);
            qTree.addElement(d);
        }

        return qTree;
    }

    private static QuadTree testStaticTree(Vec2 P0, Vec2 size, int max_depth) {
    	
        QuadTree qTree = new QuadTree(new Bounds(new Vec2(P0), new Vec2(size)), null);

        qTree.addElement(new Document(1, 0.4f, -0.99f, 0.89f));
        qTree.addElement(new Document(1, 0.4f, -0.99f, 0.88f));
        qTree.addElement(new Document(1, 0.4f, -0.99f, 0.87f));
        qTree.addElement(new Document(1, 0.4f, -0.99f, 0.86f));
        qTree.addElement(new Document(1, 0.4f, -0.99f, 0.85f));
        qTree.addElement(new Document(1, 0.4f, -0.9f, -0.1f));
        qTree.addElement(new Document(1, 0.4f, 0.9f, 0.8f));
        qTree.addElement(new Document(1, 0.4f, 0.1f, 0.1f));

        return qTree;
    }

    private static double[][][] grid(int m, int n, Vec2 p0, Vec2 size, QuadTree qTree) {
        Vec2 resolution = new Vec2(size.x / (m - 1), size.y / (n - 1)); //Divide por 1 a menos para atingir os dois extremos.
        double[][][] grid = new double[m][n][10];
        Vec2 p = new Vec2(p0.x, p0.y + size.y);//Canto esquerdo superior
        for (int i = 0; i < grid.length; i++) {
            p.x = p0.x;
            for (int j = 0; j < grid[i].length; j++) {
                for (int k = 0; k < 10; k++) {
                    grid[i][j][k] = qTree.findNeighbors(p, resolution.x, k*0.1f);
                }
                p.x += resolution.x;
            }
            p.y -= resolution.y;
        }
        return grid;
    }
}
