/** Protótio da classe Tab
 * Essa classe armazenada informações
 * sobre a visualização na respectiva aba.
 * @param id html id da aba (content)
 * @returns uma nova Tab
 */
function Tab(id){
	this.id = id;
	this.data = null;
	this.links = null;
	this.circles = []; 
	this.points = null;
	this.nodes = null;
	this.path = null;
	this.clusters = null;
	this.step = 0;
	this.simulation = null;
	this.cluterColor = null;
	this.x = null;
	this.y = null;
	this.x_inv = null;
	this.y_inv = null;
	this.minX = -1;
	this.maxX = 1;
	this.minY = -1;
	this.maxY= 1;
	this.zoomLevel = 0;
}

/**
 * Inicializa função de coloração dos clusters
 * @param m numero de clusters.
 */
Tab.prototype.initClusterColor = function(m){
	this.clusterColor = d3.scaleSequential(d3.interpolateRainbow)
	.domain([0, m]);
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
	delete this.data; 
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

