#!/bin/sh

max_depth=$1
number_of_points=$2
output_file=$3

echo $max_depth
echo $number_of_points
echo $output_file

sbt "runMain ep.db.utils.SinteticDataGenerator ${max_depth} ${number_of_points} ${output_file} 