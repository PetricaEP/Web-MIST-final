/**
 * Cria nós a partir dos documentos retornados
 * pela busca
 * @param documents documentos
 * @returns nós da visualização.
 */
function createNodes(documents){
	var docIds = [],
	nodes;
	nodes = $.map(documents, function(doc, index){
		if (doc.x !== undefined && doc.y !== undefined ){
			// Cria nova nó
			d = {
					id: doc.id,
					cluster: doc.cluster,
					r: selectedTab.radiusInterpolator(doc.rank),
					x: selectedTab.x(doc.x),
					y: selectedTab.y(doc.y),
					data: doc
			};

			// Adiciona docID para busca por referencias
			docIds.push(doc.id);

			// Atualiza centroide do cluster
			if (!selectedTab.clusters[ doc.cluster ] || (d.r > selectedTab.clusters[ doc.cluster ].r)) 
				selectedTab.clusters[ doc.cluster ] = d;
			return d;
		}
	});

	fetchReferencesAjax(docIds);

	nodes.sort(function(a,b){
		return b.data.rank - a.data.rank; 
	});
	return nodes;
}

/**
 * Consulta referencias somente para os documentos
 * com os ID's especificados.
 * @param docIds
 * @returns
 */
function fetchReferencesAjax(docIds){


	$.ajaxPrefilter(function (options, originalOptions, jqXHR) {
		jqXHR.setRequestHeader('Csrf-Token', $("input[name='csrfToken']").val());
	});

	var r = jsRoutes.controllers.HomeController.references(docIds);
	$.ajax({
		url: r.url, 
		type: r.type, 
		success: processReferences, error: errorFn, dataType: "json"});
}

function processReferences(data){	
	// Citações
	if ( data.references ){
		var i,j, docId, refId;
		var references = data.references;
		selectedTab.links = [];
		$.each(references, function(docId){
			references[docId].forEach(function(refId){
				selectedTab.links.push({	
					source: docId | 0,
					target: refId | 0
				});
			});
		});
	}

	// Desenha links entre circulos
	if ( selectedTab.links !== null ){
		selectedTab.path = d3.select("#" + selectedTab.id + " svg g")
		.selectAll("path")
		.data(selectedTab.links)
		.enter().append("path")
		.attr("class", "link")
		.attr('stroke-width', 1.5)
		.attr("marker-end", function(d) { return "url(#link" + selectedTab.id + ")"; });		

		// Força entre links (citações)
		// Circulos com alguma ligação serão posicionados 
		// próximos caso strength != 0.
		// Default strength == 0 (somente exibe links/setas)
		var forceLink = d3.forceLink()
		.id(function(d) { return d.id; })
		.links(selectedTab.links)
		.strength(0)
		.distance(0);

		// Cria uma nova simulacao caso ainda nao tenha sido criada
		setupSimulation(selectedTab);
				
		selectedTab.simulation
		.force("link", forceLink)
		.restart();		
	}
}

/**
 * Cria pontos a partir do mapa de densidade
 * @param density densidade (vetor de coordeanadas x,y)
 * @returns vetor de pontos
 */
function createPoints(density){
	var points = [];
	for(var i = 0; i < density.length; i++){
		d = {
				id: i,
				cluster: 1,
				r: 3,
				x: selectedTab.x(density[i].x),
				y: selectedTab.y(density[i].y),
				data: {x: density[i].x, y: density[i].y}
		};
		points.push(d);
	}
	return points;
}

/**
 * Exibe tooltips quando o mouse
 * estiver sobre um nó.
 */
function showTip(n){
	var tip = d3.select('.node-tooltip');

	tip.transition()
	.duration(200)
	.style("opacity", 0.9)	
	.style('display', 'block');

	tipHtml = createToolTip(n);
	tip.html(tipHtml);

	var svg = $('#' + selectedTab.id + " svg");
	var x = d3.event.clientX, 
	y = d3.event.clientY;
	var tipW = $('.node-tooltip').width(),
	tipH = $('.node-tooltip').height();

	if ( x + tipW >= svg.width())
		x -= tipW + 10;
	if ( y + tipH >= svg.height())
		y -= tipH + 10;

	y = Math.max(0, y);
	x = Math.max(0, x);

	tip
	.style("left", (x) + "px")
	.style("top", (y) + "px");

	d3.select(this)
	.transition()
	.duration(300)
	.attr('r', function(d) {
		if ( selectedTab.step == 1)
			return fixRadius *  3;
		return d.r * 1.4; 
	});
}

