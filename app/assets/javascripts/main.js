var maxNumberOfTabs = 10;

//Form submission handle
$(function() {
	
	$('[data-toggle="tooltip"]').tooltip({container: 'body'});
	$('[data-tooltip="tooltip"]').tooltip({container: 'body', trigger : 'hover'});
	$('.modal').modal({show: false});
	
	$( "#searchForm" ).submit(function( e ) {
		e.preventDefault();
		// Esconde busca avançada se habilitada
		$(".advanced-search").hide(400, function(){
			$("#advanced-search-btn span").removeClass("glyphicon-chevron-up");
			$("#advanced-search-btn span").addClass("glyphicon-chevron-down");
			$(".advanced-search").removeClass("enabled");
		});
		
		ajaxSubmitForm(-1);
	});

	$('#collision-force').slider({
		formatter: function(value) {
			return value;
		}
	});

	$('#manybody-force').slider({
		formatter: function(value) {
			return value;
		}
	});

	$("#viz-settings-btn").click(function(e){
		if ( $(".viz-settings").hasClass("enabled")){
			$(".viz-settings").hide(400, function(){
				$(".viz-settings").removeClass("enabled");
			});
		}
		else{
			$(".viz-settings").show(400, function(){
				$(".viz-settings").addClass("enabled");
			});
		}
	});

	$("#advanced-search-btn").click(function(e){
		if ( $(".advanced-search").hasClass("enabled")){
			$(".advanced-search").hide(400, function(){
				$("#advanced-search-btn span").removeClass("glyphicon-chevron-up");
				$("#advanced-search-btn span").addClass("glyphicon-chevron-down");
				$(".advanced-search").removeClass("enabled");
			});
		}
		else{
			$(".advanced-search").show(400, function(){
				$("#advanced-search-btn span").removeClass("glyphicon-chevron-down");
				$("#advanced-search-btn span").addClass("glyphicon-chevron-up");
				$(".advanced-search").addClass("enabled");
			});
		}
	});

	$(".advanced-search").hide();
	$(".viz-settings").hide();	
	
	$("#show-circles-btn").click(function(e){
		var closed = $('#show-circles-btn').hasClass('glyphicon-eye-close');
		if ( closed ){			
			d3.selectAll('circle').style('display', 'none');
		}
		else{
			d3.selectAll('circle').style('display', null);
		}
		$('#show-circles-btn').toggleClass('glyphicon-eye-close', !closed);
		$('#show-circles-btn').toggleClass('glyphicon-eye-open', closed);
	});
	
	$('#select-all-btn').click(function(e){
		deselectAll(selectedTab);
		selectAll(selectedTab);
	});
	
	$('#show-links-btn').click(function(e){
		var isActive = $('#show-links-btn').hasClass('active');
		if ( !isActive){
			showAllLinks(selectedTab);
		}
		else{
			hideAllLinks(selectedTab);
		}
	});
		
	$("#show-word-cloud-btn").click(function(e){
		var isActive = $("#show-word-cloud-btn").hasClass("active");		
		if ( !isActive ){			
			showWordCloud(selectedTab);			
		}
		else{
			hideWordCloud(selectedTab);							
		}		
	});
});

//Submission error 
errorFn = function(err){
	$('#loading').addClass('hidden');
	console.debug("Error:");
	console.debug(err);
};

//Ajax form submission
function ajaxSubmitForm(page){
	
	// Se o numero maximo de abas
	// foi atingindo mostra aviso
	// solicitando o fechamento de algumas
	// abas
	if ( tabCount >= maxNumberOfTabs ){
		showMaxTabsAlert();
		return;
	}
	
	var t = $("#terms").val(),
	op = $("#operator").val(),
	author = $("#author").val(),
	yearS = $("#year-start").val(),
	yearE = $("#year-end").val(),	
	maxDocs = $("#max-number-of-docs").val();

	$.ajaxPrefilter(function (options, originalOptions, jqXHR) {
		jqXHR.setRequestHeader('Csrf-Token', $("input[name='csrfToken']").val());
	});
	
	var r = jsRoutes.controllers.HomeController.search();
	$.ajax({
		url: r.url, 
		type: r.type, 
		data: {
			terms: t, 
			operator: op,
			author: author,
			yearStart:  yearS,
			yearEnd: yearE,			
			maxDocs: maxDocs,
			page: page,
			tabId: selectedTab !== null ? selectedTab.id : ''
		}, 
		success: createVisualization, error: errorFn, dataType: "json"});

	//Waiting...
	$('#viz-tabs').removeClass('hidden');
	$('#title-text').remove();
	$('#loading').removeClass('hidden');
}