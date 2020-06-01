#!/bin/bash

while lein test :only ctia.entity.relationship-test/test-relationship-routes
do
  :
done
