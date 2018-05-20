/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ep.db.quadtree;

import java.util.ArrayList;
import java.util.List;

import ep.db.model.Document;
import ep.db.model.IDocument;

/**
 *
 * @author Márcio Peres
 */
//////////////////////////////////////////////////////////
//
// QuadtreeLeafNode: quadtree leaf node class
// ================
public class QuadTreeLeafNode extends QuadTreeNode {

	private Bunch[] data;
	//    private int nTotalDocuments;
	private int nDocuments;
	private int numberOfBunchs;

	//    public void setnTotalDocuments(int nTotalDocuments) {
	//        this.nTotalDocuments = nTotalDocuments;
	//    }

	public QuadTreeLeafNode() {
		nodeId = numberOfNodes++;
		numberOfBunchs = QuadTree.maxElementsPerLeaf / QuadTree.maxElementsPerBunch;
		data = new Bunch[numberOfBunchs]; //25 por Bunch X 4 = 100 por Leaf
		nDocuments = 0;
	}

	public QuadTreeLeafNode(int depth, int index, int nodeId, float rankMax, float rankMin, QuadTreeBranchNode parent) {
		this.depth = depth;
		this.index = index;
		this.nodeId = nodeId;
		this.rankMax = rankMax;
		this.rankMin = rankMin;
		this.parent = parent;
		numberOfBunchs = QuadTree.maxElementsPerLeaf / QuadTree.maxElementsPerBunch;
		data = new Bunch[numberOfBunchs]; //25 por Bunch X 4 = 100 por Leaf
		nDocuments = 0;
	}

	public boolean addElement(int id, float rank, float x, float y) {
		return addElement(new Document(id, rank, x, y));
	}

	public boolean addElement(IDocument b) {
		if (nDocuments >= QuadTree.maxElementsPerLeaf) {
			return false;
		}
		int bunchIndex = nDocuments / QuadTree.maxElementsPerBunch; 
			
		if ( bunchIndex >= numberOfBunchs)
			return false;        

		if (data[bunchIndex] == null) {
			data[bunchIndex] = new Bunch();
		}

		if (data[bunchIndex].addElement(b)) {
			++nDocuments;        		               
			//        	if(nDocuments > nTotalDocuments){
			//           	nTotalDocuments = nDocuments;
			//        	}

			updateRankPathRoot(b.getRank());
			return true;
		}
		return false;
	}

	private void updateRankPathRoot(float rank) {
		if (rank < rankMin) {
			rankMin = rank;
			parent.updateRankPathRoot(rank);
		}
		if (rank > rankMax) {
			rankMax = rank;
			parent.updateRankPathRoot(rank);
		}
	}

	public int size() {
		return nDocuments;
	}

	public IDocument getDocument(int index) {
		//if (index >= nDocuments) {
		//    return null;
		//}

		int bunchIndex = index / QuadTree.maxElementsPerBunch;
		if ( bunchIndex >= numberOfBunchs)
			return null;

		if (data[bunchIndex] == null) {
			data[bunchIndex] = new Bunch();
			loadBunch(bunchIndex, null, null);
		}

		int elementIndex = index % QuadTree.maxElementsPerBunch;
		if(elementIndex < data[bunchIndex].size()){
			return data[bunchIndex].getElement(elementIndex);
		} 
		return null;
	}
	
	@Override
	public void getDocuments(List<IDocument> documents) {
		getDocuments(documents, null, null);
	}

	public void getDocuments(List<IDocument> documents, Bounds rectangle, String query) {
		// Clear bunchs before load them again
		for(Bunch b : data) {
			if ( b != null )
				b.clear();
		}
		try {
			documents.addAll(loadAllBunches(rectangle, query));
		} catch (Exception e) {
			e.printStackTrace();
			//TODO: warning
		}    		
	}

	public List<IDocument> loadBunch(int indexBunch, Bounds rectangle, String query) {
		//Load the Documents from the Bunch from Data Base
		List<IDocument> documents;
		try {
			documents = QuadTree.dbService.getDocumentsFromNode(nodeId, rectangle, query, indexBunch*QuadTree.maxElementsPerBunch, QuadTree.maxElementsPerBunch);
		} catch (Exception e) {
			e.printStackTrace();			
			return null;
		}

		for(IDocument d : documents)
			addElement(d);
		
		return documents;
	}

	public List<IDocument> loadAllBunches(Bounds rectangle, String query) throws Exception {
		//Load the Documents from all Bunchs from Data Base
		List<IDocument> documents;
		try {
			documents = QuadTree.dbService.getDocumentsFromNode(nodeId, rectangle, query);
		} catch (Exception e) {
			throw e;
		}

		for(IDocument d : documents)
			addElement(d);				   
		
		return documents;
	}	

	@Override
	public List<IDocument> getElementsByRank(List<IDocument> documents, float totalRankArea, Bounds rectangle, Boolean filledArea) {
		//Se a lista está preenchida e o RankMáximo do nó é menor que o Rank menor da lista, não há necessidade de percorrer o nó.
		if(filledArea && rankMax < documents.get(documents.size()-1).getRank()){
			return documents;
		}

		List<IDocument> documentsNew = new ArrayList<>();
		float totalRankAreaTemp = 0;
		int iList = 0, iLeaf = 0;
		while (totalRankArea > totalRankAreaTemp) {
			IDocument docList = null;
			IDocument docLeaf = null;
			if (iList < documents.size()) {
				docList = documents.get(iList);
			}
			if (iLeaf < nDocuments) {
				docLeaf = getDocument(iLeaf);
			}

			if (docList == null && docLeaf == null) {
				break;
			}

			if (docLeaf == null || 
					(docList != null && docList.getRank() > docLeaf.getRank())) {
				totalRankAreaTemp += docList.getRank();
				documentsNew.add(docList);
				iList++;
			} else if (docLeaf != null) {
				if (rectangle == null || rectangle.contains(docLeaf.getPos())) {
					totalRankAreaTemp += docLeaf.getRank();
					documentsNew.add(docLeaf);
				}
				iLeaf++;
			}
		}
		return documentsNew;
	}
}
