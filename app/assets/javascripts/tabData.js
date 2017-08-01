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
Tab.prototype.xy = function(w,h){
	this.x = function(xx){
		return w * ( xx / 2.0 + 0.5);
	};
	this.y = function(yy){
		return h * (0.5 - yy / 2.0);
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

