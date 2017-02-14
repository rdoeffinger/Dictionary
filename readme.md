#QuickDic

QuickDic offline Dictionary App for Android. Provided downloadable dictionaries are based on Wiktionaries, but can also be created from other sources (see DictionaryPC)

<a href="https://f-droid.org/repository/browse/?fdid=de.reimardoeffinger.quickdic" target="_blank">
  <img src="https://f-droid.org/badge/get-it-on.png" height="60"/>
</a>
<a href="https://play.google.com/store/apps/details?id=de.reimardoeffinger.quickdic" target="_blank">
  <img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" height="60"/>
</a>

##Build

Make sure you also checked out the Util repository,
and if you want to generate dictionaries also the DictionaryPC
repository.
They need to be at the same level as this one, not inside it.

To compile the APK, just open this repository in Android Studio.
You can also use gradle directly.

And approximation of the steps for generating dictionaries:
- go into the DictionaryPC repository
- run data/downloadInputs.sh to get the data
- run ./compile.sh (probably needs quite a few things to be installed or paths in it configured)
- run ./WiktionarySplitter.sh
- run ./generate_dictionaries.sh.

  You might want to edit the settings at the start of the file
  so it does not generate all dictionaries, and edit its input files
  like EN-trans-dictlist.txt to add new dictionaries.
  For a more manual approach, the commands it runs are something like
 ./run.sh --lang1=ES --lang2=PT --lang1Stoplist=data/inputs/stoplists/es.txt \
 --dictOut=data/outputs/ES-PT.quickdic \
 --dictInfo="(EN)Wiktionary-based ES-PT dictionary. --input1=data/inputs/wikiSplit/en/EN.data" \
 --input1Name=enwikitionary --input1Format=EnTranslationToTranslation \
 --input1LangPattern1=es --input1LangPattern2=pt
