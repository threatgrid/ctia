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
  lein uberjar
  BUILD_NAME="${CTIA_MAJOR_VERSION}-${PKG_TYPE}-${TRAVIS_BUILD_NUMBER}-${TRAVIS_COMMIT:0:8}"
  echo $BUILD_NAME
  echo "Build: $BUILD_NAME" > ./target/pkg/deb/srv/ctia/BUILD
  echo "Commit: ${TRAVIS_COMMIT}" >> ./target/pkg/deb/srv/ctia/BUILD
  echo "Version: $BUILD_NAME" >> ./target/pkg/deb/DEBIAN/control

  # Upload the jar directly to the artifacts S3 bucket
  if [ "${PKG_TYPE}" == "int" ]; then
    ARTIFACTS_BUCKET="372070498991-us-east-1-int-saltstack"
  elif [ "${PKG_TYPE}" == "rel" ]; then
    ARTIFACTS_BUCKET="372070498991-us-east-1-test-saltstack"
  fi

  ARTIFACT_NAME="${TRAVIS_BUILD_NUMBER}-${TRAVIS_COMMIT:0:8}.jar"
  pip install --upgrade --user awscli
  export PATH=$PATH:$HOME/.local/bin
  export AWS_ACCESS_KEY_ID=$DEB_ACCESS_KEY
  export AWS_SECRET_ACCESS_KEY=$DEB_SECRET_KEY
  aws s3 cp ./target/ctia.jar s3://${ARTIFACTS_BUCKET}/artifacts/ctia/${ARTIFACT_NAME} --sse aws:kms --sse-kms-key-id alias/kms-s3
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
