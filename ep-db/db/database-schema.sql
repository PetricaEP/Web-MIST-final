DROP TRIGGER IF EXISTS tsvector_doc_update ON documents;
DROP TRIGGER IF EXISTS tsvector_doc_update_freq ON documents;

DROP TABLE IF EXISTS citations;
DROP TABLE IF EXISTS documents_data;
DROP TABLE IF EXISTS document_authors;
DROP TABLE IF EXISTS documents;
DROP TABLE IF EXISTS authors;
DROP TABLE IF EXISTS nodes;

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE documents (
	doc_id				bigserial PRIMARY KEY,
	doi					varchar(100) UNIQUE,
	title				text,
	keywords 			text,
	abstract 			text,
	authors				text,
	publication_date	int,
	volume				varchar(100),
	pages				varchar(100),
	issue				varchar(100),
	container			varchar(255),
	container_issn		varchar(100),
	language			regconfig default 'english'::regconfig,
	tsv					tsvector,
	freqs				jsonb,	
	words				json,
	path				varchar(300),
	enabled				boolean default true,
	bibtex				text
);

CREATE TABLE authors (
	aut_id		bigserial PRIMARY KEY,
	aut_name	varchar(500),
	relevance	real default 0,
	aut_name_tsv	tsvector,
	UNIQUE(aut_name)
);

CREATE TABLE document_authors(
	id			bigserial PRIMARY KEY,
	doc_id		bigint REFERENCES documents(doc_id) ON UPDATE CASCADE ON DELETE CASCADE,
	aut_id		bigint REFERENCES authors(aut_id) ON UPDATE CASCADE ON DELETE CASCADE,
	UNIQUE(doc_id,aut_id)
);

CREATE TABLE nodes (
	node_id		bigint PRIMARY KEY,
	isleaf		boolean,
	rankmax		real,
	rankmin		real,
	parent_id	bigint REFERENCES nodes(node_id) ON DELETE CASCADE ,
	depth		int,
	index		int
);

CREATE TABLE documents_data (
	doc_id				bigint PRIMARY KEY REFERENCES documents(doc_id) ON UPDATE CASCADE ON DELETE CASCADE,
	node_id				bigint REFERENCES nodes(node_id) ON UPDATE CASCADE ON DELETE SET NULL,
	x					real,
	y					real,
	relevance_doc		real,
	relevance_aut		real,
	rank				real
);

ALTER TABLE nodes ADD CONSTRAINT no_self_loops_nodes_chk CHECK (node_id <> parent_id);

CREATE TABLE citations (
	id			bigserial PRIMARY KEY,
	doc_id		bigint REFERENCES documents(doc_id) ON UPDATE CASCADE ON DELETE CASCADE,
	ref_id		bigint REFERENCES documents(doc_id) ON UPDATE CASCADE ON DELETE CASCADE,
	UNIQUE( doc_id, ref_id)
);


CREATE INDEX source_idx ON citations(doc_id);
CREATE INDEX target_idx ON citations(ref_id);

ALTER TABLE citations ADD CONSTRAINT no_self_loops_chk CHECK (doc_id <> ref_id);

CREATE OR REPLACE FUNCTION authors_trigger() RETURNS TRIGGER AS $authors_trigger$
	BEGIN
  		new.aut_name_tsv := to_tsvector(coalesce(new.aut_name,''));
  	return new;
	END;
$authors_trigger$ LANGUAGE plpgsql;

CREATE TRIGGER tsvector_aut_update BEFORE INSERT OR UPDATE
    ON authors FOR EACH ROW EXECUTE PROCEDURE authors_trigger();

CREATE OR REPLACE FUNCTION documents_trigger() RETURNS TRIGGER AS $documents_trigger$
	DECLARE
		words tsvector;
		json_str json;
	BEGIN
  		new.tsv :=
	     setweight(to_tsvector(new.language, coalesce(new.title,'')), 'A') ||
	     setweight(to_tsvector(new.language, coalesce(new.keywords,'')), 'B') ||
	     setweight(to_tsvector(new.language, coalesce(new.abstract,'')), 'C');
	    
	    words := 
	      to_tsvector('simple_english', coalesce(new.title,'')) || 
	    	  to_tsvector('simple_english', coalesce(new.keywords,'')) ||
	    	  to_tsvector('simple_english', coalesce(new.abstract,''));
	    	  
	    	IF words != '' THEN
	    		BEGIN
		    		SELECT to_json(array_agg(row)) INTO json_str FROM (
		    			SELECT word,nentry FROM 
		    			ts_stat( format('SELECT %s::tsvector', quote_literal(words) ) ) ORDER BY nentry DESC) row;
		    	EXCEPTION
		    	WHEN NO_DATA_FOUND THEN
		    		json_str := NULL;		    		
	    		END;
	    	END IF;
	    	  
		new.words = json_str;
	
  		return new;
	END;
$documents_trigger$ LANGUAGE plpgsql;

