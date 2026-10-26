# About this release

## New and Noteworthy

Version 2.2.0 is the first release of this project compatible with Operaton 2.2.x. It is built on Spring Boot 4.1.

* **Keycloak logout fixed**: the logout overrides of the JWT extension and the SSO example did not recognize the current Operaton navigation markup, so clicking *Logout* left the Keycloak session active and a page refresh logged the user in again. Logout now ends the Keycloak session (#83, #110).
* **Test suite migrated to JUnit 5 and AssertJ**: the extension tests now use Operaton's `ProcessEngineExtension` and a single shared Keycloak container. 12 test classes, including all caching tests, had never run because their names did not match the Surefire patterns; they run now, raising the executed tests from 149 to 179 (#101).

{{changelogContributors}}

## Changelog

{{changelogChanges}}
