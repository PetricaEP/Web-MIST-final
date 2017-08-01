var maxNumberOfTabs = 5;

//Form submission handle
$(function() {

	$('[data-toggle="tooltip"]').tooltip();
	$('[data-tooltip="tooltip"]').tooltip();
	
	$( "#searchForm" ).submit(function( e ) {
		e.preventDefault();
		ajaxSubmitForm();
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
});

//Submission error 
errorFn = function(err){
	$('#loading').removeClass('hidden');
	console.debug("Error:");
	console.debug(err);
};

//Ajax form submission
function ajaxSubmitForm(){
	
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
	numClusters = $("#num-clusters").val(),
	width = $(".tab-content").width(),
	height = $(".tab-content").height(); 

	var r = jsRoutes.controllers.HomeController.search();
	$.ajax({url: r.url, type: r.type, data: {
		terms: t, 
		operator: op,
		author: author,
		yearStart:  yearS,
		yearEnd: yearE,
		numClusters: numClusters,
		width: width,
		height: height 
	}, 
	success: createVisualization, error: errorFn, dataType: "json"});

	//Waiting...
	$('#viz-tabs').removeClass('hidden');
	$('#title-text').remove();
	$('#loading').removeClass('hidden');
}
