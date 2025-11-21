# OpenSearch Migration Guide for CTIA

This guide provides step-by-step instructions for migrating CTIA from Elasticsearch 7.x to OpenSearch 2.x/3.x using Ductile 0.6.0.

## Executive Summary

**Good News**: Migration requires minimal configuration changes! Ductile 0.6.0 introduces transparent OpenSearch support with automatic ILM→ISM policy transformation.

### What's Required

1. **Update Ductile dependency** to 0.6.0-SNAPSHOT (or 0.6.0 when released)
2. **Add `engine` configuration** to properties or environment variables
3. **Update authentication** credentials (OpenSearch uses different defaults)
4. **Run tests** to verify compatibility

### What's NOT Required

- ❌ No changes to business logic
- ❌ No changes to CRUD operations
- ❌ No changes to query DSL
- ❌ No manual policy migration (automatic transformation!)

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Configuration Changes](#configuration-changes)
3. [Testing Strategy](#testing-strategy)
4. [Deployment](#deployment)
5. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### 1. OpenSearch Environment

Ensure your OpenSearch cluster is running and accessible:

- **Version**: OpenSearch 2.19.0 (recommended) or 3.1.0
- **Network**: Accessible from your application
- **Authentication**: Admin credentials configured

### 2. Ductile Version

The ductile dependency has been updated to 0.6.0-SNAPSHOT in `project.clj`:

```clojure
[threatgrid/ductile "0.6.0-SNAPSHOT"]
```

---

## Configuration Changes

### Option 1: Properties File

Update `resources/ctia-default.properties` (or your custom properties file):

```properties
# For Elasticsearch (existing configuration)
ctia.store.es.default.host=127.0.0.1
ctia.store.es.default.port=9207
ctia.store.es.default.version=7
ctia.store.es.default.engine=elasticsearch
ctia.store.es.default.auth.type=basic-auth
ctia.store.es.default.auth.params.user=elastic
ctia.store.es.default.auth.params.pwd=ductile

# For OpenSearch (new configuration)
ctia.store.es.default.host=opensearch-host
ctia.store.es.default.port=9200
ctia.store.es.default.version=2
ctia.store.es.default.engine=opensearch
ctia.store.es.default.auth.type=basic-auth
ctia.store.es.default.auth.params.user=admin
ctia.store.es.default.auth.params.pwd=YourPassword
```

### Option 2: Environment Variables

Set environment variables (takes precedence over properties file):

```bash
# For OpenSearch
export CTIA_STORE_ES_DEFAULT_HOST=opensearch-host
export CTIA_STORE_ES_DEFAULT_PORT=9200
export CTIA_STORE_ES_DEFAULT_VERSION=2
export CTIA_STORE_ES_DEFAULT_ENGINE=opensearch
export CTIA_STORE_ES_DEFAULT_AUTH_TYPE=basic-auth
export CTIA_STORE_ES_DEFAULT_AUTH_PARAMS_USER=admin
export CTIA_STORE_ES_DEFAULT_AUTH_PARAMS_PWD=YourPassword
```

### Configuration Details

| Property | Elasticsearch | OpenSearch |
|----------|--------------|------------|
| `engine` | `elasticsearch` | `opensearch` |
| `version` | `7` | `2` or `3` |
| `auth.params.user` | `elastic` | `admin` |
| `auth.params.pwd` | Your ES password | Your OS password |

**Note**: The `:engine` parameter defaults to `:elasticsearch` if not specified, ensuring backward compatibility.

---

## Testing Strategy

### Phase 1: Unit Tests (No Infrastructure)

```bash
# Run unit tests (no ES/OpenSearch required)
lein test ctia.stores.es.crud-test
lein test ctia.stores.es.init-test
lein test ctia.stores.es.query-test
```

### Phase 2: Local Docker Testing

**1. Start Docker containers (from ductile project):**

```bash
cd ../ductile/containers
docker-compose up -d

# Verify containers are healthy
docker-compose ps
curl -u elastic:ductile http://localhost:9207/_cluster/health
curl -u admin:admin http://localhost:9202/_cluster/health  # OpenSearch 2
curl -u admin:admin http://localhost:9203/_cluster/health  # OpenSearch 3
```

**2. Test with Elasticsearch (default):**

```bash
cd ../ctia
lein test :integration
```

**3. Test with OpenSearch:**

CTIA provides test fixtures for OpenSearch. To create OpenSearch-specific tests:

```clojure
(ns your-test-namespace
  (:require [clojure.test :refer :all]
            [ctia.test-helpers.es :as es-helpers]))

;; For OpenSearch 2
(use-fixtures :once es-helpers/fixture-properties:opensearch-store)

;; For OpenSearch 3
(use-fixtures :once es-helpers/fixture-properties:opensearch3-store)

;; Your tests here - they will run against OpenSearch
(deftest your-test
  ...)
```

The fixtures derive from the existing Elasticsearch fixture and only override:
- **Port**: 9202 (OS2) or 9203 (OS3)
- **Version**: 2 or 3
- **Engine**: `opensearch`
- **Auth**: `admin/admin` instead of `elastic/ductile`

All other configuration (indices, settings, etc.) is inherited from `fixture-properties:es-store`.

### Phase 3: Staging Environment

1. Deploy to staging with OpenSearch configuration
2. Run smoke tests
3. Monitor for errors/warnings
4. Verify ILM→ISM policy transformation
5. Check data integrity

### Phase 4: Production Rollout

1. **Blue-Green Deployment**: Set up parallel OpenSearch cluster
2. **Dual Write**: Write to both ES and OpenSearch
3. **Verify**: Compare data consistency
4. **Switch Read**: Gradually shift read traffic
5. **Switch Write**: Fully migrate to OpenSearch
6. **Decommission**: Remove Elasticsearch cluster

---

## Deployment

### Development Environment

Use Docker containers (see Phase 2 above).

### Staging/Production Environment

**1. Update Configuration:**

Deploy with OpenSearch-specific properties or environment variables.

**2. Deploy Application:**

```bash
# Build
lein uberjar

# Deploy with OpenSearch configuration
java -jar target/uberjar/ctia.jar \
  -Dctia.store.es.default.engine=opensearch \
  -Dctia.store.es.default.host=opensearch-prod.example.com \
  -Dctia.store.es.default.port=9200 \
  -Dctia.store.es.default.version=2
```

**3. Verify Deployment:**

```bash
# Check application health
curl http://localhost:3000/health

# Verify OpenSearch connectivity
# (Check application logs for successful connection)
```

---

## Troubleshooting

### Connection Issues

**Problem**: Cannot connect to OpenSearch

```
Connection refused (Connection refused)
```

**Solution**:

1. Verify OpenSearch is running: `curl http://localhost:9200`
2. Check firewall rules
3. Verify host and port configuration
4. Check OpenSearch logs: `docker logs opensearch2` (if using Docker)

### Authentication Failures

**Problem**: Unauthorized errors

```
Unauthorized ES Request
```

**Solution**:

1. **OpenSearch uses different defaults**:
   - Username: `admin` (not `elastic`)
   - Password: Check `OPENSEARCH_INITIAL_ADMIN_PASSWORD` environment variable
2. **Update configuration**:
   ```properties
   ctia.store.es.default.auth.params.user=admin
   ctia.store.es.default.auth.params.pwd=YourPassword
   ```

### Engine Detection Issues

**Problem**: Getting Elasticsearch errors on OpenSearch

```
Cannot create policy for Elasticsearch version < 7
```

**Solution**:

1. **Check engine configuration**: Ensure `engine=opensearch` is set
2. **Verify Ductile version**: Must be 0.6.0+
3. **Check logs**: Look for "ESConn" initialization logs showing correct engine

### Policy Creation Failures

**Problem**: ILM policy fails on OpenSearch

**Solution**:

1. Verify `:engine :opensearch` is configured
2. Check OpenSearch ISM plugin is installed:
   ```bash
   curl http://localhost:9200/_cat/plugins
   ```

### Performance Issues

**Problem**: Slower query performance on OpenSearch

**Solution**:

1. **Index settings**: Verify `refresh_interval`, `number_of_shards`
2. **Connection pool**: Check `timeout` settings
3. **Query patterns**: Some queries may need optimization
4. **Monitoring**: Enable OpenSearch performance analyzer

---

## Rollback Plan

### Quick Rollback (< 1 hour)

If issues are detected immediately:

```properties
# Revert to Elasticsearch configuration
ctia.store.es.default.engine=elasticsearch
ctia.store.es.default.host=elasticsearch-host
ctia.store.es.default.port=9200
ctia.store.es.default.version=7
```

Redeploy application.

### Full Rollback (< 4 hours)

If issues are detected after deployment:

1. **Restore configuration** to Elasticsearch
2. **Redeploy application** with old config
3. **Verify connectivity** to Elasticsearch
4. **Monitor** for normal operation
5. **Investigate** OpenSearch issues offline

---

## Validation Checklist

### Pre-Migration

- [x] Ductile 0.6.0-SNAPSHOT dependency updated in `project.clj`
- [ ] OpenSearch cluster deployed and accessible
- [ ] Admin credentials configured
- [ ] Backup of Elasticsearch data created
- [ ] Rollback plan documented

### Post-Migration

- [ ] Application connects to OpenSearch successfully
- [ ] Indices created with correct mappings
- [ ] ILM policies transformed to ISM policies
- [ ] Data streams working (if used)
- [ ] CRUD operations functioning
- [ ] Query performance acceptable
- [ ] Monitoring and alerting configured
- [ ] Documentation updated

---

## Configuration Examples

### Development (Docker)

```properties
ctia.store.es.default.host=127.0.0.1
ctia.store.es.default.port=9202
ctia.store.es.default.version=2
ctia.store.es.default.engine=opensearch
ctia.store.es.default.auth.type=basic-auth
ctia.store.es.default.auth.params.user=admin
ctia.store.es.default.auth.params.pwd=admin
```

### Staging

```properties
ctia.store.es.default.host=opensearch-staging.example.com
ctia.store.es.default.port=9200
ctia.store.es.default.version=2
ctia.store.es.default.engine=opensearch
ctia.store.es.default.protocol=https
ctia.store.es.default.timeout=60000
ctia.store.es.default.auth.type=basic-auth
ctia.store.es.default.auth.params.user=admin
ctia.store.es.default.auth.params.pwd=${OPENSEARCH_PASSWORD}
```

### Production

```properties
ctia.store.es.default.host=opensearch-prod.example.com
ctia.store.es.default.port=443
ctia.store.es.default.version=2
ctia.store.es.default.engine=opensearch
ctia.store.es.default.protocol=https
ctia.store.es.default.timeout=60000
ctia.store.es.default.auth.type=basic-auth
ctia.store.es.default.auth.params.user=admin
ctia.store.es.default.auth.params.pwd=${OPENSEARCH_PASSWORD}
```

---

## FAQ

### Q: Do I need to migrate my existing data?

**A**: Yes, you'll need to reindex data from Elasticsearch to OpenSearch. Options:
- Snapshot/Restore (recommended for large datasets)
- Reindex API
- Logstash
- Custom migration scripts

### Q: Will my queries work differently?

**A**: No changes needed for most queries. OpenSearch is API-compatible with Elasticsearch 7.x for the majority of operations.

### Q: What about ILM policies?

**A**: Ductile automatically transforms ILM policies to ISM format. No manual conversion needed!

### Q: Can I run both Elasticsearch and OpenSearch simultaneously?

**A**: Yes! You can maintain connections to both engines during migration by configuring different store endpoints.

### Q: What if something goes wrong?

**A**: Follow the rollback plan above. The quickest path is to revert the `engine` configuration to `elasticsearch` and redeploy.

---

## Timeline Estimate

| Phase | Duration | Description |
|-------|----------|-------------|
| Configuration Update | 1 hour | Update properties/env vars |
| Local Testing | 2 hours | Test with Docker containers |
| Staging Deployment | 2 hours | Deploy to staging |
| Staging Validation | 8 hours | Monitor and validate |
| Production Deployment | 2 hours | Deploy to production |
| Production Monitoring | 24 hours | Monitor for issues |

**Total Estimated Time**: 1-2 days for complete migration

---

## Support and Resources

### Ductile Documentation

- Main README: `../ductile/README.md`
- Lifecycle Module: `../ductile/src/ductile/lifecycle.clj`
- Features Module: `../ductile/src/ductile/features.clj`

### OpenSearch Documentation

- [OpenSearch Documentation](https://opensearch.org/docs/latest/)
- [ISM Documentation](https://opensearch.org/docs/latest/im-plugin/ism/index/)
- [API Reference](https://opensearch.org/docs/latest/api-reference/)

---

## Contact

For questions or issues during migration:

1. **Check** this guide and Ductile README.md
2. **Review** Ductile test cases for examples
3. **Test** with Docker containers before production
4. **Document** any issues encountered for the team

Happy migrating! 🚀