function createToolTip(n){
	var d = n.data;
	var tipHtml; 
	if ( d.doi !== null) {
		tipHtml = '<a href="https://dx.doi.org/' + d.doi + '" target="_blank"><p>';
	}
	else {
		tipHtml = '<a href="#"><p>';
	}

	if (d.title)
		tipHtml += "<strong>" + d.title + "</strong>";
	if ( d.authors && d.authors.length > 0){
		tipHtml +=", " + d.authors[0].name;
		for(var i = 1; i < d.authors.length; i++){
			tipHtml += "; " + d.authors[i].name;
			if (i >= 10){
				tipHtml += '; <span class="et-al"i>et al.</span>';
				break;
			}
		}
	}
	if ( d.publicationDate )
		tipHtml +=  ", " + d.publicationDate;
	tipHtml += "</p></a>";
	return tipHtml;
}

/**
 * Esconde tooltip
 * @param d nó a qual a tooltip pertence.
 * @returns
 */
function setDefaultRadius(d){

	d3.select(this)
	.transition()
	.duration(300)
	.attr('r', function(d) {
		if ( selectedTab.step == 1)
			return fixRadius;
		return d.r; 
	});	
}

/**
 * Ativa links (citações) quando nós são
 * selecionados.
 * @param d nó selecionado.
 * @param index indice do nó selecionado.
 */
function toggleLinks(d,index){

	//removeToolTip();

	// Adiciona ou remove documento do array 
	// de documentos selecionados
	var selectCircle = selectedTab.selectedCircles[d.id] === undefined;
	if ( selectCircle ){
		selectedTab.selectedCircles[d.id] = d.id;
	}
	else{
		delete selectedTab.selectedCircles[d.id];
	}

	// Marca docs selecionados
	highligthSelectedCircle(this, selectCircle);

	if ( !selectedTab.links ) return;

	d3.event.stopPropagation();

	var i;
	var paths = d3.selectAll('#' + selectedTab.id + ' path.link');
	var nodes = paths.nodes();
	var edges = paths.data();

	for(i = 0; i < edges.length; i++){
		if (edges[i].source == d){
			var l = d3.select(nodes[i]);
			l.classed('active', !l.classed('active'));
		}
	}

	// Cria tooltip fixa
//	var tip = d3.select("body").append("div")
//	.attr('class', 'fixed-tooltip')
//	.style("opacity", 0);

//	tip.transition()
//	.duration(200)
//	.style("opacity", 0.9)
//	.style("display", "block");

//	tipHtml = createToolTip(d);
//	tip.html(tipHtml);

//	var svg = $('#' + selectedTab.id + " svg");
//	var x = d3.event.clientX, 
//	y = d3.event.clientY;
//	var tipW = $('.node-tooltip').width(),
//	tipH = $('.node-tooltip').height();

//	if ( x + tipW >= svg.width())
//	x -= tipW + 10;
//	if ( y + tipH >= svg.height())
//	y -= tipH + 10;
//	tip
//	.style("left", (x) + "px")
//	.style("top", (y) + "px");

	// Marca linhas na lista de documentos como ativas
	var row = $("#" + selectedTab.id + " .documents-table table tbody tr")[index];
	$(row).toggleClass('success');
}

function highligthSelectedCircle(circle, selected){
	d3.select(circle).style("stroke-opacity", selected ? "1" : "0");
}

function removeToolTip(){
	// Remove tooltip
	d3.selectAll('.node-tooltip')
	.transition()
	.duration(500)
	.style('opacity', 0)
	.style('display', 'none');

	d3.selectAll('.fixed-tooltip')
	.transition()
	.duration(500)
	.style('opacity', 0)
	.style('display', 'none')
	.remove();
}

/**
 * Seleciona uma linha da lista de documentos.
 * @returns void
 */
function selectRow(){
	var point = getPoint(this);
	point.dispatch('click', {detail: this});
}

/**
 * Retorna o nó correspondente a linha 
 * selecionada na lista de documentos.
 * @param row objeto DOM da linha selecionada
 * @returns nó selecionado na visualização.
 */
