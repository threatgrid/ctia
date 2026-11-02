#!/bin/bash
# # GitHub Actions deployment
#
# Fails if run in non-deployment situations.
#
# Assumes awscli is set up with correct credentials for the current user.
#
# Deployment mode (first argument, default `build`):
#   build      Build the uberjar + docker image and push it to the nonprod
#              (INT account 372070498991) ECR repo. Runs under the INT OIDC role.
#   push-prod  Re-tag the image produced by `build` and push it to the
#              prod-account (862934447303) ECR repos in every prod region. Runs
#              under the native prod OIDC role (IAM_PROD_us-east-1_ctia) rather
#              than pushing cross-account from the INT role. Release branches
#              only. XDR-59666.
set -Eeuxo pipefail

DEPLOY_MODE="${1:-build}"

if [[ "${GITHUB_EVENT_NAME}" != "push" ]]; then
  echo "./build/build-actions.sh currently supports push deployments only."
  exit 1
fi

if [[ "${GITHUB_REPOSITORY}" != "threatgrid/ctia" ]]; then
  echo "./build/build-actions.sh currently deploys only via the threatgrid/ctia repository."
  exit 1
fi

# the current branch -- note: this doesn't work on PR's but we
# don't deploy on PR's.
# https://stackoverflow.com/a/58035262
CTIA_BRANCH="${GITHUB_REF#refs/heads/}"
# unique identifier for this build
# https://docs.github.com/en/actions/reference/environment-variables#default-environment-variables
CTIA_BUILD_NUMBER="${GITHUB_RUN_NUMBER}"
CTIA_COMMIT="${GITHUB_SHA}"

echo "branch: ${CTIA_BRANCH}"
echo "build number: ${CTIA_BUILD_NUMBER}"
echo "commit: ${CTIA_COMMIT}"

# Image coordinates shared by the build and push-prod modes. build_version is
# derived only from the run number and commit, so it is identical across the two
# workflow steps and the prod push can re-tag the image the build step produced.
build_version="${CTIA_BUILD_NUMBER}-${CTIA_COMMIT:0:8}"
int_registry=372070498991.dkr.ecr.us-east-1.amazonaws.com

function build-and-push-docker-image {
  build_type=$1
  if [ "$build_type" == 'int' ]; then
    repo_prefix='int'
    echo "Building docker image for integration"
  else
    #disable release build until it is ready to go to prod
    repo_prefix='test'
    echo "Building release docker image"
  fi
  docker_registry=$int_registry
  docker_repository=$repo_prefix-docker-build/ctia
  tempdir=$(mktemp -d)
  cp target/ctia.jar "$tempdir/"

  cat <<EOF >"$tempdir"/Dockerfile
FROM artifactory.devhub-cloud.cisco.com/sto-ccc-docker/hardened_alpine:${ALPINE_VERSION}

RUN apk update && \
    apk add --no-cache openjdk21-jre libc6-compat tini && \
    ln -s /usr/bin/java /bin/java && \
    mkdir /ctia

WORKDIR /ctia

ADD ctia.jar /ctia/
RUN chmod 644 /ctia/ctia.jar
USER nobody
ENTRYPOINT ["/sbin/tini", "--"]
EOF

  cd "$tempdir"
  aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin "$docker_registry"
  echo "Login to $ARTIFACTORY_URL"
  echo "$DOCKER_PASS" | docker login --username "$DOCKER_USER" --password-stdin "$ARTIFACTORY_URL"
  docker build -t "$docker_registry/$docker_repository:$build_version" .
  docker push "$docker_registry/$docker_repository:$build_version"
}

# Push the already-built release image to the prod-account ECR repos in every
# prod region. The `build` step built the release image and pushed it to the
# nonprod `test-docker-build/ctia` repo, so it is still present in this runner's
# local docker image cache; here we only re-tag and push it. This runs under the
# prod OIDC role (IAM_PROD_us-east-1_ctia in account 862934447303), so the prod
# push uses the native prod identity instead of a cross-account push from the
# INT role. XDR-59666.
function push-prod-images {
  source_image=$int_registry/test-docker-build/ctia:$build_version
  prod_repository=prod-docker-build/ctia

  prod_nam_registry=862934447303.dkr.ecr.us-east-1.amazonaws.com
  aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin "$prod_nam_registry"
  docker tag "$source_image" "$prod_nam_registry/$prod_repository:$build_version"
  docker push "$prod_nam_registry/$prod_repository:$build_version"

  prod_eu_registry=862934447303.dkr.ecr.eu-west-1.amazonaws.com
  aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin "$prod_eu_registry"
  docker tag "$source_image" "$prod_eu_registry/$prod_repository:$build_version"
  docker push "$prod_eu_registry/$prod_repository:$build_version"

  prod_apjc_registry=862934447303.dkr.ecr.ap-northeast-1.amazonaws.com
  aws ecr get-login-password --region ap-northeast-1 | docker login --username AWS --password-stdin "$prod_apjc_registry"
  docker tag "$source_image" "$prod_apjc_registry/$prod_repository:$build_version"
  docker push "$prod_apjc_registry/$prod_repository:$build_version"
}

function build-and-publish-package {
  PKG_TYPE=$1

  echo "Building new $PKG_TYPE package"

  lein uberjar

  ./scripts/uberjar-trojan-scan.clj

  BUILD_NAME="${CTIA_MAJOR_VERSION}-${PKG_TYPE}-${CTIA_BUILD_NUMBER}-${CTIA_COMMIT:0:8}"
  echo "$BUILD_NAME"
  echo "Build: $BUILD_NAME"
  echo "Commit: ${CTIA_COMMIT}"
  echo "Version: $BUILD_NAME"

  build-and-push-docker-image "$PKG_TYPE"
}

if [[ "${DEPLOY_MODE}" == "push-prod" ]]; then
  # Prod image push (XDR-59666): release branches only. The build step already
  # produced and pushed the release image to nonprod ECR; here we only re-tag
  # and push it to prod under the prod OIDC role.
  if [[ ${CTIA_BRANCH} =~ ^v[0-9]+([.][0-9]+)+$ ]]; then
    echo "OK: release branch ${CTIA_BRANCH} detected; pushing prod images"
    push-prod-images
    exit 0
  else
    echo "push-prod requested on non-release branch ${CTIA_BRANCH}; nothing to push."
    exit 0
  fi
fi

if [[ "${GITHUB_EVENT_NAME}" == "push" ]]; then
  if [[ ${CTIA_BRANCH} == "master" ]]; then
    # non-pr builds on the master branch yield master packages
    echo "OK: master branch detected"
    build-and-publish-package "int"
    exit 0

  elif [[ ${CTIA_BRANCH} =~ ^v[0-9]+([.][0-9]+)+$ ]]; then
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
