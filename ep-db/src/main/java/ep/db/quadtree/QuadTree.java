/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ep.db.quadtree;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
public class QuadTree {

	protected static final int DEFAULT_MAX_ELEMENT_PER_BUNCH = 28;
	protected static final int DEFAULT_MAX_ELEMENTS_PER_LEAF = 112;
	protected static final int DEFAULT_QUADTREE_MAX_DEPTH = 14;

	protected static int maxDepth;
	protected static int maxElementsPerBunch;
	protected static int maxElementsPerLeaf;
	
	protected QuadTreeBranchNode root;
	protected int leafCount = 0;
	protected int branchCount = 0;
	protected int depth = 0;
	protected long depthMask;
	protected Bounds bounds;
	protected Vec2 resolution;
	protected Vec2 scale;

	public static DatabaseService dbService;

	private static float fatFactor;

	static double getFatFactor() {
		return fatFactor;
	}

	static void setFatFactor(float s) {
		if (s > 1) {
			fatFactor = s;
		}
	}

	public QuadTree(Bounds b, int maxDepth, int maxElementsPerBunch, int maxElementsPerLeaf, DatabaseService dbService) {
		this.root = new QuadTreeBranchNode();
		root.parent = null;
		root.index = -1;
		this.leafCount = 0;
		this.branchCount = 1;
		this.depth = 0;
		this.bounds = b;
		this.depthMask = sizeBits(maxDepth - 1);
		this.bounds.inflate(fatFactor);
		this.resolution = bounds.size().multiply(1f / sizeBits(maxDepth));
		this.scale = new Vec2(this.resolution);
		this.scale.inverse();
		
		QuadTree.maxDepth = maxDepth;
		QuadTree.maxElementsPerBunch = maxElementsPerBunch;
		QuadTree.maxElementsPerLeaf = maxElementsPerLeaf;
		QuadTree.dbService = dbService;
	}
	
	public QuadTree(Bounds b, DatabaseService dbService) {
		this(b, DEFAULT_QUADTREE_MAX_DEPTH, DEFAULT_MAX_ELEMENT_PER_BUNCH, DEFAULT_MAX_ELEMENTS_PER_LEAF, dbService);
	}

	public void setRoot(QuadTreeBranchNode branch) {
		this.root = branch;
	}

	public Bounds boundingBox() {
		return bounds;
	}

	Vec2 getResolution() {
		return resolution;
	}

	public QuadTreeBranchNode getRoot() {
		return root;
	}

	int getLeafCount() {
		return leafCount;
	}

	int getBranchCount() {
		return branchCount;
	}

	int getDepth() {
		return depth;
	}

	static long sizeBits(int d) {
		return 1L << d;
	}

	void setDepth(int d) {
		depth = d;
	}

	QuadTreeNode findLeaf(QuadTreeKey k) {
		return findLeaf(k, depthMask, root);
	}

	boolean hasLeaf(QuadTreeKey k) {
		return findLeaf(k) != null;
	}

	private static QuadTreeNode findLeaf(QuadTreeKey k, long mask, QuadTreeBranchNode branch) {
		QuadTreeNode child = branch.getChild(k.childIndex(mask));

		return child == null || child.isLeaf() ? child : findLeaf(k, mask >> 1, (QuadTreeBranchNode) child);
	}

	Bounds boundingBox(QuadTreeKey key, int depth) {
		Vec2 s = nodeSize(depth);
		Vec2 t = new Vec2(s);
		Vec2 p = bounds.getP1().add(t.multiply(new Vec2(key.getX(), key.getY())));
		return new Bounds(p, s);
	}

	Vec2 point(QuadTreeKey key, int depth, Vec2 p) {
		return bounds.getP1().add(nodeSize(depth).multiply(new Vec2(key.getX(), key.getY()).add(p)));
	}

	Vec2 center(QuadTreeKey key, int depth) {
		return point(key, depth, new Vec2(0.5f, 0.5f));
	}

	QuadTreeKey calculateKey(Vec2 p) {
		Vec2 t = new Vec2(p);
		return new QuadTreeKey((t.sub(bounds.getP1())).multiply(scale));
	}

	private void divideLeafInBranchWith4Leafs(QuadTreeLeafNode leaf, IDocument d) {
		createBranchChild(leaf.getParent(), leaf.index);
		leafCount--; //The leaf is replaced with branchNode

		//Dividing the elements
		for (int i = 0; i < leaf.size(); i++) {
			QuadTreeLeafNode newLeaf = makeLeaf(calculateKey(leaf.getDocument(i).getPos()));
			newLeaf.addElement(leaf.getDocument(i));
		}
	}

	public void addElements(List<? extends IDocument> documents) {
		for (IDocument document : documents) {
			addElement(document);
		}
	}

