var margin = {top: 100, right: 100, bottom: 100, left: 100},
padding = 3, // separation between same-color nodes
clusterPadding = 6, // separation between different-color nodes
fixRadius = 5;

//Inicializa visualização
$(function(){

	$("#step-btn").prop('disabled', true);
	$("#reheat-btn").prop('disabled', true);
	$("#show-list-btn").prop('disabled', true);

	$("#reset-btn").click(resetVisualization);
	$("#zoom-btn").click(zoomTool);
	$("#zoom-btn").on( "mouseleave", function(){
		$(this).tooltip('hide');
	} );
	$("#show-list-btn").on( "mouseleave", function(){
		$(this).tooltip('hide');
	} );

	$("#step-btn").click(nextVisualizationStep);
	$("#reheat-btn").click(reheat);
	$("#show-list-btn").click(showListTool);

	// Tooltip on mouse over
	var tip = d3.select("body").append("div")
	.attr("class", "node-tooltip")
	.style("opacity", 0);

});

//Criar uma visualização em uma nova aba
createVisualization = function(jsonData){

	// Cria nova aba
	var	currentTab = addNewTab(jsonData.op);
	currentTab.step = 1;

	$('#loading').addClass('hidden');
	$("#step-btn").prop('disabled', false);
	$("#show-list-btn").prop('disabled', false);

	var svg = d3.select("#" + currentTab.id + " svg"),
	width = $("#" + currentTab.id + " svg").width(),
	height = width * 6 / 16; //$("#" + currentTab.id + " svg").height();

	$("#" + currentTab.id + " svg").height(height);
	$(".tab-content").height(height);

	svg.attr('width', width);
	svg.attr('height', height);
	
	svg.on('click', function(){
		d3.select('.fixed-tooltip')
		.transition()
		.duration(500)
		.style('opacity', 0)
		.style('display', 'none')
		.remove();
	});

	var n = jsonData.documents.length, // no. total de documentos
	m = jsonData.nclusters; // no. de clusters

	// Se não houver nenhum documento, 
	// não há nada que desenhar
	if ( n === 0 ){
		return;
	}

	var isColoringByRelevance = $('#color-schema').prop('checked');
	var minRank, maxRank, minRadius, maxRadius;
	maxRank = jsonData.documents[0].rank;
	minRank = jsonData.documents[n-1].rank;
	maxRadius = width * 0.05;
	minRadius = width * 0.005;

	//Adiciona data ao vetor de dados de cada aba
	var radiiInterpolator = d3.scaleLinear()
	.domain([minRank, maxRank])
	.range([minRadius, maxRadius]);

	currentTab.loadData(jsonData, radiiInterpolator, width * height);

	// Valores min/max das coordenadas x,y 
	// dos documentos atuais
	var minX = -1, 
	maxX = 1,
	minY = -1,
	maxY = 1;

	if ( typeof jsonData.min_max !== "undefined" && jsonData.min_max.length == 4){
		minX = jsonData.min_max[0];
		maxX = jsonData.min_max[1];
		minY = jsonData.min_max[2];
		maxY = jsonData.min_max[3];
	}

	// Inicializa transformações 2D (x,y) 
	// do intervalo [-1,1] para [0, width/height]
	currentTab.xy(width, height, minX, maxX, minY, maxY);
	currentTab.xy_inverse(width, height);

	// Criar contornos
	var contours = d3.contourDensity()
	.x(function(d){ return selectedTab.x(d.x);})
	.y(function(d){ return selectedTab.y(d.y);})
	.size([width,height])
	.bandwidth(40)
	(currentTab.densities);

	// Inicializa coloração dos clusters
	if ( isColoringByRelevance ){
		currentTab.initColorSchema(true, minRank, maxRank);
	}
	else{
		currentTab.initColorSchema(false, 0, m);
	}
	

	// Coloração dos contornos
	var contourColor = d3.scaleSequential(d3.interpolateOranges)
	.domain(d3.extent(contours.map(function(p) { return p.value;}))); // Points per square pixel.

	// Array para armazenar o maior nó de cada cluster (centroide)
	currentTab.clusters = new Array(m);

	//Cria nós
	currentTab.nodes = createNodes(currentTab.documents);

	// Se pontos do contorno devem ser mostrados na
	// visualizaçã, cria pontos.
	var showPoints = $("#show-density-points").prop('checked');
	if ( showPoints ){
		currentTab.points = createPoints(currentTab.densities);
	}

	// Marcadores dos links (setas)
	svg.append("defs").selectAll("marker")
	.data(["link" + currentTab.id])
	.enter().append("marker")
	.attr("id", function(d) { return d; })
	.attr("viewBox", "0 -5 10 10")
	.attr("refX", 8)
	.attr("refY", 0)
	.attr("markerWidth", 6)
	.attr("markerHeight", 6)
	.attr("orient", "auto")
	.append("path")
	.attr("d", "M0,-5L10,0L0,5");

	// Contornos
	svg.insert("g", "g")
	.attr("fill", "none")
//	.attr("stroke", "#000")
//	.attr("stroke-width", 0.5)
//	.attr("stroke-linejoin", "round")
	.selectAll("path")
	.data(contours)
	.enter().append("path")
	.attr("d", d3.geoPath())
	.attr("fill", function(d){ return contourColor(d.value);});

	var g = svg.append("g");

	// Circulos
	currentTab.circles = g
	.datum(currentTab.nodes)
	.selectAll('.circle')
	.data(function(d) { return d;})
	.enter().append('circle')
	.attr('r', function(d) { return fixRadius; })
	.attr('cx', function(d) { return d.x;})
	.attr('cy', function(d) { return  d.y;})
	.attr('fill', function(d) { return  selectedTab.coloring(d);})
	.attr('stroke', 'black')
	.attr('stroke-width', 1)
	.attr('fill-opacity', 0.65)
	.on("mouseover", showTip)
	.on("mouseout", hideTip)
	.on("click", toggleLinks);

	// Se há pontos de densidade, 
	// então desenha na visualização.
	var point;
	if ( currentTab.points !== null ){
		point = g
		.datum(currentTab.points)
		.selectAll('.circle')
		.data(function(d) { return d;})
		.enter().append('circle')
		.attr('r', function(d) { return d.r; })
		.attr('cx', function(d) { return d.x;})
		.attr('cy', function(d) { return  d.y;})
		.attr('fill', function(d) { return  selectedTab.coloring(d);})
		.attr('stroke', 'black')
		.attr('stroke-width', 1);
	}

	// Ferramenta de seleção (escondida por enquanto).
	var selection = svg.append("path")
	.attr("class", "selection")
	.attr("visibility", "hidden");

	var isActive = $("#zoom-btn").hasClass("active");
	if ( isActive )
		activeZoom( svg );
	else
		desactiveZoom ( svg );

	// Mostra tooltips sobre os pontos de densidade,
	// apenas coordenadas x,y.
//	if ( showPoints ){
//		point
//		.on("mouseover", function(p){
//			var d = p.data;
//			var html = "<p>x = " + d.x + ", y = " + d.y + "<p>";
//
//			tip.transition()
//			.duration(500)
//			.style("opacity", 0);
//
//			tip.transition()
//			.duration(200)
//			.style("opacity", 0.9)
//			.style("display", "block");
//
//			tip.html(html)
//			.style("left", "150px")
//			.style("top", "150px");
//			d3.select(this).style("stroke-opacity", 1);
//
//		})
//		.on("mouseout", hideTip);
//	}

	if ( currentTab.parentId === null ){
		createMiniMap(svg, currentTab);
	}
	else{
		copyMiniMapFromParent(svg, currentTab, minX, minY, maxX, maxY);
	}

	// ###### Fim do primeiro passo: criar visualização ######
};

