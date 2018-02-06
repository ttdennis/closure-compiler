#! /bin/sh

if ! [ -x "$(command -v npm)" ]; then
  echo 'Error: npm is not installed.' >&2
  exit 1
fi

IFS=' '
CLOSURE="../target/closure-compiler-1.0-SNAPSHOT.jar"
PACKAGES="lodash async moment request underscore body-parser glob jquery q yargs webpack"
TOTAL=0

# check if logs already exist
[ ! -d "./logs" ] && mkdir logs

# do we clean up?
if [ "$#" = "1" ] && [ "$1" = "clean" ] 
    then 
        rm -rf ./logs ./package.json ./package-lock.json ./node_modules
        exit
fi

# we need a package json for npm
echo "[X] Creating package.json dummy file"
echo "{}" > package.json

echo "[X] Installing npm packages"
npm install $PACKAGES 2>/dev/null 1>&2

echo "[X] Start package test"
for pkg in $PACKAGES; do
    java -jar $CLOSURE --js ./node_modules/$pkg 1> /dev/null 2> ./logs/$pkg.log
    NUMLOOPS=$(cat ./logs/$pkg.log | grep loop | wc -l | sed -e 's/^[ \t]*//')
    TOTAL=$(($TOTAL+$NUMLOOPS))
    echo "[$pkg] \t$NUMLOOPS potential input-dependend nested loop vulns detected"
done

echo "[X] $TOTAL total potential vulns. Yeah"
