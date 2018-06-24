/* ***** BEGIN LICENSE BLOCK *****
 *
 * Copyright (c) 2005-2007 Universidade de Sao Paulo, Sao Carlos/SP, Brazil.
 * All Rights Reserved.
 *
 * This file is part of Projection Explorer (PEx).
 *
 * How to cite this work:
 *  
@inproceedings{paulovich2007pex,
author = {Fernando V. Paulovich and Maria Cristina F. Oliveira and Rosane 
Minghim},
title = {The Projection Explorer: A Flexible Tool for Projection-based 
Multidimensional Visualization},
booktitle = {SIBGRAPI '07: Proceedings of the XX Brazilian Symposium on 
Computer Graphics and Image Processing (SIBGRAPI 2007)},
year = {2007},
isbn = {0-7695-2996-8},
pages = {27--34},
doi = {http://dx.doi.org/10.1109/SIBGRAPI.2007.39},
publisher = {IEEE Computer Society},
address = {Washington, DC, USA},
}
 *  
 * PEx is free software: you can redistribute it and/or modify it under 
 * the terms of the GNU General Public License as published by the Free 
 * Software Foundation, either version 3 of the License, or (at your option) 
 * any later version.
 *
 * PEx is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
 * for more details.
 *
 * This code was developed by members of Computer Graphics and Image
 * Processing Group (http://www.lcad.icmc.usp.br) at Instituto de Ciencias
 * Matematicas e de Computacao - ICMC - (http://www.icmc.usp.br) of 
 * Universidade de Sao Paulo, Sao Carlos/SP, Brazil. The initial developer 
 * of the original code is Fernando Vieira Paulovich <fpaulovich@gmail.com>.
 *
 * Contributor(s): Rosane Minghim <rminghim@icmc.usp.br>
 *
 * You should have received a copy of the GNU General Public License along 
 * with PEx. If not, see <http://www.gnu.org/licenses/>.
 *
 * ***** END LICENSE BLOCK ***** */

package ep.db.mdp.projection;

import ep.db.mdp.dissimilarity.DissimilarityType;

/**
 *
 * @author Fernando Vieira Paulovich
 */
public class ProjectionData {

    public DissimilarityType getDissimilarityType() {
        return distanceType;
    }

    public void setDissimilarityType(DissimilarityType distanceType) {
        this.distanceType = distanceType;
    }

    public ProjectorType getProjectorType() {
        return this.projector;
    }

    public void setProjectorType(ProjectorType projector) {
        this.projector = projector;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public int getNumberIterations() {
        return numberIterations;
    }

    public void setNumberIterations(int numberIterations) {
        this.numberIterations = numberIterations;
    }

    public float getFractionDelta() {
        return fractionDelta;
    }

    public void setFractionDelta(float fractionDelta) {
        this.fractionDelta = fractionDelta;
    }   

    public int getNumberControlPoints() {
        return numberControlPoint;
    }

    public void setNumberControlPoints(int numberControlPoint) {
        this.numberControlPoint = numberControlPoint;
    }

    public ControlPointsType getControlPointsChoice() {
        return controlPointsChoice;
    }

    public void setControlPointsChoice(ControlPointsType controlPointsChoice) {
        this.controlPointsChoice = controlPointsChoice;
    }    
    
    public void setPercentage(float percentage) {
		this.percentage = percentage;
	}
    
    public float getPercentage() {
		return percentage;
	}
    
    @Override
    public String toString() {
    		StringBuilder sb = new StringBuilder();
    		sb.append("Source file: ");
    		sb.append(sourceFile);
    		sb.append("\n");
    		sb.append("Distance Type: ");
    		sb.append(distanceType.name());
    		sb.append("\n");
    		sb.append("Num. iterations: ");
    		sb.append(numberIterations);
    		sb.append("\n");
    		sb.append("Projector: ");
    		sb.append(projector.name());
    		sb.append("\n");
    		sb.append("Num. cp: ");
    		sb.append(numberControlPoint);
    		sb.append("\n");
    		sb.append("CP choice: ");
    		sb.append(controlPointsChoice.name());
    		sb.append("\n");
    		sb.append("Percentage KNN: ");
    		sb.append(percentage);
    		sb.append("\n");
    		return sb.toString();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        ProjectionData pdata = new ProjectionData();
        pdata.distanceType = this.distanceType;
        pdata.sourceFile = this.sourceFile;       
        pdata.numberIterations = this.numberIterations;
        pdata.fractionDelta = this.fractionDelta;
        pdata.projector = this.projector;        
        pdata.numberControlPoint = this.numberControlPoint;
        pdata.controlPointsChoice = this.controlPointsChoice;        
        pdata.percentage = this.percentage;

        return pdata;
    }

   
    //diss used to calculate distances over ArrayLists
    private DissimilarityType distanceType = DissimilarityType.NONE;
    //General use
    private String sourceFile = "";    
    private int numberIterations = 50;
    private float fractionDelta = 8.0f;
    private ProjectorType projector = ProjectorType.NONE;    
    private int numberControlPoint = 10;
    private ControlPointsType controlPointsChoice = ControlPointsType.KMEANS;
    private float percentage = 1.0f;
}
