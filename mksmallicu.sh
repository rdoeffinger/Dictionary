#!/bin/sh
DST="$2"
# Gradle is hopelessly buggy, so just create the
# directory ourselves...
mkdir -p $(dirname "$DST")
cp $1 "$DST"
zip -ws -d "$DST" com/ibm/icu/impl/data/icudt*b/*.res com/ibm/icu/impl/data/icudt*b/*.spp com/ibm/icu/impl/data/icudt*b/unames.icu
zip -d "$DST" com/ibm/icu/impl/data/icudt57b/brkitr/cjdict.dict com/ibm/icu/impl/data/icudt*b/curr/* com/ibm/icu/impl/data/icudt*b/lang/* com/ibm/icu/impl/data/icudt*b/rbnf/* com/ibm/icu/impl/data/icudt*b/region/* com/ibm/icu/impl/data/icudt*b/unit/* com/ibm/icu/impl/data/icudt*b/zone/*
zip -d "$DST" com/ibm/icu/impl/data/Holiday*.class
zip -d "$DST" com/ibm/icu/impl/duration/*
zip -d "$DST" com/ibm/icu/impl/ICULangDataTables*.class com/ibm/icu/impl/ICURegionDataTables*.class com/ibm/icu/impl/*Currency*.class com/ibm/icu/impl/TrieIter*.class com/ibm/icu/impl/*Format.class com/ibm/icu/impl/*TimeZone*.class com/ibm/icu/impl/*ZoneMeta*.class com/ibm/icu/impl/LocaleDisplay*.class com/ibm/icu/impl/RelativeDate*.class
zip -d "$DST" com/ibm/icu/text/CompactDecimalDataCache*.class com/ibm/icu/text/*Currency*.class com/ibm/icu/text/Date*.class com/ibm/icu/text/*Format*.class com/ibm/icu/text/*TimeZone*.class com/ibm/icu/text/NF*.class com/ibm/icu/text/RBNF*.class com/ibm/icu/text/*Substitution*.class
zip -d "$DST" com/ibm/icu/util/*Currency*.class com/ibm/icu/util/GlobalizationPreferences*.class com/ibm/icu/util/*Calendar*.class com/ibm/icu/util/EasterRule*.class com/ibm/icu/util/*DateRule*.class com/ibm/icu/util/*Holiday*.class com/ibm/icu/util/*Time*.class com/ibm/icu/util/Range.class com/ibm/icu/util/STZInfo*.class com/ibm/icu/util/Measure*.class
zip -d "$DST" license.html
zip -d "$DST" com/ibm/icu/impl/coll/* com/ibm/icu/impl/data/icudt*b/coll/* com/ibm/icu/text/*Collat* com/ibm/icu/text/StringSearch* com/ibm/icu/text/AlphabeticIndex* com/ibm/icu/impl/text/RbnfScanner*