CREATE TRIGGER tsvector_doc_update BEFORE INSERT OR UPDATE
    ON documents FOR EACH ROW EXECUTE PROCEDURE documents_trigger();
    
 CREATE OR REPLACE FUNCTION documents_freqs() RETURNS TRIGGER AS $documents_freqs_trigger$
 	DECLARE
 		json_str	 jsonb;
 	BEGIN 
	 	
	 	IF new.tsv IS NOT NULL THEN
		 	BEGIN
		   		SELECT to_jsonb(array_agg(row)) INTO json_str FROM ( 
		   		SELECT word, nentry FROM  
		   		ts_stat( format('SELECT %s::tsvector', quote_literal(new.tsv) ) ) ORDER BY nentry DESC) row;
		    EXCEPTION
		    	WHEN NO_DATA_FOUND THEN
		    		json_str := NULL;
		    END;
		END IF;
	    
	    new.freqs := json_str;
	    
	  	return new;
	END;
$documents_freqs_trigger$ LANGUAGE plpgsql;

CREATE TRIGGER tsvector_doc_update_freq BEFORE INSERT OR UPDATE
    ON documents FOR EACH ROW EXECUTE PROCEDURE documents_freqs();
    
CREATE OR REPLACE FUNCTION documents_data() RETURNS TRIGGER AS $documents_data_trigger$
	BEGIN
		INSERT INTO documents_data(doc_id,x,y,relevance_doc, relevance_aut,rank) VALUES (new.doc_id,0.0,0.0,0.0,0.0,0.0);
		return new;
	END;
$documents_data_trigger$ LANGUAGE plpgsql;

CREATE TRIGGER document_data_insert AFTER INSERT ON documents
	FOR EACH ROW EXECUTE PROCEDURE documents_data();

CREATE OR REPLACE FUNCTION array_to_tsvector2(arr tsvector[]) RETURNS tsvector AS $array_to_tsvector2$
	DECLARE
		tsv tsvector := '';
		e tsvector;
	BEGIN
		FOREACH e IN ARRAY arr
		LOOP
			tsv := tsv || e;
		END LOOP;
		
		RETURN tsv;
	END;
$array_to_tsvector2$ LANGUAGE plpgsql;


CREATE or REPLACE FUNCTION jsonb_sub_array(jsonb_array jsonb, from_pos int, to_pos int) 
RETURNS jsonb AS $jsonb_sub_array$
	DECLARE
		sub_array jsonb;
	BEGIN
		SELECT jsonb_agg(value) INTO sub_array 
    		FROM jsonb_array_elements(jsonb_array) WITH ordinality
	    WHERE ordinality-1 BETWEEN from_pos AND to_pos-1;
	    RETURN sub_array;
    END;
$jsonb_sub_array$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION insert_authors(doc_id bigint, authors text[]) RETURNS void AS $insert_authors$
	DECLARE
		aut text;
		aut_id bigint := -1;
		stack text;
 	BEGIN	 	
	 	FOREACH aut IN ARRAY authors
	 	LOOP
	 		BEGIN
	 			INSERT INTO authors(aut_name) VALUES(aut) ON CONFLICT (aut_name) DO NOTHING RETURNING authors.aut_id INTO aut_id;
	 			INSERT INTO document_authors(doc_id, aut_id) VALUES (doc_id, aut_id);	 			 		
	 		END;
	 			
	 	END LOOP;
	END;
$insert_authors$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION insert_authors_from_documents() RETURNS void  AS $insert_authors_from_documents$
 	DECLARE
 		doc RECORD; 		
 	BEGIN 
	 	FOR doc IN SELECT * FROM documents LOOP
	 		IF doc.authors IS NOT NULL THEN
	 			BEGIN
	 			PERFORM insert_authors(doc.doc_id, regexp_split_to_array(doc.authors, ';'));
	 			END;
	 		END IF;
	 	END LOOP;
	END;
$insert_authors_from_documents$ LANGUAGE plpgsql;

-- Index

CREATE INDEX documents_data_node_id_index ON documents_data (node_id);
CREATE INDEX documents_data_relevance_index ON documents_data (rank);
CREATE INDEX document_authors_doc_id_index ON document_authors(doc_id);
CREATE INDEX document_authors_aut_id_index ON document_authors(aut_id);
CREATE INDEX documents_enabled_index ON documents(enabled);
CREATE INDEX documents_tsv_index ON documents USING gin(tsv);
CREATE INDEX authors_tsv_index ON authors USING gin(aut_name_tsv);
CREATE INDEX authors_on_name_trigram_index ON authors USING gin (aut_name gin_trgm_ops);


-- Dictionaries and Configurations
CREATE TEXT SEARCH DICTIONARY simple_english
   (TEMPLATE = pg_catalog.simple, STOPWORDS = english);

CREATE TEXT SEARCH CONFIGURATION simple_english
   (copy = english);
ALTER TEXT SEARCH CONFIGURATION simple_english
   ALTER MAPPING FOR asciihword, asciiword, hword, hword_asciipart, hword_part, word
   WITH simple_english;
