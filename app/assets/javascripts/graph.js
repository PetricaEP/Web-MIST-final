$(function(){
	var type = $("#graph-type").html();
	var url = null;
	url = jsRoutes.controllers.GraphController.getGraph(type);
	$.ajax({url: url.url, type: url.type,
		success: createGraph, error: errorFn, dataType: "json"
	});
});

//Submission error 
errorFn = function(err){
	$('#loading').removeClass('hidden');
	console.debug("Error:");
	console.debug(err);
};

function createGraph(graph){
	var svg = d3.select("svg"),
	width = +svg.attr("width"),
	height = +svg.attr("height"),
	maxRadius = width * 0.02,
	minRadius = width * 0.005,
	padding = 6;

	
	var color = d3.scaleOrdinal(d3.schemeCategory20);

	// Get max/min relevance
	var maxRev = graph.nodes[0].rank,
	minRev = graph.nodes[0].rank;
	
	for(var i = 1; i < graph.nodes.length; i++){
		if ( graph.nodes[i].rank > maxRev)
			maxRev = graph.nodes[i].rank;
		if ( graph.nodes[i].rank < minRev)
			minRev = graph.nodes[i].rank;
	}

	// Radius interpolator based on document relevance
	var radiusInterpolator = d3.scaleLinear()
	.domain([minRev, maxRev])
	.range([minRadius, maxRadius])
	.interpolate(d3.interpolateRound);

	//Collision force
	var forceCollide = d3.forceCollide()
	.radius(function(d) { 
		return radiusInterpolator(d.rank) + padding;
	})
	.strength(1)
	.iterations(1);
	
	var forceLink = d3.forceLink()
		.id(function(d) { return d.id; });
//		.strength(1);
	
	var simulation = d3.forceSimulation()
	.force("link", forceLink)
	.force("charge", d3.forceManyBody())
	.force("collide", forceCollide)
	.force("x", d3.forceX(width/2))
	.force("y", d3.forceY(height/2));

	// Per-type markers, as they don't inherit styles.
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
	
	var node = svg.append("g")
	.attr("class", "nodes")
	.selectAll("circle")
	.data(graph.nodes)
	.enter().append("circle")
	.attr("r", function(d){ return radiusInterpolator(d.rank);})
	.attr("fill", function(d) { return color(d.rank*100); })
	.call(d3.drag()
			.on("start", dragstarted)
			.on("drag", dragged)
			.on("end", dragended));
	
	var link = svg.append("g")
	.attr("class", "links")
	.selectAll("line")
	.data(graph.edges)
	.enter().append("line")
	.attr("stroke-width", function(d) { return d.weight;})
	.attr("marker-end", function(d) { return "url(#link)"; });

	node.append("title")
	.text(function(d) { return d.title; });

	simulation
	.nodes(graph.nodes)
	.on("tick", ticked);

	simulation.force("link")
	.links(graph.edges);

	function ticked() {
		link
		.attr("x1", function(d) { return d.source.x; })
		.attr("y1", function(d) { return d.source.y; })
		.attr("x2", function(d) { return d.target.x; })
		.attr("y2", function(d) { return d.target.y; });

		node
		.attr("cx", function(d) { return d.x; })
		.attr("cy", function(d) { return d.y; });
	}

	function dragstarted(d) {
		if (!d3.event.active) simulation.alphaTarget(0.3).restart();
		d.fx = d.x;
		d.fy = d.y;
	}

	function dragged(d) {
		d.fx = d3.event.x;
		d.fy = d3.event.y;
	}

	function dragended(d) {
		if (!d3.event.active) simulation.alphaTarget(0);
		d.fx = null;
	}
}