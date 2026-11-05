# Changelog

## [1.19.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.18.0...v1.19.0) (2026-11-05)

### Features

* audit evidence views, handle broken frames, add alert-quality scores and update the README ([55ab9ae](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/55ab9ae226bf9a14c801d75ce8fa1c0ae7635ef1))

## [1.18.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.17.0...v1.18.0) (2026-11-05)

### Features

* add the Caddy web door, serve the dashboard and keep the MCP endpoint private ([b45df78](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/b45df788e3c1e935d8031fbb5c0657927ab0db46))

## [1.17.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.16.0...v1.17.0) (2026-11-05)

### Features

* show the evidence frames on the approval screen and add the eval runner ([98fe3c8](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/98fe3c8827c0013d0183cf4818f078aa81468102))

### Bug Fixes

* let the eval runner find the clips and the key, and ignore officer reports when scoring ([ec46e07](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/ec46e072c286031f860c619046b4aaf68b251b93))

## [1.16.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.15.0...v1.16.0) (2026-11-04)

### Features

* add the admin pages for sites and villagers and the per-site history filter ([a214e4b](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/a214e4bf9b270acb43cbf77dd429b47d7f614a5c))

## [1.15.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.14.0...v1.15.0) (2026-11-04)

### Features

* add the new analysis page with the boundary picker ([8480e92](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/8480e92b3a68eddd8eedd0a1681617e37843abc5))

## [1.14.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.13.0...v1.14.0) (2026-11-04)

### Features

* add the live analysis page and the officer approvals queue ([4d632fa](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/4d632fa5ce049ea08a6fac3727ee73900b2519c4))

### Bug Fixes

* show a message when sign-in works but the page cannot tell who is signed in ([ac24dcb](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/ac24dcb87765c5614eb73bda4baaacea431e5a48))

## [1.13.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.12.0...v1.13.0) (2026-11-04)

### Features

* add the dashboard foundation with sign-in, the analyses list and three languages ([a61eb02](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/a61eb02b6cf5fa8a576d43b7e2671e0d20142100))
* add the history, analysis detail and live update endpoints for the dashboard ([a34d1fb](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/a34d1fb334e0ad9dd29f8fad42677f00eaecaa57))

### Bug Fixes

* send the template wording when the guard changes an alert's level ([2f5ce8a](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/2f5ce8aa060d37f6b89121e110cf49926ce3a13a))

## [1.12.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.11.0...v1.12.0) (2026-11-04)

### Features

* let admins manage sites and villagers through the API ([58298e3](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/58298e3e504535308bdad7bbe35ffb5eed2a2b42))

### Bug Fixes

* start the worker after a healthy backend and keep passwords out of URLs ([43b7aed](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/43b7aed751ed700ad1ccf6039f0538a4a79755e3))

## [1.11.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.10.0...v1.11.0) (2026-11-04)

### Features

* add the AI service worker that runs the graph from the queues ([5beb1c7](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/5beb1c7628eec8f7573210f5c06af63f7bdfdd45))

## [1.10.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.9.0...v1.10.0) (2026-11-04)

### Features

* add login, uploads and officer approvals to the Spring API ([ff12802](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/ff12802efdbd0265ddd6cf559cafa63253aeaa4a))
* connect Spring to the AI service queues with approvals, timeouts and recovery ([47a9546](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/47a9546ec95f91075307292dc81f355444a938fc))

## [1.9.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.8.0...v1.9.0) (2026-11-04)

### Features

* connect Spring to the AI service queues with approvals, timeouts and recovery ([#20](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/issues/20)) ([862e821](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/862e8210c0f22517f6f6c23b4abfa07d9a9958d1))

## [1.8.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.7.0...v1.8.0) (2026-11-04)

### Features

* add the Python MCP client so the agent graph can call Spring's tools - GM-19 ([#19](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/issues/19)) ([97a1ea0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/97a1ea0ad4be1378c2e63100fa573843b973551e))

## [1.7.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.6.0...v1.7.0) (2026-11-04)

### Features

* send real SMS through Twilio when enabled, with timeouts and failures recorded - GM-18 ([#18](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/issues/18)) ([927ac90](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/927ac905db722e72548c196fdd101697785c8781))

## [1.6.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.5.0...v1.6.0) (2026-11-04)

### Features

* expose the alert tools to the AI service as a key-protected MCP server - GM-17 ([#17](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/issues/17)) ([77b37d1](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/77b37d107a1a361ef89c94973e898a9251f99668))

## [1.5.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.4.0...v1.5.0) (2026-11-04)

### Features

* retry SMS deliveries left pending or failed without ever sending twice - GM-16 ([#16](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/issues/16)) ([ce2eec8](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/ce2eec8dd0f00ba34bca8a074b6e242fc7a30b30))

## [1.4.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.3.0...v1.4.0) (2026-11-04)

### Features

* add the alert dispatch core with idempotent alerts, the policy re-check and shadow mode - GM-14 ([#15](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/issues/15)) ([6507ccc](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/6507ccc656e9c95b2420dca0590497642e474458))

## [1.3.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.2.0...v1.3.0) (2026-11-04)

### Features

* add the PostgreSQL schema as Flyway migration V1 with constraint tests - GM-10 ([#14](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/issues/14)) ([01c5981](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/01c5981fbda577f2a76043c094148f69e27384da))

## [1.2.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.1.0...v1.2.0) (2026-11-04)

### Features

* add the Spring alert policy re-check that reads the shared policy table - GM-11 ([#13](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/issues/13)) ([0408e03](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/0408e03da32ca6f9b4a3b92520f09541853a6d98))

## [1.1.0](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/compare/v1.0.0...v1.1.0) (2026-11-04)

### Features

* add a Phase 0 script that checks the Meta Model API against the code's assumptions - GM-7 ([#11](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/issues/11)) ([6379977](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/63799774788d6d8416fc6e6aeb05c0fd9352fa91))

## 1.0.0 (2026-11-04)

### Features

* add design tokens, light and dark themes and a risk badge - GM-9 ([#8](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/issues/8)) ([860d546](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/860d546d99dda5539a08b546eb755c1691970cfd))
* add deterministic policy guard with template fallback alerts ([1c6687e](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/1c6687e323e274bd39195bfab1370b9ff60c0585))
* add the LangGraph responder-verifier pipeline - GM-8 ([#7](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/issues/7)) ([01fd2ed](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/01fd2ed6df236bbdcdce0b79253f296c7149eddd))
* cache completed SAM results by clip content - GM-5 ([#5](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/issues/5)) ([98dc009](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/98dc009ecff63f7ae22579e878022b8c074c0617))
* make the prototype propose JSON and let the policy guard decide - GM-6 ([#6](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/issues/6)) ([138a5e4](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/138a5e4d582cbabb160ac1044cddf2f6892cda0d))

### Bug Fixes

* add an explicit SAM call timeout and log perception failures ([b32e9ed](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/b32e9ed9b19ff30dc43178060f1bd51a60053e23))
* extend the Claude Code guard to PowerShell and block shell access to secrets ([#9](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/issues/9)) ([7d6954e](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/7d6954e4388bf53a6e4e6e993f14ae1dd26a5dd0))
* pin the changelog preset so semantic-release can write release notes ([#10](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/issues/10)) ([91268d4](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/91268d49107ad3276087ee2f064ab16961433593))
* report SAM perception failures as errors instead of no elephants ([72856e7](https://github.com/apsariUdithara/elephant-early-warning-multi-agent-system-GajaMitra/commit/72856e7904badb409050cf3af9610e5d95123f07))
