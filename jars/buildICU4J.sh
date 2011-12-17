#!/bin/bash -e

DIR=`dirname $0`/icu4j-4_8_1_1

cd $DIR
ant clean || true
ant collator transliterator

ant moduleJar

ls -l icu4j.jar
