#!/bin/bash -e

DIR=`dirname $0`/icu4j-4_2_1-src

cd $DIR
ant clean
ant collator
ant collIndices

rm  classes/com/ibm/icu/impl/data/icudt42b/ubidi.icu
rm  classes/com/ibm/icu/impl/data/icudt42b/cnvalias.icu
rm -rf classes/com/ibm/icu/impl/data/icudt42b/brkitr

rm -rf ./safe
mkdir ./safe
# Need this res_index.txt file because the process to auto-generate it from the context of the jar doesn't work on Android.
mv classes/com/ibm/icu/impl/data/icudt42b/coll/res_index.txt ./safe
mv classes/com/ibm/icu/impl/data/icudt42b/coll/en* ./safe
mv classes/com/ibm/icu/impl/data/icudt42b/coll/de* ./safe
mv classes/com/ibm/icu/impl/data/icudt42b/coll/ucadata.icu ./safe
rm classes/com/ibm/icu/impl/data/icudt42b/coll/*
mv ./safe/* classes/com/ibm/icu/impl/data/icudt42b/coll/
rm -rf ./safe

ant moduleJar

ls -l $DIR/icu4j.jar