//Segundo passo: atribui raios ao circulos
function secondStep(){
	selectedTab.circles
	.transition()
	.duration(750)
	.attr('r', function(d) { return d.r; })
	.attr('fill-opacity', function(d) { return 0.3; });
}

//Terceiro passo: inicializa esquema de forças
function thirdStep(){

	// Configuração de forças
	var collideStrength = parseFloat( $("#collision-force").val() ),
	manyBodyStrength = parseFloat($("#manybody-force").val()),
	clusteringForceOn = $("#clustering-force").prop('checked');

	// Força de colisão
	var forceCollide = d3.forceCollide()
	.radius(function(d) { return d.r + padding; }) // raio do circulo + padding
	.strength(collideStrength)
	.iterations(1); //somente 1 iteração (economia de memoria).

	// Força de atração
	// Evita dispersão
	var forceGravity = d3.forceManyBody()
	.strength( manyBodyStrength );

	// Inicializa simulação
	selectedTab.simulation = d3.forceSimulation(selectedTab.nodes)
	.force("collide", forceCollide)
	.force("gravity", forceGravity)
	.on("tick", ticked)
	.on("end", endSimulation);

	// Força entre links (citações)
	// Circulos com alguma ligação serão posicionados 
	// próximos caso strength != 0.
	// Default strenght == 0 (somente exibe links/setas)
	if ( selectedTab.links !== null ){
		var forceLink = d3.forceLink()
		.id(function(d) { return d.id; })
		.links(selectedTab.links)
		.strength(0)
		.distance(0);

		selectedTab.simulation
		.force("link", forceLink);
	}

	// Se a força de clusterização estiver selecionada,
	// ativa a força na simulação.
	if ( clusteringForceOn )
		selectedTab.simulation.force("cluster", forceCluster());
}

