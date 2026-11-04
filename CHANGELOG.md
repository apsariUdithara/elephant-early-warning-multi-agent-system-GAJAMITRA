# Changelog

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
