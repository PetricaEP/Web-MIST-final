#!/bin/sh

doc_file=$1
xy_file=$2

echo $doc_file
echo $xy_file

sbt "runMain ep.db.utils.VNA2CSV ${doc_file} ${xy_file}" 