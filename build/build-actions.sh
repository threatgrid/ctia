#!/bin/bash
# # GitHub Actions deployment
#
# Fails if run in non-deployment situations.
#
# Assumes awscli is set up with correct credentials.
#
# Requires secrets:
# - CYBRIC_API_KEY
# - DOCKERHUB_PASSWORD
# - DOCKERHUB_USERNAME
set -e

if [[ "${GITHUB_EVENT_NAME}" != "push" ]];
  echo "./build/build-actions.sh currently supports push deployments only."
  exit 1
fi

if [[ "${GITHUB_REPOSITORY}" != "threatgrid/ctia" ]];
  echo "./build/build-actions.sh currently deploys only via the threatgrid/ctia repository."
  exit 1
fi

# the current branch -- note: this doesn't work on PR's but we
# don't deploy on PR's.
# https://stackoverflow.com/a/58035262
CTIA_BRANCH="${GITHUB_REF#refs/heads/}"
# unique identifier for this build
# https://docs.github.com/en/actions/reference/environment-variables#default-environment-variables
CTIA_BUILD_NUMBER="${GITHUB_RUN_ID}-${GITHUB_RUN_NUMBER}"
CTIA_COMMIT="${GITHUB_SHA}"

echo "branch: ${CTIA_BRANCH}"
echo "build number: ${CTIA_BUILD_NUMBER}"
echo "commit: ${CTIA_COMMIT}"

function build-and-publish-package {
  PKG_TYPE=$1

  echo "Building new $PKG_TYPE package"
  ( set -x && lein uberjar )
  BUILD_NAME="${CTIA_MAJOR_VERSION}-${PKG_TYPE}-${CTIA_BUILD_NUMBER}-${CTIA_COMMIT:0:8}"
  echo "$BUILD_NAME"
  echo "Build: $BUILD_NAME"
  echo "Commit: ${CTIA_COMMIT}"
  echo "Version: $BUILD_NAME"

  # Upload the jar directly to the artifacts S3 bucket
  if [ "${PKG_TYPE}" == "int" ]; then
    ARTIFACTS_BUCKET="372070498991-us-east-1-int-saltstack"
  elif [ "${PKG_TYPE}" == "rel" ]; then
    ARTIFACTS_BUCKET="372070498991-us-east-1-test-saltstack"
  fi

  ARTIFACT_NAME="${CTIA_BUILD_NUMBER}-${CTIA_COMMIT:0:8}.jar"
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

if [[ "${GITHUB_EVENT_NAME}" == "push" ]]; then
  if [[ ${CTIA_BRANCH} == "master" ]]; then
    # non-pr builds on the master branch yield INT packages
    echo "OK: master branch detected"
    build-and-publish-package "int"
    exit 0

  elif [[ ${CTIA_BRANCH} =~ ^v[0-9]+(.[0-9]+)+$ ]]; then
    # non-pr builds on 'v?.?' branches yield REL packages
    echo "OK: v branch detected using regex"
    build-and-publish-package "rel"
    exit 0

  else
    echo "Not on master or release branch. Not building a package."
    exit 1
  fi
else
  echo "Not building package on event ${GITHUB_EVENT_NAME}"
  exit 1
fi
