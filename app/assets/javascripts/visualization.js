var margin = {top: 100, right: 100, bottom: 100, left: 100},
padding = 3, // separation between same-color nodes
fixRadius = 5;

//Inicializa visualização
$(function(){

	$("#reset-btn").prop('disabled', true);
	$("#step-btn").prop('disabled', true);
	$("#reheat-btn").prop('disabled', true);
	$("#show-list-btn").prop('disabled', true);
	$("#zoom-btn").prop('disabled', true);
	$("#download-btn").prop('disabled', true);
	$("#show-word-cloud-btn").prop('disabled', true);

	$("#reset-btn").click(resetVisualization);
	$("#zoom-btn").click(zoomTool);
	$("#download-btn").click(downloadDocuments);

//	$("#zoom-btn").on( "mouseleave", function(){
//	$(this).tooltip('hide');
//	} );
	$("#show-list-btn").on( "mouseleave", function(){
		$(this).tooltip('hide');
	} );

	$("#step-btn").click(nextVisualizationStep);
	$("#reheat-btn").click(reheat);
	$("#show-list-btn").click(showListTool);

	// Tooltip on mouse over
	d3.select("body").append("div")
	.attr("class", "node-tooltip")
	.style("opacity", 0);
	
	//Static (fixed_ tooltip
	d3.select("body").append("div")
	.attr("class", "fixed-tooltip")
	.style("opacity", 1);

});