function getPoint(row){
	var index = parseInt($(row).children('td.doc-index').html());
	return selectedTab.circles.filter(function (d, i) { return i === index;});
}

/**
 * Exibe arcos entre círculos na visualização
 * @param l link (arco) entre dois nós
 * @returns objeto SVG para desenhar o arco
 */
function linkArc(l) {
	var dx = l.target.x - l.source.x,
	dy = l.target.y - l.source.y,
	dr = Math.sqrt(dx * dx + dy * dy);
	return "M" + l.source.x + "," + l.source.y + "A" + dr + "," + dr + " 0 0,1 " + l.target.x + "," + l.target.y;
}

/**
 * Força de clusterização.
 * Esta força mantém nós de um mesmo cluster
 * próximos na visualização e pode ser 
 * ativada/desativada nas configurações
 * da visualização.
 * @returns nova força de clusterização.
 */
function forceCluster(){

	var nodes;

	function force(alpha) {
		nodes.forEach(function(d) {
			var cluster = selectedTab.clusters[d.cluster];
			if (cluster === d) return;
			var x = d.x - cluster.x,
			y = d.y - cluster.y,
			l = Math.sqrt(x * x + y * y),
			r = d.r + cluster.r;
			if (l !== r) {
				l = (l - r) / l * alpha;
				d.x -= x *= l;
				d.y -= y *= l;
				cluster.x += x;
				cluster.y += y;
			}  
		});
	}

	force.initialize = function(_) {
		var width = $("#" + selectedTab.id + " svg").width(),
		height = $("#" + selectedTab.id + " svg").height();

		nodes = _;
	};

	return force;
}

/**
 * Criar retangulo de seleção (zoom)
 * @param x coordenada inical x
 * @param y coordenada inical y
 * @param w largura da seleção (retângulo).
 * @param h altura da seleção (retângulo).
 * @returns objeto SVG para desenhar retângulo.
 */
function rect(x, y, w, h) {
	return "M"+[x-w/2,y-h/2]+" l"+[w,0]+" l"+[0,h]+" l"+[-w,0]+"z";
}

/**
 * Executa próximo passo na visualização.
 * @returns void
 */
function nextVisualizationStep(){
	if ( selectedTab.step == 1){
		secondStep();
		++selectedTab.step;
	}
	else if ( selectedTab.step == 2){
		thirdStep();
		++selectedTab.step;
		$('#step-btn').prop('disabled',true);
	}
}

/**
 * Reaquece visualização.
 * Incrementa valor do parâmetro alpha na
 * simulação em 0.01 e reinicia simulação.
 * @returns
 */
function reheat(){
	var alpha = selectedTab.simulation.alpha();
	alpha += 0.01;
	selectedTab.simulation
	.alpha(alpha)
	.alphaTarget(0.001)
	.restart();
}

//####### Tabela de documentos #######

/**
 *  Exibe/Esconde lista de documentos.
 * @returns void
 */
function showListTool(){
	var isActive = !$(this).hasClass("active");
	$('#' + selectedTab.id + " .documents-table table").toggleClass("hidden", !isActive);
	if (  !isActive ){
		hideDocumentList();
	}
	else{
		showDocumentList();
		//Make rows clickable
		$('#' + selectedTab.id + " .documents-table table tbody tr").click(selectRow);
		$('#' + selectedTab.id + " .documents-table table tbody tr").on('mouseover', function(){
			var p = getPoint(this);
			p.dispatch('mouseover');
		});
		$('#' + selectedTab.id + " .documents-table table tbody tr").on('mouseout', function(){
			var p = getPoint(this);
			p.dispatch('mouseout');
		});
	}
}

/**
 * Cria lista de documentos.
 * @returns void
 */
function showDocumentList(){
	var info = '<p>' + Messages('js.show.doc.list.info') + ': ' + selectedTab.n + '</p>';
	$('#' + selectedTab.id + ' .total-documents-info').append(info);
	selectedTab.nodes.forEach(function(d, ind){
		//Add to documents table
		addDocumentToTable(ind, d);
	});
}

/**
 * Esconde lista de documentos.
 * @returns void
 */
function hideDocumentList(){
	$('#' + selectedTab.id + ' .documents-table .table tbody').empty();
	$('#' + selectedTab.id + ' .total-documents-info').empty();
}

