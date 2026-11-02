# URL-scheme validation on ingest (XFV-135)

CTIA rejects a write whose URL-typed field carries a scheme other than
`http`/`https` (`javascript:`, `data:`, `vbscript:`, ...). This is a
defense-in-depth backstop against stored XSS: a poisoned URL that reaches a UI
and is rendered as a clickable link fires when clicked. The primary control is
output-encoding at the UI's render sink, owned by the UI team; this gate stops
the value from being stored in the first place.

The pure predicates live in `ctia.domain.url-safety` so they are unit-testable
in isolation; `ctia.flows.crud/url-scheme-check` is the thin flow wrapper that
adds the prev-entity diff, the error map, and the short-circuit. This mirrors
how `tlp-check` / `authorized-*-check` delegate to `ctia.domain.access-control`.

## Which fields are checked

`url-typed-keys` (`:url`, `:source_uri`, `:origin_uri`, `:reason_uri`,
`:identity`) is a hand-curated set of the CTIM keys whose values are URI-typed
and get rendered as hyperlinks downstream. It is a cross-schema curation, not
the key set of any single schema, so it is kept in sync with CTIM by hand: a
future URI-typed field with a new name has to be added here.

Matching is by key name at any nesting depth. That assumes every field with one
of these names is a URI-typed http(s) field; a future free-text field that
happened to reuse one of these names would be wrongly gated and would need
handling here.

Free-text fields and observable IOC values are deliberately *not* in the set:
they legitimately carry malicious URLs as threat intel.

`:identity` is the awkward one. It is URI-typed only in RelatedIdentity; in
`actor.cljc` and `identity_assertion.cljc` it is a nested map. A non-string
`:identity` value (a map) is recursed into, not flagged.

## How a value is normalized before its scheme is read

Browsers ignore assorted characters when resolving a URL, so an attacker can
split or mask a scheme and still have it fire at the render sink. Before reading
the scheme, `unsafe-url-scheme` therefore:

1. HTML-decodes the value (`decode-html-entities`), and
2. strips characters a browser ignores: ASCII C0 controls and space
   (`\x00-\x20`, `\x7f`), Unicode format chars (`\p{Cf}`: ZWSP U+200B, BOM
   U+FEFF, SOFT HYPHEN U+00AD, ...), and separators (`\p{Z}`: NBSP U+00A0, ...).

So `java<TAB>script:`, `&#106;avascript:`, `javascript&colon;`,
`java&#8203;script:` and `&#65279;javascript:` all normalize to
`javascript:` and are caught.

The scheme is lower-cased with `Locale/ROOT`, not the JVM default locale. Under
a Turkish/Azeri default, `str/lower-case` folds an ASCII `I` to a dotless `ı`
("javascrıpt"), which would miss the check. No safe scheme contains an `i`
today, so there is no security impact, but `Locale/ROOT` keeps the two
lower-casings (decoder and scheme extractor) consistent and locale-independent.

## Which HTML entities are decoded, and why only those

`scheme-relevant-named-entities` is a closed set of the named references that
can help reconstruct a scheme:

- `&amp;` -> `&` -- the entity delimiter itself. `&amp;` is the canonical
  encoding of `&`, so `javascript&amp;colon;alert(1)` decodes to
  `javascript&colon;alert(1)` then `javascript:alert(1)`. This is what makes
  the double-encoding case (below) work.
- `&colon;` -> `:` -- the scheme delimiter, the one structural character with a
  named reference.
- `&Tab;` -> `\t` and `&NewLine;` -> `\n` -- whitespace the WHATWG URL parser
  strips from anywhere in a URL, so `java&NewLine;script:` -> `javascript:`.

No HTML5 named reference decodes to a single ASCII *letter* that could spell a
scheme name (the only ASCII-letter-producing entity, `&fjlig;` -> "fj", cannot
form an executable scheme). So an attacker can only hide the `&`/`:` delimiters
or insert tab/newline between letters -- this closed set is all the gate needs
to decode. References that decode to a non-scheme, non-parser-stripped character
(e.g. `&ZeroWidthSpace;` -> U+200B, which the URL parser does *not* strip from a
scheme) cannot reconstruct an executable scheme and are left encoded.

Named references are matched case-insensitively (`Locale/ROOT`). Over-decoding
can only reveal a scheme, never create a false positive on a legitimate http(s)
URL, because the scheme sits at the front and is extracted first -- a decoded
`&amp;`/`&colon;` in a query string cannot change it. Decoding *can* surface a
scheme on a scheme-less value that encoded a colon (a filename
`report&#58;final.pdf` -> `report:final.pdf`, rejected as a non-http(s) scheme);
URI-typed fields are expected to carry absolute http(s) URLs, so that rejection
is acceptable.

