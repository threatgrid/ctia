#!/usr/bin/env zsh

src="${0:h}"
dst="$src/../img"
for fic in $src/*.dot; do
    dest="$dst/${${fic:t}:r}.png"
    dot $fic -o$dest -Tpng
    print -- "Generated ${dest:t}"
done
