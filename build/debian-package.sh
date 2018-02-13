#!/bin/bash
set -ev

echo "branch: ${TRAVIS_BRANCH}"
echo "build number: ${TRAVIS_BUILD_NUMBER}"
echo "pull request: ${TRAVIS_PULL_REQUEST}"
echo "commit: ${TRAVIS_COMMIT}"
echo "tag: ${TRAVIS_TAG}"

function build-and-publish-package {
  PKG_TYPE=$1

  echo "Building new $PKG_TYPE package"
  DEB_BUCKET=debian-packages.iroh.amp.cisco.com
  lein uberjar
  mkdir ./target/pkg
  cp -rf ./build/package/deb ./target/pkg/
  cp ./target/ctia.jar target/pkg/deb/srv/ctia/ctia.jar
  BUILD_NAME="${CTIA_MAJOR_VERSION}-${PKG_TYPE}-${TRAVIS_BUILD_NUMBER}-${TRAVIS_COMMIT:0:8}"
  echo $BUILD_NAME
  echo "Build: $BUILD_NAME" > ./target/pkg/deb/srv/ctia/BUILD
  echo "Commit: ${TRAVIS_COMMIT}" >> ./target/pkg/deb/srv/ctia/BUILD
  echo "Version: $BUILD_NAME" >> ./target/pkg/deb/DEBIAN/control
  cat ./target/pkg/deb/srv/ctia/BUILD
  cat ./target/pkg/deb/DEBIAN/control
  # Build the debian package
  dpkg-deb -Z gzip -b ./target/pkg/deb ./target/pkg/ctia-$BUILD_NAME.deb

  deb-s3 upload  --preserve-versions --access-key-id $DEB_ACCESS_KEY --secret-access-key $DEB_SECRET_KEY --bucket $DEB_BUCKET --arch amd64 --codename ctia --component $PKG_TYPE ./target/pkg/ctia-$BUILD_NAME.deb
}

if [ "${TRAVIS_PULL_REQUEST}" = "false" ]; then
  if [[ ${TRAVIS_BRANCH} == "master" ]]; then
    # non-pr builds on the master branch yield INT packages
    echo "OK: master branch detected"
    build-and-publish-package "int"

  elif [[ ${TRAVIS_BRANCH} == "release" ]]; then
    # non-pr builds on 'release' branch yield REL packages
    echo "OK: release branch detected using regex"
    build-and-publish-package "rel"

  elif [[ ${TRAVIS_BRANCH} =~ ^rel-[0-9]{4}(0[1-9]|1[0-2])(0[1-9]|[1-2][0-9]|3[0-1])$ ]]; then
    # non-pr builds on 'rel-yyyymmdd' branches yield REL packages
    # To be removed at a future date, depending on the success of the new method
    echo "OK: release branch detected using regex"
    build-and-publish-package "rel"

  elif [[ ${TRAVIS_BRANCH} =~ ^v[0-9]+(.[0-9]+)+$ ]]; then
    # non-pr builds on 'v?.?' branches yield REL packages
    # To be removed at a future date, once we're sure the process works.
    echo "OK: v branch detected using regex"
    build-and-publish-package "rel"

  else
    echo "Not on master or release branch. Not building a package."
  fi
else
  echo "Build is for a pull request.  Not building a package."
fi
