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

import org.jblas.FloatMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ep.db.mdp.DistanceMatrix;
import ep.db.mdp.ForceScheme;
import ep.db.mdp.dissimilarity.Dissimilarity;
import ep.db.mdp.dissimilarity.Euclidean;

/**
 *
 * @author Fernando Vieira Paulovich
 */
public class IDMAPProjection  extends Projection {

	
	private static Logger logger = LoggerFactory.getLogger(IDMAPProjection.class);
	
	public float[][] project(FloatMatrix matrix, ProjectionData pdata) {
		Dissimilarity diss = new Euclidean();
		DistanceMatrix dmat_aux = new DistanceMatrix(matrix, diss);
		float[][] projection = this.project(dmat_aux, pdata);
		return projection;
	}
	
	public float[][] project(DistanceMatrix dmat, ProjectionData pdata) {
		this.dmat = dmat;

		long start = System.currentTimeMillis();

		FastmapProjection proj = new FastmapProjection();
		float[][] projection = proj.project(dmat);

		if (projection != null) {
			ForceScheme force = new ForceScheme(pdata.getFractionDelta(), projection.length);
			System.out.println("FractionDelta..." + pdata.getFractionDelta());

			float error = Float.MAX_VALUE;
			for (int i = 0; i < pdata.getNumberIterations(); i++) {
				error = force.iteration(dmat, projection);
				if ( logger.isInfoEnabled() ) {
					String msg = "Iteration " + i + " - error: " + error;
					logger.info(msg);
				}
			}
		}

		long finish = System.currentTimeMillis();
		logger.info("Interactive Document Map (IDMAP) time: " + (finish - start) / 1000.0f + "s");
		
		return projection;
	}
}
