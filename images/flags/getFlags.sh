#!/bin/bash -e

OLD_DIR=`pwd`
DIR=`dirname $0`
cd $DIR

curl commons.wikimedia.org/wiki/Sovereign-state_flags | 
    egrep -o "\"[^\"]+\"" | 
    egrep Flag_of | 
    egrep upload\\.wikimedia | 
    sed -E 's!^.*(upload.*\svg)/.*$!\1!' | 
    sed 's!thumb/!!' > flag_files.txt

for i in `cat flag_files.txt`
do
  curl --remote-name $i
done

cd $OLD_DIR
