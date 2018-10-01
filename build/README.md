# CTIA Package Build

The CTIA package build uses `lein uberjar` to create a new *.jar* package. The generated package is uploaded to a specific S3 bucket and Tenzin automation tools are triggered to deploy it. If the commit happens on the `master` branch, the package is automatically deployed to *INT*. If the commit happens in a branch that follows the semantic versioning (Ex.: v1.0), it will be automatically deployed to *TEST*.

*PROD* deployments are manually triggered on release days or when applying patches and use the packages stored on the *TEST* S3 bucket.
