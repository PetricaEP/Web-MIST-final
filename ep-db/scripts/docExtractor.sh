#!/bin/sh

directory=$1

echo $directory

sbt "runMain ep.db.extractor.DocumentParserService ${directory}"