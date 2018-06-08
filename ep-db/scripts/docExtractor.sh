#!/bin/sh


directory=$1
CONFIG_FILE=$2

echo $directory
echo ${CONFIG_FILE}

#-----------------------#
# CLASS PATH            #
#-----------------------#

#THE_CLASSPATH=
#for i in `ls ./lib/*.jar`
#do
#        THE_CLASSPATH=${THE_CLASSPATH}:${i}
#done


# COMPILE AND ASSEMBLY USING SBT
#sbt compile assembly

# RUN JAVA 
java -cp "dist/ep-db.jar" ep.db.extractor.DocumentParserService ${directory} ${CONFIG_FILE}