/** Função para enviar coordenadas da area selecionada
 * pela ferramenta de zoom
 * @param start posicao x,y inicial
 * @param end posicao x,y final
 * @returns void
 **/
function selectArea(p){

	if ( tabs.length >= maxNumberOfTabs ){
		showMaxTabsAlert();
		return;
	}

	var numClusters = $("#num-clusters").val();
	var r = jsRoutes.controllers.HomeController.zoom(),
	width = $("#" + selectedTab.id + " svg").width(),
	height = $("#" + selectedTab.id + " svg").height(),
	selectionWidth = $(".selection").width(),
	selectionHeight = $(".selection").height();

	// Transforma coordenada para espaço original
	// no intervalo [-1,1]
	var start = [ selectedTab.x_inv(p[0] - selectionWidth/2 ) , selectedTab.y_inv(p[1] - selectionHeight/2) ],
	end = [ selectedTab.x_inv(p[0] + selectionWidth/2), selectedTab.y_inv(p[1] + selectionHeight/2)];
	$.ajax({url: r.url, type: r.type, data: {
		start: start,
		end: end,
		width: width,
		height: height,
		numClusters: numClusters,
		zoomLevel: selectedTab.zoomLevel + 1
	},
	success: createVisualization, error: errorFn, dataType: "json"});
}

/** Finaliza simulação.
 * Ativa botao de reheating e habilita tooltips.
 * 
 * @returns void
 */
function endSimulation(){
	selectedTab.circles
	.attr("fill-opacity", 0.65);

	$("#reheat-btn").prop('disabled', false);
}

/** Executa a cada iteracão da simulação.
 * Corrigi posição cx,cy dos circulos evitando
 * que estes saiam fora da area de visualização
 * (bounding box).
 *  
 * @returns void
 */
function ticked(){

	var width = $('#' + selectedTab.id + " svg").width();
	var height = $('#' + selectedTab.id + " svg").height();

	selectedTab.circles
	.attr("cx", function(d) { return (d.x = Math.max(d.r, Math.min(width - d.r, d.x)));})
	.attr("cy", function(d) { return (d.y = Math.max(d.r, Math.min(height - d.r, d.y)));});

	if ( selectedTab.path !== null ){
		selectedTab.path
		.attr("d", linkArc);
	}
}