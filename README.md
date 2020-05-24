# QuickDic
An offline dictionary for Android.

## Cloning
Make sure you use the `--recursive` option to clone, or otherwise
make sure the `Util` submodule is correctly checked out.

If you want to generate dictionaries, clone the DictionaryPC
repository as well. It needs to be at the same level as this one, not
inside it.

## Compiling
To compile the APK, just open this repository in Android Studio.
You can also use Gradle directly.
Note that compilation is only tested on Linux.
It is possible to disable the smallicu/makesmallicu step if it causes
issues, though help to debug any such issue would be welcome.

## Generating dictionaries
An approximation of the steps for generating dictionaries:
1. Go into the DictionaryPC repository
2. Run `data/downloadInputs.sh` to get the data
3. Run `./compile.sh` (probably needs quite a few things to be installed or paths in it configured)
   Alternatively, download a release binary: https://github.com/rdoeffinger/DictionaryPC/releases
4. Run `./WiktionarySplitter.sh`
5. Run `./generate_dictionaries.sh`
   You might want to edit the settings at the start of the file
   so it does not generate all dictionaries, and edit its input files
   like EN-trans-dictlist.txt to add new dictionaries.
   For a more manual approach, the commands it runs are something like
  `./run.sh --lang1=ES --lang2=PT --lang1Stoplist=data/inputs/stoplists/es.txt \
  --dictOut=data/outputs/ES-PT.quickdic \
  --dictInfo="(EN)Wiktionary-based ES-PT dictionary." --input1=data/inputs/wikiSplit/en/EN.data \
  --input1Name=enwikitionary --input1Format=EnTranslationToTranslation \
  --input1LangPattern1=es --input1LangPattern2=pt`