## The decode is iterated to a fixed point, under a budget

An inner reference can only become visible after an outer one is decoded:
`javascript&#38;colon;alert(1)` -> `javascript&colon;alert(1)` ->
`javascript:alert(1)`. So decoding loops until the string stops changing. Each
pass either shortens the string (a decoded entity collapses to one character) or
leaves it byte-identical (an unparseable or oversized numeric entity is left
untouched, which is the loop's exit condition), so it always converges.

The loop is capped at a budget of 8 passes. The cap bounds work, and it also
defines the failure mode: a value with more stacked layers than the budget
(9+ nested `&#38;`) is left partially decoded. That fails safe -- a
partially-decoded value presents neither an http(s) nor a dangerous scheme, so
`unsafe-url-scheme` returns nil, and a browser HTML-decodes only one layer per
render, so it never reaches the payload in a single hop either.

## Scheme-less and ambiguous values

A value is scheme-checked only when its prefix matches the RFC 3986 scheme
grammar (`url-scheme-re`: `ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )` then
`:`). Truly scheme-less / relative values (`/a/b`, `example.com/a`) carry no
scheme and pass.

A bare authority like `example.com:8080/path` matches the scheme grammar with
scheme `example.com`, which is not allowlisted, so it is rejected. That is
acceptable: CTIM URI fields carry absolute http(s) URLs, not bare host:port
authorities.

## The `transient:` exemption

Transient references (`transient:0`, `transient:<uuid>`, ...) are CTIA-internal
placeholders callers put in URI-typed fields (e.g. `:source_uri`) to cross-link
entities within one bulk/bundle submission. They are exempt because `transient:`
is not a browser-executable scheme -- even an unresolved `transient:<arbitrary>`
is inert at the render sink. Transient IDs are arbitrary client-chosen strings
(not necessarily UUIDs), and a plain URI field is not a reference field the flow
rewrites, so the exemption cannot rest on the value being rewritten later. This
check also runs *before* transient IDs are resolved (see
`ctia.flows.crud/validate-entities` -> `create-ids-from-transient`).

## On update, only newly-introduced values are checked

Like `authorized-groups-check` / `authorized-users-check`, `url-scheme-check`
gates only the values the caller introduces relative to the stored
`prev-entity`; a disallowed value byte-identical to one already stored is not
re-flagged. On create, `prev-entity` is nil, so every value is checked.

This matters on update/patch. `POST /incident/:id/status` sends only
`{:status ...}`, but `patch-entities` deep-merges the whole prev-entity
(including its `:source_uri`) before this runs. Re-validating the full document
would 400 a status update that never mentioned a URI carrying a value that
predates the gate.

The diff is value-level, not occurrence- or path-level (`set/difference` over
`{:field :scheme :value}`): no dangerous scheme+value pair absent from the
stored entity can be introduced, but re-writing another copy of a pair already
stored is not blocked -- including a copy that lands under the same-named
url-typed key at a different path (a top-level `:url` value also placed in an
`external_references[].url`, which shares the `{:field :url ...}` diff element).

That is an accepted tradeoff, not an oversight. The pair is already stored and
therefore already renders at a url-typed sink, so the payload already fires; an
additional byte-identical sink of an already-renderable value grants no new
capability, and cannot escalate between url-typed keys since they are all
render sinks by definition. A path-aware / multiset diff would add complexity
for no change to the invariant that matters: no dangerous value renders that was
not already renderable. A pre-gate value persisting until a data migration
cleans it up is the same accepted tradeoff the two `authorized_*` checks make.

The caller's `:login`/`:groups` (threaded via `ident-map`, as the `authorized_*`
checks do) are carried into the error map so the `:warn` log lets operators
attribute a burst of rejected `javascript:` writes to a caller.

## Scanning nested collections

`collect-unsafe-url-fields` walks maps/vectors/sets and, under a url-typed key,
uses `tree-seq` over sequential/set nesting so a string value is found at any
list depth (`{:url [["javascript:.."]]}` included). Maps are deliberately not a
`tree-seq` branch: a nested map under a url-typed key (e.g. an Identity map
under `:identity`) is handled by the outer recursion, not flagged as a string
there. A dangerous scheme nested under a *non*-url-typed key (`{:url {:inner
"js:.."}}`) is intentionally not flagged, since CTIM URI fields are string-typed.