	public void addElement(IDocument d) {
		QuadTreeKey key = calculateKey(d.getPos());
		QuadTreeLeafNode leaf = makeLeaf(key);
		if (!leaf.addElement(d)) {
			divideLeafInBranchWith4Leafs(leaf, d);
			addElement(d);
		}
	}

	void makeEmptyLeafs() {
		makeEmptyLeafs(getRoot());
	}

	QuadTreeLeafNode makeLeaf(QuadTreeKey key, long mask, QuadTreeBranchNode branch) {
		{
			if (branch.getDepth() == maxDepth) {
				return null;
			}

			int i = key.childIndex(mask);
			QuadTreeNode child = branch.getChild(i);

			if (child == null) {
				child = createLeafChild(branch, i);

				int depth = child.getDepth();

				if (depth > getDepth()) {
					setDepth(depth);
				}
			} else if (!child.isLeaf()) {
				return makeLeaf(key, mask >> 1, (QuadTreeBranchNode) (child));
			}
			return (QuadTreeLeafNode) (child);
		}
	}

	QuadTreeLeafNode makeLeaf(QuadTreeKey key) {
		return makeLeaf(key, depthMask, getRoot());
	}

	QuadTreeLeafNode createLeafChild(QuadTreeBranchNode parent, int i) {
		leafCount++;
		return parent.createLeafChild(i);
	}

	QuadTreeBranchNode createBranchChild(QuadTreeBranchNode parent, int i) {
		branchCount++;
		return parent.createBranchChild(i);
	}

	Vec2 nodeSize(int depth) {
		Vec2 r = new Vec2(resolution);
		return r.multiply(sizeBits(maxDepth - depth));
	}

	void makeEmptyLeafs(QuadTreeBranchNode branch) {

		for (int i = 0; i < 4; i++) {
			QuadTreeNode child = branch.getChild(i);

			if (child == null) {
				createLeafChild(branch, i);
			} else if (!branch.isLeaf()) {
				makeEmptyLeafs((QuadTreeBranchNode) child);
			}
		}
	}

	public int findInRectangle(Bounds rectangle, List<IDocument> documents, List<QuadTreeNode> nodes) {
		documents.clear();
		nodes.clear();
		rectangleSearchAll(rectangle, new QuadTreeKey(0), getRoot(), documents, nodes);
		return documents.size();
	}

	public List<IDocument> findInRectangleByRank(Bounds rectangle, List<IDocument> documents, List<QuadTreeNode> nodes, float totalRankArea) {
		documents.clear();
		nodes.clear();
		return rectangleSearchByRank(rectangle, new QuadTreeKey(0), getRoot(), totalRankArea, documents, false);
	}

	public double findNeighbors(Vec2 p, double radius, List<IDocument> documentList, List<QuadTreeNode> nodes, float rankMinLimit) {
		if (radius <= 0) {
			return 0;
		}
		documentList.clear();
		return radiusSearch(p, radius * radius, new QuadTreeKey(0), getRoot(), documentList, nodes, rankMinLimit);
	}

	public double findNeighbors(Vec2 p, double radius, float rankMinLimit) {
		List<IDocument> documentList = new ArrayList<>();
		List<QuadTreeNode> nodes = new ArrayList<>();
		return findNeighbors(p, radius, documentList, nodes, rankMinLimit);
	}

	private void addNodeAndChildrenInList(List<QuadTreeNode> nodes, QuadTreeNode node) {
		nodes.add(node);
		if (!node.isLeaf()) {
			QuadTreeBranchNode branch = (QuadTreeBranchNode) node;
			for (int i = 0; i < 4; i++) {
				if (branch.children[i] != null) {
					addNodeAndChildrenInList(nodes, branch.children[i]);
				}
			}
		}
	}

	public List<IDocument> rectangleSearchByRank(Bounds rectangle, QuadTreeKey key, QuadTreeBranchNode branch, float totalRankArea, List<IDocument> documents, Boolean filledArea) {
		int depth = branch.getDepth() + 1;

		for (int i = 0; i < 4; i++) {
			if (branch.hasChild(i)) {
				QuadTreeNode child = branch.getChild(i);
				QuadTreeKey childKey = key.pushChild(i);

				Bounds bChild = boundingBox(childKey, depth);
				int inter = rectangle.intersect(bChild);

				if (inter == 2) {
					documents = child.getElementsByRank(documents, totalRankArea, null, filledArea);
				} else if (inter == 1) { //Test if point intersects the Rect
					if (!child.isLeaf()) {
						documents = rectangleSearchByRank(rectangle, childKey, (QuadTreeBranchNode) child, totalRankArea, documents, filledArea);
					} else {
						documents = child.getElementsByRank(documents, totalRankArea, rectangle, filledArea);
					}
				}
			}
		}
		return documents;
	}

