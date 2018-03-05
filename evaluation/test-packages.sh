#! /bin/sh

if ! [ -x "$(command -v npm)" ]; then
  echo 'Error: npm is not installed.' >&2
  exit 1
fi

IFS=' '
CLOSURE="../target/closure-compiler-1.0-SNAPSHOT.jar"
PACKAGES="lodash async moment request underscore body-parser glob q yargs webpack uuid axios core-js js-yaml mocha yosay superagent css-loader co vue dotenv xml2js joi path jsonwebtoken marked"
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

echo "[X] Cleaning packages"
find node_modules -type f -name "*min.js" -delete
find ./node_modules/* -name "node_modules" -type d -exec rm -rf {} +

LINES=0
for pkg in $PACKAGES; do
    LINES=$(($LINES + `( find ./node_modules/$pkg -name '*.js' -print0 | xargs -0 cat ) 2>/dev/null | wc -l`))
done

echo "[X] Start package test"
START_TIME=$SECONDS
for pkg in $PACKAGES; do
    PKG_TIME=$(python -c 'from time import time; print int(round(time() * 1000))')
    java -jar $CLOSURE --js ./node_modules/$pkg 1> /dev/null 2> ./logs/$pkg.log
    NUMLOOPS=$(cat ./logs/$pkg.log | grep "nested loop" | wc -l | sed -e 's/^[ \t]*//')
    VULNS=$(cat ./logs/$pkg.log | grep -A 3 loop)
    TOTAL=$(($TOTAL+$NUMLOOPS))
    echo "[$pkg] \t$NUMLOOPS potential input-dependend nested loop vulns detected"
    PKG_ELAPSED=$(($(python -c 'from time import time; print int(round(time() * 1000))') - $PKG_TIME))
    echo "\t\t took $PKG_ELAPSED ms"
    LINES=`( find ./node_modules/$pkg -name '*.js' -print0 | xargs -0 cat ) 2>/dev/null | wc -l`
    echo "\t\t $LINES lines of code"
done
ELAPSED_TIME=$(($SECONDS - $START_TIME))

echo "[X] $TOTAL total potential vulns. Yeah"
echo "[X] The analysis took $ELAPSED_TIME seconds"
echo "[X] I analyzed $LINES lines of code"
