#!/bin/bash
# # GitHub Actions deployment
#
# Fails if run in non-deployment situations.
#
# Assumes awscli is set up with correct credentials for the current user.
set -Eeuxo pipefail

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

function build-and-push-docker-image {
  build_type=$1
  if [ "$build_type" == 'int' ];
  then
    repo_prefix='int'
    echo "Building docker image for integration"
  else
    #disable release build until it is ready to go to prod
    repo_prefix='test'
    echo "Building release docker image"
  fi
  build_version="${CTIA_BUILD_NUMBER}-${CTIA_COMMIT:0:8}"
  docker_registry=372070498991.dkr.ecr.us-east-1.amazonaws.com
  docker_nomad_repository=$repo_prefix-docker-build/ctia
  docker_eks_repository=ctr-$repo_prefix-eks/$repo_prefix-iroh
  tempdir=$(mktemp -d)
  cp target/ctia.jar "$tempdir/"
  cat <<EOF >"$tempdir"/entrypoint.sh
#!/bin/sh
set -x
if [ -n "\${INTERNAL_CA_PATH+set}" ];
then
  cp \$INTERNAL_CA_PATH/* /usr/local/share/ca-certificates/
  update-ca-certificates
fi
exec runuser -u nobody -- "\${@}"
EOF

  cat <<EOF >"$tempdir"/Dockerfile
FROM 372070498991.dkr.ecr.us-east-1.amazonaws.com/$repo_prefix-docker-build/cloud9_alpine_java:58-4cba19e3
USER root
RUN apk update
RUN apk add runuser
RUN mkdir /ctia
WORKDIR /ctia

ADD ctia.jar /ctia/
ADD 'https://dtdg.co/latest-java-tracer' /ctia/dd-java-agent.jar
RUN chmod 644 /ctia/ctia.jar
RUN chmod 644 /ctia/dd-java-agent.jar
ADD entrypoint.sh /
RUN chmod 755 /entrypoint.sh
ENTRYPOINT ["/sbin/tini", "--", "/entrypoint.sh"]
EOF

  cd "$tempdir"
  aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin "$docker_registry"
  docker build -t "$docker_registry/$docker_nomad_repository:$build_version" .
  docker push "$docker_registry/$docker_nomad_repository:$build_version"
  docker tag "$docker_registry/$docker_nomad_repository:$build_version" "$docker_registry/$docker_eks_repository:ctia-$build_version"
  docker push "$docker_registry/$docker_eks_repository:ctia-$build_version"

  if [ "$build_type" == 'rel' ]
  then
    prod_nomad_repository=prod-docker-build/ctia
    prod_nam_registry=862934447303.dkr.ecr.us-east-1.amazonaws.com
    aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin "$prod_nam_registry"
    docker tag "$docker_registry/$docker_nomad_repository:$build_version" "$prod_nam_registry/$prod_nomad_repository:$build_version"
    docker push "$prod_nam_registry/$prod_nomad_repository:$build_version"

    prod_eu_registry=862934447303.dkr.ecr.eu-west-1.amazonaws.com
    aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin "$prod_eu_registry"
    docker tag "$docker_registry/$docker_nomad_repository:$build_version" "$prod_eu_registry/$prod_nomad_repository:$build_version"
    docker push "$prod_eu_registry/$prod_nomad_repository:$build_version"

    prod_apjc_registry=862934447303.dkr.ecr.ap-northeast-1.amazonaws.com
    aws ecr get-login-password --region ap-northeast-1 | docker login --username AWS --password-stdin "$prod_apjc_registry"
    docker tag "$docker_registry/$docker_nomad_repository:$build_version" "$prod_apjc_registry/$prod_nomad_repository:$build_version"
    docker push "$prod_apjc_registry/$prod_nomad_repository:$build_version"
  fi
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

  # Upload the jar directly to the artifacts S3 bucket
  if [ "${PKG_TYPE}" == "int" ]; then
    ARTIFACTS_BUCKET="372070498991-us-east-1-int-saltstack"
  elif [ "${PKG_TYPE}" == "rel" ]; then
    ARTIFACTS_BUCKET="372070498991-us-east-1-test-saltstack"
  else
    echo "Bad PKG_TYPE: ${PKG_TYPE}"
    exit 1
  fi

  ARTIFACT_NAME="${CTIA_BUILD_NUMBER}-${CTIA_COMMIT:0:8}.jar"
  aws s3 cp ./target/ctia.jar s3://${ARTIFACTS_BUCKET}/artifacts/ctia/"${ARTIFACT_NAME}" --sse aws:kms --sse-kms-key-id alias/kms-s3
  build-and-push-docker-image "$PKG_TYPE"
}

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
