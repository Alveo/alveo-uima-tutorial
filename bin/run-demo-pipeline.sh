#!/bin/sh

mvn -e exec:java -Dexec.mainClass=au.edu.alveo.uima.tutorial.PosTagDemo -Dexec.args="$*" -f${0%/*/*}/pom.xml
