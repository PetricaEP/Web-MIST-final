CREATE OR REPLACE FUNCTION calpagerank_docs(alpha double precision) RETURNS void AS $$

	DECLARE 

		flag BOOLEAN := true;

		nodeNum Integer;

		delta double precision;

	BEGIN

		DROP TABLE IF EXISTS pagerank;

		DROP TABLE IF EXISTS edgeWithOuterDegree;

		SELECT COUNT(*) INTO nodeNum FROM (SELECT DISTINCT doc_id FROM citations

							  UNION

							  SELECT DISTINCT ref_id FROM citations) foo;

		CREATE TABLE pagerank AS SELECT * , CASE WHEN nodeNum <> 0 THEN 1.00/nodeNum ELSE 0 END AS pr FROM (SELECT DISTINCT doc_id FROM citations

							  UNION

							  SELECT DISTINCT ref_id FROM citations) foo;

		CREATE TABLE weight AS SELECT citations.doc_id, CASE WHEN count(citations.ref_id) <> 0 THEN 1.00/COUNT(citations.ref_id) ELSE 0 END AS wei FROM citations GROUP BY citations.doc_id;



		CREATE TABLE edgeWithOuterDegree AS SELECT citations.doc_id, citations.ref_id, weight.wei FROM citations JOIN weight ON citations.doc_id = weight.doc_id;

		DROP TABLE weight;



		WHILE flag LOOP

			flag := false;

			CREATE TABLE pagerank1 AS SELECT edgeWithOuterDegree.ref_id as doc_id, SUM(pagerank.pr*edgeWithOuterDegree.wei*alpha) AS pr

					FROM pagerank LEFT JOIN edgeWithOuterDegree ON pagerank.doc_id = edgeWithOuterDegree.doc_id GROUP BY edgeWithOuterDegree.ref_id;

			CREATE TABLE currentpagerank AS SELECT pagerank.doc_id, CASE WHEN nodeNum <> 0 THEN (1.0 - alpha)/nodeNum+COALESCE(pagerank1.pr,0) ELSE COALESCE(pagerank1.pr,0) END as pr FROM pagerank LEFT JOIN pagerank1 ON pagerank.doc_id = pagerank1.doc_id;

			DROP TABLE pagerank1;

			SELECT |/SUM((pagerank.pr - currentpagerank.pr)^2) INTO delta FROM pagerank JOIN currentpagerank ON pagerank.doc_id = currentpagerank.doc_id;

			IF delta < 0.0000001 THEN

				flag = true;

			END IF;

			DROP TABLE pagerank;

			ALTER TABLE currentpagerank RENAME TO pagerank;

		END LOOP;

		UPDATE documents_data SET relevance_doc = pr FROM pagerank WHERE documents_data.doc_id = pagerank.doc_id;
		
		DROP TABLE IF EXISTS edgeWithOuterDegree;
		
		DROP TABLE IF EXISTS pagerank;

	END;

	$$ LANGUAGE plpgsql;
	
	
	CREATE OR REPLACE FUNCTION calpagerank_authors(alpha double precision) RETURNS void AS $$

	DECLARE 

		flag BOOLEAN := true;

		nodeNum Integer;

		delta double precision;

	BEGIN

		DROP TABLE IF EXISTS pagerank;

		DROP TABLE IF EXISTS edgeWithOuterDegree;
		
		CREATE TABLE citations_authors AS SELECT a.aut_id src, ra.aut_id des FROM citations c 
			INNER JOIN document_authors a ON c.doc_id = a.doc_id 
			INNER JOIN document_authors ra ON c.ref_id = ra.doc_id;

		SELECT COUNT(*) INTO nodeNum FROM (SELECT DISTINCT src FROM citations_authors

							  UNION

							  SELECT DISTINCT des FROM citations_authors) foo;

		CREATE TABLE pagerank AS SELECT * , CASE WHEN nodeNum <> 0 THEN 1.00/nodeNum ELSE 0 END AS pr FROM (SELECT DISTINCT src FROM citations_authors

							  UNION

							  SELECT DISTINCT des FROM citations_authors) foo;

		CREATE TABLE weight AS SELECT citations_authors.src, CASE WHEN COUNT(citations_authors.des) <> 0 THEN 1.00/COUNT(citations_authors.des) ELSE 0 END AS wei FROM citations_authors GROUP BY citations_authors.src;

		CREATE TABLE edgeWithOuterDegree AS SELECT citations_authors.src, citations_authors.des, weight.wei FROM citations_authors JOIN weight ON citations_authors.src = weight.src;

		DROP TABLE weight;



		WHILE flag LOOP

			flag := false;

			CREATE TABLE pagerank1 AS SELECT edgeWithOuterDegree.des as src, SUM(pagerank.pr*edgeWithOuterDegree.wei*alpha) AS pr

					FROM pagerank LEFT JOIN edgeWithOuterDegree ON pagerank.src = edgeWithOuterDegree.src GROUP BY edgeWithOuterDegree.des;

			CREATE TABLE currentpagerank AS SELECT pagerank.src, CASE WHEN nodeNum <> 0 THEN (1.0 - alpha)/nodeNum+COALESCE(pagerank1.pr,0) ELSE COALESCE(pagerank1.pr,0) END AS pr FROM pagerank LEFT JOIN pagerank1 ON pagerank.src = pagerank1.src;

			DROP TABLE pagerank1;

			SELECT |/SUM((pagerank.pr - currentpagerank.pr)^2) INTO delta FROM pagerank JOIN currentpagerank ON pagerank.src = currentpagerank.src;

			IF delta < 0.0000001 THEN

				flag = true;

			END IF;

			DROP TABLE pagerank;

			ALTER TABLE currentpagerank RENAME TO pagerank;

		END LOOP;

		UPDATE authors SET relevance = pr FROM pagerank WHERE authors.aut_id = pagerank.src;
		
		DROP TABLE IF EXISTS citations_authors;
		 
		DROP TABLE IF EXISTS edgeWithOuterDegree;
		
		DROP TABLE IF EXISTS pagerank;

	END;

	$$ LANGUAGE plpgsql;
	
	CREATE OR REPLACE FUNCTION update_documents_rank(docRelevance double precision, authorRelevance double precision) RETURNS void AS $$
		
	BEGIN
		
		 UPDATE documents_data dd set rank = (docRelevance * dd.relevance_doc + authorRelevance * coalesce(a.relevance,0)), relevance_aut = coalesce(a.relevance,0)  
		 FROM (SELECT doc_id, sum(a.relevance) relevance FROM document_authors da INNER JOIN authors a ON da.aut_id = a.aut_id GROUP BY da.doc_id) a 
		 WHERE dd.doc_id = a.doc_id;

	END;

	$$ LANGUAGE plpgsql;