//Criar uma visualização em uma nova aba
createVisualization = function(jsonData){

	var svg;

	// Esconde loading...
	$('#loading').addClass('hidden');	

	// Se não houver nenhum documento, 
	// não há nada que desenhar
	var n = jsonData.documents.length;
	if ( n === 0 ){
		noDocumentsFound();
		var isZoomActive = $('#zoom-btn').hasClass('active');
		if ( isZoomActive ){
			svg = d3.select("#" + selectedTabId + " svg.visualization");
			activeZoom(svg);
		}
		return;
	}

	var	currentTab;
	if ( jsonData.page >= 0 ){
		currentTab = tabs[ jsonData.tabId ];		
		currentTab.page = jsonData.page;		
		cleanVisualization( currentTab );		
	}
	else {
		// Cria nova aba
		currentTab = addNewTab(jsonData.op);
		currentTab.ndocs = jsonData.ndocs; // numero total de documentos (todas as paginas)		
		currentTab.page = 0;
	}
	
	currentTab.step = 1;	

	$("#reset-btn").prop('disabled', false);
	$("#step-btn").prop('disabled', false);
	$("#show-list-btn").prop('disabled', false);
	$("#zoom-btn").prop('disabled', false);
	$("#download-btn").prop('disabled', false);
	$("#show-word-cloud-btn").prop('disabled', false);


	svg = d3.select("#" + currentTab.id + " svg.visualization");
	var footerHeight = 80, // 72px;
	headerHeight = $('.navbar').height() + $('.nav-tabs-wrapper').height();

	var maxHeight = $(window).height() - (headerHeight + footerHeight);
	var width = $("#" + currentTab.id + " svg.visualization").width(),	
	height = Math.min( width * 9 / 16 - (headerHeight + footerHeight), maxHeight); //$("#" + currentTab.id + " svg").height();

	$('#' + currentTab.id + ' .visualization-wrapper')
	.css('max-height', height)
	.css('width', '100%')
	.css('left', '0%');

	$("#" + currentTab.id + " svg.visualization").height(height);
	$(".tab-content").height(height);

	svg.attr('viewBox', '0 0 ' + width + ' ' + height);
	svg.attr('preserveAspectRatio', 'xMidYMid meet');

	svg.on('click', function(){
		d3.select('.node-tooltip')
		.transition()
		.duration(500)
		.style('opacity', 0)
		.style('display', 'none');		
	});	
		
	$('.node-tooltip').on('mouseleave', function(){
		d3.select('.node-tooltip')
		.transition()
		.duration(500)
		.style('opacity', 0)
		.style('display', 'none');		
	});	

	// Adiciona paginacao (Previous, Next)
	addPagination(currentTab);
	addFixedToolTip(currentTab);
	
	var maxDocs = $("#max-number-of-docs").val();	
	var minRank, maxRank, minRadius, maxRadius;

	maxRank = jsonData.documents[0].rank;
	minRank = jsonData.documents[n-1].rank;
	maxRadius = width * jsonData.maxRadiusPerc;
	minRadius = width * jsonData.minRadiusPerc;

	// Radius interpolator based on document relevance
	currentTab.radiusInterpolator = d3.scaleLinear()
	.domain([minRank, maxRank])
	.range([minRadius, maxRadius])
	.interpolate(d3.interpolateRound);

	// Carrega documentos para a aba atual
	var index = currentTab.loadData(jsonData, width * height, maxDocs);	

	// Valores min/max das coordenadas x,y 
	// dos documentos atuais
	var minMaxX, minMaxY;
	if ( jsonData.op === "zoom"){
		minMaxX = [jsonData.bounds[0], jsonData.bounds[2]];
		minMaxY = [jsonData.bounds[1], jsonData.bounds[3]];
	}
	else{
		minMaxX = [-1,1]; //[d3.extent(jsonData.documents,function(d){ return d.x; });
		minMaxY = [-1,1]; //d3.extent(jsonData.documents,function(d){ return d.y; });
	}

	var densityMap = jsonData.densityMap;

	// Inicializa transformações 2D (x,y) 
	// do intervalo [-1,1] para [0, width/height]
	currentTab.xy(width, height, minMaxX[0], minMaxX[1], minMaxY[0],minMaxY[1]);
	currentTab.xy_inverse(width, height);

	// Criar contornos
	var contours = null, contourColor;
	var gridSize = [256,256];
	if (currentTab.densities !== undefined && currentTab.densities !== null){
		if ( densityMap === 1 ){
			contours = d3.contourDensity()
			.x(function(d){ return selectedTab.x(d.x);})
			.y(function(d){ return selectedTab.y(d.y);})
			.size([width,height])
			.thresholds(20)
			.bandwidth(20);			
		}
		else{
//			var thresholds = d3.range(0,5)
//			.map(function(p){ return  Math.pow(2,p);});

			gridSize = jsonData.gridSize;
			contours = d3.contours()
			.thresholds(20)
			.size(gridSize);					
		}
	}

	$.when(contourDensityPromise(contours, densityMap, currentTab, minMaxX, minMaxY)).done( function( contours, tab, densityMap, minMaxX, minMaxY ){

		var svg = d3.select("#" + tab.id + " svg.visualization");
		if ( contours !== null ){
			var contourColor = d3.scaleSequential(function(t) {
				if ( t > 0)
					return d3.interpolateOranges(t);
				return "rgb(255,255,255)";
			})
			.domain(d3.extent(contours.map(function(p) { return p.value;}))); // Points per square pixel.
			
			svg.insert("g", "g")
			.attr("fill", "none")
//			.attr("stroke", "#000")
//			.attr("stroke-width", 0.5)
//			.attr("stroke-linejoin", "round")
			.selectAll("path")
			.data(contours)
			.enter().append("path")
			.attr("d", densityMap === 1 ? d3.geoPath() : d3.geoPath(d3.geoIdentity()
					.scale(width / contours.size()[0])
			))
			.attr("fill", function(d){ return contourColor(d.value);});
		}

		// Criar ou copia minimapa
		if ( currentTab.parentId === null ){
			createMiniMap(svg, tab);
		}
		else{
			copyMiniMapFromParent(svg, tab, minMaxX[0], minMaxY[0], minMaxX[1], minMaxY[1]);
		}

		contours = null; //Libera memoria
	} );
	
	contours = null; //Libera memoria
	jsonData = null; //Libera memoria

	// inicializa esquema de cores
	currentTab.initColorSchema(minRank, maxRank);	

	//Cria nós
	currentTab.nodes = createNodes(currentTab);	

	// Se pontos do contorno devem ser mostrados na
	// visualizaçã, cria pontos.	
	var showPoints = $("#density-points-on-off").prop('checked');
	if ( showPoints ){
		currentTab.points = createPoints(currentTab.densities);
	}	

	// Marcadores dos links (setas)
	var defs = svg.append("defs");
	defs.selectAll("marker")
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
	.attr('fill-opacity', 0.80)
	.on("mouseover", showTip)
	.on("mouseout", setDefaultRadius)
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

	// Exibe/Esconde circulos?
	var showCircles = $('#show-circles-btn').hasClass('glyphicon-eye-close');
	if ( showCircles ){
		d3.selectAll('circle')
		.style('display', null);
	}
	else{
		d3.selectAll('circle')
		.style('display', 'none');
	}

	// Adiciona slider de relevancia
	addRelevanceSlider(currentTab);
	
	// Constroi nuvem de palavras	
	var wordsMap = d3.map();
	$.each(currentTab.documents, function(ind, d){
		if ( d.words ){
			for(var i = 0; i < d.words.length; i++){
				var word = d.words[i];
				if ( wordsMap.has( word.text ) ){
					wordsMap.get( word.text ).size += word.size;					
				}
				else{
					wordsMap.set( word.text, {text: word.text, size: word.size});
				}
			}
		}
	});

	// Configura da nuvem de palavras
	var cfg = {
			selector: '#' + currentTab.id + ' .word-cloud',
			minWordSize: 10,
			maxWordSize: 60,
			minWordNumber: 0,
			maxWordNumber: -1,
			width: width / 3,
			height: height,
			colors: [
				'#1f77b4', 
				'#aec7e8',
				'#ff7f0e', 
				'#ffbb78', 
				'#2ca02c',
				'#98df8a',
				'#d62728',
				'#ff9896',
				'#9467bd',
				'#c5b0d5'
				]
	};

	$.when( getWordCloudPromise( wordsMap.values(), cfg, currentTab ) ).done(function( wc, tab ){ tab.wordCloud = wc; });			

	setupSimulation(currentTab);
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
	manyBodyStrength = parseFloat($("#manybody-force").val());	

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
	selectedTab.simulation
	.force("collide", forceCollide)
	.force("gravity", forceGravity);		
	
	// Reinicia simulacao
	//reheat();
	selectedTab.simulation
	//.alphaTarget(0.001)
	.restart();
}

