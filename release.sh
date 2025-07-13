VER=$(grep versionName AndroidManifest.xml | sed -e 's/.*"\(.*\)".*/v\1/')
cp build/outputs/mapping/release/mapping.txt mapping-$VER.txt
cp release/Dictionary-release.apk Dictionary-$VER.apk
cp release/Dictionary-release.aab Dictionary-$VER.aab
gpg -a --detach-sign Dictionary-$VER.apk Dictionary-$VER.aab
git tag -u 06D4D9C7 "$VER"
