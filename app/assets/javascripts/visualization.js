var tip;

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
	tip = d3.select("body").append("div")
	.attr("class", "node-tooltip")
	.style("opacity", 0);

});

//Criar uma visualização em uma nova aba
createVisualization = function(jsonData){

	// Cria nova aba
	var	currentTab = addNewTab(jsonData.op);

	//Adiciona data ao vetor de dados de cada aba
	currentTab.data = jsonData;
	currentTab.step = 1;

	$('#loading').addClass('hidden');
	$("#step-btn").prop('disabled', false);
	$("#show-list-btn").prop('disabled', false);

	var svg = d3.select("#" + currentTab.id + " svg"),
	width = $("#" + currentTab.id + " svg").width(),
	height = $("#" + currentTab.id + " svg").height();

	svg.attr('width', width);
	svg.attr('height', height);

//	//Clear previous visualization
//	svg.selectAll('g').remove();
//	svg.selectAll('defs').remove();
//	$("#" + currentTab.id + " .documents-table table tbody").empty();

	var n = currentTab.data.documents.length, // no. total de documentos
	m = currentTab.data.nclusters; // no. de clusters

	// Se não houver nenhum documento, 
	// não há nada que desenhar
	if ( n === 0 ){
		return;
	}

	// Inicializa transformações 2D (x,y) 
	// do intervalo [-1,1] para [0, width,height]
	currentTab.xy(width, height);

	// Criar contornos
	var contours = d3.contourDensity()
	.x(function(d){ return selectedTab.x(d[0]);})
	.y(function(d){ return selectedTab.y(d[1]);})
	.size([width,height])
	.bandwidth(40)
	(currentTab.data.density);

	// Inicializa coloração dos clusters
	currentTab.initClusterColor(m);

	// Coloração dos contornos
	var contourColor = d3.scaleSequential(d3.interpolateOranges)
	.domain(d3.extent(contours.map(function(p) { return p.value;}))); // Points per square pixel.

	// Array para armazenar o maior nó de cada cluster (centroide)
	currentTab.clusters = new Array(m);

	//Cria nós
	currentTab.nodes = createNodes(currentTab.data.documents);

	// Se pontos do contorno devem ser mostrados na
	// visualizaçã, cria pontos.
	var showPoints = $("#show-density-points").prop('checked');
	if ( showPoints ){
		currentTab.points = createPoints(currentTab.data.density);
	}

	// Marcadores dos links (setas)
	svg.append("defs").selectAll("marker")
	.data(["link"])
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
	.attr('fill', function(d) { return  selectedTab.clusterColor(d.cluster);})
	.attr('stroke', 'black')
	.attr('stroke-width', 1)
	.attr('fill-opacity', 0.65);

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
		.attr('fill', function(d) { return  selectedTab.clusterColor(d.cluster);})
		.attr('stroke', 'black')
		.attr('stroke-width', 1);
	}

	// Desenha links entre circulos
	if ( currentTab.links !== null ){
		currentTab.path = g
		.selectAll("path")
		.data(currentTab.links)
		.enter().append("path")
		.attr("class", "link")
		.attr('stroke-width', 1.5)
		.attr("marker-end", function(d) { return "url(#link)"; });
	}

	// Ferramenta de seleção (escondia por enquanto).
	var selection = svg.append("path")
	.attr("class", "selection")
	.attr("visibility", "hidden");

	// Zoom: inicia seleção
	var startSelection = function(start) {
		selection.attr("d", rect(start[0], start[0], 0, 0))
		.attr("visibility", "visible");
	};
	// Zoom: selecionando
	var moveSelection = function(start, moved) {
		selection.attr("d", rect(start[0], start[1], moved[0]-start[0], moved[1]-start[1]));
	};
	// Zoom: fim da seleção
	var endSelection = function(start, end) {
		selection.attr("visibility", "hidden");
		selectArea(start,end);
	};

	// Eventos de seleção (zoom)
	svg.on("mousedown", function() {
		var isActive = $("#zoom-btn").hasClass("active");
		if ( isActive ){
			var subject = d3.select(window), parent = this.parentNode,
			start = d3.mouse(parent);
			startSelection(start);
			subject
			.on("mousemove.selection", function() {
				moveSelection(start, d3.mouse(parent));
			}).on("mouseup.selection", function() {
				endSelection(start, d3.mouse(parent));
				subject.on("mousemove.selection", null).on("mouseup.selection", null);
			});
		}
	});

	// Mostra tooltips sobre os pontos de densidade,
	// apenas coordenadas x,y.
	if ( showPoints ){
		point
		.on("mouseover", function(p){
			var d = p.data;
			var html = "<p>x = " + d.x + ", y = " + d.y + "<p>";

			tip.transition()
			.duration(500)
			.style("opacity", 0);

			tip.transition()
			.duration(200)
			.style("opacity", 0.9)
			.style("display", "block");

			tip.html(html)
			.style("left", "150px")
			.style("top", "150px");
			d3.select(this).style("stroke-opacity", 1);

		})
		.on("mouseout", hideTip);
	}

//	++currentTab.step;

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

	// Força entre links (citações)
	// Circulos com alguma ligação serão posicionados 
	// próximos caso strength != 0.
	// Default strenght == 0 (somente exibe links/setas)
	var forceLink = d3.forceLink()
	.id(function(d) { return d.id; })
	.links(selectedTab.links)
	.strength(0)
	.distance(0);

	// Força de atração
	// Evita dispersão
	var forceGravity = d3.forceManyBody()
	.strength( manyBodyStrength );

	// Inicializa simulação
	selectedTab.simulation = d3.forceSimulation(selectedTab.nodes)
	.force('link', forceLink)
	.force("collide", forceCollide)
	.force("gravity", forceGravity)
	.on("tick", ticked)
	.on("end", endSimulation);

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
function selectArea(start, end){
	
	if ( tabCount >= maxNumberOfTabs ){
		showMaxTabsAlert();
		return;
	}
	
	var numClusters = $("#num-clusters").val();
	var r = jsRoutes.controllers.HomeController.zoom(),
	width = $("#" + selectedTab.id + " svg").width(),
	height = $("#" + selectedTab.id + " svg").height();

	$.ajax({url: r.url, type: r.type, data: {
		start: start,
		end: end,
		width: width,
		height: height,
		numClusters: numClusters
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
	.attr("fill-opacity", 0.65)
	.on("mouseover", showTip)
	.on("mouseout", hideTip)
	.on("click", toggleLinks);

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