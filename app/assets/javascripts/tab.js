/*
 * Funções para manipulação das abas.
 */

var tabCount, tabIndex, selectedTab, tabs;

/**
 * Inicializa variavies globais
 */
$(function(){
	tabs = [];
	tabCount = 0;
	tabIndex = 0;
	selectedTab = null;
});

/**
 * Adiciona nova aba, criando um novo
 * objeto Tab.
 * @param op tipo de operação (zoom ou search).
 * @returns nova aba (objeto Tab).
 */
function addNewTab(op){
	var zoomLevel, newTab, t;

	//Contador de abas
	++tabCount;
	++tabIndex;

	if ( op === 'search' ){
		zoomLevel = 0;
		t = $("#terms").val() + 
		" " + $("#author").val() +
		" " + $("#year-start").val() +
		" " + $("#year-end").val();
		
		newTab = addTab('main-'+tabIndex, Messages('js.tab.search.title', tabIndex, t), null, t);
		newTab.title = t;
	}else{ // op === 'zoom'
		zoomLevel = selectedTab.zoomLevel + 1;
		t = selectedTab.title;
		newTab = addTab('zoom-'+tabIndex, Messages('js.tab.zoom.title', zoomLevel, t), selectedTab.id, t);
		newTab.title = t;
	}

	// Zoom Level
	newTab.zoomLevel = zoomLevel;

	return newTab;
}

/**
 * Funcão auxiliar para criar nó DOM 
 * correspondente a nova aba.
 * @param id id do objeto DOM a ser criado.
 * @param title título da nova aba.
 * @returns nova aba (objecto Tab).
 */
function addTab(id, title, parentId, altTextTitle){
	// Criar um link para a nova aba no menu de abas
	$('#viz-tabs').append('<li role="presentation" class="">' + 
			'<a href="#' + id + '" aria-controls="' + id + '" role="tab"' +  
			'data-toggle="tab" class="tab-title" data-tab-index="' + tabIndex + '" id="tab-' + id + '" title="' + altTextTitle + '">' + 
			'<button class="close"><span aria-hidden="true" class="glyphicon glyphicon-remove"></span></button>' + title + 
	' </a></li>');
	$('.tab-content').append('<div role="tabpanel" class="tab-pane" id="' + id + '">' + newTabContent() + '</div>');

	// Adiciona tab ao vetor e marca a nova tab
	// como selecionada
	tabs[id] = new Tab(id, parentId);
	selectedTab = tabs[id];

	// Trata evento de click
	$('#tab-' + id).click(function (e) {
		e.preventDefault();
		$(this).tab('show');
	});

	// Se o click for no ícone para fechar a aba (X)
	$('#tab-'+id + ' .close').click(function(e){
		// Pega id da aba
		var tabId = $(this).parent().attr('aria-controls');
		// Remove conteúdo da aba
		$('#' +tabId).remove();
		// Seleciona aba anteiror ou posterior,
		// caso existir e muda visualização
		// para esta aba.
		var prevTab = $(this).parents('li').prev();
		if ( !prevTab.length )
			prevTab = $(this).parents('li').next();
		// Se não houver mais abas
		// desabilita botões e marca 
		// aba selecionada como null.
		if ( !prevTab.length ){
			selectedTab = null;
			$("#step-btn").prop('disabled', true);
			$("#reheat-btn").prop('disabled', true);
			$("#zoom-btn").prop('disabled', true);
			$("#show-list-btn").prop('disabled', true);
		}

		// Remove seletor da aba e respectivo
		// objeto Tab.
		$(this).parents('li').remove();
		deleteTab(tabId);
		--tabCount;

		// Se ainda existir outra aba
		// exibe a aba e marca a aba selecionada
		if ( prevTab.length ){
			$(prevTab).children('a').tab('show');
			selectedTabId = $(prevTab).children('a').attr('aria-controls');
			selectedTab = tabs[selectedTabId];
		}
	});

	// Eventos de troca de aba
	$('#tab-'+id).on('show.bs.tab', function(e){
		selectedTabId = $(e.target).attr('aria-controls');
		selectedTab = tabs[selectedTabId];
		$("#step-btn").prop('disabled', selectedTab.step === 3);
		$("#reheat-btn").prop('disabled', selectedTab.step !== 3);
		$(".visualization-wrapper")
		.toggleClass("zoom-cursor", $("#zoom-btn").hasClass("active"));

		var isZoomActive = $('#zoom-btn').hasClass('active');
		if ( isZoomActive ){
			var svg = d3.select("#" + selectedTabId + " svg.visualization");
			activeZoom(svg);			
		}
		
		var isWordCloudActive = $("#" + selectedTab.id + " .word-cloud").css('display') !== 'none';
		$('#show-word-cloud-btn').toggleClass('active', isWordCloudActive);
		
		if (selectedTab.simulation && selectedTab.step == 2){
			selectedTab.simulation.restart();
		}
	})
	.on('hide.bs.tab', function(e){
		var prevTabId = $(e.target).attr('aria-controls');
		d3.select("#" + prevTabId + " svg")
		.on('mousemove', null)
		.on('click.selection', null)
		.select('.selection')
		.attr("visibility", "hidden");
		
		if ( tabs[prevTabId].simulation ){
			tabs[prevTabId].simulation.stop();
		}

	});

	// Exibe nova aba adicionada
	$('#tab-'+ id).tab('show');

	return tabs[id];
}

/**
 * Delete um objeto aba e libera memoria.
 * @param tabId id do nó DOM da aba.
 * @returns void
 */
function deleteTab(tabId){
	tabs[tabId].deleteTab();
	delete tabs[tabId];
}

/**
 * Criar conteúdo inicial para a nova aba.
 * @param id id do nó DOM da nova aba
 * @returns conteúdo HTML da nova aba.
 */
function newTabContent(){
	return '<div class="minimap-wrapper"></div>' +
	'<div class="row visualization-content">' +
	'<div class="word-cloud col-sm-4"></div>' +
	'<div class="visualization-wrapper col-sm-12"><svg id="visualization" class="visualization"></svg></div></div>' +	
	'<div class="viz-controls-footer row"><div id="slider-range" class="slider-range col-sm-4"><input type="text" class="slider" value=""/></div></div>' +
	'<div class="documents-list_wrapper">' +
	'<div class="documents-table">' +
	'<div class="total-documents-info"></div>' +
	'<table class="table table-hover table-striped hidden">' +
	'<thead><tr><th class="doc-index"></th><th class="doc-title">' + Messages('js.table.doc.title') + '</th><th class="doc-authors">' + 
	Messages('js.table.doc.authors') + '</th>' +
	'<th class="doc-year">' + Messages('js.table.doc.year') + '</th><th class="doc-relevance">' + Messages('js.table.doc.rank') + '</th>' +	//
	'</tr></thead><tbody></tbody></table></div></div>';
}