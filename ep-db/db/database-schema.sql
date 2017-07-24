DROP TRIGGER IF EXISTS tsvector_doc_update ON documents;
DROP TRIGGER IF EXISTS tsvector_doc_update_freq ON documents;

DROP TABLE IF EXISTS citations;
DROP TABLE IF EXISTS documents_data;
DROP TABLE IF EXISTS document_authors;
DROP TABLE IF EXISTS documents;
DROP TABLE IF EXISTS authors;
DROP TABLE IF EXISTS nodes;

CREATE TABLE documents (
	doc_id				bigserial PRIMARY KEY,
	doi					varchar(100) UNIQUE,
	title				text,
	keywords 			text,
	abstract 			text,
	publication_date	int,
	volume				varchar(100),
	pages				varchar(100),
	issue				varchar(100),
	container			varchar(255),
	container_issn		varchar(100),
	language			regconfig,
	tsv					tsvector,
	freqs				jsonb
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
	relevance			real
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
	BEGIN
  		new.tsv :=
	     setweight(to_tsvector(new.language, coalesce(new.title,'')), 'D') ||
	     setweight(to_tsvector(new.language, coalesce(new.keywords,'')), 'B') ||
	     setweight(to_tsvector(new.language, coalesce(new.abstract,'')), 'C');
  	return new;
	END;
$documents_trigger$ LANGUAGE plpgsql;

CREATE TRIGGER tsvector_doc_update BEFORE INSERT OR UPDATE
    ON documents FOR EACH ROW EXECUTE PROCEDURE documents_trigger();
    
 CREATE OR REPLACE FUNCTION documents_freqs() RETURNS TRIGGER AS $documents_freqs_trigger$
 	DECLARE
 		json_str	jsonb;
 		sumFreqs int;
 	BEGIN 
	 	
	 	IF new.tsv IS NOT NULL THEN
		 	BEGIN
		   		SELECT array_to_json(array_agg(row)) INTO json_str FROM 
		   		ts_stat( format('SELECT %s::tsvector', quote_literal(new.tsv) ) ) row;
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
		INSERT INTO documents_data(doc_id,x,y,relevance) VALUES (new.doc_id,0.0,0.0,0.0);
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