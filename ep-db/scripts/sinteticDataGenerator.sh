#!/bin/sh

max_depth=$1
number_of_points=$2
x=$3
y=$4
output_file=$5

echo $max_depth
echo $number_of_points
echo $x
echo $y
echo $output_file

sbt "runMain ep.db.utils.SinteticDataGenerator ${max_depth} ${number_of_points} ${x} ${y} ${output_file}" 