x.x.x
- deprecated URI generator in favor of https://github.com/cognizone/semanticz-irigenerator

## [0.8.0] - 2025-07-09

### Changed
- Upgraded from Springboot 2.7.18 to Springboot 3.5.3
- Upgraded from Java 11 to Java 17
- Upgraded gradle to 7.6.4 to be compatible with Java 17
- Upgraded from Jena 4 to Jena 5
- Upgraded rest of libs to match the new java/spring versions

- Moved the index package from service to async-indexing
- Removed security and security2 modules, also asquareroot/security package because security should be managed by https://github.com/cognizone/security