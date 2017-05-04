#!/bin/bash
set -ev
lein do clean, javac, test :all
