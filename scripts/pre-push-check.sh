#!/bin/bash

title(){
    echo
    echo "=============="
    echo $*
    echo "=============="
    echo
}

title "Cleaning"
lein clean

title "Bikeshed"
lein with-profile prepush bikeshed

title "Kibit"
lein with-profile prepush kibit

title "Test"
lein test
