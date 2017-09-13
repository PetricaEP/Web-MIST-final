var padding = 6.0;

/** Protótio da classe Tab
 * Essa classe armazenada informações
 * sobre a visualização na respectiva aba.
 * @param id html id da aba (content)
 * @returns uma nova Tab
 */
function Tab(id, parentId){
	this.id = id;
	this.documents = null;
	this.densities = null;
	this.n = 0;
	this.nclusters = 0;
	this.links = null;
	this.circles = []; 
	this.points = null;
	this.nodes = null;
	this.path = null;
	this.clusters = null;
	this.step = 0;
	this.simulation = null;
	this.color = null;
	this.isColorByRelevance = true;
	this.x = null;
	this.y = null;
	this.x_inv = null;
	this.y_inv = null;
	this.minX = -1;
	this.maxX = 1;
	this.minY = -1;
	this.maxY= 1;
	this.zoomLevel = 0;
	this.parentId = parentId;
}

Tab.prototype.loadData = function(data, interpolator, maxArea, maxDocs){
	var area = 0, index = 0;
	this.documents = {};
	this.n = data.documents.length;

	while ( ( area < maxArea || index < maxDocs) && index < data.documents.length ){
		var doc = data.documents[index];
		var r = interpolator(doc.rank);
		doc.radius = r;
		area += 4 * (r + padding) * (r + padding);
		this.documents[doc.id] = doc;
		++index;
	}

	this.densities = [];
	for( var i = index; i < data.documents.length; i++){
		this.densities.push(data.documents[i]);
	}

	this.ncluster = data.nclusters;
	
	return index;
};

/**
 * Inicializa função de coloração dos clusters
 * @param m numero de clusters.
 */
Tab.prototype.initClusteringColorSchema = function(m){
	this.color = d3.scaleSequential(d3.interpolateRainbow)
	.domain([0, m]);
};

/**
 * Inicializa função de coloração dos nós.
 * @param isColorByRelevance usa esquema de cores por relevancia
 * @param a valor minimo 
 * @param b valor maximo
 */
Tab.prototype.initColorSchema = function(isColorByRelevance, a, b){
	this.isColorByRelevance  = isColorByRelevance;
	if ( isColorByRelevance ){
		this.color = palette(a,b);
	}
	else{
		this.color = d3.scaleSequential(d3.interpolateRainbow)
		.domain([a, b]);
	}
};

/**
 * Aplica esquema de cor em um nó
 * @param d nó.
 */
Tab.prototype.coloring = function(d){
	if ( this.isColorByRelevance ){
		if ( d.data !== undefined )
			return this.color(d.data.rank);
		return this.color(d.rank);
	}
	return this.color(d.cluster);
};

/**
 * Inicializa função de transformação
 * de coordenadas.
 * @param w width do SVG
 * @param h height do SVG
 */
Tab.prototype.xy = function(w,h, minX, maxX, minY, maxY){
	this.minX = minX;
	this.maxX = maxX;
	this.minY = minY;
	this.maxY= maxY;
	this.x = function(xx){
		return w * ( xx - this.minX) / (this.maxX - this.minX);
	};
	this.y = function(yy){
		return h * (yy - this.minY) / (this.maxY - this.minY);
	};
};

Tab.prototype.xy_inverse = function(w,h){
	this.x_inv = function(xx){
		return xx * (this.maxX - this.minX) / w + this.minX;
	};
	this.y_inv = function(yy){
		return yy * (this.maxY - this.minY) / h + this.minY;
	};
};

/**
 * Deleta Tab e para simulação.
 */
Tab.prototype.deleteTab = function(){
	delete this.documents;
	delete this.densities;
	delete this.links;
	delete this.circles; 
	delete this.points;
	delete this.nodes;
	delete this.path;
	delete this.clusters;
	delete this.step;

	if ( this.simulation )
		this.simulation.stop();
	delete this.simulation;
};