	public void rectangleSearchAll(Bounds rectangle, QuadTreeKey key, QuadTreeBranchNode branch, List<IDocument> documentList, List<QuadTreeNode> nodes) {
		int depth = branch.getDepth() + 1;

		for (int i = 0; i < 4; i++) {
			if (branch.hasChild(i)) {
				QuadTreeNode child = branch.getChild(i);
				QuadTreeKey childKey = key.pushChild(i);

				Bounds bChild = boundingBox(childKey, depth);
				int inter = rectangle.intersect(bChild);

				if (inter == 2) {
					child.getDocuments(documentList);
					addNodeAndChildrenInList(nodes, child);
				} else if (inter == 1) { //Test if point intersects the Rect
					if (!child.isLeaf()) {
						rectangleSearchAll(rectangle, childKey, (QuadTreeBranchNode) child, documentList, nodes);
					} else {
						QuadTreeLeafNode leaf = (QuadTreeLeafNode) child;

						for (int j = 0; j < leaf.size(); j++) {
							IDocument d = leaf.getDocument(j);
							if (rectangle.contains(d.getPos())) {
								documentList.add(d);
							}
						}
						addNodeAndChildrenInList(nodes, child);
					}
				}
			}
		}
	}

	public double radiusSearch(Vec2 p, double r2, QuadTreeKey key, QuadTreeBranchNode branch, List<IDocument> elementList, List<QuadTreeNode> nodes, float rankMinLimit) {
		int depth = branch.getDepth() + 1;
		double s2 = searchSize2(normSquared(nodeSize(depth)), r2);
		double d2sum = 0;

		for (int i = 0; i < 4; i++) {
			if (branch.hasChild(i)) {
				QuadTreeNode child = branch.getChild(i);
				//Nenhum documento na sub-arvore do nó irá satisfazer a busca
				if(child.rankMax < rankMinLimit){
					continue;
				}                
				QuadTreeKey childKey = key.pushChild(i);
				Vec2 t = new Vec2(p);
				double d2 = normSquared(t.sub(center(childKey, depth)));

				if (eps() + d2 > s2) {
					continue;
				}
				if (!child.isLeaf()) {
					d2sum += radiusSearch(p, r2, childKey, (QuadTreeBranchNode) child, elementList, nodes, rankMinLimit);
				} else {
					QuadTreeLeafNode leaf = (QuadTreeLeafNode) child;

					for (int j = 0; j < leaf.size(); j++) {
						IDocument d = leaf.getDocument(j);
						//O documento não satisfaz a busca
						if(d.getRank() < rankMinLimit){
							continue;
						}
						Vec2 q = new Vec2(p);
						double docd2 = normSquared(q.sub(d.getPos()));
						if (docd2 <= r2) {
							d2sum += docd2;
							elementList.add(d);
						}
					}
					addNodeAndChildrenInList(nodes, child);
				}
			}
		}
		return d2sum;
	}
	
	public QuadTree loadQuadTree(){
		return dbService.loadQuadTree(this);
	}
	
	public boolean persistQuadTree(){
		return dbService.persistQuadTree(this);
	}

	private static double eps() {
		return 0.0000001;
	}

	private static double normSquared(Vec2 p) {
		return (p.x * p.x) + (p.y * p.y);
	}

	private double searchSize2(double d2, double r2) {
		return d2 * 0.25f + r2 + Math.sqrt(d2 * r2);
	}
	
	public int getMaxDepth() {
		return maxDepth;
	}
	
	public int getMaxElementsPerBunch() {
		return maxElementsPerBunch;
	}
	
	public int getMaxElementsPerLeaf() {
		return maxElementsPerLeaf;
	}

	public static void main(String[] args) {

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

		Vec2 p1 = new Vec2(-1, -1);
		Vec2 p2 = new Vec2(2,2);
		Bounds bounds = new Bounds(p1,p2);
		QuadTree quadTree = new QuadTree(bounds,
				config.getQuadTreeMaxDepth(),
				config.getQuadTreeMaxElementsPerBunch(),
				config.getQuadTreeMaxElementsPerLeaf(),
				dbService);

		System.out.println("Creating QuadTree...");

		List<Document> docs;
		try {
			docs = dbService.getAllSimpleDocuments();
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		quadTree.addElements(docs);

		System.out.println("Saving QuadTree to DB...");

		dbService.persistQuadTree(quadTree);

		System.out.println("Done!");	

		System.out.println("Loading QuadTree from DB...");

		QuadTree quadTree2 = new QuadTree(bounds, dbService);
		dbService.loadQuadTree(quadTree2);
		
		System.out.println("Done!");
	}

}
