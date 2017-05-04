# CTIA Debian Package Build

Resources for automatically building debian packages for
CTIA in Travis-CI.

## Contents of Build Directory

This `build` directory contains a package template used
to construct a debian package in Travis-CI, as well as
scripts for assembling and publishing the debian package
to S3.

The scripts include `run-tests.sh`, which can be called
by `travis.yml` to run tests, and `debian-package.sh`,
which will assemble new packages on merges to the
`master` branch, as well as release branches (which
conform to the `rel-YYYYmmdd` naming pattern).  No
other branch builds or PR builds will result in the
creation of a new debian package.

### Debian Package Template
```
$ tree
.
└── package
    └── deb
        ├── DEBIAN
        │   └── control
        ├── etc
        │   └── init.d
        │       └── ctia
        └── srv
            └── ctia
                └── start.sh
```