function setupSimulation(tab){
	// Inicializa simulação
	if ( tab.simulation === undefined || tab.simulation === null ){
		tab.simulation = d3.forceSimulation(tab.nodes)
		.stop()
		.on("tick", function() { tab.ticked(); })
		.on("end", function() { tab.endSimulation(); });
	}
	else {
		tab.simulation
		.on("tick", function() { tab.ticked(); })
		.on("end", function() { tab.endSimulation(); });
	}
}

/** Função para enviar coordenadas da area selecionada
 * pela ferramenta de zoom
 * @param start posicao x,y inicial
 * @param end posicao x,y final
 * @returns void
 **/
function selectArea(p){

	if ( tabCount >= maxNumberOfTabs ){
		showMaxTabsAlert();
		return;
	}
	
	var r = jsRoutes.controllers.HomeController.zoom(),
	selectionWidth = $(".selection")[0].style.width.replace("px", ""),
	selectionHeight = $(".selection")[0].style.height.replace("px",""),
	maxDocs = $("#max-number-of-docs").val();

	$.ajaxPrefilter(function (options, originalOptions, jqXHR) {
		jqXHR.setRequestHeader('Csrf-Token', $("input[name='csrfToken']").val());
	});

	// Transforma coordenada para espaço original
	// no intervalo [-1,1]
	var start = [ selectedTab.x_inv(p[0] - selectionWidth/2 ) , selectedTab.y_inv(p[1] - selectionHeight/2) ],
	end = [ selectedTab.x_inv(p[0] + selectionWidth/2), selectedTab.y_inv(p[1] + selectionHeight/2)];

	selectedTab.query.start = start;
	selectedTab.query.end = end;

	$.ajax({
		url: r.url, 
		type: r.type, 
		data: selectedTab.query,
		success: createVisualization, 
		error: errorFn, 
		dataType: "json"
	});
	
	$('#zoom-btn').click();
}