/**
 * Adiciona um documento à lista de documentos.
 * @param index indice do documento na lista.
 * @param node respectivo nó na visualização.
 * @returns void
 */
function addDocumentToTable(index, node){
	var doc = node.data;
	var title;

	if ( doc.doi )
		title = '<a href="https://dx.doi.org/' + doc.doi + '" target="_blank">' + 
		doc.title + '</a>';
	else
		title = doc.title;

	var row = '<tr><td class="doc-index">' + index + '</td><td class="doc-title">' + title +
	'</td><td class="doc-authors">';


	if ( doc.authors && doc.authors.length > 0){
		row += doc.authors[0].name;
		for(var i = 1; i < doc.authors.length; i++){
			row += "; " + doc.authors[i].name;
		}
	}
	row += '</td><td class="doc-year">';
	if ( doc.publicationDate )
		row += doc.publicationDate;
	row += '</td>';

	var rank = (doc.documentRank * 100).toFixed(3) + " / " + (doc.authorsRank * 100).toFixed(3) + " = " + (doc.rank * 100 ).toFixed(3);

	row += '<td class="doc-relevance">' + rank + '</td>';
	/* row += '<td class="doc-cluster">' + 
		'<svg><circle cx="15" cy="15" r="10" stroke-width="0" fill="' + selectedTab.coloring(doc) + '"/></svg>'  +
		'</td>';
	 */
	row += '</tr>';
	$('#' + selectedTab.id + ' .documents-table .table tbody').append(row);
}


/** Reseta visualização.
 * Marca todos links como inativos e linhas
 * da lista de documentos como não selecionadas.
 * @param e evento click
 * @returns void
 */
function resetVisualization(e){
	// Links
	d3.selectAll("#" + selectedTab.id + ' path.link').classed('active', false);
	$("#" + selectedTab.id + " .documents-table table tbody tr").removeClass('success');
	// Circulos selecionados
	d3.selectAll('circle').style("stroke-opacity", "0");	
}

/**
 * Altera cursor caso ferramenta de zoom 
 * esteja selecionada para um lupa.
 * @param e evento de click no botão.
 * @returns void
 */
function zoomTool(e){
	var isActive = !$(this).hasClass("active");
	$(".visualization-wrapper")
	.toggleClass("zoom-cursor", isActive);
	var svg = d3.select("#" + selectedTab.id + " svg");
	if ( isActive ){
		activeZoom(svg);
	}
	else{
		desactiveZoom(svg);
	}
}

/**
 * Ativa zoom
 * @param svg svg da aba atual
 * @returns void
 */
function activeZoom(svg){

	svg
	.on('mousemove', function(){
		var start = d3.mouse(this);
		startSelection(start, svg);
	})
	.on("click.selection", function() {
		// Remove event listeners
//		d3.select(this).on("mousemove", null);
//		d3.select(this).on("wheel", null);
//		d3.select(this).on("click.selection", null);		
		endSelection(d3.mouse(this));
		$('#loading').removeClass('hidden');
	})
	.on("wheel", function(){
		var delta = d3.event.deltaY,
		newWidth, newHeight;

		var selectionWidth = $(".selection")[0].style.width.replace("px", ""),
		svgWidth = svg.attr('width');

		if ( delta > 0 ){
			newWidth = Math.min(svgWidth, selectionWidth * 1.1);
			newHeight = newWidth * 6 / 16;
		}
		else{
			newWidth = Math.max(200, selectionWidth * 0.9);
			newHeight = newWidth * 6 / 16;
		}

		$(".selection")[0].style.width = newWidth + 'px';
		$(".selection")[0].style.height = newHeight + 'px';

		var start = d3.mouse(this);
		svg.select('.selection')
		.attr("d", rect(start[0], start[1], newWidth, newHeight));
	});

	// Remove listener para eventos de click
	// nos circulos
	if ( selectedTab.circles.length > 0 ){
		selectedTab.circles
		.on("mouseover", null)
		.on("mouseout", null);
	}

	d3.selectAll('.selection')
	.attr("visibility", "visible");
}

/**
 * Desativa zoom
 * @param svg svg da aba atual
 * @returns void
 */
