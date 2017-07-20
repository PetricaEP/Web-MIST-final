/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ep.db.quadtree;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.List;

import javax.swing.JPanel;

import ep.db.model.IDocument;

/**
 *
 * @author Márcio Peres
 */
public class Points extends JPanel {

    QuadTree quadTree;
    Point size;
    List<IDocument> selectedList;
    List<QuadTreeNode> selectedNodes;
    float radius = 0;
    Point p = new Point(0, 0);
    Point rectangleP1 = new Point(0, 0);
    Point rectangleP2 = new Point(0, 0);
    double[][][] grid;

    public void setGrid(double[][][] grid) {
        this.grid = grid;
    }

    public void setRectangleP1(Point rectangleP1) {
        this.rectangleP1 = rectangleP1;
        radius = 0;
    }

    public void setRectangleP2(Point rectangleP2) {
        this.rectangleP2 = rectangleP2;
        radius = 0;
    }

    public void setSelectedList(List<IDocument> selectedList) {
        this.selectedList = selectedList;
    }

    public void setSelectedNodes(List<QuadTreeNode> selectedNodes) {
        this.selectedNodes = selectedNodes;
    }

    public void setRadius(float radius) {
        this.radius = radius;
        rectangleP1 = new Point(0, 0);
        rectangleP2 = new Point(0, 0);
    }

    public void setP(Point p) {
        this.p = p;
    }

    public Points(QuadTree quadTree, int panelWidth, int panelHeight) {
        super();
        this.quadTree = quadTree;
        size = new Point(panelWidth, panelHeight);
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g;

        renderRectangle(g2d, 0, 0, size.x, size.y, quadTree.getRoot());

        g2d.setColor(Color.blue);
        g2d.drawRect(rectangleP1.x,
                rectangleP1.y,
                rectangleP2.x - rectangleP1.x,
                rectangleP2.y - rectangleP1.y);

        g2d.drawOval(
                (int) p.getX() - mapRadiusX(radius),
                (int) p.getY() - mapRadiusY(radius),
                mapRadiusX(radius) * 2, mapRadiusY(radius) * 2);

        if (grid != null) {
            Vec2 resolution = new Vec2((float)size.x / (grid[0].length - 1), (float)size.y / (grid.length - 1));
            Point p = new Point(0, 0);
            double maior = Double.MIN_VALUE, menor = Double.MAX_VALUE;
            for (int i = 0; i < grid.length; i++) {
                for (int j = 0; j < grid[i].length; j++) {
                    if(grid[i][j][0] > maior){
                        maior = grid[i][j][0];
                    }
                    if(grid[i][j][0] < menor){
                        menor = grid[i][j][0];
                    }
                }
            }
            
            for (int i = 0; i < grid.length; i++) {
                p.x = 0;
                p.y = (int) (i * resolution.y);
                for (int j = 0; j < grid[i].length; j++) {
                    p.x = (int) (j * resolution.x);
                    double value = (grid[i][j][0] - menor)/(maior-menor);
                    int c = 255-(int)(value * 255);
                    g2d.setColor(new Color(c, c, c, 128));
                    g2d.fillOval(p.x-(int)(resolution.x/2), p.y-(int)(resolution.y/2), (int)resolution.x, (int)resolution.y);
                }
            }
        }
    }

    private void renderRectangle(Graphics2D g2d, int x, int y, int w, int h, QuadTreeNode node) {

        if (selectedNodes != null && node != null && selectedNodes.contains(node)) {
            g2d.setColor(Color.lightGray);
            g2d.fillRect(x, y, w, h);
        } else {
            g2d.setColor(Color.white);
            g2d.fillRect(x, y, w, h);
        }

        g2d.setColor(Color.black);
        g2d.drawRect(x, y, w, h);

        if (node != null) {
            if (!node.isLeaf()) {
                QuadTreeBranchNode branch = (QuadTreeBranchNode) node;
                renderRectangle(g2d, x, y + h / 2, w / 2, h / 2, branch.getChild(0)); //SO
                renderRectangle(g2d, x, y, w / 2, h / 2, branch.getChild(1)); //NO
                renderRectangle(g2d, x + w / 2, y + h / 2, w / 2, h / 2, branch.getChild(2)); //SE
                renderRectangle(g2d, x + w / 2, y, w / 2, h / 2, branch.getChild(3)); //NE
            } else {
                QuadTreeLeafNode leaf = (QuadTreeLeafNode) node;
                for (int i = 0; i < leaf.size(); i++) {
                    IDocument d = leaf.getDocument(i);
                    System.out.println("Selected: " + d.getX() + ", " + d.getY());
                    g2d.setColor(Color.red);

                    if (selectedList != null) {
                        if (selectedList.contains(d)) {
                            g2d.setColor(Color.green);
                        }
                    }
                    g2d.fillOval(
                            mapX(d.getPos().x) - 2,
                            mapY(d.getPos().y) - 2,
                            10, 10);
                }
            }
        }
    }

    int mapRadiusX(float radius) {
        return (int) (size.x * ((radius / quadTree.boundingBox().size().x)));
    }

    int mapRadiusY(float radius) {
        return (int) (size.y * ((radius / quadTree.boundingBox().size().y)));
    }

    int mapX(float i) {
        return (int) (size.x * ((i / quadTree.boundingBox().size().x) + 0.5f));
    }

    int mapY(float j) {
        return (int) (size.y * (0.5f - (j / quadTree.boundingBox().size().y)));
    }
}
