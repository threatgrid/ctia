#!/bin/bash
set -e

echo "branch: ${TRAVIS_BRANCH}"
echo "build number: ${TRAVIS_BUILD_NUMBER}"
echo "pull request: ${TRAVIS_PULL_REQUEST}"
echo "commit: ${TRAVIS_COMMIT}"
echo "tag: ${TRAVIS_TAG}"

function build-and-publish-package {
  PKG_TYPE=$1

  echo "Building new $PKG_TYPE package"
  ( set -x && lein uberjar )
  BUILD_NAME="${CTIA_MAJOR_VERSION}-${PKG_TYPE}-${TRAVIS_BUILD_NUMBER}-${TRAVIS_COMMIT:0:8}"
  echo "$BUILD_NAME"
  echo "Build: $BUILD_NAME"
  echo "Commit: ${TRAVIS_COMMIT}"
  echo "Version: $BUILD_NAME"

  # Upload the jar directly to the artifacts S3 bucket
  if [ "${PKG_TYPE}" == "int" ]; then
    ARTIFACTS_BUCKET="372070498991-us-east-1-int-saltstack"
  elif [ "${PKG_TYPE}" == "rel" ]; then
    ARTIFACTS_BUCKET="372070498991-us-east-1-test-saltstack"
  fi

  ARTIFACT_NAME="${TRAVIS_BUILD_NUMBER}-${TRAVIS_COMMIT:0:8}.jar"
  ( set -x && pip install --upgrade --user awscli )
  export PATH=$PATH:$HOME/.local/bin
  ( set -x && aws s3 cp ./target/ctia.jar s3://${ARTIFACTS_BUCKET}/artifacts/ctia/"${ARTIFACT_NAME}" --sse aws:kms --sse-kms-key-id alias/kms-s3 )

  # Run Vulnerability Scan in the artifact using ZeroNorth - INT only
  # WARNING: don't `set -x` here, exposes credentials
  if [ "${PKG_TYPE}" == "int" ]; then
    echo "$DOCKERHUB_PASSWORD" | docker login -u "$DOCKERHUB_USERNAME" --password-stdin
    sudo docker pull zeronorth/owasp-5-job-runner
    sudo docker run -v "${PWD}"/target/ctia.jar:/code/ctia.jar -e CYBRIC_API_KEY="${CYBRIC_API_KEY}" -e POLICY_ID=IUkmdVdkSjms9CjeWK-Peg -e WORKSPACE="${PWD}"/target -v /var/run/docker.sock:/var/run/docker.sock --name zeronorth zeronorth/integration:latest python cybric.py
    echo "Waiting the ZeroNorth Vulnerability Scanner to finish..."
    while [[ -n $(docker ps -a --format "{{.ID}}" -f status=running -f ancestor=zeronorth/owasp-5-job-runner) ]]; do sleep 5; done
  fi
}

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
