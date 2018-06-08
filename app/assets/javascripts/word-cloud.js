function wordCloud(cfg) {
	var fill;
	
	if (cfg.colors === undefined)
		fill = d3.schemeCategory10;
	else
		fill = function(i) {
		return i < cfg.colors.length ? cfg.colors[i] : '#999999';
	};

	var wordScale = d3.scaleLinear().range([cfg.minWordSize, cfg.maxWordSize]);
	// Construct the word cloud's SVG element
	d3.select(cfg.selector).selectAll("svg").remove();
	var svg = d3.select(cfg.selector).append("svg")
	.attr("width", cfg.width)
	.attr("height", cfg.height)
	.append("g")
	.attr("transform", "translate(" + cfg.width / 2 + "," + cfg.height / 2 + ")");

	d3.select(cfg.selector)
	.style('left', '-100%');

	// Draw the word cloud
	function draw(words) {
		svg.selectAll("g text").remove();

		var cloud = svg.selectAll("g text")
		.data(words, function(d) { return d.text; });

		cloud.enter().append("text")
		.style("font-family", "Impact")
		.style("font-size", function(d) { return d.size + "px"; })
		.style("fill", function(d, i) { return fill(i); })
		.attr("text-anchor", "middle")
		.text(function(d) { return d.text; })
		.on('mouseover', function(d, i) {
			d3.select(this).attr('fill-opacity', 1);
			d3.select(this).attr('stroke', fill(i));
		})
		.on('mouseout', function(d) {
			d3.select(this).attr('fill-opacity', 0.9);
			d3.select(this).attr('stroke', 'none');
		})
		.style('cursor', 'pointer')
		.transition()
		.duration(500)
		.style("font-size", function(d) { return d.size + "px"; })
		.attr("transform", function(d) {
			return "translate(" + [d.x, d.y] + ")rotate(" + d.rotate + ")";
		}).style("fill-opacity", 0.9);

//		cloud.exit()
//		.transition()
//		.duration(200)
//		.style('fill-opacity', 1e-6)
//		.attr('font-size', 1)
//		.remove();
	}
	return {
		update: function(words) {
			wordScale.domain([
				d3.min(words, function(d) { return d.size; }),
				d3.max(words, function(d) { return d.size; })
				]);			

			var wc = d3.layout.cloud().size([cfg.width, cfg.height])
			.words(words.filter(function(d){
				return d.size > cfg.minWordNumber && (cfg.maxWordNumber < 0 || d.size <= cfg.maxWordNumber );
			}))
			.padding(5)
			.rotate(function() { return 0; }) //~~(Math.random() * 2) * 90; })
			.font("Impact")
			.fontSize(function(d) { return wordScale(d.size); })
			.spiral("archimedean")
			.timeInterval(500)
			.on("end", draw)			
			.start();

		}

	};
}