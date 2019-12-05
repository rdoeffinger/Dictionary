#!/bin/bash -e

OLD_DIR=`pwd`
DIR=`dirname $0`
cd $DIR

curl https://commons.wikimedia.org/wiki/Sovereign-state_flags | 
    egrep -o "\"[^\"]+\"" |
    egrep Flag_of |
    egrep upload\\.wikimedia |
    sed 's!thumb/!!' |
    sed 's!.svg/.*!.svg!' |
    egrep -v '\\' |
    sed s/\"// |
    uniq > flag_files.txt

for i in `cat flag_files.txt`
do
  curl --remote-name $i
done

cd $OLD_DIR
