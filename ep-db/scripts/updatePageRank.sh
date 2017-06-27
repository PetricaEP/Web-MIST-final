#!/bin/sh

DIR=${PWD##*/}

echo $DIR
if [ "scripts" == "$DIR" ]; then
	cd "../"
fi

sbt "runMain ep.db.pagerank.RelevanceCalculator"