function desactiveZoom(svg){
	svg.on('mouseover', null);
	svg.on('click.selection', null);
	d3.selectAll('.selection')
	.attr("visibility", "hidden");

	selectedTab.circles
	.on("mouseover", showTip)
	.on("mouseout", setDefaultRadius);
}

//Zoom: inicia seleção
function startSelection(start, svg) {
	var selectionWidth = $(".selection")[0].style.width,
	selectionHeight = $(".selection")[0].style.height;

	if ( selectionWidth === "" || selectionHeight === ""){
		$(".selection")[0].style.width = "200px";
		$(".selection")[0].style.height = "75px";
		selectionWidth = "200px";
		selectionHeight = "75px";
	}

	selectionWidth = selectionWidth.replace("px", "");
	selectionHeight = selectionHeight.replace("px", "");

	svg.select('.selection')
	.attr("d", rect(start[0], start[1], selectionWidth, selectionHeight))
	.attr("text", d3.mouse(svg));
}

//Zoom: fim da seleção
function endSelection(end) {
	selectArea(end);
}

/**
 * Cria aviso de maximo de abas atingido.
 * @returns
 */
function showMaxTabsAlert(){
	var alertHtml = '<div id="tabs-alert" class="alert alert-danger alert-dismissible" role="alert">' +
	'<button type="button" class="close" data-dismiss="alert" aria-label="Close">' +
	'<span aria-hidden="true">&times;</span></button>' + Messages('js.max.tabs.alert') + '</div>';	
	$('#tabs-alert-wrapper').append(alertHtml);
	$('#tabs-alert').alert();
}

function createMiniMap(svg, tab){
//	serialize our SVG XML to a string.
	var source = (new XMLSerializer()).serializeToString(svg.node());
//	Put the svg into an image tag so that the Canvas element can read it in.
	var img = new Image();

	img.src = 'data:image/svg+xml;base64,'+window.btoa(source);

	img.onload = function(){
		// Now that the image has loaded, put the image into a canvas element.
		var headerHeight = $('.navbar').height() + $('.nav-tabs-wrapper').height();
		var canvas = d3.select('#' + tab.id + " .minimap-wrapper").append('canvas', ':first-child')
		.classed('minimap', true).node();
		canvas.width = $("#" + tab.id + " .visualization-wrapper").width() * 0.2;
		canvas.height = Math.min( canvas.width * 9 / 16, headerHeight);
		var ctx = canvas.getContext('2d');
		ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
	};
}

function copyMiniMapFromParent(svg, tab, minX, minY, maxX, maxY){

	var canvas = d3.select('#' + tab.id + " .minimap-wrapper").append('canvas', ':first-child')
	.classed('minimap', true).node(),
	parentCanvas = $('#' + tab.parentId + ' .minimap-wrapper .minimap');

	canvas.width = $("#" + tab.id + " .minimap").width();
	canvas.height = $("#" + tab.id + " .minimap").height();
	var ctx = canvas.getContext('2d');
	ctx.drawImage(parentCanvas[0], 0, 0, canvas.width, canvas.height);

	// Acha posicoes x,y da selecao
	if ( minX > -1 && minY > -1 && maxX < 1 && maxY < 1){
		var xx = canvas.width * ( minX + 1) / 2,
		yy = canvas.height * (minY + 1) / 2,
		sx = canvas.width * ( maxX + 1) / 2 - xx,
		sy = canvas.height * (maxY + 1) / 2 - yy;
		ctx.beginPath();
		ctx.rect(xx,yy, sx, sy);
		ctx.fillStyle = "rgba(147,215,237,0.7)";
		ctx.fill();
	}

}

function noDocumentsFound(){
	$('.modal').modal('show');
}

/**
 * Cria paleta de cores para documentos
 * @param a valor minimo
 * @param b valor maximo
 * @returns d3.scaleThreshold
 */
function palette(a, b) {
	var d = (b-a)/7;
	return d3.scaleThreshold()
	.range(['#556b2f','#6f7d22','#7f921e','#8ca722','#95bf2b','#e8c219','#ffd700'])
	.domain([a+1*d,a+2*d,a+3*d,a+4*d,a+5*d,a+6*d,a+7*d]);
}


/**
 * Trata evento de alteracao 
 * dos sliders de rank min/max
 */
