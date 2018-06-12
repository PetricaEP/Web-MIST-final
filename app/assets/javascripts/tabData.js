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
	this.links = null;
	this.circles = []; 
	this.points = null;
	this.nodes = null;
	this.path = null;	
	this.step = 0;
	this.simulation = null;
	this.color = null;	
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
	this.selectedCircles = [];
	this.query = null;
	this.wordCloud = null;
	this.ndocs = 0;
	this.view_docs = 0;
	this.page = 0;
}

Tab.prototype.clean = function(){
	this.documents = null;
	this.densities = null;
	this.n = 0;	
	this.links = null;
	this.circles = []; 
	this.points = null;
	this.nodes = null;
	this.path = null;	
	this.step = 0;
	this.simulation = null;
	this.color = null;	
	this.x = null;
	this.y = null;
	this.x_inv = null;
	this.y_inv = null;
	this.minX = -1;
	this.maxX = 1;
	this.minY = -1;
	this.maxY= 1;	
	this.selectedCircles = [];
	this.query = null;
	this.wordCloud = null;	
	this.view_docs = 0;	
};

Tab.prototype.loadData = function(data, maxArea, maxDocs){
	var area = 0, i = 0;
	this.documents = {};
	this.n = data.documents.length;

	this.view_docs = 0;
	for(; i < data.documents.length; i++){
		var doc = data.documents[i];
		this.documents[doc.id] = doc;
		++this.view_docs;
	}

	this.densities = data.densities;	
	this.query = data.query;
	
	return i;
};

/**
 * Inicializa função de coloração dos nós. 
 * @param a valor minimo 
 * @param b valor maximo
 */
Tab.prototype.initColorSchema = function(a, b){	
	this.color = palette(a,b,null);	
};

/**
 * Aplica esquema de cor em um nó
 * @param d nó.
 */
Tab.prototype.coloring = function(d){	
	if ( d.data !== undefined )
		return this.color(d.data.rank);
	return this.color(d.rank);	
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

Tab.prototype.processReferences = function(data){	
	// Citações
	if ( data.references ){
		var docId, refId;
		var references = data.references;
		this.links = [];
		var map = d3.map(references);
		var keys = map.keys();
		if ( keys.length > 0 ) {
			for(var i = 0; i < keys.length; i++){
				var refs = map.get(keys[i]);
				for(var j = 0; j < refs.length; j++) {
					this.links.push({	
						source: keys[i] | 0,
						target: refs[j] | 0
					});
				}
			}
		}
	}

	// Desenha links entre circulos
	if ( this.links !== null ){
		var self = this;
		this.path = d3.select("#" + this.id + " svg.visualization g")
		.selectAll("path")
		.data(this.links)
		.enter().append("path")
		.attr("class", "link")
		.attr('stroke-width', 1.5)
		.attr('visibility', 'hidden')
		.attr("marker-end", "url(#link" + this.id + ")");		

		// Força entre links (citações)
		// Circulos com alguma ligação serão posicionados 
		// próximos caso strength != 0.
		// Default strength == 0 (somente exibe links/setas)
		var forceLink = d3.forceLink()
		.id(function(d) { return d.id; })
		.links(this.links)
		.strength(0)
		.distance(0);

		// Adiciona links a simulacao
		this.simulation
		.force('link', forceLink);		
		
		this.ticked();
				
		// Cria uma nova simulacao caso ainda nao tenha sido criada
		//setupSimulation(selectedTab);
				
		//selectedTab.simulation
		//.force("link", forceLink)
		//.restart();		
	}
};


/** Executa a cada iteracão da simulação.
 * Corrigi posição cx,cy dos circulos evitando
 * que estes saiam fora da area de visualização
 * (bounding box).
 *  
 * @returns void
 */
Tab.prototype.ticked = function() {
	var width = $('#' + this.id + " svg.visualization").width();
	var height = $('#' + this.id + " svg.visualization").height();

	this.circles
	.attr("cx", function(d) { return (d.x = Math.max(d.r, Math.min(width - d.r, d.x)));})
	.attr("cy", function(d) { return (d.y = Math.max(d.r, Math.min(height - d.r, d.y)));});

	if ( this.path !== null ){
		this.path
		.attr("d", linkArc);
	}
};

/** Finaliza simulação.
 * Ativa botao de reheating e habilita tooltips.
 * 
 * @returns void
 */
Tab.prototype.endSimulation = function(){
	this.circles
	.attr("fill-opacity", 0.80);

	$("#reheat-btn").prop('disabled', false);
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
	delete this.step;

	if ( this.simulation )
		this.simulation.stop();
	delete this.simulation;
};

