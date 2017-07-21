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
	width = $("svg").width(),
	height = $("svg").height(),
	maxRadius = width * 0.02,
	minRadius = width * 0.005,
	padding = 6;

	var focusNode = null, highlightNode = null;
	var highlightColor = "#555";

	var totalnumberOfLinksToDisplay = graph.edges.length;
//	var heightScalingFactor = (totalnumberOfLinksToDisplay > 30) ? 5 : 35;
//	height = heightScalingFactor * totalnumberOfLinksToDisplay;

//	var maximumPossibleWidth = 10 * totalnumberOfLinksToDisplay;
//	if(maximumPossibleWidth > width) {
//	width = maximumPossibleWidth;
//	}
	var path, markerPath, node;
	var nodes = {};

	var color = d3.scaleOrdinal(d3.schemeCategory20);

	// Get max/min relevance
	var maxRev = d3.max(graph.nodes, function(n){
		return n.rank;
	});
	minRev = d3.min(graph.nodes, function(n){
		return n.rank;
	});

	var linkedByIndex = {};
	graph.edges.forEach(function(d) {
		linkedByIndex[d.source + "," + d.target] = true;
	});

	// Radius interpolator based on document relevance
	var radiusInterpolator = d3.scaleLinear()
	.domain([minRev, maxRev])
	.range([minRadius, maxRadius])
	.interpolate(d3.interpolateRound);

	svg.style("opacity", 0);

	svg.transition()
	.duration(2000)
	.style("opacity",1)
	.attr("width", width)
	.attr("height", height);

	var zoom = d3.zoom()
	.scaleExtent([0.1, 1])
	.on("zoom", zoomed);

	var forceLink = d3.forceLink()
	.id(function(d) { return d.id; })
	.distance(2 * totalnumberOfLinksToDisplay);

	var simulation = d3.forceSimulation()
	.force("link", forceLink)
	.force("charge", d3.forceManyBody().strength(-400))
	.force("x", d3.forceX(width/2).strength(0.08))
	.force("y", d3.forceY(height/2).strength(0.08));

	// Node tooltip
	var tip = d3.select("body").append("div")
	.attr("class", "node-tooltip")
	.style("opacity", 0);

	// Per-type markers, as they don't inherit styles.
	svg.append("svg:defs").selectAll("marker")
	.data(["end"])
	.enter().append("svg:marker")
	.attr("id", function(d) { return d; })
	.attr("viewBox", "0 -5 10 10")
	.attr("refX", -1.5)
	.attr("refY", -1.5)
	.attr("markerWidth", 6)
	.attr("markerHeight", 6)
	.attr("orient", "auto")
	.append("svg:path")
	.attr("d", "M0,-5L10,0L0,5");

	svg.call(zoom);

	path = svg.append("svg:g")
	.selectAll("path.link")
	.data(graph.edges)
	.enter().append("svg:path")
	.attr("class", "link")
	.attr("stroke-width", function(d) { return d.weight > 1 ? (d.weight * 2 ) : (d.weight + 0.5);});

	//This code is added to make arrow heads appearing in the middle of curve connecting two nodes
	markerPath = svg.append("svg:g")
	.selectAll("path.marker")
	.data(graph.edges)
	.enter().append("svg:path")
	.attr("class", "marker_only")
	.attr("marker-end", "url(#end)");

	//Define nodes
	node = svg.append("g")
	.selectAll(".node")
	.data(graph.nodes)
	.enter().append("circle")
	.attr("r", function(d){ return radiusInterpolator(d.rank);})
	.style("fill", function(d) { return color(d.rank*100); })
	.attr("class", "node");
	
	node
	.on("mouseover", function(d){
		var radius = radiusInterpolator(d.rank);

		d3.select(this).classed("node-active", true);
		d3.select(this)
		.transition()
		.duration(750)
		.attr("r", radius * 2);

		var html = getNodeTextAsHtml(d); 

		tip.transition()
		.duration(500)
		.style("opacity", 0);

		tip.transition()
		.duration(200)
		.style("opacity", 0.9)
		.style("display", "block");

		tip.html(html)
		.style("left", (d3.event.clientX + radius/2) + "px")
		.style("top", (d3.event.clientY + radius/2) + "px");
		d3.select(this).style("stroke-opacity", 1);

		focusNode = d;
		setFocus(d);
		setHighlight(d);
	})
	.on("mouseout", function(d){
		node.classed("node-active", false);
		d3.select(this)
		.transition()
		.duration(200)
		.attr("r", radiusInterpolator(d.rank));

		if (focusNode!==null)
		{
			focusNode = null;
			node.style("opacity", 1);
			path.style("opacity", 1);
			markerPath.style("opacity", 1);
		}

		exitHighlight();

		// User has moved off the visualization and onto the tool-tip
		tip.on("mouseover", function (t) {
			tip.on("mouseleave", function (t) {
				tip.transition().duration(500)
				.style("opacity", 0)
				.style("display", "none");

			});
		});
	})
	.call(d3.drag()
			.on("start", dragstarted)
			.on("drag", dragged)
			.on("end", dragended));

	simulation
	.nodes(graph.nodes)
	.on("tick", function(){
		ticked(node, path, markerPath);
	});

	simulation.force("link")
	.links(graph.edges);

	function exitHighlight()
	{
		highlightNode = null;
		if (focusNode === null)
		{
			svg.style("cursor","move");
			if (highlightColor!="white")
			{
				node.style("stroke", "white");
				path.style("stroke", function(o) {
					return (isNumber(o.rank) && o.rank >= 0) ? color(o.rank) : "#666";
				});
			}
		}
	}

	function setFocus(d)
	{	
		node.style("opacity", function(o) {
			return isConnected(d, o) ? 1 : 0.1;
		});

		path.style("opacity", function(o) {
			return o.source.id == d.id || o.target.id == d.id ? 1 : 0.1;
		});
		
		markerPath.style("opacity", function(o) {
			return o.source.id == d.id || o.target.id == d.id ? 1 : 0.1;
		});	
	}

	function setHighlight(d)
	{
		svg.style("cursor","pointer");
		if (focusNode !== null) d = focusNode;
		highlightNode = d;

		if (highlightColor!="white")
		{
			node.style("stroke", function(o) {
				return isConnected(d, o) ? highlightColor : "white";});

			path.style("stroke", function(o) {
				return o.source == d.id || o.target == d.id ? highlightColor : 
					((isNumber(o.rank) && o.rank>=0)?color(o.rank) : "#666");

			});
		}
	}

	function isConnected(a, b) {
		return linkedByIndex[a.id + "," + b.id] || linkedByIndex[b.id + "," + a.id] || a.id == b.id;
	}

	function getNodeTextAsHtml(d){
		var html = "";
		if ( typeof d.name !== "undefined" ){
			html = "<p><strong>" + d.name + "</strong></p>";
		}
		else if ( typeof d.title !== "undefined"){
			html = '<a href="https://dx.doi.org/' + d.doi + '" target="_blank"><p>';
			if (d.title)
				html += "<strong>" + d.title + "</strong>";
			if ( d.publicationDate )
				html +=  ", " + d.publicationDate;
			html += "</p></a>";
		}

		html += "<p>Rank: " + d.rank+ "</p>";

		return html;
	}

	function zoomed() {
		d3.selectAll("g").attr("transform", d3.event.transform);
	}

	function ticked(node, path, markerPath) {
		//To adjust links connecting two nodes
		path.attr("d", function(d){
			var dx = d.target.x - d.source.x,
			dy= d.target.y - d.source.y,
			dr = Math.sqrt(dx * dx + dy * dy);

			return "M" + 
			d.source.x + "," +
			d.source.y + "A" +
			dr + "," + dr + " 0 0,1 " +
			d.target.x + "," +
			d.target.y;
		});

		//To adjust arrow heads on connecting links which appear to be present
		//In the middle
		markerPath.attr("d", function(d) {
			var dx = d.target.x - d.source.x,
			dy = d.target.y - d.source.y,
			dr = Math.sqrt(dx * dx + dy * dy);
			// We know the center of the arc will be some distance perpendicular from the
			// link segment's midpoint. The midpoint is computed as:
			var endX = (d.target.x + d.source.x) / 2;
			var endY = (d.target.y + d.source.y) / 2;
			// Notice that the paths are the arcs generated by a circle whose
			// radius is the same as the distance between the nodes. This simplifies the
			// trig as we can simply apply the 30-60-90 triangle rule to find the difference
			// between the radius and the distance to the segment midpoint from the circle
			// center.
			var len = dr - ((dr/2) * Math.sqrt(3));

			// Remember that is we have a line's slope then the perpendicular slope is the
			// negative inverse.
			endX = endX + (dy * len/dr);
			endY = endY + (-dx * len/dr);

			return "M" + d.source.x + "," + d.source.y + "A" + dr + "," + dr + " 0 0,1 " + endX + "," + endY;
		});

		//How much each node should translate when graph is played with
//		node.attr("transform", function(d) {
//		return "translate(" + d.x + "," + d.y + ")"; 
//		});
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

	function isNumber(n) {
		return !isNaN(parseFloat(n)) && isFinite(n);
	}	
}