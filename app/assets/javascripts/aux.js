/**
 * Cria nós a partir dos documentos retornados
 * pela busca
 * @param documents documentos
 * @returns nós da visualização.
 */
function createNodes(documents){
	return $.map(documents, function(doc, index){
		if (doc.x !== undefined && doc.y !== undefined ){
			// Cria nova nó
			d = {
					id: doc.id,
					cluster: doc.cluster,
					r: doc.radius,
					x: selectedTab.x(doc.x),
					y: selectedTab.y(doc.y),
					data: doc
			};

			// Citações
			if ( doc.references ){
				var i;
				var references = doc.references;
				selectedTab.links = [];
				for(i = 0; i < references.length; i++){
					selectedTab.links.push({	
						source: doc.id,
						target: references[i]
					});
				}
			}

			// Atualiza centroide do cluster
			if (!selectedTab.clusters[ doc.cluster ] || (d.r > selectedTab.clusters[ doc.cluster ].r)) 
				selectedTab.clusters[ doc.cluster ] = d;
			return d;
		}
	});
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
				x: selectedTab.x(density[i][0]),
				y: selectedTab.y(density[i][1]),
				data: {x: density[i][0], y: density[i][1]}
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
	tip.transition()
	.duration(500)
	.style("opacity", 0);

	tip.transition()
	.duration(200)
	.style("opacity", 0.9)
	.style("display", "block");

	var d = n.data;
	var tipHtml = '<a href="https://dx.doi.org/' + d.doi + '" target="_blank"><p>';
	if (d.title)
		tipHtml += "<strong>" + d.title + "</strong>";
	if ( d.authors && d.authors.length > 0){
		tipHtml +=", " + d.authors[0].name;
		for(var i = 1; i < d.authors.length; i++){
			tipHtml += "; " + d.authors[i].name;
		}
	}
	if ( d.publicationDate )
		tipHtml +=  ", " + d.publicationDate;
	tipHtml += "</p></a>";
	tip.html(tipHtml)
	.style("left", (d3.event.clientX + n.r/2) + "px")
	.style("top", (d3.event.clientY + n.r/2) + "px");
	d3.select(this).style("stroke-opacity", 1);
}

/**
 * Esconde tooltip
 * @param d nó a qual a tooltip pertence.
 * @returns
 */
function hideTip(d){
	d3.select(this).style("stroke-opacity", 0);
	// Mouse foi movido para fora da visualização e da tooltip
	tip.on("mouseover", function (t) {
		tip.on("mouseleave", function (t) {
			tip.transition().duration(500)
			.style("opacity", 0)
			.style("display", "none");

		});
	});
}

/**
 * Ativa links (citações) quando nós são
 * selecionados.
 * @param d nó selecionado.
 * @param index indice do nó selecionado.
 */
function toggleLinks(d,index){
	if ( !selectedTab.links ) return;
	var i;
	var paths = d3.selectAll('#' + selectedTab.id + ' path.link').nodes();

	var edges = selectedTab.links;
	for(i = 0; i < edges.length; i++){
		if (edges[i].source == d){
			var l = d3.select(paths[edges[i].index]);
			l.classed('active', !l.classed('active'));
		}
	}

	// Marca linhas na lista de documentos como ativas
	var row = $("#" + selectedTab.id + " .documents-table table tbody tr")[index];
	$(row).toggleClass('success');
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
}

/**
 * Adiciona um documento à lista de documentos.
 * @param index indice do documento na lista.
 * @param node respectivo nó na visualização.
 * @returns void
 */
function addDocumentToTable(index, node){
	var doc = node.data;
	var row = '<tr><td class="doc-index">' + index + '</td><td class="doc-title">' + doc.title +
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
	row += '</td><td class="doc-doi">';
	if ( doc.doi )
		row += '<a href="https://dx.doi.org/' + d.doi + '" target="_blank">' + 
		doc.doi + '</a>';

	row += '</td><td class="doc-relevance">' + (doc.rank * 100 ).toFixed(3) + '</td><td class="doc-cluster">' + 
	'<svg><circle cx="15" cy="15" r="10" stroke-width="0" fill="' + selectedTab.clusterColor(doc.cluster) + '"/></svg>'  +
	'</td></tr>';
	$('#' + selectedTab.id + ' .documents-table .table tbody').append(row);
}


/** Reseta visualização.
 * Marca todos links como inativos e linhas
 * da lista de documentos como não selecionadas.
 * @param e evento click
 * @returns void
 */
function resetVisualization(e){
	d3.selectAll("#" + selectedTab.id + ' path.link').classed('active', false);
	$("#" + selectedTab.id + " .documents-table table tbody tr").removeClass('success');
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
		d3.select(this).on("mousemove", null);
		d3.select(this).on("wheel", null);
		endSelection(d3.mouse(this));
	})
	.on("wheel", function(){
		var delta = d3.event.deltaY,
		newWidth, newHeight;
		
		var selectionWidth = $(".selection").width(),
			svgWidth = svg.attr('width');
		
		if ( delta > 0 ){
			newWidth = Math.min(svgWidth, selectionWidth * 1.1);
			newHeight = newWidth * 6 / 16;
		}
		else{
			newWidth = Math.max(200, selectionWidth * 0.9);
			newHeight = newWidth * 6 / 16;
		}
		
		$(".selection").width(newWidth);
		$(".selection").height(newHeight);
		
		var start = d3.mouse(this);
		svg.select('.selection')
		.attr("d", rect(start[0], start[1], newWidth, newHeight));
	});

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
}

//Zoom: inicia seleção
function startSelection(start, svg) {
	var selectionWidth = $(".selection").width(),
	selectionHeight = $(".selection").height();

	svg.select('.selection')
	.attr("d", rect(start[0], start[1], selectionWidth, selectionHeight));
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
	'<span aria-hidden="true">&times;</span></button>' +
	'<strong>Warning!</strong> Too may tabs opened. Please, close some of the tabs before continue.</div>';	
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
		var canvas = d3.select('#' + tab.id).insert('canvas', ':first-child')
		.classed('minimap', true).node();
		canvas.width = $("#" + tab.id + " canvas").width();
		canvas.height = canvas.width * 6 / 16;
		var ctx = canvas.getContext('2d');
		ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
	};
}

function copyMiniMapFromParent(svg, tab, minX, minY, maxX, maxY){
	
	var canvas = d3.select('#' + tab.id).insert('canvas', ':first-child')
		.classed('minimap', true).node(),
	parentCanvas = $('#' + tab.parentId).children('canvas');
	
	canvas.width = $("#" + tab.id + " canvas").width();
	canvas.height = canvas.width * 6 / 16;
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