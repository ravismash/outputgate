# outputgate

outputgate is an open-source Java library that lets a team write JUnit tests for LLM features
using deterministic checks that fail the build. A contract composes gates (JSON schema,
strict type binding, cross-field arithmetic, source-span provenance, regex, cost and
latency ceilings); an output either passes every gate or is rejected with every violation
listed. No gate calls a model.

Status: pre-release scaffold. Nothing is implemented yet.

License: MIT.
