# Riemann

[Riemann](http://riemann.io) is a monitoring service for distributed systems.

We use Riemann in two places:

1. Via `ctia.metrics.riemann.*` properties
  - implemented in `ctia.lib.metrics.riemann`
  - probably only useful during development, very resource-intensive
  - should probably be considered deprecated, unclear if it still works
    - was disabled in this repository's `resources/ctia-default.properties` in 2016
2. Via `ctia.log.riemann.*` properties
  - implemented in `ctia.lib.riemann`
  - includes a per-request response-time logger with secrets obfuscation

Since the `ctia.log.riemann.*` properties is used in practice,
it is described below.

## ctia.log.riemann.*

Here's a typical configuration to enable per-request logging via Riemann.

```
ctia.log.riemann.enabled=true
ctia.log.riemann.host=127.0.0.1
ctia.log.riemann.port=5555
ctia.log.riemann.interval-in-ms=1000 # every second
ctia.log.riemann.batch-size=10
ctia.log.riemann.service-prefix=Dev CTIA
```

To enable per-request metrics bind `ctia.log.riemann.enabled` to `true`.
The other fields are self explanatory, except the service prefix.

Riemann includes a `service` field (see [Riemann concepts](http://riemann.io/concepts.html))
that includes a description of the metric so they can be easily identified/aggregated.

The `ctia.log.riemann.service-prefix` property is a string that should identify
the current CTIA deployment.

For example, the per-request metric is named `"API response time ms"`, and with the
service-prefix property set to `"Production CTIA"`, the `service` field becomes
`"Production CTIA API response time ms"`.

## Riemann local development

### Live reloading

The Riemann dev configuration can be live-reloaded from `containers/dev/riemann.config`,
using the some-what delicate instructions contained therein.

### Dashboard

The dev docker container boots the Riemann dashboard at [](http://localhost:4567).

As a quick primer, hold command (or control) and click on the big `Riemann` title.
Press `e`, then select `Log`. The query is a boolean statement the filters out
false matches. So use `true` as the query, and click `Apply`.

Now using the CTIA API should trigger events on the dashboard. You can also see
events scroll by in the docker-compose log.
