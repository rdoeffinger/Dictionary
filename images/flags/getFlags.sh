#!/bin/bash -e

OLD_DIR=`pwd`
DIR=`dirname $0`
cd $DIR

curl https://commons.wikimedia.org/wiki/Sovereign-state_flags |
    egrep -o 'src="[^"]+/Flag_of_[^/"]+\.svg' |
    sed -e 's#src="##' -e 's#thumb/##' > flag_files.txt

while read i ; do
  curl --remote-name "$i"
done < flag_files.txt

cd $OLD_DIR