function sliderRankSlide(e){	
	var rankFactor = selectedTab.rankFactor;
	d3.selectAll('#' + selectedTab.id + ' circle')
	.style('opacity', "0.3")
	.filter(function(d,i){return d.data.rank * rankFactor >= e.value[0] && d.data.rank * rankFactor <= e.value[1];})
	.style('opacity', "1.0");
}

function sliderRankChange(e){
	// Atualiza nuvem de palavras
	// Constroi nuvem de palavras	
	var wordsMap = d3.map(); 
	var rankFactor = selectedTab.rankFactor;
	d3.selectAll('#' + selectedTab.id + ' circle')	
	.filter(function(d,i){return d.data.rank * rankFactor >= e.value[0] && d.data.rank * rankFactor <= e.value[1];})	
	.each( function(d){		
		for( var i = 0; i < d.data.words.length; i++){
			var word = d.data.words[i];
			if ( wordsMap.has( word.text ) ){
				wordsMap.get( word.text ).size += word.size;
			}
			else{
				wordsMap.set(word.text, {text: word.text, size: word.size});
			}			
		}
	});	

	selectedTab.wordCloud.update(wordsMap.values());
}

/**
 * Faz download de documentos selecionados
 *
 */
function downloadDocuments(){
	var docIds = [];
	for(var key in selectedTab.selectedCircles)
		docIds.push(key);
	var r = jsRoutes.controllers.HomeController.download(docIds);
	window.location=r.absoluteURL();
}

function transformContours(geometry){
	geometry.value *= Math.pow(2, -2 * 2); // Density in points per square pixel.
	geometry.coordinates.forEach(transformPolygon);
	return geometry;
}

function transformPolygon(coordinates) {
	coordinates.forEach(transformRing);
}

function transformRing(coordinates) {
	coordinates.forEach(transformPoint);
}

function transformPoint(coordinates) {
	coordinates[0] = coordinates[0] * Math.pow(2, 2) - 30;
	coordinates[1] = coordinates[1] * Math.pow(2, 2) - 30;
}

/**
 * Adiciona elementos para paginacao na
 * após a visualizacao (botoes next e previous)
 * @param tab aba de navegacao onde sera inserido a paginacao 
 * @returns void
 */  
function addPagination(tab){
	var maxDocs = $("#max-number-of-docs").val();
	var numPages = ( tab.ndocs / maxDocs ) | 0;
	var paginationHtml = '<div class="pagination col-sm-6"><nav aria-label="..."><ul class="pager">' +
	'<li class="previous ' + (tab.page === 0 ? 'disabled' : '' ) + '"><a href="#" data-page="prev">' + Messages('js.page.previous') + '</a></li>' +
	'<li class="total-documents">' + Messages('js.page.total.docs', tab.page * maxDocs + 1, Math.min( (tab.page + 1) * maxDocs, tab.ndocs), tab.ndocs) + '</li>' +
	'<li class="next ' + ( tab.page === numPages ? 'disabled' : '' ) + '"><a href="#" data-page="next">' + Messages('js.page.next') + '</a></li></ul></nav></div>';
	$('#' + tab.id + ' .viz-controls-footer').prepend(paginationHtml);
	$('#' + tab.id + ' .viz-controls-footer .pagination a').click(function(e){
		if ( $(e.target).parent().hasClass('disabled') !== true ){
			var newPage = $(e.target).data().page === 'next' ? tab.page + 1 : tab.page - 1;
			ajaxSubmitForm(newPage);
		}
	});
}

/**
 * 
 * @param tab
 * @returns
 */
function cleanVisualization( tab ){
	$('#' + tab.id).empty().append(newTabContent());
}

function contourDensityPromise( contours, densityMap, tab, minMaxX, minMaxY ){
	var deferred = $.Deferred();

	setTimeout( function(){
		if ( contours === null ){
			deferred.resolve( null, tab, null, minMaxX, minMaxY );
		}
		else{
			deferred.resolve( contours( tab.densities ), tab, densityMap, minMaxX, minMaxY );
		}
		tab.densities = null;
	}, 5);

	return deferred.promise();
}

function getWordCloudPromise( words, cfg, tab ){
	var defer = $.Deferred();
	setTimeout(function() {		
		var wc = wordCloud(cfg);
		wc.update(words);						
		defer.resolve(wc, tab);
	}, 5);
	return defer.promise();
}