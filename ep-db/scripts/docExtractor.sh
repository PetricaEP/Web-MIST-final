#!/bin/sh

directory=$1

sbt "runMain ep.db.extractor.DocumentParserService ${directory}"