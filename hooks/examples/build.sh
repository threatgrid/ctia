#!/usr/bin/env zsh

dst=../../test/resources/hooks
builddir=build

if (($# == 0)); then
    lst=( *hook*(/) )
else
    lst=$@
fi

[[ ! -d $builddir ]] && mkdir $builddir

for d in $lst; do

    javac -source 1.7 -target 1.7 -classpath ../../hooks -d ./$builddir $d/*.java
    cd $builddir
    jar="${${(C)d}:gs/-//}.jar"
    jar cf $jar *
    cd ..
    print -- "generated: $builddir/$jar"
    [[ ! -d $dst ]] && mkdir -p $dst
    cp -f $builddir/$jar $dst/$jar
    print -- "copied in: $dst/$jar"
    rm -rf $builddir/*

done

rm -rf $builddir
