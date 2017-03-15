# Obserable Verdict Tests

These are the tests for the obserable "ctia/type/value/verdict" route.

## Store agnostic tests

Tests files in this directory use the CTIA APIs to perform tests that
do not rely on a particular storage backends.  Therefore they are
written to test all stores (using the deftest-for-each-store macro).

## Store specific tests

There are some tests that we need to do that require us to insert
objects directly into the storage backend, bypassing the CTIA APIs.
For example, we need to test what happens when a verdict has expired.
Writing lots of tests that sleep are slow, cumbersome, and unreliable.
To simulate these scenarios, we insert objects directly into the
storage backend (with time values that the APIs would not allow).
Such test are organized into their own subdirectories (named after the
store abstraction).  These tests should be duplicated, as appropriate,
in each store subdir.
